package io.yak.ops.business.quality.dao;

import io.yak.ops.common.bean.po.quality.QualityAlertEventPO;
import io.yak.ops.common.bean.po.quality.QualityMonitorPO;
import io.yak.ops.common.bean.po.quality.QualityMonitorSettingPO;
import io.yak.ops.common.bean.po.quality.QualityQueryPO.AlertEventRow;
import io.yak.ops.common.bean.po.quality.QualityQueryPO.MonitorRow;
import io.yak.ops.common.bean.po.quality.QualityQueryPO.TableAssetRow;
import io.yak.ops.common.bean.po.quality.QualityQueryPO.TableMonitorSummaryRow;
import io.yak.ops.common.bean.po.quality.QualityRulePO;
import io.yak.ops.common.bean.po.quality.QualityTableAssetPO;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** 质量监控、规则、注册表与运行设置数据访问边界。 */
public interface QualityMonitorDao {
  long countMonitors(Map<String, Object> params);
  List<MonitorRow> selectMonitors(Map<String, Object> params);
  MonitorRow selectMonitor(long id);
  List<ProjectMonitorRef> selectScheduledMonitorsForRecovery();
  List<TableMonitorSummaryRow> selectTableSummaries(Map<String, Object> params);
  long insertMonitor(QualityMonitorPO monitor);
  boolean updateMonitor(QualityMonitorPO monitor);
  boolean softDeleteMonitor(long id);
  boolean existsMonitorTarget(Long excludeId, long dataSourceId, String databaseName, String schemaName, String tableName);
  void lockMonitor(long monitorId);
  boolean updateMonitorResult(long monitorId, String executionNo, String result, LocalDateTime runTime);

  List<QualityRulePO> selectRules(long monitorId);
  void replaceRules(long monitorId, List<QualityRulePO> rules);

  QualityMonitorSettingPO selectSetting(long monitorId);
  void upsertSetting(QualityMonitorSettingPO setting);
  void insertAlert(QualityAlertEventPO alert);
  List<AlertEventRow> selectRecentAlertEvents(int limit);
  long countAlertEventsSince(LocalDateTime since);

  long countTableAssets(Map<String, Object> params);
  List<TableAssetRow> selectTableAssets(Map<String, Object> params);
  List<QualityTableAssetPO> selectTableAssetTargets(long dataSourceId, String databaseName);
  boolean existsTableAssetTarget(long dataSourceId, String databaseName, String schemaName, String tableName);
  int upsertTableAssets(List<QualityTableAssetPO> assets);
  int countMonitorsForAsset(long assetId);
  boolean softDeleteTableAsset(long assetId);

  /** Explicit cross-Project startup dispatch reference; ordinary CRUD remains CurrentProject-bound. */
  record ProjectMonitorRef(long projectId, long monitorId) {
    public ProjectMonitorRef {
      if (projectId <= 0L) throw new IllegalArgumentException("projectId must be positive");
      if (monitorId <= 0L) throw new IllegalArgumentException("monitorId must be positive");
    }
  }
}
