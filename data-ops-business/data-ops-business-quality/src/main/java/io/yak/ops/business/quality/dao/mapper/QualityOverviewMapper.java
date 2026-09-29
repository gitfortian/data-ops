package io.yak.ops.business.quality.dao.mapper;

import io.yak.ops.business.quality.dao.model.QualityOverviewPO.AnalyticsStatsRow;
import io.yak.ops.business.quality.dao.model.QualityOverviewPO.DimensionRow;
import io.yak.ops.business.quality.dao.model.QualityOverviewPO.IssueRow;
import io.yak.ops.business.quality.dao.model.QualityOverviewPO.StatsRow;
import io.yak.ops.business.quality.dao.model.QualityOverviewPO.TrendRow;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;

/** 数据质量总览聚合查询。 */
@Mapper
public interface QualityOverviewMapper {

  StatsRow selectStats(Map<String, Object> params);

  AnalyticsStatsRow selectAnalyticsStats(Map<String, Object> params);

  List<DimensionRow> selectDimensions(Map<String, Object> params);

  List<TrendRow> selectAnalyticsTrend(Map<String, Object> params);

  List<IssueRow> selectRecentIssues(Map<String, Object> params);
}
