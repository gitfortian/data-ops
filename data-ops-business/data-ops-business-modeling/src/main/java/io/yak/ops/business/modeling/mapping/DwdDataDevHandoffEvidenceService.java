package io.yak.ops.business.modeling.mapping;

import io.yak.ops.business.modeling.version.PhysicalDataDevHandoffReadinessService;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A single Modeling-owned read-only inspection of the exact published DWD
 * version and its immutable mapping. Never returns DataDev write permission,
 * TaskRevision identity, target SQL or an execution result (DR-07).
 */
@Service
@RequiredArgsConstructor
public class DwdDataDevHandoffEvidenceService {
  private static final int MAX_RULES = 1000;
  private final PhysicalDataDevHandoffReadinessService readiness;
  private final DwdMappingPublicationService publications;

  public record Report(
      PhysicalDataDevHandoffReadinessService.Report readiness,
      DwdMappingPublicationService.History publication,
      String status,
      List<String> blockers,
      boolean authorizedForTaskCreation) {}

  @Transactional(transactionManager = "yakBusinessTransactionManager", readOnly = true)
  public Report inspect(Long modelId, int versionNo) {
    if (!positive(modelId) || versionNo <= 0) {
      throw new IllegalArgumentException("必须提供精确 DWD ModelVersionRef");
    }
    var version = readiness.inspect(modelId, versionNo);
    if (!"DWD".equals(version.layerCode())
        || version.version() == null
        || !Objects.equals(version.modelId(), modelId)
        || !Objects.equals(version.version().modelId(), modelId)
        || version.version().versionNo() != versionNo
        || !positive(version.version().versionId())
        || !sha256(version.version().frozenStructureSha256())
        || !sha256(version.inspectionSha256())) {
      throw new IllegalStateException("Modeling 交接预检的模型版本身份或结构摘要不一致");
    }
    var published = publications.history(modelId, versionNo);
    if (!Objects.equals(published.modelVersionId(), version.version().versionId())
        || published.versionNo() != versionNo) {
      throw new IllegalStateException("DWD 冻结 MappingRevision 与精确物理发布版本不一致");
    }

    List<String> blockers = new ArrayList<>(version.blockers());
    if ("LEGACY_UNBOUND".equals(published.state()) && published.mapping() == null) {
      blockers.add("历史 DWD 物理版本没有冻结 MappingRevision，拒绝作为正式交接依据");
    } else {
      if (!"DESIGN_PUBLISHED_NOT_EXECUTED".equals(published.state())
          || published.mapping() == null) {
        throw new IllegalStateException("DWD 发布映射的状态或历史证据损坏");
      }
      validateMapping(published.mapping());
    }
    if (!"DESIGN_REVIEWABLE".equals(version.designStatus())
        && blockers.isEmpty()) {
      blockers.add("当前物理设计不满足人工审阅条件，请重新核验来源与标准");
    }
    if (!version.selectedLatestVersion() && blockers.isEmpty()) {
      blockers.add("该历史版本已不是当前发布指针，仅能只读查看");
    }
    if (!"PUBLISHED".equals(version.modelStatus()) && blockers.isEmpty()) {
      blockers.add("模型当前不是已发布状态，只能查看历史证据");
    }
    // No success of this Modeling-only inspection may authorize a task write.
    boolean reviewable = blockers.isEmpty()
        && "DESIGN_REVIEWABLE".equals(version.designStatus())
        && version.selectedLatestVersion()
        && "PUBLISHED".equals(version.modelStatus());
    return new Report(version, published,
        reviewable ? "REVIEWABLE_ONLY" : "DESIGN_BLOCKED",
        List.copyOf(blockers), false);
  }

  private static void validateMapping(DwdMappingDraftService.HistoricalRevision mapping) {
    if (!positive(mapping.mappingRevisionId()) || !positive(mapping.mappingSetId())
        || mapping.revisionNo() <= 0 || !positive(mapping.logicalModelId())
        || mapping.logicalVersionNo() <= 0
        || !sha256(mapping.contentSha256())
        || !sha256(mapping.logicalSnapshotSha256())
        || !sha256(mapping.physicalStructureSha256())
        || mapping.rules() == null || mapping.rules().isEmpty()
        || mapping.rules().size() > MAX_RULES) {
      throw new IllegalStateException("冻结映射缺少有效的版本身份、内容摘要或完整字段规则");
    }
    Set<String> targets = new HashSet<>();
    Set<Long> attributes = new HashSet<>();
    for (var rule : mapping.rules()) {
      if (rule == null || rule.targetColumn() == null
          || rule.targetColumn().isBlank()
          || !rule.targetColumn().equals(rule.targetColumn().trim())
          || !positive(rule.logicalAttributeId())
          || !positive(rule.stdFieldId())
          || !Set.of("DIRECT", "RENAME").contains(rule.kind())
          || !targets.add(rule.targetColumn().toLowerCase(Locale.ROOT))
          || !attributes.add(rule.logicalAttributeId())) {
        throw new IllegalStateException("冻结映射列重复、字段引用非法或规则超出 DIRECT/RENAME 范围");
      }
    }
  }

  private static boolean positive(Long value) {
    return value != null && value > 0;
  }

  private static boolean sha256(String value) {
    return value != null && value.matches("[a-f0-9]{64}");
  }
}
