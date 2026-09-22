package io.yak.ops.business.quality.monitor;

import io.yak.framework.common.PageData;
import io.yak.ops.business.quality.config.ConditionalOnQualityEnabled;
import io.yak.ops.business.quality.domain.QualityDomain.AlertEvent;
import io.yak.ops.business.quality.domain.QualityDomain.Monitor;
import io.yak.ops.business.quality.domain.QualityDomain.MonitorSettings;
import io.yak.ops.business.quality.domain.QualityDomain.TableMonitorSummary;
import io.yak.ops.business.quality.domain.QualityQuery;
import io.yak.ops.business.quality.repository.QualityMonitorRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Read-side access to quality monitor definitions and settings. */
@Component
@ConditionalOnQualityEnabled
public class QualityMonitorReader {
  /** 工作台告警卡快照：近 24h 计数 + 最近事件（上限 50，超限按需翻页，不做全量）。 */
  public record AlertOverview(long last24hCount, List<AlertEvent> recent) {}

  private static final int MAX_ALERT_RECENT = 50;

  private final QualityMonitorRepository repository;

  public QualityMonitorReader(QualityMonitorRepository repository) {
    this.repository = repository;
  }

  @Transactional(readOnly = true, transactionManager = "yakBusinessTransactionManager")
  public PageData<Monitor> page(QualityQuery.Monitor query) {
    return repository.pageMonitors(query);
  }

  @Transactional(readOnly = true, transactionManager = "yakBusinessTransactionManager")
  public Monitor require(long id) {
    return repository.findMonitor(id)
        .orElseThrow(() -> new IllegalArgumentException("质量监控不存在：" + id));
  }

  @Transactional(readOnly = true, transactionManager = "yakBusinessTransactionManager")
  public MonitorSettings settings(long id) {
    require(id);
    return repository.findMonitorSettings(id);
  }

  @Transactional(readOnly = true, transactionManager = "yakBusinessTransactionManager")
  public List<TableMonitorSummary> tableSummaries(
      long dataSourceId, String databaseName, String schemaName) {
    if (dataSourceId <= 0L) throw new IllegalArgumentException("数据源编号无效");
    return repository.tableSummaries(dataSourceId, databaseName, schemaName);
  }

  @Transactional(readOnly = true, transactionManager = "yakBusinessTransactionManager")
  public AlertOverview alertOverview(int recentLimit) {
    int limit = Math.max(1, Math.min(recentLimit, MAX_ALERT_RECENT));
    return new AlertOverview(
        repository.countAlertEventsSince(LocalDateTime.now().minusHours(24)),
        repository.recentAlertEvents(limit));
  }
}
