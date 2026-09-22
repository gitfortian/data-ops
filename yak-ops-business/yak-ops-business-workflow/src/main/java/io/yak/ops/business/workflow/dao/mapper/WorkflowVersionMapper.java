package io.yak.ops.business.workflow.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.yak.ops.common.bean.po.workflow.WorkflowVersionPO;
import org.apache.ibatis.annotations.Param;

/** Workflow versions persist Project directly because RUNTIME versions may not have a parent Workflow. */
public interface WorkflowVersionMapper extends BaseMapper<WorkflowVersionPO> {
  WorkflowVersionPO selectByIdAndProject(
      @Param("id") String id,
      @Param("projectId") long projectId);

  int initializeEngineDefinition(
      @Param("id") String id,
      @Param("projectId") long projectId,
      @Param("engineDefinitionJson") String engineDefinitionJson);

  int initializeRuntimeMetadata(
      @Param("id") String id,
      @Param("projectId") long projectId,
      @Param("runtimeMetadataJson") String runtimeMetadataJson);
}
