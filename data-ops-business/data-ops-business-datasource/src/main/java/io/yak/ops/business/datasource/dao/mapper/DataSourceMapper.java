package io.yak.ops.business.datasource.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.yak.ops.business.datasource.dao.model.DataSourceSummaryRow;
import io.yak.ops.business.datasource.dao.model.DataSourcePO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 数据源 MyBatis 映射接口。
 *
 * <p>原 XML 已移除；汇总列是 {@code COUNT(*)} / {@code SUM(CASE WHEN …)} / {@code COUNT(DISTINCT …)}
 * 聚合，Wrapper 无法表达，SQL 逐字保留在注解里。
 */
@Mapper
public interface DataSourceMapper extends BaseMapper<DataSourcePO> {

  @Select(
      """
      SELECT COUNT(*) AS total,
             COALESCE(SUM(CASE WHEN conn_status = 'CONNECTED' THEN 1 ELSE 0 END), 0) AS connected,
             COALESCE(SUM(CASE WHEN conn_status = 'DISCONNECTED' THEN 1 ELSE 0 END), 0) AS disconnected,
             COALESCE(SUM(CASE WHEN conn_status = 'UNKNOWN' THEN 1 ELSE 0 END), 0) AS unknown,
             COUNT(DISTINCT environment) AS environment_count
      FROM yak_ops_data_source
      """)
  DataSourceSummaryRow selectSummary();

  @Select(
      """
      SELECT COUNT(*) AS total,
             COALESCE(SUM(CASE WHEN conn_status = 'CONNECTED' THEN 1 ELSE 0 END), 0) AS connected,
             COALESCE(SUM(CASE WHEN conn_status = 'DISCONNECTED' THEN 1 ELSE 0 END), 0) AS disconnected,
             COALESCE(SUM(CASE WHEN conn_status = 'UNKNOWN' THEN 1 ELSE 0 END), 0) AS unknown,
             COUNT(DISTINCT environment) AS environment_count
      FROM yak_ops_data_source
      WHERE project_id = #{projectId}
      """)
  DataSourceSummaryRow selectSummaryByProject(@Param("projectId") Long projectId);
}
