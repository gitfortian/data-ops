package io.yak.ops.business.sync.realtime.dao.mapper;

import io.yak.ops.business.sync.realtime.dao.model.RealtimeJobListRow;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 实时同步任务列表的查询语句。
 *
 * <p>原 XML 已移除。列表需要"取每个 definition 最新一条 deployment"的关联子查询、
 * 由 deployment 派生的状态列（{@code CASE WHEN}）与 {@code JOIN + 聚合}，
 * Wrapper 无法表达，SQL 逐字保留在注解里。
 *
 * <p>注意：原 XML 用 {@code <sql>} 片段（{@code latestExecutionJoin} / {@code filters}）被两条语句
 * {@code <include>} 复用；注解里没有片段机制，两处各展开一次——改过滤条件时需同步两处。
 */
@Mapper
public interface RealtimeJobQueryMapper {

  @Select(
      """
      <script>
      SELECT COUNT(*)
      FROM yak_realtime_job_definition d
      LEFT JOIN yak_realtime_job_deployment p
        ON p.id = (
          SELECT MAX(p2.id)
          FROM yak_realtime_job_deployment p2
          WHERE p2.definition_id = d.id
            AND p2.project_id = d.project_id
        )
      WHERE d.project_id = #{projectId}
      <if test="keyword != null and keyword != ''">
        AND (d.job_name LIKE #{keyword} OR d.description LIKE #{keyword})
      </if>
      <if test="id != null">AND d.id = #{id}</if>
      <if test="releaseState != null and releaseState != ''">AND d.release_state = #{releaseState}</if>
      <if test="stateGroup == 'RUNNING'">
        AND p.observed_state IN ('STARTING','RUNNING','STOPPING')
      </if>
      <if test="stateGroup == 'STOPPED'">
        AND (p.id IS NULL OR p.observed_state = 'STOPPED')
      </if>
      <if test="stateGroup == 'ABNORMAL'">
        AND p.observed_state IN ('FAILED','UNKNOWN','CONFLICT')
      </if>
      </script>
      """)
  long countByProject(
      @Param("projectId") long projectId,
      @Param("keyword") String keyword,
      @Param("id") Long id,
      @Param("releaseState") String releaseState,
      @Param("stateGroup") String stateGroup);

  @Select(
      """
      <script>
      SELECT d.id,
             d.job_name,
             d.description,
             d.runtime_environment_id,
             d.spec_json,
             d.release_state,
             CASE WHEN p.id IS NULL THEN 'STOPPED' ELSE p.desired_state END AS desired_state,
             CASE WHEN p.id IS NULL THEN 'STOPPED' ELSE p.observed_state END AS observed_state,
             d.definition_version,
             d.published_version,
             CASE
               WHEN p.id IS NOT NULL
                AND p.desired_state = 'RUNNING'
                AND p.observed_state = 'RUNNING'
                AND p.definition_version_id IS NOT NULL
                AND d.published_definition_version_id IS NOT NULL
                AND p.definition_version_id &lt;&gt; d.published_definition_version_id
               THEN TRUE ELSE FALSE
             END AS published_update_available,
             d.config_digest,
             p.error_message AS last_error,
             d.create_time,
             d.update_time,
             p.id AS deployment_id,
             p.definition_version AS deployment_definition_version,
             p.spec_summary AS deployment_spec_summary,
             p.config_digest AS deployment_config_digest,
             p.idempotency_key AS deployment_idempotency_key,
             p.gateway_job_id AS deployment_gateway_job_id,
             p.runtime_revision AS deployment_runtime_revision,
             p.runtime_environment_snapshot_json AS deployment_runtime_environment_snapshot_json,
             p.status AS deployment_status,
             p.result_uncertain AS deployment_result_uncertain,
             p.error_message AS deployment_error_message,
             p.create_time AS deployment_create_time,
             p.update_time AS deployment_update_time
      FROM yak_realtime_job_definition d
      LEFT JOIN yak_realtime_job_deployment p
        ON p.id = (
          SELECT MAX(p2.id)
          FROM yak_realtime_job_deployment p2
          WHERE p2.definition_id = d.id
            AND p2.project_id = d.project_id
        )
      WHERE d.project_id = #{projectId}
      <if test="keyword != null and keyword != ''">
        AND (d.job_name LIKE #{keyword} OR d.description LIKE #{keyword})
      </if>
      <if test="id != null">AND d.id = #{id}</if>
      <if test="releaseState != null and releaseState != ''">AND d.release_state = #{releaseState}</if>
      <if test="stateGroup == 'RUNNING'">
        AND p.observed_state IN ('STARTING','RUNNING','STOPPING')
      </if>
      <if test="stateGroup == 'STOPPED'">
        AND (p.id IS NULL OR p.observed_state = 'STOPPED')
      </if>
      <if test="stateGroup == 'ABNORMAL'">
        AND p.observed_state IN ('FAILED','UNKNOWN','CONFLICT')
      </if>
      ORDER BY d.update_time DESC, d.id DESC
      LIMIT #{limit} OFFSET #{offset}
      </script>
      """)
  List<RealtimeJobListRow> pageByProject(
      @Param("projectId") long projectId,
      @Param("keyword") String keyword,
      @Param("id") Long id,
      @Param("releaseState") String releaseState,
      @Param("stateGroup") String stateGroup,
      @Param("limit") int limit,
      @Param("offset") int offset);
}
