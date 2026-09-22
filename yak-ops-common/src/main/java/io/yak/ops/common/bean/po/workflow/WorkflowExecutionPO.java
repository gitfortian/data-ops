package io.yak.ops.common.bean.po.workflow;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;
import lombok.Data;

/** 工作流执行实例持久化对象。 */
@Data
@TableName("yak_workflow_execution")
public class WorkflowExecutionPO {
  @TableId(type = IdType.INPUT)
  private String id;
  private Long projectId;
  private String definitionId;
  private String sourceExecutionId;
  private String status;
  private String inputJson;
  private Boolean schedulingStopped;
  private Instant runStartedAt;
  private Instant pausedAt;
  private Long pausedDurationMs;
  private Instant createdAt;
  private Instant updatedAt;
  private Instant endedAt;
  private String workflowName;
  private String workflowVersionId;
  private Integer workflowVersionNo;
  private Boolean testRun;
  private Integer edgeCount;
  private Long workflowTimeoutSeconds;
  private String failureStrategy;
  private String runtimeMetadataJson;
}
