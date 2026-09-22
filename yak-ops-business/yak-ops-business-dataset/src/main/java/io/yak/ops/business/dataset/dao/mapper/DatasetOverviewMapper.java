package io.yak.ops.business.dataset.dao.mapper;

import io.yak.ops.business.dataset.dao.model.DatasetOverviewSummaryPO;
import io.yak.ops.business.dataset.dao.model.DatasetPO;
import java.sql.Timestamp;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** Bounded SQL projections for Dataset overview surfaces. */
@Mapper
public interface DatasetOverviewMapper {

  @Select("""
      SELECT
          (SELECT COUNT(*) FROM yak_dataset) AS datasetCount,
          (SELECT COUNT(*)
             FROM yak_dataset
            WHERE create_time >= #{from}
              AND create_time < #{to}) AS createdCount
      """)
  DatasetOverviewSummaryPO selectSummary(
      @Param("from") Timestamp from, @Param("to") Timestamp to);

  @Select("""
      SELECT
          (SELECT COUNT(*) FROM yak_dataset WHERE project_id = #{projectId}) AS datasetCount,
          (SELECT COUNT(*)
             FROM yak_dataset
            WHERE project_id = #{projectId}
              AND create_time >= #{from}
              AND create_time < #{to}) AS createdCount
      """)
  DatasetOverviewSummaryPO selectSummaryByProject(
      @Param("projectId") Long projectId,
      @Param("from") Timestamp from,
      @Param("to") Timestamp to);

  @Select("""
      SELECT
          id,
          project_id AS projectId,
          development_node_id AS developmentNodeId,
          name,
          description,
          status,
          current_version_id AS currentVersionId,
          create_time AS createTime,
          update_time AS updateTime
      FROM yak_dataset
      ORDER BY update_time DESC, id DESC
      LIMIT #{limit}
      """)
  List<DatasetPO> selectRecent(@Param("limit") int limit);

  @Select("""
      SELECT
          id,
          project_id AS projectId,
          development_node_id AS developmentNodeId,
          name,
          description,
          status,
          current_version_id AS currentVersionId,
          create_time AS createTime,
          update_time AS updateTime
      FROM yak_dataset
      WHERE project_id = #{projectId}
      ORDER BY update_time DESC, id DESC
      LIMIT #{limit}
      """)
  List<DatasetPO> selectRecentByProject(
      @Param("projectId") Long projectId, @Param("limit") int limit);

  @Select("""
      SELECT
          id,
          project_id AS projectId,
          development_node_id AS developmentNodeId,
          name,
          description,
          status,
          current_version_id AS currentVersionId,
          create_time AS createTime,
          update_time AS updateTime
      FROM yak_dataset
      WHERE status = 'ONLINE'
      ORDER BY update_time DESC, id DESC
      LIMIT #{limit}
      """)
  List<DatasetPO> selectRecentOnline(@Param("limit") int limit);

  @Select("""
      SELECT
          id,
          project_id AS projectId,
          development_node_id AS developmentNodeId,
          name,
          description,
          status,
          current_version_id AS currentVersionId,
          create_time AS createTime,
          update_time AS updateTime
      FROM yak_dataset
      WHERE project_id = #{projectId}
        AND status = 'ONLINE'
      ORDER BY update_time DESC, id DESC
      LIMIT #{limit}
      """)
  List<DatasetPO> selectRecentOnlineByProject(
      @Param("projectId") Long projectId, @Param("limit") int limit);
}
