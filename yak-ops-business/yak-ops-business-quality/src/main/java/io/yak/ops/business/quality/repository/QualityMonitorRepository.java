package io.yak.ops.business.quality.repository;

import io.yak.framework.common.PageData;
import io.yak.ops.business.quality.domain.QualityDomain.AlertEvent;
import io.yak.ops.business.quality.domain.QualityDomain.Monitor;
import io.yak.ops.business.quality.domain.QualityDomain.MonitorSettings;
import io.yak.ops.business.quality.domain.QualityDomain.MonitorSettingsSpec;
import io.yak.ops.business.quality.domain.QualityDomain.MonitorSpec;
import io.yak.ops.business.quality.domain.QualityDomain.Rule;
import io.yak.ops.business.quality.domain.QualityDomain.RuleSpec;
import io.yak.ops.business.quality.domain.QualityDomain.TableMonitorSummary;
import io.yak.ops.business.quality.domain.QualityQuery;
import io.yak.ops.common.enums.quality.QualityEnums.CheckResult;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** Persistence port for quality monitor definitions, settings and rules. */
public interface QualityMonitorRepository {
  PageData<Monitor> pageMonitors(QualityQuery.Monitor query);
  Optional<Monitor> findMonitor(long id);
  List<TableMonitorSummary> tableSummaries(long dataSourceId, String databaseName, String schemaName);
  List<AlertEvent> recentAlertEvents(int limit);
  long countAlertEventsSince(LocalDateTime since);
  boolean existsMonitorForTarget(Long excludeId, long dataSourceId, String databaseName, String schemaName, String tableName);
  long insertMonitor(MonitorSpec monitor);
  boolean updateMonitor(long id, MonitorSpec monitor);
  boolean deleteMonitor(long id);
  MonitorSettings findMonitorSettings(long monitorId);
  void upsertMonitorSettings(long monitorId, MonitorSettingsSpec settings);
  void replaceRules(long monitorId, List<RuleSpec> rules);
  List<Rule> listRules(long monitorId);
  void lockMonitor(long monitorId);
  boolean updateMonitorResult(long monitorId, String executionNo, CheckResult result, LocalDateTime runTime);
}
