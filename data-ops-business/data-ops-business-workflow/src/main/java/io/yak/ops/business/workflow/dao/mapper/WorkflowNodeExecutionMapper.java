package io.yak.ops.business.workflow.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.yak.ops.business.workflow.dao.model.WorkflowNodeExecutionPO;
import org.apache.ibatis.annotations.Insert;

/** 工作流节点执行 Mapper。 */
public interface WorkflowNodeExecutionMapper extends BaseMapper<WorkflowNodeExecutionPO> {

  @Insert(
      """
      INSERT INTO yak_workflow_node_execution
        (id, workflow_execution_id, node_id, failure_policy, status, output_json, error_message,
         failure_handled, downstream_continuation_allowed)
      VALUES
        (#{id}, #{workflowExecutionId}, #{nodeId}, #{failurePolicy}, #{status}, #{outputJson},
         #{errorMessage}, #{failureHandled}, #{downstreamContinuationAllowed})
      ON DUPLICATE KEY UPDATE
        failure_policy = VALUES(failure_policy),
        status = VALUES(status),
        output_json = VALUES(output_json),
        error_message = VALUES(error_message),
        failure_handled = VALUES(failure_handled),
        downstream_continuation_allowed = VALUES(downstream_continuation_allowed)
      """)
  @Insert(databaseId = "postgresql", value = """
      INSERT INTO yak_workflow_node_execution
        (id, workflow_execution_id, node_id, failure_policy, status, output_json, error_message,
         failure_handled, downstream_continuation_allowed)
      VALUES
        (#{id}, #{workflowExecutionId}, #{nodeId}, #{failurePolicy}, #{status}, #{outputJson},
         #{errorMessage}, #{failureHandled}, #{downstreamContinuationAllowed})
      ON CONFLICT (id) DO UPDATE SET
        failure_policy = excluded.failure_policy,
        status = excluded.status,
        output_json = excluded.output_json,
        error_message = excluded.error_message,
        failure_handled = excluded.failure_handled,
        downstream_continuation_allowed = excluded.downstream_continuation_allowed
      """)
  int upsert(WorkflowNodeExecutionPO nodeExecution);
}
