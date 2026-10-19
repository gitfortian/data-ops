package io.yak.ops.business.modeling.version;

import io.yak.ops.business.modeling.domain.Model;
import io.yak.ops.business.modeling.repository.ModelRepository;
import io.yak.ops.business.modeling.structure.ModelStructureService;
import io.yak.ops.business.modeling.structure.StructureView;
import io.yak.ops.business.semantic.api.PublicationStandardEvidenceApi;
import io.yak.ops.core.project.CurrentProject;
import java.util.List;
import java.util.Objects;
import java.util.Set;
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
      String authorizationStatus) {
    public Report {
      columns = List.copyOf(columns);
      findings = List.copyOf(findings);
    }
  }

  public ModelPublicationReadinessService(
      ModelRepository models, ModelStructureService structures,
      CurrentProject project, PublicationStandardEvidenceApi semantic) {
    this.models = models;
    this.structures = structures;
    this.project = project;
    this.semantic = semantic;
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
      // Even a VALID current catalog result does not establish T3's trusted
      // authorization and revocation-to-commit fence. Do not cache it as a
      // durable publication token or pass it to ModelVersionService.publish.
      List<String> findings = observed.safeReasonCodes().isEmpty()
          ? List.of("当前标准证据已核验；跨 Security/Business 提交保护未完成，正式发布尚未准入")
          : observed.safeReasonCodes();
      return new Report(modelId, model.layerCode(), request.physicalStructureSha256(),
          observed.verdict(), observed.semanticRevision(), observed.evidenceSha256(),
          observed.columns(), findings, false, "SECURITY_COMMIT_FENCE_NOT_ADMITTED");
    } catch (RuntimeException failure) {
      return unavailable(model, "标准证据服务不可用，未形成可信预检结果");
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
              && Objects.equals(item.standardFieldId(), expected.standardFieldId()))
          .count();
      if (matches != 1) return false;
    }
    return true;
  }

  private static Report unavailable(Model model, String reason) {
    return new Report(model.id(), model.layerCode(), null,
        PublicationStandardEvidenceApi.Verdict.UNKNOWN, null, null,
        List.of(), List.of(reason), false, "EVIDENCE_UNAVAILABLE");
  }
}
