package io.yak.ops.business.quality.asset;

import io.yak.ops.business.quality.config.ConditionalOnQualityEnabled;
import io.yak.ops.business.quality.gateway.datasource.QualityDataCatalogGateway;
import io.yak.ops.business.quality.gateway.datasource.QualityDataCatalogGateway.QualityPhysicalTable;
import io.yak.ops.business.quality.repository.QualityTableAssetRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Finds physical datasource tables that have not been registered as quality assets. */
@Component
@ConditionalOnQualityEnabled
public class QualityTableCandidateReader {
  private final QualityTableAssetRepository repository;
  private final QualityDataCatalogGateway catalogGateway;
  private final QualityTableTargetPolicy targetPolicy;

  public QualityTableCandidateReader(
      QualityTableAssetRepository repository,
      QualityDataCatalogGateway catalogGateway,
      QualityTableTargetPolicy targetPolicy) {
    this.repository = repository;
    this.catalogGateway = catalogGateway;
    this.targetPolicy = targetPolicy;
  }

  @Transactional(readOnly = true, transactionManager = "yakBusinessTransactionManager")
  public CandidatePage candidates(
      long dataSourceId,
      String databaseName,
      String schemaName,
      String keyword,
      int current,
      int pageSize) {
    targetPolicy.requireDataSourceId(dataSourceId);
    int safeCurrent = Math.max(1, current);
    int safePageSize = Math.max(1, Math.min(pageSize, 100));
    Set<String> registeredTargets =
        repository.listTableAssetTargets(dataSourceId, databaseName).stream()
            .map(
                target ->
                    targetPolicy.targetKey(
                        target.databaseName(), target.schemaName(), target.tableName()))
            .collect(Collectors.toSet());
    List<QualityPhysicalTable> available =
        catalogGateway.listTables(
                dataSourceId,
                databaseName,
                schemaName,
                targetPolicy.trimToNull(keyword))
            .stream()
            .map(table -> normalize(table, databaseName))
            .filter(
                table ->
                    !registeredTargets.contains(
                        targetPolicy.targetKey(
                            table.databaseName(), table.schemaName(), table.tableName())))
            .sorted(
                Comparator.comparing(
                    QualityPhysicalTable::tableName, String.CASE_INSENSITIVE_ORDER))
            .toList();
    int fromIndex = Math.min((safeCurrent - 1) * safePageSize, available.size());
    int toIndex = Math.min(fromIndex + safePageSize, available.size());
    return new CandidatePage(
        available.subList(fromIndex, toIndex), available.size(), safeCurrent, safePageSize);
  }

  private QualityPhysicalTable normalize(
      QualityPhysicalTable table, String selectedDatabase) {
    return new QualityPhysicalTable(
        targetPolicy.firstNonBlank(table.databaseName(), selectedDatabase),
        targetPolicy.trimToNull(table.schemaName()),
        table.tableName(),
        targetPolicy.trimToNull(table.tableType()),
        targetPolicy.trimToNull(table.remarks()));
  }

  public record CandidatePage(
      List<QualityPhysicalTable> records,
      long total,
      int current,
      int pageSize) {
    public CandidatePage {
      records = records == null ? List.of() : List.copyOf(records);
    }
  }
}
