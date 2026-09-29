package io.yak.ops.business.workflow.backfill;

import io.yak.ops.business.workflow.dao.WorkflowBackfillDao;
import io.yak.ops.business.workflow.dao.WorkflowScheduleTriggerDao;
import io.yak.ops.business.workflow.repository.support.WorkflowJsonCodec;
import io.yak.ops.common.bean.po.workflow.WorkflowBackfillPO;
import io.yak.ops.common.bean.po.workflow.WorkflowScheduleTriggerPO;
import io.yak.ops.common.bean.vo.workflow.WorkflowBackfillVO;
import java.util.List;
import java.util.Locale;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Backfill 批次查询；运行进度直接以 Trigger Ledger 为事实来源。 */
@Component
@ConditionalOnProperty(prefix = "yak.database", name = "enabled", havingValue = "true", matchIfMissing = true)
public class WorkflowBackfillQuery {
  private final WorkflowBackfillDao dao;
  private final WorkflowScheduleTriggerDao triggers;
  private final WorkflowJsonCodec json;

  public WorkflowBackfillQuery(
      WorkflowBackfillDao dao,
      WorkflowScheduleTriggerDao triggers,
      WorkflowJsonCodec json) {
    this.dao = dao;
    this.triggers = triggers;
    this.json = json;
  }

  public List<WorkflowBackfillVO> list(String workflowId, String scheduleId, String status) {
    String state = normalize(status);
    return dao.selectList(workflowId, scheduleId).stream()
        .map(this::view)
        .filter(value -> state == null || state.equals(value.status()))
        .toList();
  }

  public WorkflowBackfillVO get(String id) {
    return view(require(id));
  }

  public WorkflowBackfillPO require(String id) {
    if (id == null || id.isBlank()) throw new IllegalArgumentException("Backfill ID 不能为空");
    WorkflowBackfillPO value = dao.select(id.trim());
    if (value == null) throw new IllegalArgumentException("Backfill 批次不存在：" + id);
    return value;
  }

  public WorkflowBackfillVO view(WorkflowBackfillPO value) {
    List<WorkflowScheduleTriggerPO> items = triggers.selectByBackfillId(value.getId());
    int waiting = count(items, "RECEIVED") + count(items, "WAITING");
    int running = count(items, "LAUNCHING") + count(items, "REACTIVATING") + count(items, "RUNNING");
    int succeeded = count(items, "SUCCEEDED");
    int failed = count(items, "FAILED");
    int canceled = count(items, "CANCELED");
    int skipped = count(items, "SKIPPED");
    String status = deriveStatus(value, waiting, running, succeeded, failed, canceled, skipped);

    return new WorkflowBackfillVO(
        value.getId(),
        value.getWorkflowId(),
        value.getWorkflowVersionId(),
        value.getWorkflowVersionNo(),
        value.getScheduleId(),
        value.getScheduleName(),
        value.getName(),
        status,
        value.getOperationType() == null ? "BACKFILL" : value.getOperationType(),
        value.getSourceExecutionId(),
        value.getStartBusinessDate(),
        value.getEndBusinessDate(),
        value.getCronExpression(),
        value.getTimezone(),
        value.getExecutionStrategy(),
        json.readMap(value.getInputJson()),
        value.getTotalCount() == null ? items.size() : value.getTotalCount(),
        waiting,
        running,
        succeeded,
        failed,
        canceled,
        skipped,
        value.getCreateTime(),
        value.getUpdateTime());
  }

  private String deriveStatus(
      WorkflowBackfillPO value,
      int waiting,
      int running,
      int succeeded,
      int failed,
      int canceled,
      int skipped) {
    if ("CANCELED".equals(value.getStatus())) return "CANCELED";
    if (waiting > 0 || running > 0) return "RUNNING";
    int total = value.getTotalCount() == null ? 0 : value.getTotalCount();
    if (total <= 0) return "CREATED";
    if (succeeded == total) return "SUCCEEDED";
    if (failed == total) return "FAILED";
    if (succeeded > 0 && failed + canceled + skipped > 0) return "PARTIAL_SUCCESS";
    if (failed > 0) return "FAILED";
    if (canceled + skipped >= total) return "CANCELED";
    return "PARTIAL_SUCCESS";
  }

  private int count(List<WorkflowScheduleTriggerPO> values, String status) {
    return (int) values.stream().filter(value -> status.equals(value.getStatus())).count();
  }

  private String normalize(String value) {
    return value == null || value.isBlank() ? null : value.trim().toUpperCase(Locale.ROOT);
  }
}
