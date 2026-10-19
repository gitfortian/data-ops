package io.yak.ops.business.modeling.version;

import io.yak.ops.business.modeling.domain.Model;
import io.yak.ops.business.modeling.mapping.DwdMappingDraftService;
import io.yak.ops.business.modeling.repository.ModelRepository;
import io.yak.ops.business.modeling.structure.ModelStructureService;
import io.yak.ops.business.modeling.structure.StructureView;
import io.yak.ops.business.semantic.api.PublicationStandardEvidenceApi;
import io.yak.ops.core.project.CurrentProject;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Real, server-owned non-ODS publication preview for the modeling workbench.
 * This is not a release permit or a model-version write.
 *
 * <p>Locks the current model/structure in the actual business transaction
 * and asks Semantic to observe the entire saved column set under its persisted
 * project-revision row lock. The separate Security database cannot yet fence
 * a Modeling commit, so the returned publication eligibility is ALWAYS false.
 * Never infer that current semantic catalog validity authorizes publication.
 */
@Service
public class ModelPublicationReadinessService {
  private static final Set<String> SUPPORTED = Set.of("DWD", "DIM", "DWS", "ADS");
  private final ModelRepository models;
  private final ModelStructureService structures;
  private final CurrentProject project;
  private final PublicationStandardEvidenceApi semantic;
  private final DwdMappingDraftService mappings;

  public record Report(
      Long modelId,
      String layerCode,
      String structureSha256,
      PublicationStandardEvidenceApi.Verdict standardsVerdict,
      Long semanticRevision,
      String standardEvidenceSha256,
      List<PublicationStandardEvidenceApi.ColumnEvidence> columns,
      List<String> findings,
      boolean authorizedForPublication,
      String authorizationStatus,
      MappingContext mapping) {
    public Report {
      columns = List.copyOf(columns);
      findings = List.copyOf(findings);
    }
  }

  /**
   * Precise DWD logical/physical mapping provenance shown on the same release
   * confirmation, without mutating a MappingSet or freezing new revisions.
   * FROZEN_SAVED_DRAFT does not prove the separate Security publish fence.
   */
  public record MappingContext(
      String status, Long mappingRevisionId, Long mappingSetId,
      Long logicalModelId, Integer logicalVersionNo,
      String logicalSnapshotSha256, String physicalStructureSha256,
      String mappingContentSha256, String finding) {}

  @Autowired
  public ModelPublicationReadinessService(
      ModelRepository models, ModelStructureService structures,
      CurrentProject project, PublicationStandardEvidenceApi semantic,
      DwdMappingDraftService mappings) {
    this.models = models;
    this.structures = structures;
    this.project = project;
    this.semantic = semantic;
    this.mappings = mappings;
  }

  /** Historical unit tests do not construct a real DWD mapping repository. */
  public ModelPublicationReadinessService(
      ModelRepository models, ModelStructureService structures,
      CurrentProject project, PublicationStandardEvidenceApi semantic) {
    this(models, structures, project, semantic, null);
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public Report inspect(Long modelId) {
    if (modelId == null || modelId <= 0) {
      throw new IllegalArgumentException("请选择有效的物理模型");
    }
    Model model = models.findByIdForUpdate(modelId)
        .orElseThrow(() -> new IllegalArgumentException("物理模型不存在或不属于当前项目"));
    if (model.isRecycled() || model.layerCode() == null
        || !SUPPORTED.contains(model.layerCode())) {
      throw new IllegalArgumentException("正式标准发布预检仅适用于 DWD/DIM/DWS/ADS");
    }

    StructureView structure = structures.getForUpdate(modelId);
    if (structure == null || !Objects.equals(structure.modelId(), modelId)
        || structure.columns() == null || structure.columns().isEmpty()) {
      return unavailable(model, "物理列为空或结构身份不一致，无法进行全字段核验");
    }
    MappingContext mappingContext = "DWD".equals(model.layerCode())
        ? inspectMapping(modelId) : null;
    // The model-root/project and physical structure are server-side only.
    // The request never trusts a project identifier or an incomplete column
    // collection supplied by the browser.
    var request = new PublicationStandardEvidenceApi.BatchRequest(
        project.requireProjectId(), modelId, model.layerCode(),
        DraftPublicationPhysicalSnapshot.fingerprint(structure),
        structure.columns().stream()
            .map(c -> new PublicationStandardEvidenceApi.ColumnRequest(
                c.columnName(), c.dataType(), c.stdFieldId(), c.fieldRole()))
            .toList());
    try {
      var observed = semantic.evaluate(request);
      if (observed == null || !Objects.equals(observed.modelId(), modelId)
          || observed.projectId() != request.projectId()
          || !Objects.equals(observed.layerCode(), request.layerCode())
          || !Objects.equals(observed.physicalStructureSha256(), request.physicalStructureSha256())
          || !samePhysicalColumns(request, observed)) {
        return unavailable(model, "标准证据提供方范围或列集合不一致，禁止复用结果");
      }
      // A provider verdict must agree with every concrete per-column status.
      // Do not show a green current-catalog result when it contains a blocked
      // column, an absent StandardField or incomplete revision/digest identity.
      if (observed.verdict() == PublicationStandardEvidenceApi.Verdict.VALID
          && (observed.semanticRevision() == null || observed.semanticRevision() <= 0
              || observed.digestAlgorithmId() == null
              || observed.digestAlgorithmId().isBlank()
              || observed.evidenceSha256() == null
              || !observed.evidenceSha256().matches("[a-f0-9]{64}")
              || !observed.safeReasonCodes().isEmpty()
              || observed.columns().stream().anyMatch(c ->
                  !"VALID".equals(c.status()) || c.standardFieldId() == null
                      || c.standardFieldId() <= 0))) {
        return unavailable(model, "标准证据宣称合格，但列状态或冻结身份不完整，禁止发布");
      }
      // Even a VALID current catalog result does not establish T3's trusted
      // authorization and revocation-to-commit fence. Do not cache it as a
      // durable publication token or pass it to ModelVersionService.publish.
      List<String> findings = observed.safeReasonCodes().isEmpty()
          ? List.of("当前标准证据已核验；跨 Security/Business 提交保护未完成，正式发布尚未准入")
          : observed.safeReasonCodes();
      return new Report(modelId, model.layerCode(), request.physicalStructureSha256(),
          observed.verdict(), observed.semanticRevision(), observed.evidenceSha256(),
          observed.columns(), findings, false, "SECURITY_COMMIT_FENCE_NOT_ADMITTED",
          mappingContext);
    } catch (RuntimeException failure) {
      return unavailable(model, "标准证据服务不可用，未形成可信预检结果");
    }
  }

  /**
   * Read exact persisted MappingRevision and logical source for the same DWD
   * model, not a guessed version or a client-selected latest row. Never call
   * requireFrozenForPublication here: that method can create revisions.
   */
  private MappingContext inspectMapping(Long modelId) {
    if (mappings == null) {
      return new MappingContext("UNAVAILABLE", null, null, null, null,
          null, null, null, "映射服务不可用；无法确认逻辑版本与冻结修订");
    }
    try {
      var draft = mappings.get(modelId);
      if (draft == null) {
        return new MappingContext("MISSING", null, null, null, null,
            null, null, null, "尚未保存 DWD 逻辑→物理映射");
      }
      var frozen = mappings.revisions(modelId);
      if (frozen.isEmpty()) {
        return new MappingContext("NOT_FROZEN", null, draft.mappingSetId(),
            draft.logicalModelId(), draft.logicalVersionNo(),
            draft.logicalSnapshotSha256(), draft.physicalStructureSha256(),
            null, "映射草稿尚未冻结为不可变 MappingRevision");
      }
      var latest = frozen.get(0);
      var historical = mappings.revisionDetail(modelId, latest.mappingRevisionId());
      boolean identical =
          java.util.Objects.equals(draft.mappingSetId(), historical.mappingSetId())
          && java.util.Objects.equals(draft.logicalModelId(), historical.logicalModelId())
          && draft.logicalVersionNo() == historical.logicalVersionNo()
          && java.util.Objects.equals(draft.logicalSnapshotSha256(),
              historical.logicalSnapshotSha256())
          && java.util.Objects.equals(draft.physicalStructureSha256(),
              historical.physicalStructureSha256())
          && java.util.Objects.equals(latest.contentSha256(), historical.contentSha256())
          && draft.draftRevision() == latest.draftRevision();
      return new MappingContext(identical ? "FROZEN_SAVED_DRAFT" : "STALE",
          historical.mappingRevisionId(), historical.mappingSetId(),
          historical.logicalModelId(), historical.logicalVersionNo(),
          historical.logicalSnapshotSha256(), historical.physicalStructureSha256(),
          historical.contentSha256(), identical
              ? "已找到当前保存草稿对应的不可变映射修订；仍须正式发布时再次核验"
              : "逻辑来源或映射草稿已变化，需要重新核验并冻结映射");
    } catch (RuntimeException error) {
      return new MappingContext("UNKNOWN", null, null, null, null,
          null, null, null, "映射历史读取或内容完整性验证失败，不能视为已冻结");
    }
  }

  /** Reject same-sized but different-column evidence from a stale or confused provider. */
  private static boolean samePhysicalColumns(
      PublicationStandardEvidenceApi.BatchRequest request,
      PublicationStandardEvidenceApi.BatchResult observed) {
    if (observed.columns().size() != request.columns().size()) return false;
    for (var expected : request.columns()) {
      long matches = observed.columns().stream().filter(item ->
          item != null && item.columnName() != null
              && item.columnName().equalsIgnoreCase(expected.columnName())
              && Objects.equals(item.physicalType(), expected.physicalType())
              && Objects.equals(item.standardFieldId(), expected.standardFieldId())
              && Objects.equals(item.semanticRole(), expected.physicalRole()))
          .count();
      if (matches != 1) return false;
    }
    return true;
  }

  private static Report unavailable(Model model, String reason) {
    return new Report(model.id(), model.layerCode(), null,
        PublicationStandardEvidenceApi.Verdict.UNKNOWN, null, null,
        List.of(), List.of(reason), false, "EVIDENCE_UNAVAILABLE", null);
  }
}
