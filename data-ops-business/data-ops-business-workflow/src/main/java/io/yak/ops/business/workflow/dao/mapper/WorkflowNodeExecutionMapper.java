package io.yak.ops.business.workflow.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.yak.ops.common.bean.po.workflow.WorkflowNodeExecutionPO;

/** 工作流节点执行 Mapper。 */
public interface WorkflowNodeExecutionMapper extends BaseMapper<WorkflowNodeExecutionPO> {
  int upsert(WorkflowNodeExecutionPO nodeExecution);
}
