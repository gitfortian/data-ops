package io.yak.ops.common.bean.po.workflow;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** 工作流节点执行持久化对象。 */
@Data
@TableName("yak_workflow_node_execution")
public class WorkflowNodeExecutionPO {
  @TableId(type = IdType.INPUT)
  private String id;
  private String workflowExecutionId;
  private String nodeId;
  private String failurePolicy;
  private String status;
  private String outputJson;
  private String errorMessage;
  private Boolean failureHandled;
  private Boolean downstreamContinuationAllowed;
}
