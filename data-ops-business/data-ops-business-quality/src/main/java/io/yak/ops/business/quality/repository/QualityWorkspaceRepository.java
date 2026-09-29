package io.yak.ops.business.quality.repository;

import io.yak.ops.business.quality.domain.QualityDomain.ColumnReport;
import io.yak.ops.business.quality.domain.QualityDomain.DimensionReport;
import io.yak.ops.business.quality.domain.QualityDomain.OperationLog;
import io.yak.ops.business.quality.domain.QualityDomain.ReportOverview;
import io.yak.ops.business.quality.domain.QualityDomain.TrendPoint;
import io.yak.ops.business.quality.domain.QualityDomain.WorkspaceStats;
import java.time.LocalDateTime;
import java.util.List;

/** 监控工作台读模型 Repository。 */
public interface QualityWorkspaceRepository {
  WorkspaceStats stats(long monitorId);
  ReportOverview overview(long monitorId, LocalDateTime reportStart, LocalDateTime reportEnd);
  List<DimensionReport> dimensions(long monitorId, LocalDateTime reportStart, LocalDateTime reportEnd);
  List<TrendPoint> trend(long monitorId, LocalDateTime trendStart, LocalDateTime reportEnd);
  List<ColumnReport> columns(long monitorId, LocalDateTime reportStart, LocalDateTime reportEnd);
  long countOperationLogs(long monitorId);
  List<OperationLog> operationLogs(long monitorId, int current, int pageSize);
}
