package io.yak.ops.business.quality.dao.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.yak.ops.business.quality.config.ConditionalOnQualityEnabled;
import io.yak.ops.business.quality.dao.QualityMonitorDao;
import io.yak.ops.business.quality.dao.mapper.QualityAlertEventMapper;
import io.yak.ops.business.quality.dao.mapper.QualityMonitorMapper;
import io.yak.ops.business.quality.dao.mapper.QualityMonitorSettingMapper;
import io.yak.ops.business.quality.dao.mapper.QualityQueryMapper;
import io.yak.ops.business.quality.dao.mapper.QualityRuleMapper;
import io.yak.ops.business.quality.dao.mapper.QualityTableAssetMapper;
import io.yak.ops.business.quality.dao.mapper.QualityWriteMapper;
import io.yak.ops.common.bean.po.quality.QualityAlertEventPO;
import io.yak.ops.common.bean.po.quality.QualityMonitorPO;
import io.yak.ops.common.bean.po.quality.QualityMonitorSettingPO;
import io.yak.ops.common.bean.po.quality.QualityQueryPO.AlertEventRow;
import io.yak.ops.common.bean.po.quality.QualityQueryPO.MonitorRow;
import io.yak.ops.common.bean.po.quality.QualityQueryPO.TableAssetRow;
import io.yak.ops.common.bean.po.quality.QualityQueryPO.TableMonitorSummaryRow;
import io.yak.ops.common.bean.po.quality.QualityRulePO;
import io.yak.ops.common.bean.po.quality.QualityTableAssetPO;
import io.yak.ops.core.project.CurrentProject;
import io.yak.ops.core.project.ProjectContextError;
import io.yak.ops.core.project.ProjectContextException;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
@ConditionalOnQualityEnabled
@DependsOn("qualityFlyway")
public class QualityMonitorDaoImpl implements QualityMonitorDao {
  private final QualityQueryMapper queryMapper;
  private final QualityWriteMapper writeMapper;
  private final QualityMonitorMapper monitorMapper;
  private final QualityRuleMapper ruleMapper;
  private final QualityMonitorSettingMapper settingMapper;
  private final QualityTableAssetMapper tableAssetMapper;
  private final QualityAlertEventMapper alertEventMapper;
  private final CurrentProject currentProject;

  @Override
  public long countMonitors(Map<String, Object> params) {
    return queryMapper.countMonitors(scoped(params));
  }

  @Override
  public List<MonitorRow> selectMonitors(Map<String, Object> params) {
    return queryMapper.selectMonitors(scoped(params));
  }

  @Override
  public MonitorRow selectMonitor(long id) {
    return queryMapper.selectMonitor(currentProjectId(), id);
  }

  @Override
  public List<ProjectMonitorRef> selectScheduledMonitorsForRecovery() {
    return queryMapper.selectScheduledMonitorsForRecovery().stream()
        .map(
            row ->
                new ProjectMonitorRef(
                    requirePositive(row.getProjectId(), "质量监控缺少 Project 归属"),
                    requirePositive(row.getId(), "质量监控恢复引用缺少监控编号")))
        .toList();
  }

  @Override
  public List<TableMonitorSummaryRow> selectTableSummaries(Map<String, Object> params) {
    return queryMapper.selectTableSummaries(scoped(params));
  }

  @Override
  public long insertMonitor(QualityMonitorPO monitor) {
    long projectId = currentProjectId();
    bindProject(monitor, projectId);
    monitorMapper.insert(monitor);
    if (monitor.getId() == null) {
      throw new IllegalStateException("质量监控创建成功，但未返回监控编号");
    }
    return monitor.getId();
  }

  @Override
  public boolean updateMonitor(QualityMonitorPO monitor) {
    long projectId = currentProjectId();
    bindProject(monitor, projectId);
    return monitorMapper.update(
        monitor,
        Wrappers.<QualityMonitorPO>lambdaUpdate()
            .eq(QualityMonitorPO::getProjectId, projectId)
            .eq(QualityMonitorPO::getId, monitor.getId())
            .eq(QualityMonitorPO::getDeleted, false)) > 0;
  }

  @Override
  public boolean softDeleteMonitor(long id) {
    long projectId = currentProjectId();
    int affected =
        monitorMapper.update(
            null,
            Wrappers.<QualityMonitorPO>lambdaUpdate()
                .eq(QualityMonitorPO::getProjectId, projectId)
                .eq(QualityMonitorPO::getId, id)
                .eq(QualityMonitorPO::getDeleted, false)
                .set(QualityMonitorPO::getDeleted, true)
                .set(QualityMonitorPO::getEnabled, false));
    if (affected > 0) {
      ruleMapper.update(
          null,
          Wrappers.<QualityRulePO>lambdaUpdate()
              .eq(QualityRulePO::getMonitorId, id)
              .eq(QualityRulePO::getDeleted, false)
              .set(QualityRulePO::getDeleted, true)
              .set(QualityRulePO::getEnabled, false));
    }
    return affected > 0;
  }

  @Override
  public boolean existsMonitorTarget(
      Long excludeId,
      long dataSourceId,
      String databaseName,
      String schemaName,
      String tableName) {
    long projectId = currentProjectId();
    var query =
        Wrappers.<QualityMonitorPO>lambdaQuery()
            .eq(QualityMonitorPO::getProjectId, projectId)
            .eq(QualityMonitorPO::getDeleted, false)
            .eq(QualityMonitorPO::getDataSourceId, dataSourceId)
            .eq(QualityMonitorPO::getTableName, tableName);
    if (databaseName == null) query.isNull(QualityMonitorPO::getDatabaseName);
    else query.eq(QualityMonitorPO::getDatabaseName, databaseName);
    if (schemaName == null) query.isNull(QualityMonitorPO::getSchemaName);
    else query.eq(QualityMonitorPO::getSchemaName, schemaName);
    if (excludeId != null) query.ne(QualityMonitorPO::getId, excludeId);
    return monitorMapper.selectCount(query) > 0;
  }

  @Override
  public void lockMonitor(long monitorId) {
    writeMapper.lockMonitor(currentProjectId(), monitorId);
  }

  @Override
  public boolean updateMonitorResult(
      long monitorId,
      String executionNo,
      String result,
      LocalDateTime runTime) {
    long projectId = currentProjectId();
    return monitorMapper.update(
        null,
        Wrappers.<QualityMonitorPO>lambdaUpdate()
            .eq(QualityMonitorPO::getProjectId, projectId)
            .eq(QualityMonitorPO::getId, monitorId)
            .eq(QualityMonitorPO::getDeleted, false)
            .set(QualityMonitorPO::getLastExecutionNo, executionNo)
            .set(QualityMonitorPO::getLastResult, result)
            .set(QualityMonitorPO::getLastRunTime, runTime)) > 0;
  }

  @Override
  public List<QualityRulePO> selectRules(long monitorId) {
    requireActiveMonitor(monitorId);
    return ruleMapper.selectList(
        Wrappers.<QualityRulePO>lambdaQuery()
            .eq(QualityRulePO::getMonitorId, monitorId)
            .eq(QualityRulePO::getDeleted, false)
            .orderByAsc(QualityRulePO::getSortOrder)
            .orderByAsc(QualityRulePO::getId));
  }

  @Override
  public void replaceRules(long monitorId, List<QualityRulePO> rules) {
    requireActiveMonitor(monitorId);
    ruleMapper.update(
        null,
        Wrappers.<QualityRulePO>lambdaUpdate()
            .eq(QualityRulePO::getMonitorId, monitorId)
            .eq(QualityRulePO::getDeleted, false)
            .set(QualityRulePO::getDeleted, true)
            .set(QualityRulePO::getEnabled, false));
    for (QualityRulePO rule : rules) ruleMapper.insert(rule);
  }

  @Override
  public QualityMonitorSettingPO selectSetting(long monitorId) {
    requireOwnedMonitor(monitorId);
    return settingMapper.selectById(monitorId);
  }

  @Override
  public void upsertSetting(QualityMonitorSettingPO setting) {
    requireOwnedMonitor(setting.getMonitorId());
    writeMapper.upsertMonitorSetting(setting);
  }

  @Override
  public void insertAlert(QualityAlertEventPO alert) {
    requireOwnedMonitor(alert.getMonitorId());
    alertEventMapper.insert(alert);
  }

  @Override
  public List<AlertEventRow> selectRecentAlertEvents(int limit) {
    return queryMapper.selectRecentAlertEvents(currentProjectId(), limit);
  }

  @Override
  public long countAlertEventsSince(LocalDateTime since) {
    return queryMapper.countAlertEventsSince(currentProjectId(), since);
  }

  @Override
  public long countTableAssets(Map<String, Object> params) {
    return queryMapper.countTableAssets(scoped(params));
  }

  @Override
  public List<TableAssetRow> selectTableAssets(Map<String, Object> params) {
    return queryMapper.selectTableAssets(scoped(params));
  }

  @Override
  public List<QualityTableAssetPO> selectTableAssetTargets(
      long dataSourceId,
      String databaseName) {
    long projectId = currentProjectId();
    var query =
        Wrappers.<QualityTableAssetPO>lambdaQuery()
            .eq(QualityTableAssetPO::getProjectId, projectId)
            .eq(QualityTableAssetPO::getDeleted, false)
            .eq(QualityTableAssetPO::getDataSourceId, dataSourceId);
    if (databaseName != null) {
      query.eq(QualityTableAssetPO::getDatabaseName, databaseName);
    }
    return tableAssetMapper.selectList(query);
  }

  @Override
  public boolean existsTableAssetTarget(
      long dataSourceId,
      String databaseName,
      String schemaName,
      String tableName) {
    long projectId = currentProjectId();
    return tableAssetMapper.selectCount(
            Wrappers.<QualityTableAssetPO>lambdaQuery()
                .eq(QualityTableAssetPO::getProjectId, projectId)
                .eq(QualityTableAssetPO::getDeleted, false)
                .eq(QualityTableAssetPO::getDataSourceId, dataSourceId)
                .eq(
                    QualityTableAssetPO::getDatabaseName,
                    databaseName == null ? "" : databaseName)
                .eq(
                    QualityTableAssetPO::getSchemaName,
                    schemaName == null ? "" : schemaName)
                .eq(QualityTableAssetPO::getTableName, tableName))
        > 0;
  }

  @Override
  public int upsertTableAssets(List<QualityTableAssetPO> assets) {
    if (assets == null || assets.isEmpty()) return 0;
    long projectId = currentProjectId();
    for (QualityTableAssetPO asset : assets) {
      bindProject(asset, projectId);
      writeMapper.upsertTableAsset(asset);
    }
    return assets.size();
  }

  @Override
  public int countMonitorsForAsset(long assetId) {
    return queryMapper.countMonitorsForAsset(currentProjectId(), assetId);
  }

  @Override
  public boolean softDeleteTableAsset(long assetId) {
    long projectId = currentProjectId();
    return tableAssetMapper.update(
            null,
            Wrappers.<QualityTableAssetPO>lambdaUpdate()
                .eq(QualityTableAssetPO::getProjectId, projectId)
                .eq(QualityTableAssetPO::getId, assetId)
                .eq(QualityTableAssetPO::getDeleted, false)
                .set(QualityTableAssetPO::getDeleted, true)
                .set(QualityTableAssetPO::getUpdatedAt, LocalDateTime.now()))
        > 0;
  }

  private Map<String, Object> scoped(Map<String, Object> params) {
    Map<String, Object> scoped = new LinkedHashMap<>();
    if (params != null) scoped.putAll(params);
    scoped.put("projectId", currentProjectId());
    return scoped;
  }

  private void requireActiveMonitor(long monitorId) {
    long projectId = currentProjectId();
    Long count =
        monitorMapper.selectCount(
            Wrappers.<QualityMonitorPO>lambdaQuery()
                .eq(QualityMonitorPO::getProjectId, projectId)
                .eq(QualityMonitorPO::getId, monitorId)
                .eq(QualityMonitorPO::getDeleted, false));
    if (count == null || count == 0L) throw projectNotFound();
  }

  private void requireOwnedMonitor(Long monitorId) {
    if (monitorId == null || monitorId <= 0L) throw projectNotFound();
    long projectId = currentProjectId();
    Long count =
        monitorMapper.selectCount(
            Wrappers.<QualityMonitorPO>lambdaQuery()
                .eq(QualityMonitorPO::getProjectId, projectId)
                .eq(QualityMonitorPO::getId, monitorId));
    if (count == null || count == 0L) throw projectNotFound();
  }

  private void bindProject(QualityMonitorPO monitor, long projectId) {
    requireCompatibleProject(monitor.getProjectId(), projectId);
    monitor.setProjectId(projectId);
  }

  private void bindProject(QualityTableAssetPO asset, long projectId) {
    requireCompatibleProject(asset.getProjectId(), projectId);
    asset.setProjectId(projectId);
  }

  private void requireCompatibleProject(Long requestedProjectId, long currentProjectId) {
    if (requestedProjectId != null && !Objects.equals(requestedProjectId, currentProjectId)) {
      throw projectNotFound();
    }
  }

  private long currentProjectId() {
    return currentProject.requireProjectId();
  }

  private static long requirePositive(Long value, String message) {
    if (value == null || value <= 0L) throw new IllegalStateException(message);
    return value;
  }

  private static ProjectContextException projectNotFound() {
    return new ProjectContextException(ProjectContextError.PROJECT_NOT_FOUND);
  }
}
