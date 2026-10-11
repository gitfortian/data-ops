package io.yak.ops.business.modeling.version;

import io.yak.ops.business.modeling.domain.Model;
import io.yak.ops.business.modeling.exception.ModelingException;
import io.yak.ops.business.modeling.repository.ModelRepository;
import io.yak.ops.business.modeling.standard.PhysicalStandardCoverageInspectionService;
import io.yak.ops.business.modeling.structure.ModelStructureService;
import io.yak.ops.business.modeling.structure.StructureFingerprint;
import io.yak.ops.business.modeling.structure.StructureView;
import io.yak.ops.common.enums.modeling.ModelingErrorCode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A read-only handoff readiness projection, owned by Modeling. This cannot
 * create DataDev nodes/drafts, validate a DataDev plugin/environment, or issue a
 * permission to publish/run. Exact ModelVersionRef is an evidence coordinate
 * ONLY until DR-07 authorizes persistent DataDev provenance.
 */
@Service
@RequiredArgsConstructor
public class PhysicalDataDevHandoffReadinessService {
  private static final int MAX_COLUMNS = 1000;
  private final ModelRepository models;
  private final ModelVersionService versions;
  private final ModelStructureService structures;
  private final PhysicalStandardCoverageInspectionService standardCoverage;

  public record VersionRef(Long modelId, Long versionId, int versionNo,
                           String frozenStructureSha256) {}
  public record Dependency(String code, String status, String explanation) {}
  public record Report(Long modelId, String modelCode, String layerCode,
                       String modelStatus, VersionRef version,
                       String currentStructureSha256, String currentStandardEvidenceSha256,
                       boolean selectedLatestVersion, String designStatus,
                       String dataDevStatus, int frozenColumnCount,
                       int currentColumnCount, int frozenStandardRefCount,
                       int frozenUnboundColumnCount, List<Dependency> dependencies,
                       List<String> blockers, List<String> nextActions,
                       String inspectionSha256) {}

  @Transactional(transactionManager = "yakBusinessTransactionManager", readOnly = true)
  public Report inspect(Long modelId, int versionNo) {
    if (modelId == null || modelId <= 0 || versionNo <= 0) {
      throw invalid("必须明确本项目的物理模型 ID 和精确发布版本号");
    }
    Model model = models.findById(modelId).orElseThrow(() ->
        new ModelingException(ModelingErrorCode.NOT_FOUND, "物理模型不存在或不属于当前项目"));
    if (model.isRecycled() || !Set.of("DWD", "DIM").contains(
        model.layerCode() == null ? "" : model.layerCode().toUpperCase(java.util.Locale.ROOT))) {
      throw invalid("本交接预检仅适用于未回收 DWD/DIM 物理模型");
    }

    var selected = versions.getVersionDetail(modelId, versionNo);
    if (selected == null || selected.version() == null || selected.structure() == null
        || selected.meta() == null || !Objects.equals(selected.version().modelId(), modelId)
        || selected.version().versionNo() != versionNo
        || !Objects.equals(selected.structure().modelId(), modelId)
        || !Objects.equals(model.layerCode(), selected.meta().get("layerCode"))) {
      throw invalid("历史版本的身份、分层或结构证据不一致，拒绝形成交接证据");
    }
    StructureView frozen = selected.structure();
    StructureView live = structures.get(modelId);
    if (live == null || !Objects.equals(live.modelId(), modelId)
        || frozen.columns() == null || live.columns() == null
        || frozen.columns().size() > MAX_COLUMNS || live.columns().size() > MAX_COLUMNS) {
      throw invalid("当前或历史字段集合不完整，无法进行精确版本对照");
    }
    validateColumns(frozen.columns());
    validateColumns(live.columns());

    // DWD/DIM standard status is CURRENT and authorized by Semantic; it is not
    // a historical standard version nor an approval of the selected snapshot.
    var coverage = standardCoverage.inspect(modelId);
    if (coverage == null || !Objects.equals(coverage.modelId(), modelId)
        || !Objects.equals(coverage.layerCode(), model.layerCode())
        || !Objects.equals(coverage.structureSha256(), StructureFingerprint.of(live))) {
      throw invalid("标准治理核验的当前物理结构证据已经过期或串用其他模型");
    }

    String frozenHash = StructureFingerprint.of(ModelVersionService.semanticView(frozen));
    String currentHash = StructureFingerprint.of(ModelVersionService.semanticView(live));
    boolean same = ModelVersionService.semanticView(frozen)
        .equals(ModelVersionService.semanticView(live));
    boolean latest = Objects.equals(model.latestVersionNo(), versionNo)
        && Objects.equals(model.publishedVersionId(), selected.version().id());
    long withStandard = frozen.columns().stream()
        .filter(c -> c.stdFieldId() != null && c.stdFieldId() > 0).count();
    int unbound = (int) (frozen.columns().size() - withStandard);

    List<Dependency> requirements = new ArrayList<>();
    List<String> blockers = new ArrayList<>();
    requirements.add(new Dependency("EXACT_MODEL_VERSION", "PINNED",
        "固定 Modeling modelId/versionId/versionNo 和冻结结构语义指纹；不使用模型 latest 猜测版本"));
    requirements.add(new Dependency("FROZEN_STRUCTURE", same ? "MATCHES_CURRENT" : "DRIFTED",
        "逐项比较该不可变结构版本与当前设计的列/键/索引/表级定义"));
    if (!same) blockers.add("当前物理设计与所选发布版本不同，须明确选择目标版本并核对变更");
    requirements.add(new Dependency("PUBLISHED_POINTER", latest ? "LATEST" : "NOT_LATEST",
        "模型当前发布指针必须与所选版本一致，历史版本仍可只读查阅"));
    if (!latest) blockers.add("所选版本并非当前发布指针，不可当作最新物理目标");
    if (!"PUBLISHED".equals(model.status().name())) {
      blockers.add("模型当前非 PUBLISHED；历史版本只能作为审阅证据");
    }
    if (frozen.columns().isEmpty()) blockers.add("选中版本没有冻结的目标物理列");
    if (unbound > 0) blockers.add("冻结版本有 " + unbound
        + " 个物理列未记录正式 StandardFieldRef；技术列豁免尚未获批");

    requirements.add(new Dependency("CURRENT_STANDARD", coverage.status(),
        "当前标准证据指纹：" + coverage.evidenceSha256()
            + "；不可替代发布当时的标准状态或版本"));
    if (!"COVERED_NOW".equals(coverage.status())) {
      blockers.add("当前 DWD/DIM 标准字段或业务过程存在需要人工处理的缺口");
    }
    requirements.add(new Dependency("HISTORIC_STANDARD_PROVENANCE", "NOT_FROZEN",
        "现有物理版本只有列引用 ID，未冻结标准版本/过程与批准过的逻辑→物理映射"));
    requirements.add(new Dependency("LOGICAL_PHYSICAL_MAPPING", "NOT_APPROVED",
        "DR-06 尚未批准可持久的逻辑/物理版本映射身份，不能推断逻辑版本"));
    requirements.add(new Dependency("DATADEV_PROVENANCE", "NOT_APPROVED",
        "DR-07 尚未批准 ModelVersionRef 在 DataDev Draft/Revision 的正式持久关联"));
    requirements.add(new Dependency("TARGET_ENVIRONMENT", "NOT_CHECKED",
        "目标环境、目标库、运行身份和写权限应由 DataDev 自身验证，Modeling 不做连接猜测"));
    requirements.add(new Dependency("TASK_PLUGIN_AND_RUNTIME", "NOT_CHECKED",
        "DataDev 插件能力、任务 SQL、发布修订及执行必须由 DataDev 自己验证和承担"));
    List<String> actions = List.of(
        "核对所选精确物理版本及当前版本漂移，确认业务过程与各物理列的正式标准",
        "由领域 Owner 批准 DR-06/07 后确定不可变 ModelVersionRef/逻辑映射及 DataDev provenance 合同",
        "交接获批后由 DataDev 独立核验目标环境/任务插件/执行身份并管理 Node、Draft、Revision 和 Execution",
        "真实环境授权 E2E 保留为后续统一手动验收，不属于当前设计证据");
    String designStatus = blockers.isEmpty() ? "DESIGN_REVIEWABLE" : "DESIGN_NEEDS_REVIEW";
    // Even a clean design cannot be promoted to DataDev authoring readiness:
    // DR-07 / runtime authorization are distinct owner decisions.
    String inspectionHash = digest(modelId + "|" + selected.version().id() + "|"
        + versionNo + "|" + frozenHash + "|" + currentHash + "|"
        + coverage.evidenceSha256() + "|" + designStatus + "|" + latest);
    return new Report(modelId, model.code(), model.layerCode(), model.status().name(),
        new VersionRef(modelId, selected.version().id(), versionNo, frozenHash),
        currentHash, coverage.evidenceSha256(), latest, designStatus,
        "NOT_AUTHORIZED_FOR_TASK_CREATION", frozen.columns().size(),
        live.columns().size(), (int) withStandard, unbound,
        List.copyOf(requirements), List.copyOf(blockers), actions, inspectionHash);
  }

  private static void validateColumns(List<StructureView.ColumnView> columns) {
    Set<String> names = new HashSet<>();
    for (var col : columns) {
      if (col == null || col.columnName() == null || col.columnName().isBlank()
          || !names.add(col.columnName().trim().toLowerCase(java.util.Locale.ROOT))) {
        throw invalid("目标物理字段名称为空或重复，不能形成可审阅版本");
      }
    }
  }
  private static String digest(String input) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
          .digest(input.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException("缺少 SHA-256", impossible);
    }
  }
  private static ModelingException invalid(String why) {
    return new ModelingException(ModelingErrorCode.INVALID_COLUMN, why);
  }
}
