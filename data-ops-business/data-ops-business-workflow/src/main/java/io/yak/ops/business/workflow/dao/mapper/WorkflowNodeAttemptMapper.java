package io.yak.ops.business.workflow.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.yak.ops.common.bean.po.workflow.WorkflowNodeAttemptPO;
import org.apache.ibatis.annotations.Param;

/** 工作流节点 Attempt Mapper。 */
public interface WorkflowNodeAttemptMapper extends BaseMapper<WorkflowNodeAttemptPO> {
  int upsert(WorkflowNodeAttemptPO attempt);

  int bindExternalExecution(
      @Param("attemptId") String attemptId,
      @Param("externalExecutionId") String externalExecutionId);
}
