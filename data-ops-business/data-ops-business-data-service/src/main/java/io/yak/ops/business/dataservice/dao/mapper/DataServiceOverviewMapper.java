package io.yak.ops.business.dataservice.dao.mapper;

import io.yak.ops.business.dataservice.dao.model.DataServiceOverviewHotApiPO;
import io.yak.ops.business.dataservice.dao.model.DataServiceOverviewSummaryPO;
import io.yak.ops.business.dataservice.dao.model.DataServiceOverviewTrendPO;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** SQL-side aggregation for Data Service overview surfaces. */
@Mapper
public interface DataServiceOverviewMapper {

  @Select("""
      SELECT
          (SELECT COUNT(*) FROM yak_ops_data_service_api WHERE project_id = #{projectId}) AS apiTotal,
          (SELECT COUNT(*) FROM yak_ops_data_service_api
             WHERE project_id = #{projectId} AND enabled = 1) AS runningApis,
          COUNT(*) AS totalCalls,
          COALESCE(SUM(CASE WHEN success = 1 THEN 1 ELSE 0 END), 0) AS successCalls,
          COALESCE(SUM(CASE WHEN duration_ms > 0 THEN duration_ms ELSE 0 END), 0)
              AS totalDurationMs,
          COALESCE(SUM(CASE WHEN row_count > 0 THEN row_count ELSE 0 END), 0)
              AS totalRows
      FROM yak_ops_data_service_call_log
      WHERE project_id = #{projectId}
        AND create_time >= #{from}
        AND create_time <= #{to}
      """)
  DataServiceOverviewSummaryPO selectSummary(
      @Param("projectId") Long projectId,
      @Param("from") LocalDateTime from,
      @Param("to") LocalDateTime to);

  @Select("""
      SELECT
          FLOOR(TIMESTAMPDIFF(MINUTE, #{from}, create_time) / #{bucketMinutes}) AS bucketIndex,
          COUNT(*) AS calls,
          COALESCE(SUM(CASE WHEN success = 1 THEN 1 ELSE 0 END), 0) AS successCalls,
          COALESCE(SUM(CASE WHEN success = 0 THEN 1 ELSE 0 END), 0) AS failureCalls,
          COALESCE(SUM(CASE WHEN duration_ms > 0 THEN duration_ms ELSE 0 END), 0)
              AS totalDurationMs
      FROM yak_ops_data_service_call_log
      WHERE project_id = #{projectId}
        AND create_time >= #{from}
        AND create_time <= #{to}
      GROUP BY FLOOR(TIMESTAMPDIFF(MINUTE, #{from}, create_time) / #{bucketMinutes})
      ORDER BY bucketIndex
      """)
  List<DataServiceOverviewTrendPO> selectTrend(
      @Param("projectId") Long projectId,
      @Param("from") LocalDateTime from,
      @Param("to") LocalDateTime to,
      @Param("bucketMinutes") int bucketMinutes);

  @Select("""
      SELECT
          call_log.api_id AS apiId,
          COALESCE(MAX(api.name), MAX(call_log.service_name)) AS name,
          COALESCE(MAX(api.path), MAX(call_log.service_path)) AS path,
          COUNT(*) AS calls,
          COALESCE(SUM(CASE WHEN call_log.success = 1 THEN 1 ELSE 0 END), 0) AS successCalls,
          COALESCE(SUM(CASE
              WHEN call_log.duration_ms > 0 THEN call_log.duration_ms
              ELSE 0
          END), 0) AS totalDurationMs
      FROM yak_ops_data_service_call_log call_log
      LEFT JOIN yak_ops_data_service_api api
        ON api.id = call_log.api_id AND api.project_id = call_log.project_id
      WHERE call_log.project_id = #{projectId}
        AND call_log.create_time >= #{from}
        AND call_log.create_time <= #{to}
      GROUP BY call_log.api_id
      ORDER BY calls DESC, call_log.api_id ASC
      LIMIT #{limit}
      """)
  List<DataServiceOverviewHotApiPO> selectHotApis(
      @Param("projectId") Long projectId,
      @Param("from") LocalDateTime from,
      @Param("to") LocalDateTime to,
      @Param("limit") int limit);
}
