package io.yak.ops.business.modeling.derive;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.modeling.domain.Model;
import io.yak.ops.business.modeling.domain.ModelStatus;
import io.yak.ops.business.modeling.exception.ModelingException;
import io.yak.ops.business.modeling.repository.ModelRepository;
import io.yak.ops.business.modeling.repository.ModelVersionRepository;
import io.yak.ops.business.modeling.structure.ModelStructureService;
import io.yak.ops.business.modeling.structure.StructureFingerprint;
import io.yak.ops.business.modeling.structure.StructureView;
import io.yak.ops.common.enums.modeling.ModelingErrorCode;
import io.yak.ops.common.version.VersionDigests;
import io.yak.ops.core.project.CurrentProject;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Modeling owns the DWS aggregation grain. A source model is a pinned immutable
 * ModelVersion, not a mutable model ID. Design save is NEVER SQL execution.
 */
@Component
public class DwsGrainDesignService {
  private static final Set<String> PERIODS = Set.of("1h", "1d", "1w", "1m", "ALL");
  private static final Set<String> FUNCTIONS =
      Set.of("SUM", "COUNT", "COUNT_DISTINCT", "MIN", "MAX", "AVG");
  private static final Pattern SHA256 = Pattern.compile("^[a-f0-9]{64}$");

  public record Measure(String column, String function) {}
  public record Request(
      String statPeriod, String timeZone, String businessTimeColumn,
      String periodBucketColumn, List<String> dimensions, List<Measure> measures,
      List<Long> upstreamModelIds, String expectedStructureFingerprint) {}
  public record UpstreamVersion(
      long modelId, long versionId, int versionNo, String checksum) {}
  public record Design(
      String statPeriod, String timeZone, String businessTimeColumn,
      String periodBucketColumn, List<String> dimensions, List<Measure> measures,
      List<UpstreamVersion> upstreamVersions) {}
  public record Saved(
      long modelId, long revision, String structureFingerprint,
      String designSha256, String designJson, Design design) {}

  private final ModelRepository models;
  private final ModelVersionRepository versions;
  private final ModelStructureService structures;
  private final CurrentProject projects;
  private final JdbcTemplate jdbc;
  private final ObjectMapper mapper;

  public DwsGrainDesignService(
      ModelRepository models, ModelVersionRepository versions,
      ModelStructureService structures, CurrentProject projects,
      JdbcTemplate jdbc, ObjectMapper mapper) {
    this.models = models;
    this.versions = versions;
    this.structures = structures;
    this.projects = projects;
    this.jdbc = jdbc;
    this.mapper = mapper;
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public Saved save(long modelId, Request request, String operator) {
    if (request == null || operator == null || operator.isBlank()) {
      throw new IllegalArgumentException("必须提供 DWS 设计、操作人与编辑基线");
    }
    long projectId = projects.requireProjectId();
    Model model = requireDws(models.findByIdForUpdate(modelId).orElseThrow(
        () -> new IllegalArgumentException("当前项目没有该 DWS 模型")));
    StructureView structure = structures.getForUpdate(modelId);
    String fingerprint = StructureFingerprint.of(structure);
    if (!fingerprint.equals(request.expectedStructureFingerprint())) {
      throw new IllegalStateException("DWS 表结构已发生变化，请重新打开核对后保存粒度");
    }
    Design design = validateAndFreeze(model, structure, request);
    String json = encode(design);
    String digest = VersionDigests.sha256Hex(json);
    Optional<Saved> existing = get(modelId);
    if (existing.isPresent()
        && existing.get().designSha256().equals(digest)
        && existing.get().structureFingerprint().equals(fingerprint)) {
      return existing.get();
    }
    long revision = existing.map(Saved::revision).orElse(0L) + 1L;
    if (existing.isEmpty()) {
      jdbc.update("INSERT INTO yak_modeling_dws_grain_design "
          + "(project_id,model_id,structure_fingerprint,design_sha256,design_json,"
          + "revision,updated_by,updated_at) VALUES (?,?,?,?,?,?,?,CURRENT_TIMESTAMP)",
          projectId, modelId, fingerprint, digest, json, revision, operator);
    } else {
      int changed = jdbc.update("UPDATE yak_modeling_dws_grain_design SET "
          + "structure_fingerprint=?,design_sha256=?,design_json=?,revision=?,"
          + "updated_by=?,updated_at=CURRENT_TIMESTAMP "
          + "WHERE project_id=? AND model_id=? AND revision=?",
          fingerprint, digest, json, revision, operator,
          projectId, modelId, existing.get().revision());
      if (changed != 1) throw new IllegalStateException("DWS 粒度设计发生并发变化");
    }
    return new Saved(modelId, revision, fingerprint, digest, json, design);
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager", readOnly = true)
  public Optional<Saved> inspect(long modelId) {
    requireDws(models.findById(modelId).orElseThrow(
        () -> new IllegalArgumentException("当前项目没有该 DWS 模型")));
    return get(modelId);
  }

  public record UpstreamCandidate(
      long modelId, String code, String name, Long publishedVersionId,
      Integer publishedVersionNo, boolean usable) {}

  @Transactional(transactionManager = "yakBusinessTransactionManager", readOnly = true)
  public List<UpstreamCandidate> candidates(long modelId) {
    Model target = requireDws(models.findById(modelId).orElseThrow(
        () -> new IllegalArgumentException("当前项目没有该 DWS 模型")));
    return models.listByProcessLayer(target.processId(), "DWD").stream().map(model -> {
      boolean valid = model.status() != ModelStatus.DISABLED
          && model.publishedVersionId() != null
          && versions.findById(model.publishedVersionId(), model.id()).isPresent();
      return new UpstreamCandidate(model.id(), model.code(), model.name(),
          model.publishedVersionId(), model.latestVersionNo(), valid);
    }).toList();
  }

  /**
   * Called while ModelVersionService holds the physical model write lock.
   * Returns the exact frozen design payload embedded in immutable ModelVersion.metaJson.
   * No baseline draft or stale upstream can pass publication.
   */
  public Saved requireForPublish(Model model, StructureView structure) {
    requireDws(model);
    Saved saved = get(model.id()).orElseThrow(
        () -> new IllegalStateException("DWS 尚未保存统计粒度合同，禁止设计发布"));
    if (!saved.structureFingerprint().equals(StructureFingerprint.of(structure))
        || !saved.designSha256().equals(VersionDigests.sha256Hex(saved.designJson()))) {
      throw new IllegalStateException("DWS 结构或设计已漂移，须重新确认粒度合同");
    }
    Design d = saved.design();
    Request request = new Request(d.statPeriod(), d.timeZone(),
        d.businessTimeColumn(), d.periodBucketColumn(), d.dimensions(),
        d.measures(), d.upstreamVersions().stream().map(UpstreamVersion::modelId).toList(),
        saved.structureFingerprint());
    Design current = validateAndFreeze(model, structure, request);
    if (!current.equals(d)) {
      throw new IllegalStateException("DWS 上游发布版本发生变化，须重新核对并保存粒度合同");
    }
    return saved;
  }

  private Design validateAndFreeze(Model model, StructureView structure, Request request) {
    if (request.statPeriod() == null || !PERIODS.contains(request.statPeriod())
        || request.timeZone() == null || request.timeZone().isBlank()
        || request.timeZone().length() > 64) {
      throw new IllegalArgumentException("DWS 必须明确统计周期和 IANA 时区");
    }
    try { ZoneId.of(request.timeZone()); }
    catch (DateTimeException error) { throw new IllegalArgumentException("无效的统计时区", error); }
    if (request.businessTimeColumn() == null || request.businessTimeColumn().isBlank()
        || request.periodBucketColumn() == null || request.periodBucketColumn().isBlank()
        || request.dimensions() == null || request.dimensions().isEmpty()
        || request.measures() == null || request.measures().isEmpty()
        || request.upstreamModelIds() == null || request.upstreamModelIds().isEmpty()) {
      throw new IllegalArgumentException("DWS 必须确定业务时间、周期桶、分组维度、度量及上游模型");
    }
    Map<String, StructureView.ColumnView> cols = new HashMap<>();
    for (StructureView.ColumnView c : structure.columns()) {
      cols.put(c.columnName().toLowerCase(Locale.ROOT), c);
    }
    Set<String> dimensionNames = new HashSet<>();
    for (String name : request.dimensions()) {
      StructureView.ColumnView col = requiredColumn(cols, name);
      if (!dimensionNames.add(name.toLowerCase(Locale.ROOT))
          || !"DIMENSION".equalsIgnoreCase(col.fieldRole())
          || col.stdFieldId() == null) {
        throw new IllegalArgumentException("DWS 分组维度重复、无标准字段或字段角色不符：" + name);
      }
    }
    if (!dimensionNames.contains(request.periodBucketColumn().toLowerCase(Locale.ROOT))) {
      throw new IllegalArgumentException("统计周期桶必须属于 DWS 分组维度，防止跨周期错误汇总");
    }
    Set<String> measureNames = new HashSet<>();
    for (Measure measure : request.measures()) {
      if (measure == null || measure.column() == null || measure.function() == null) {
        throw new IllegalArgumentException("DWS 度量定义不完整");
      }
      StructureView.ColumnView col = requiredColumn(cols, measure.column());
      if (!measureNames.add(measure.column().toLowerCase(Locale.ROOT))
          || dimensionNames.contains(measure.column().toLowerCase(Locale.ROOT))
          || !"MEASURE".equalsIgnoreCase(col.fieldRole())
          || col.stdFieldId() == null || !FUNCTIONS.contains(measure.function())
          || !measure.function().equalsIgnoreCase(col.aggregateFunc())) {
        throw new IllegalArgumentException("DWS 度量标准/角色/聚合函数不一致：" + measure.column());
      }
    }

    List<Model> upstream = models.listByProcessLayer(model.processId(), "DWD");
    Map<Long, Model> candidates = new HashMap<>();
    for (Model item : upstream) candidates.put(item.id(), item);
    Set<Long> unique = new HashSet<>();
    List<UpstreamVersion> refs = new java.util.ArrayList<>();
    for (Long sourceId : request.upstreamModelIds()) {
      if (sourceId == null || !unique.add(sourceId) || !candidates.containsKey(sourceId)) {
        throw new IllegalArgumentException("DWS 上游模型重复、跨过程或不属于 DWD");
      }
      Model source = candidates.get(sourceId);
      if (source.status() == ModelStatus.DISABLED || source.publishedVersionId() == null) {
        throw new IllegalStateException("DWS 上游没有可用正式发布版本：" + source.code());
      }
      var version = versions.findById(source.publishedVersionId(), source.id())
          .orElseThrow(() -> new IllegalStateException("DWS 上游发布版本缺失"));
      if (version.versionNo() <= 0 || version.checksum() == null
          || !SHA256.matcher(version.checksum()).matches()) {
        throw new IllegalStateException("DWS 上游版本摘要无效");
      }
      // Read the typed immutable snapshot: string containment is not
      // evidence that a column actually exists in the published schema.
      StructureView published;
      try {
        published = mapper.readValue(version.structureJson(), StructureView.class);
      } catch (Exception error) {
        throw new IllegalStateException("DWS 上游正式结构快照损坏", error);
      }
      if (published.modelId() != null && !source.id().equals(published.modelId())) {
        throw new IllegalStateException("DWS 上游正式结构属于其他模型");
      }
      boolean hasBusinessTime = published.columns() != null
          && published.columns().stream().anyMatch(col ->
              request.businessTimeColumn().equalsIgnoreCase(col.columnName())
                  && col.dataType() != null
                  && (col.dataType().toUpperCase(Locale.ROOT).contains("DATE")
                      || col.dataType().toUpperCase(Locale.ROOT).contains("TIME")));
      if (!hasBusinessTime) {
        throw new IllegalStateException("DWS 上游正式快照没有有效业务时间字段："
            + request.businessTimeColumn());
      }
      refs.add(new UpstreamVersion(source.id(), version.id(),
          version.versionNo(), version.checksum()));
    }
    return new Design(request.statPeriod(), request.timeZone(),
        request.businessTimeColumn(), request.periodBucketColumn(),
        List.copyOf(request.dimensions()), List.copyOf(request.measures()), List.copyOf(refs));
  }

  private static StructureView.ColumnView requiredColumn(
      Map<String, StructureView.ColumnView> columns, String name) {
    var col = name == null ? null : columns.get(name.toLowerCase(Locale.ROOT));
    if (col == null) throw new IllegalArgumentException("DWS 目标字段不存在：" + name);
    return col;
  }

  private Model requireDws(Model model) {
    if (!"DWS".equalsIgnoreCase(model.layerCode())
        || model.processId() == null || model.id() == null
        || model.status() == ModelStatus.DISABLED) {
      throw new IllegalArgumentException("只允许当前项目有效 DWS 模型配置汇总粒度");
    }
    return model;
  }

  private Optional<Saved> get(long modelId) {
    return jdbc.query("SELECT model_id,revision,structure_fingerprint,design_sha256,"
        + "design_json FROM yak_modeling_dws_grain_design "
        + "WHERE project_id=? AND model_id=?",
        (rs, rowNo) -> decode(rs), projects.requireProjectId(), modelId)
        .stream().findFirst();
  }

  private Saved decode(ResultSet rs) throws SQLException {
    String json = rs.getString("design_json");
    try {
      return new Saved(rs.getLong("model_id"), rs.getLong("revision"),
          rs.getString("structure_fingerprint"), rs.getString("design_sha256"),
          json, mapper.readValue(json, Design.class));
    } catch (JsonProcessingException error) {
      throw new IllegalStateException("DWS 粒度合同无法读取", error);
    }
  }

  private String encode(Design value) {
    try { return mapper.writeValueAsString(value); }
    catch (JsonProcessingException error) {
      throw new IllegalStateException("DWS 粒度合同无法序列化", error);
    }
  }
}
