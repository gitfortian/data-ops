package io.yak.ops.business.modeling.logical;

import io.yak.ops.business.modeling.logical.LogicalPhysicalHandoffPreviewService.ColumnCheck;
import io.yak.ops.business.modeling.logical.LogicalPhysicalHandoffPreviewService.Preview;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ephemeral, explicitly user-selected mapping review against freshly read source evidence.
 * NO mapping is saved, approved or published here. PD-010 remains proposed.
 */
@Service
@RequiredArgsConstructor
public class LogicalPhysicalHandoffReviewService {
  private static final int MAX_ITEMS = 1000;
  private static final String GENERIC_PREVIEW_BLOCKER =
      "存在缺失、失效、未覆盖或歧义的字段引用；仅允许人工修正后重新预检";

  private final LogicalPhysicalHandoffPreviewService previews;

  /** Stable IDs are chosen by a human; names and field-code similarity are not identities. */
  public record Selection(Long logicalAttributeId, Long physicalColumnId) {}
  public record ReviewRequest(String logicalSnapshotSha256, String physicalStructureSha256,
                              List<Selection> selections) {}
  public record ReviewRow(Long logicalAttributeId, Long physicalColumnId, Long stdFieldId,
                          String physicalColumn, String logicalAttribute) {}
  public record ReviewResult(Long logicalModelId, int logicalVersionNo, Long physicalModelId,
                             String logicalSnapshotSha256, String physicalStructureSha256,
                             boolean evidenceUnchanged, boolean readyForManualDesign,
                             List<String> blockers, List<ReviewRow> reviewedMappings,
                             String status) {}

  @Transactional(transactionManager = "yakBusinessTransactionManager", readOnly = true)
  public ReviewResult validate(Long logicalId, int versionNo, Long physicalId,
                               ReviewRequest request) {
    // Scope and permissions are always rechecked by the underlying canonical repositories.
    Preview latest = previews.preview(logicalId, versionNo, physicalId);
    boolean unchanged = request != null
        && Objects.equals(request.logicalSnapshotSha256(), latest.logicalSnapshotSha256())
        && Objects.equals(request.physicalStructureSha256(), latest.physicalStructureSha256());
    List<String> blockers = new ArrayList<>();
    if (!unchanged) {
      blockers.add("逻辑快照或物理结构证据已变化（或缺少指纹），请刷新预检并重新选择");
      return result(latest, false, blockers, List.of());
    }

    Map<Long, ColumnCheck> physicalById = new HashMap<>();
    Map<Long, ColumnCheck> logicalById = new HashMap<>();
    for (ColumnCheck check : latest.columns()) {
      if (check.physicalColumnId() != null) {
        if (physicalById.putIfAbsent(check.physicalColumnId(), check) != null) {
          blockers.add("物理列身份重复，禁止形成审阅候选");
        }
      }
      if (check.logicalAttributeId() != null) {
        logicalById.putIfAbsent(check.logicalAttributeId(), check);
      }
    }
    // Explicit manual selection resolves ambiguity, but cannot waive any other policy.
    for (String blocker : latest.blockers()) {
      if (!GENERIC_PREVIEW_BLOCKER.equals(blocker)) blockers.add(blocker);
    }

    List<Selection> selections = request.selections();
    if (selections == null || selections.isEmpty() || selections.size() > MAX_ITEMS) {
      blockers.add("请选择至少一条且不超过 " + MAX_ITEMS + " 条映射");
      return result(latest, true, blockers, List.of());
    }
    Set<Long> usedLogical = new HashSet<>();
    Set<Long> usedPhysical = new HashSet<>();
    List<ReviewRow> rows = new ArrayList<>();
    for (Selection selection : selections) {
      if (selection == null || selection.logicalAttributeId() == null
          || selection.physicalColumnId() == null) {
        blockers.add("映射必须包含明确的逻辑属性 ID 和物理列 ID");
        continue;
      }
      ColumnCheck physical = physicalById.get(selection.physicalColumnId());
      ColumnCheck logical = logicalById.get(selection.logicalAttributeId());
      if (physical == null || logical == null) {
        blockers.add("映射引用不属于本次版本和项目可访问范围");
        continue;
      }
      if (!usedLogical.add(selection.logicalAttributeId())
          || !usedPhysical.add(selection.physicalColumnId())) {
        blockers.add("首次人工审阅只支持一对一属性与列映射，重复身份必须重新选择");
        continue;
      }
      if (physical.physicalStdFieldId() == null
          || !Objects.equals(physical.physicalStdFieldId(), logical.physicalStdFieldId())) {
        blockers.add("所选属性与列的标准字段身份不一致或缺失，不能按名称猜测关联");
        continue;
      }
      if ("INVALID_STANDARD".equals(physical.result())
          || "INVALID_LOGICAL_STANDARD".equals(logical.result())) {
        blockers.add("所选映射的标准字段已失效或当前用户无权访问");
        continue;
      }
      rows.add(new ReviewRow(selection.logicalAttributeId(), selection.physicalColumnId(),
          physical.physicalStdFieldId(), physical.physicalColumn(),
          logical.logicalAttribute()));
    }
    if (usedPhysical.size() != physicalById.size()) {
      blockers.add("尚有物理列未由用户明确选择映射");
    }
    if (usedLogical.size() != logicalById.size()) {
      blockers.add("尚有冻结逻辑属性未被覆盖，请明确映射后再次审阅");
    }
    if (rows.size() != selections.size()) {
      blockers.add("存在无效映射，不能形成完整审阅证据");
    }
    return result(latest, true, blockers, rows);
  }

  private static ReviewResult result(Preview latest, boolean unchanged, List<String> blockers,
                                     List<ReviewRow> rows) {
    boolean ready = unchanged && blockers.isEmpty();
    // This result is NEVER a persisted mapping, authorization, published version or SQL plan.
    return new ReviewResult(latest.logicalModelId(), latest.logicalVersionNo(),
        latest.physicalModelId(), latest.logicalSnapshotSha256(),
        latest.physicalStructureSha256(), unchanged, ready, List.copyOf(blockers),
        List.copyOf(rows), ready ? "REVIEWABLE_ONLY" : "BLOCKED");
  }
}
