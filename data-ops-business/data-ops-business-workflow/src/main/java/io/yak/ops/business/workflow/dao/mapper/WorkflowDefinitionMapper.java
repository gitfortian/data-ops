package io.yak.ops.business.workflow.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.yak.ops.common.bean.po.workflow.WorkflowDefinitionPO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 工作流定义 Mapper。 */
public interface WorkflowDefinitionMapper extends BaseMapper<WorkflowDefinitionPO> {

  @Insert(
      """
      INSERT INTO yak_workflow_definition
        (id, project_id, name, description, status, draft_revision, latest_version_no, active_version_id,
         draft_json, latest_execution_id, latest_execution_status, create_time, update_time)
      VALUES
        (#{id}, #{projectId}, #{name}, #{description}, #{status}, #{draftRevision}, #{latestVersionNo},
         #{activeVersionId}, #{draftJson}, #{latestExecutionId}, #{latestExecutionStatus},
         #{createTime}, #{updateTime})
      ON DUPLICATE KEY UPDATE
        project_id = COALESCE(VALUES(project_id), project_id),
        name = VALUES(name),
        description = VALUES(description),
        status = VALUES(status),
        draft_revision = VALUES(draft_revision),
        latest_version_no = VALUES(latest_version_no),
        active_version_id = VALUES(active_version_id),
        draft_json = VALUES(draft_json),
        latest_execution_id = VALUES(latest_execution_id),
        latest_execution_status = VALUES(latest_execution_status),
        update_time = VALUES(update_time)
      """)
  int upsert(WorkflowDefinitionPO definition);

  /** 发布/回滚路径的行锁：与调度 Trigger Ledger 同一把定义行锁。 */
  @Select("SELECT id FROM yak_workflow_definition WHERE id = #{workflowId} FOR UPDATE")
  String lockWorkflow(@Param("workflowId") String workflowId);
}
