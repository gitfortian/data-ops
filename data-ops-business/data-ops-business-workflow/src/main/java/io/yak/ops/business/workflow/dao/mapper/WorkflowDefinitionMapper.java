package io.yak.ops.business.workflow.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.yak.ops.common.bean.po.workflow.WorkflowDefinitionPO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 工作流定义 Mapper。 */
public interface WorkflowDefinitionMapper extends BaseMapper<WorkflowDefinitionPO> {
  int upsert(WorkflowDefinitionPO definition);

  /** 发布/回滚路径的行锁：与调度 Trigger Ledger 同一把定义行锁。 */
  @Select("SELECT id FROM yak_workflow_definition WHERE id = #{workflowId} FOR UPDATE")
  String lockWorkflow(@Param("workflowId") String workflowId);
}
