package io.yak.ops.common.bean.po.workflow;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Data;

/** 工作流调度 Trigger Ledger 持久化对象。 */
@Data
@TableName("yak_workflow_schedule_trigger")
public class WorkflowScheduleTriggerPO {
  @TableId(type = IdType.INPUT)
  private String id;
  private Long projectId;
  private String scheduleId;
  private String workflowId;
  private String backfillId;
  private String triggerId;
  private String dedupeKey;
  private String triggerSource;
  private Instant plannedFireTime;
  private Instant actualFireTime;
  private LocalDate businessDate;
  private String executionStrategy;
  private String misfireStrategy;
  private String status;
  private String workflowExecutionId;
  private String executionStatus;
  private String message;
  private String errorMessage;
  private Instant launchedAt;
  private Instant completedAt;
  private Instant createTime;
  private Instant updateTime;
}
