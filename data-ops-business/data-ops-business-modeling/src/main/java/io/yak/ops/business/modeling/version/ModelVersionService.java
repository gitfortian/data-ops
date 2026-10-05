package io.yak.ops.business.modeling.version;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.modeling.api.ModelingStructureApi;
import io.yak.ops.business.modeling.domain.Model;
import io.yak.ops.business.modeling.domain.ModelStatus;
import io.yak.ops.business.modeling.domain.ModelVersion;
import io.yak.ops.business.modeling.domain.ModelVersionSummary;
import io.yak.ops.business.modeling.repository.ModelRepository;
import io.yak.ops.business.modeling.repository.ModelVersionRepository;
import io.yak.ops.business.modeling.structure.ModelStructureService;
import io.yak.ops.business.modeling.structure.StructureView;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Model version management: publish immutable snapshots and rollback.
 *
 * <p>Publish: reads the current structure, serialises it to JSON, computes a
 * SHA-256 checksum, and stores an immutable version row. Idempotent — if the
 * checksum matches the latest version, no new row is created.
 *
 * <p>Rollback: restores a historical version's structure as the current draft
 * and creates a NEW version entry (version chain grows linearly).
 */
@Service
public class ModelVersionService {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final ModelRepository modelRepository;
  private final ModelVersionRepository versionRepository;
  private final ModelStructureService structureService;

  @Autowired
  public ModelVersionService(
      ModelRepository modelRepository,
      ModelVersionRepository versionRepository,
      ModelStructureService structureService) {
    this.modelRepository = modelRepository;
    this.versionRepository = versionRepository;
    this.structureService = structureService;
  }

  /**
   * Publishes the current structure as a new immutable version.
   *
   * <p>If the structure has not changed since the latest version (same checksum),
   * the existing version is returned without creating a duplicate.
   */
  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public PublishResult publish(Long modelId, String operator) {
    Model model = requireLiveModelForUpdate(modelId);
    return publishLocked(model, structureService.getForUpdate(modelId), operator);
  }

  /** Publishes only when the current structure still matches the snapshot approved by the user. */
  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public PublishResult publishApproved(Long modelId, String expectedFingerprint, String operator) {
    Model model = requireLiveModelForUpdate(modelId);
    StructureView structure = structureService.getForUpdate(modelId);
    if (expectedFingerprint == null || !expectedFingerprint.equals(fingerprint(structure))) {
      throw new IllegalStateException("模型结构已偏离送审依据，请撤销后重新提交");
    }
    return publishLocked(model, structure, operator);
  }

  /** Content digest used to bind a publish approval to the exact semantic structure. */
  @Transactional(
      transactionManager = "yakBusinessTransactionManager",
      readOnly = true,
      rollbackFor = Exception.class)
  public String structureFingerprint(Long modelId) {
    return fingerprint(structureService.get(modelId));
  }

  private PublishResult publishLocked(Model model, StructureView structure, String operator) {
    Long modelId = model.id();

    String structureJson = serialiseStructure(structure);
    // 语义投影（去行主键/瞬态字段）做内容等值：全量替换保存会重建列行并换自增 id，
    // 若把 id 计入内容指纹，回滚后原样再发布会被误判“有变化”而伪追加版本。
    String semanticJson = serialiseStructure(semanticView(structure));
    String checksum = sha256(semanticJson);
    int columnCount = structure.columns() == null ? 0 : structure.columns().size();

    // Idempotent: semantic equality with the latest version (不依赖存量行旧口径 checksum)
    ModelVersion latest = versionRepository.findLatestByModelId(modelId).orElse(null);
    if (latest != null && semanticJson.equals(
        serialiseStructure(semanticView(deserialiseStructure(latest.structureJson()))))) {
      if (!java.util.Objects.equals(model.publishedVersionId(), latest.id())) {
        modelRepository.updatePublishState(
            modelId, ModelStatus.PUBLISHED.name(), latest.id(), latest.versionNo(), operator);
      }
      return new PublishResult(latest, false);
    }

    int nextVersionNo = versionRepository.nextVersionNo(modelId);
    ModelVersion version = versionRepository.insert(
        modelId, nextVersionNo, structureJson, serialiseMeta(model),
        columnCount, checksum, operator);

    modelRepository.updatePublishState(
        modelId, ModelStatus.PUBLISHED.name(), version.id(), nextVersionNo, operator);

    return new PublishResult(version, true);
  }

  /** Lists all versions for a model (newest first, lightweight). */
  public List<ModelVersionSummary> listVersions(Long modelId) {
    requireLiveModel(modelId);
    return versionRepository.listByModelId(modelId);
  }

  /** 当前活表结构（草稿）——仅供无快照可读的存量兜底路径复用。 */
  public StructureView liveStructure(Long modelId) {
    return structureService.get(modelId);
  }

  /** Returns the full version detail including structure snapshot. */
  public VersionDetailView getVersionDetail(Long modelId, int versionNo) {
    requireLiveModel(modelId);
    ModelVersion version = versionRepository.findByVersionNo(modelId, versionNo)
        .orElseThrow(() -> new IllegalArgumentException(
            "版本不存在：modelId=" + modelId + ", versionNo=" + versionNo));
    StructureView structure = deserialiseStructure(version.structureJson());
    return new VersionDetailView(version, structure, parseMeta(version.metaJson()));
  }

  /**
   * 回滚第一步（两步语义）：用目标版本内容覆盖当前草稿并置回 DRAFT。
   *
   * <p>不追加版本、不移动发布指针——消费方（血缘/派生/DDL）继续读旧发布版，
   * 必须再次「发布」才生效；需要直接生效时由前端串调 rollback→publish（回滚并发布）。
   */
  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public StructureView rollback(Long modelId, int versionNo, String operator) {
    requireLiveModel(modelId);
    ModelVersion target = versionRepository.findByVersionNo(modelId, versionNo)
        .orElseThrow(() -> new IllegalArgumentException(
            "回滚目标版本不存在：modelId=" + modelId + ", versionNo=" + versionNo));

    StructureView snapshot = deserialiseStructure(target.structureJson());
    structureService.save(modelId, toSaveRequest(snapshot), operator);
    return structureService.get(modelId);
  }

  // ---- internal helpers ----

  private Model requireLiveModel(Long modelId) {
    return modelRepository.findById(modelId)
        .orElseThrow(() -> new IllegalArgumentException("模型不存在或已删除：" + modelId));
  }

  private Model requireLiveModelForUpdate(Long modelId) {
    return modelRepository.findByIdForUpdate(modelId)
        .orElseThrow(() -> new IllegalArgumentException("模型不存在或已删除：" + modelId));
  }

  private static String fingerprint(StructureView structure) {
    return sha256(serialise(semanticView(structure)));
  }

  private static String serialise(StructureView structure) {
    try {
      return MAPPER.writeValueAsString(structure);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("结构序列化失败", e);
    }
  }

  private String serialiseStructure(StructureView structure) {
    try {
      return MAPPER.writeValueAsString(structure);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("结构序列化失败", e);
    }
  }

  private StructureView deserialiseStructure(String json) {
    try {
      return MAPPER.readValue(json, StructureView.class);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("版本快照反序列化失败", e);
    }
  }

  private ModelingStructureApi.SaveStructureRequest toSaveRequest(StructureView snapshot) {
    List<ModelingStructureApi.ColumnInput> columns = snapshot.columns().stream()
        .map(col -> new ModelingStructureApi.ColumnInput(
            col.columnName(),
            col.dataType(),
            col.length(),
            col.scale(),
            col.nullable(),
            col.defaultValue(),
            col.comment(),
            col.businessDescription(),
            col.stdTypeId(),
            col.stdNamingId(),
            col.stdCodeSetCode(),
            col.stdUnitId(),
            col.stdCaliberId(),
            col.stdSecurityId(),
            col.stdFieldId(),
            col.fieldRole(),
            col.aggregateFunc(),
            col.transformExpr()))
        .toList();

    List<ModelingStructureApi.IndexInput> indexes = snapshot.indexes() == null
        ? List.of()
        : snapshot.indexes().stream()
            .map(idx -> new ModelingStructureApi.IndexInput(
                idx.indexName(),
                idx.uniqueIndex(),
                idx.indexType(),
                idx.columns()))
            .toList();

    ModelingStructureApi.PartitionInput partitionInput = null;
    if (snapshot.partition() != null && snapshot.partition().type() != null) {
      partitionInput = new ModelingStructureApi.PartitionInput(
          snapshot.partition().type(),
          snapshot.partition().columns(),
          snapshot.partition().expression());
    }

    return new ModelingStructureApi.SaveStructureRequest(
        snapshot.tableName(),
        snapshot.tableComment(),
        columns,
        snapshot.primaryKey(),
        indexes,
        partitionInput,
        snapshot.tableProperties());
  }

  private static String sha256(String input) {
    return io.yak.ops.common.version.VersionDigests.sha256Hex(input);
  }

  /** 内容等值的语义视图：剔除行主键 id、modelId 与草稿 status 等非内容字段。 */
  static StructureView semanticView(StructureView view) {
    return new StructureView(
        null,
        view.modelCode(),
        view.modelName(),
        view.dialect(),
        null,
        view.modelDescription(),
        view.tableName(),
        view.tableComment(),
        view.columns() == null ? List.of() : view.columns().stream()
            .map(c -> new StructureView.ColumnView(null, c.columnName(), c.dataType(),
                c.length(), c.scale(), c.nullable(), c.defaultValue(), c.comment(),
                c.businessDescription(), c.sortOrder(), c.stdTypeId(), c.stdNamingId(),
                c.stdCodeSetCode(), c.stdUnitId(), c.stdCaliberId(), c.stdSecurityId(),
                c.stdFieldId(), c.fieldRole(), c.aggregateFunc(), c.transformExpr()))
            .toList(),
        view.primaryKey(),
        view.indexes() == null ? List.of() : view.indexes().stream()
            .map(i -> new StructureView.IndexView(null, i.indexName(), i.uniqueIndex(),
                i.indexType(), i.columns()))
            .toList(),
        view.partition(),
        view.tableProperties());
  }

  /** 元数据快照：发布当时模型的名称/描述/分层/业务域/方言（可空值逐个 put，不整体 Map.of）。 */
  private String serialiseMeta(Model model) {
    java.util.Map<String, Object> meta = new java.util.LinkedHashMap<>();
    meta.put("code", model.code());
    meta.put("name", model.name());
    meta.put("description", model.description());
    meta.put("layerCode", model.layerCode());
    meta.put("dialect", model.dialect() == null ? null : model.dialect().name());
    meta.put("domainId", model.domainId());
    try {
      return MAPPER.writeValueAsString(meta);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("模型元数据序列化失败", e);
    }
  }

  private java.util.Map<String, Object> parseMeta(String metaJson) {
    if (metaJson == null || metaJson.isBlank()) {
      return java.util.Map.of();
    }
    try {
      return MAPPER.readValue(metaJson,
          MAPPER.getTypeFactory().constructMapType(java.util.LinkedHashMap.class,
              String.class, Object.class));
    } catch (JsonProcessingException e) {
      return java.util.Map.of();
    }
  }

  /** Result of a publish operation. */
  public record PublishResult(ModelVersion version, boolean created) {}

  /** Full version detail including the structure snapshot and metadata snapshot. */
  public record VersionDetailView(
      ModelVersion version, StructureView structure, java.util.Map<String, Object> meta) {}
}
