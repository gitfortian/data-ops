package io.yak.ops.common.bean.po.workflow;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;
import lombok.Data;

/** 工作流节点尝试持久化对象。 */
@Data
@TableName("yak_workflow_node_attempt")
public class WorkflowNodeAttemptPO {
  @TableId(type = IdType.INPUT)
  private String id;
  private String nodeExecutionId;
  private String workflowExecutionId;
  private String nodeId;
  private Integer attemptNo;
  private Instant availableAt;
  private String status;
  private String resumeTargetStatus;
  private Instant startedAt;
  private Instant pausedAt;
  private Long pausedDurationMs;
  private Instant endedAt;
  private String errorMessage;
  private String failureReason;
  private String externalExecutionId;
}
