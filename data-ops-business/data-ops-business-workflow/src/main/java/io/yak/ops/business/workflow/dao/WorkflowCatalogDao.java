package io.yak.ops.business.workflow.dao;

import io.yak.ops.business.workflow.dao.model.WorkflowDefinitionPO;
import io.yak.ops.business.workflow.dao.model.WorkflowVersionPO;
import java.util.List;

/** 工作流定义与版本数据访问接口。 */
public interface WorkflowCatalogDao {
  List<WorkflowDefinitionPO> selectDefinitions();

  List<WorkflowVersionPO> selectPublishedVersions(String workflowId);

  WorkflowVersionPO selectVersionById(String versionId);

  /** 以行锁读取定义行（发布/回滚事务内调用）；不存在时返回 null。 */
  String lockDefinition(String workflowId);

  int upsertDefinition(WorkflowDefinitionPO definition);

  int insertVersion(WorkflowVersionPO version);

  int deleteDefinition(String workflowId);

  int initializeEngineDefinition(String versionId, String engineDefinitionJson);

  int initializeRuntimeMetadata(String versionId, String runtimeMetadataJson);
}
