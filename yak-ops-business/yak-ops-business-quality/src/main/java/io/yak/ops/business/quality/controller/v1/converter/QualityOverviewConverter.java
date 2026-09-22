package io.yak.ops.business.quality.controller.v1.converter;

import io.yak.ops.business.quality.workspace.QualityOverviewReader.AnalyticsOverview;
import io.yak.ops.business.quality.workspace.QualityOverviewReader.AnalyticsSummary;
import io.yak.ops.business.quality.workspace.QualityOverviewReader.DimensionHealth;
import io.yak.ops.business.quality.workspace.QualityOverviewReader.IssueContributor;
import io.yak.ops.business.quality.workspace.QualityOverviewReader.TrendPoint;
import io.yak.ops.common.bean.vo.quality.QualityOverviewVO;
import org.springframework.stereotype.Component;

/** 数据质量总览读模型到接口响应的显式转换。 */
@Component
public class QualityOverviewConverter {

  public QualityOverviewVO.Overview overview(AnalyticsOverview value) {
    return new QualityOverviewVO.Overview(
        value.rangeStart(),
        value.rangeEnd(),
        summary(value.summary()),
        value.dimensions().stream().map(this::dimension).toList(),
        value.issueContributors().stream().map(this::contributor).toList(),
        value.trend().stream().map(this::trend).toList());
  }

  private QualityOverviewVO.Summary summary(AnalyticsSummary value) {
    return new QualityOverviewVO.Summary(
        value.executionCount(),
        value.activeMonitorCount(),
        value.executedRuleCount(),
        value.passedRuleCount(),
        value.failedRuleCount(),
        value.errorRuleCount(),
        value.issueRuleCount(),
        value.issueExecutionCount(),
        value.affectedMonitorCount(),
        value.affectedTableCount(),
        value.affectedColumnCount(),
        value.passRate(),
        value.issueRate(),
        value.averageDurationMs(),
        value.latestExecutionAt());
  }

  private QualityOverviewVO.Dimension dimension(DimensionHealth value) {
    return new QualityOverviewVO.Dimension(
        value.dimension(), value.total(), value.issues(), value.passRate());
  }

  private QualityOverviewVO.IssueContributor contributor(IssueContributor value) {
    return new QualityOverviewVO.IssueContributor(
        value.dimension(), value.issues(), value.ratio());
  }

  private QualityOverviewVO.TrendPoint trend(TrendPoint value) {
    return new QualityOverviewVO.TrendPoint(
        value.date(),
        value.executionCount(),
        value.activeMonitorCount(),
        value.executedRuleCount(),
        value.passedRuleCount(),
        value.failedRuleCount(),
        value.errorRuleCount(),
        value.issueExecutionCount(),
        value.passRate(),
        value.issueRate(),
        value.averageDurationMs());
  }
}
