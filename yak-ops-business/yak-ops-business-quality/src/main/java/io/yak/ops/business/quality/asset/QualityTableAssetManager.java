package io.yak.ops.business.quality.asset;

import io.yak.ops.business.quality.config.ConditionalOnQualityEnabled;
import io.yak.ops.business.quality.domain.QualityDomain.TableAssetSpec;
import io.yak.ops.business.quality.gateway.datasource.QualityDataCatalogGateway;
import io.yak.ops.business.quality.gateway.datasource.QualityDataCatalogGateway.QualityPhysicalTable;
import io.yak.ops.business.quality.repository.QualityTableAssetRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Command-side lifecycle for quality table registration. */
@Component
@ConditionalOnQualityEnabled
public class QualityTableAssetManager {
  private final QualityTableAssetRepository repository;
  private final QualityDataCatalogGateway catalogGateway;
  private final QualityTableTargetPolicy targetPolicy;

  public QualityTableAssetManager(
      QualityTableAssetRepository repository,
      QualityDataCatalogGateway catalogGateway,
      QualityTableTargetPolicy targetPolicy) {
    this.repository = repository;
    this.catalogGateway = catalogGateway;
    this.targetPolicy = targetPolicy;
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager")
  public RegisterResult register(QualityTableAssetCommand.Register command, String operator) {
    if (command.dataSourceId() == null) {
      throw new IllegalArgumentException("数据源编号无效");
    }
    targetPolicy.requireDataSourceId(command.dataSourceId());
    String selectedDatabase = targetPolicy.trimToNull(command.databaseName());
    List<QualityPhysicalTable> physicalTables =
        catalogGateway.listTables(command.dataSourceId(), selectedDatabase, null, null);

    Map<String, QualityPhysicalTable> physicalTableMap = new LinkedHashMap<>();
    for (QualityPhysicalTable table : physicalTables) {
      String key =
          targetPolicy.targetKey(
              targetPolicy.firstNonBlank(table.databaseName(), selectedDatabase),
              table.schemaName(),
              table.tableName());
      physicalTableMap.putIfAbsent(key, table);
    }

    Map<String, QualityTableAssetCommand.Item> requestedTables = new LinkedHashMap<>();
    for (QualityTableAssetCommand.Item item : command.tables()) {
      String database = targetPolicy.firstNonBlank(item.databaseName(), selectedDatabase);
      requestedTables.putIfAbsent(
          targetPolicy.targetKey(database, item.schemaName(), item.tableName()), item);
    }

    String registeredBy = normalizeOperator(operator);
    List<TableAssetSpec> writes =
        requestedTables.entrySet().stream()
            .map(
                entry -> {
                  QualityTableAssetCommand.Item requested = entry.getValue();
                  QualityPhysicalTable physical = physicalTableMap.get(entry.getKey());
                  if (physical == null) {
                    throw new IllegalArgumentException(
                        "数据表已不存在或无法通过数据源插件发现：" + requested.tableName());
                  }
                  return new TableAssetSpec(
                      command.dataSourceId(),
                      requireText(command.dataSourceName(), "数据源名称不能为空"),
                      targetPolicy.firstNonBlank(physical.databaseName(), selectedDatabase),
                      targetPolicy.trimToNull(physical.schemaName()),
                      physical.tableName(),
                      targetPolicy.trimToNull(physical.tableType()),
                      targetPolicy.trimToNull(physical.remarks()),
                      registeredBy);
                })
            .toList();

    int registered = repository.registerTableAssets(writes);
    return new RegisterResult(command.tables().size(), registered);
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager")
  public boolean unregister(long id) {
    if (id <= 0L) {
      throw new IllegalArgumentException("注册表编号无效");
    }
    if (repository.countMonitorsForTableAsset(id) > 0) {
      throw new IllegalStateException("当前数据表已配置质量监控，请先删除监控后再取消注册");
    }
    if (!repository.deleteTableAsset(id)) {
      throw new IllegalArgumentException("注册数据表不存在：" + id);
    }
    return true;
  }

  private String normalizeOperator(String operator) {
    String normalized = targetPolicy.trimToNull(operator);
    return normalized == null ? "system" : normalized;
  }

  private String requireText(String value, String message) {
    String normalized = targetPolicy.trimToNull(value);
    if (normalized == null) {
      throw new IllegalArgumentException(message);
    }
    return normalized;
  }

  public record RegisterResult(int requested, int registered) {}
}
