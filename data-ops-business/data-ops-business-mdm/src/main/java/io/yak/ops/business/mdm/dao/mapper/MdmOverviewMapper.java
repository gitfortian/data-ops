package io.yak.ops.business.mdm.dao.mapper;

import io.yak.ops.business.mdm.dao.MdmOverviewCardRow;
import io.yak.ops.business.mdm.dao.MdmOverviewTotalsRow;
import java.util.List;
import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 主数据总览的有界 SQL 投影(home-overview-contract:服务端聚合、禁止无界 list() 后在
 * JVM 里统计、禁止 N+1)。两条查询覆盖六卡 + 管线节点 + 实体卡片。
 */
@Mapper
public interface MdmOverviewMapper {

  @Select(
      """
      SELECT
          (SELECT COUNT(*) FROM yak_mdm_entity
            WHERE project_id = #{projectId}) AS entities,
          (SELECT COUNT(*) FROM yak_mdm_record
            WHERE project_id = #{projectId} AND status = 'ACTIVE') AS active_records,
          (SELECT COUNT(*) FROM yak_mdm_change
            WHERE project_id = #{projectId} AND approval_status = 'PENDING') AS pending_changes,
          (SELECT COUNT(*) FROM yak_mdm_clean_rule
            WHERE project_id = #{projectId} AND enabled = 1) AS clean_rules,
          (SELECT COUNT(*) FROM yak_mdm_distribution
            WHERE project_id = #{projectId} AND status = 'ACTIVE') AS distribution_targets,
          (SELECT COUNT(*) FROM yak_mdm_subscription
            WHERE project_id = #{projectId} AND status = 'ACTIVE') AS subscribers,
          (SELECT COUNT(*) FROM yak_mdm_collect_link
            WHERE project_id = #{projectId}) AS collect_links,
          (SELECT COUNT(*) FROM yak_mdm_merge_log
            WHERE project_id = #{projectId}) AS merge_logs,
          (SELECT COUNT(*) FROM yak_mdm_distribution
            WHERE project_id = #{projectId}
              AND status = 'ACTIVE'
              AND last_distribute_fail > 0) AS failing_distributions
      """)
  @ConstructorArgs({
    @Arg(column = "entities", javaType = long.class),
    @Arg(column = "active_records", javaType = long.class),
    @Arg(column = "pending_changes", javaType = long.class),
    @Arg(column = "clean_rules", javaType = long.class),
    @Arg(column = "distribution_targets", javaType = long.class),
    @Arg(column = "subscribers", javaType = long.class),
    @Arg(column = "collect_links", javaType = long.class),
    @Arg(column = "merge_logs", javaType = long.class),
    @Arg(column = "failing_distributions", javaType = long.class)
  })
  MdmOverviewTotalsRow selectTotals(@Param("projectId") Long projectId);

  @Select(
      """
      SELECT
          e.id AS entity_id,
          e.entity_code AS entity_code,
          e.entity_name AS entity_name,
          (SELECT COUNT(*) FROM yak_mdm_record r
            WHERE r.project_id = e.project_id AND r.entity_id = e.id
              AND r.status = 'ACTIVE') AS active_records,
          (SELECT COUNT(*) FROM yak_mdm_distribution d
            WHERE d.project_id = e.project_id AND d.entity_id = e.id
              AND d.status = 'ACTIVE') AS distribution_targets,
          (SELECT COUNT(*) FROM yak_mdm_subscription s
            WHERE s.project_id = e.project_id AND s.entity_id = e.id
              AND s.status = 'ACTIVE') AS subscribers,
          (SELECT COUNT(*) FROM yak_mdm_change c
            WHERE c.project_id = e.project_id AND c.entity_id = e.id
              AND c.approval_status = 'PENDING') AS pending_changes
      FROM yak_mdm_entity e
      WHERE e.project_id = #{projectId}
      ORDER BY e.update_time DESC, e.id DESC
      LIMIT #{limit}
      """)
  @ConstructorArgs({
    @Arg(column = "entity_id", javaType = Long.class),
    @Arg(column = "entity_code", javaType = String.class),
    @Arg(column = "entity_name", javaType = String.class),
    @Arg(column = "active_records", javaType = long.class),
    @Arg(column = "distribution_targets", javaType = long.class),
    @Arg(column = "subscribers", javaType = long.class),
    @Arg(column = "pending_changes", javaType = long.class)
  })
  List<MdmOverviewCardRow> selectRecentCards(
      @Param("projectId") Long projectId, @Param("limit") int limit);
}
