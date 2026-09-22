package io.yak.ops.business.quality.monitor;

import io.yak.ops.business.quality.config.ConditionalOnQualityEnabled;
import io.yak.ops.business.quality.repository.QualityMonitorRepository;
import io.yak.ops.business.quality.repository.QualityTableAssetRepository;
import org.springframework.stereotype.Component;

/** Invariants for the monitored physical target and optional WHERE fragment. */
@Component
@ConditionalOnQualityEnabled
public class QualityMonitorPolicy {
  private final QualityMonitorRepository monitorRepository;
  private final QualityTableAssetRepository tableAssetRepository;

  public QualityMonitorPolicy(
      QualityMonitorRepository monitorRepository,
      QualityTableAssetRepository tableAssetRepository) {
    this.monitorRepository = monitorRepository;
    this.tableAssetRepository = tableAssetRepository;
  }

  public void validateTarget(Long excludeId, QualityMonitorCommand.Save command) {
    if (command.dataSourceId() == null || command.dataSourceId() <= 0L) {
      throw new IllegalArgumentException("请选择有效的数据源");
    }
    String tableName = requireText(command.tableName(), "数据表名称不能为空");
    if (!tableAssetRepository.existsTableAssetTarget(
        command.dataSourceId(), command.databaseName(), command.schemaName(), tableName)) {
      throw new IllegalStateException("当前数据表尚未注册，请先在按表配置页面注册数据表");
    }
    if (monitorRepository.existsMonitorForTarget(
        excludeId, command.dataSourceId(), command.databaseName(), command.schemaName(), tableName)) {
      throw new IllegalStateException("当前数据表已经创建质量监控，请直接进入监控详情");
    }
    validateWhereClause(command.whereClause());
  }

  private void validateWhereClause(String value) {
    String filter = trimToNull(value);
    if (filter == null) return;
    String upper = filter.toUpperCase();
    if (filter.contains(";") || upper.contains("--") || upper.contains("/*")
        || upper.matches("(?s).*\\b(INSERT|UPDATE|DELETE|DROP|ALTER|TRUNCATE|CREATE)\\b.*")) {
      throw new IllegalArgumentException("数据范围仅允许填写 WHERE 条件片段");
    }
  }

  static String requireText(String value, String message) {
    String normalized = trimToNull(value);
    if (normalized == null) throw new IllegalArgumentException(message);
    return normalized;
  }

  static String trimToNull(String value) {
    if (value == null) return null;
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }
}
