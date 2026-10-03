package io.yak.ops.business.sync.realtime.dao.mapper;

import io.yak.ops.business.sync.realtime.dao.model.RealtimeJobDefinitionPO;
import io.yak.ops.business.sync.realtime.dao.model.RealtimeJobDeploymentPO;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 实时同步的写入/对账命令语句。
 *
 * <p>原 XML 已移除。这批语句都需要数据库原子语义或跨表关联，Wrapper 无法表达，SQL 逐字保留在注解里：
 * 行锁（{@code FOR UPDATE}）、{@code MAX()} 关联子查询、{@code TIMESTAMPADD} 租约、
 * {@code CASE WHEN} 条件更新。
 */
@Mapper
public interface RealtimeJobCommandMapper {

  @Select(
      """
      SELECT * FROM yak_realtime_job_definition
      WHERE id = #{id} AND project_id = #{projectId}
      FOR UPDATE
      """)
  RealtimeJobDefinitionPO lockDefinitionByProject(
      @Param("id") long id, @Param("projectId") long projectId);

  /** Explicit cross-Project dispatcher scan; callers must restore Project before business IO. */
  @Select(
      """
      SELECT p.*
      FROM yak_realtime_job_deployment p
      WHERE p.project_id IS NOT NULL
        AND p.id = (
          SELECT MAX(p2.id)
          FROM yak_realtime_job_deployment p2
          WHERE p2.definition_id = p.definition_id
            AND p2.project_id = p.project_id
        )
        AND p.observed_state IN ('STARTING','RUNNING','STOPPING','UNKNOWN','CONFLICT')
      ORDER BY p.project_id ASC, p.definition_id ASC
      """)
  List<RealtimeJobDeploymentPO> reconcileExecutionsForDispatch();

  @Select(
      """
      SELECT p.*
      FROM yak_realtime_job_deployment p
      WHERE p.project_id = #{projectId}
        AND p.id = (
          SELECT MAX(p2.id)
          FROM yak_realtime_job_deployment p2
          WHERE p2.definition_id = p.definition_id
            AND p2.project_id = #{projectId}
        )
        AND p.observed_state IN ('STARTING','RUNNING','STOPPING','UNKNOWN','CONFLICT')
      ORDER BY p.definition_id ASC
      """)
  List<RealtimeJobDeploymentPO> reconcileExecutionsByProject(@Param("projectId") long projectId);

  @Update(
      """
      UPDATE yak_realtime_runtime_lease
      SET lease_owner = #{owner}, lease_until = TIMESTAMPADD(SECOND, #{leaseSeconds}, CURRENT_TIMESTAMP(3))
      WHERE id = 1
        AND (lease_until IS NULL OR lease_until < CURRENT_TIMESTAMP(3) OR lease_owner = #{owner})
      """)
  int tryAcquireLease(@Param("owner") String owner, @Param("leaseSeconds") int leaseSeconds);

  @Update(
      """
      UPDATE yak_realtime_job_deployment
      SET observed_state = #{observedState},
          status = #{deploymentState},
          gateway_job_id = COALESCE(gateway_job_id, #{engineJobId}),
          result_uncertain = CASE WHEN #{observedState} = 'UNKNOWN' THEN result_uncertain ELSE 0 END,
          error_message = #{error}
      WHERE id = #{deploymentId}
        AND project_id = #{projectId}
      """)
  int reconcileDeploymentByProject(
      @Param("deploymentId") long deploymentId,
      @Param("projectId") long projectId,
      @Param("observedState") String observedState,
      @Param("deploymentState") String deploymentState,
      @Param("engineJobId") String engineJobId,
      @Param("error") String error);
}
