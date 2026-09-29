package io.yak.ops.common.bean.vo.workflow;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Backfill 创建前的逻辑计划预览。 */
public record WorkflowBackfillPreviewVO(
    String scheduleId,
    String cronExpression,
    String timezone,
    LocalDate startBusinessDate,
    LocalDate endBusinessDate,
    int totalCount,
    boolean truncated,
    List<OccurrenceVO> occurrences) {

  public WorkflowBackfillPreviewVO {
    occurrences = occurrences == null ? List.of() : List.copyOf(occurrences);
  }

  public record OccurrenceVO(
      LocalDate businessDate,
      Instant scheduleInstant,
      String scheduleTime) {
  }
}
