package io.yak.ops.business.workflow.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.yak.ops.common.bean.po.workflow.WorkflowExecutionPO;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/** 工作流执行实例 Mapper。 */
public interface WorkflowExecutionMapper extends BaseMapper<WorkflowExecutionPO> {
  int upsert(WorkflowExecutionPO execution);

  List<String> selectExecutionIds(@Param("projectId") long projectId);

  List<String> selectRecoverableExecutionIds(@Param("projectId") long projectId);

  /** Explicit cross-Project dispatcher used only to discover durable startup recovery identities. */
  List<WorkflowExecutionPO> selectRecoverableExecutionsForDispatch();

  long countActiveExecutions(
      @Param("workflowId") String workflowId,
      @Param("projectId") long projectId);

  String selectEffectiveRuntimeMetadata(
      @Param("executionId") String executionId,
      @Param("projectId") long projectId);

  String selectAuditCarrierJson(
      @Param("executionId") String executionId,
      @Param("projectId") long projectId);

  int updateAuditCarrier(
      @Param("executionId") String executionId,
      @Param("projectId") long projectId,
      @Param("carrierJson") String carrierJson);
}
