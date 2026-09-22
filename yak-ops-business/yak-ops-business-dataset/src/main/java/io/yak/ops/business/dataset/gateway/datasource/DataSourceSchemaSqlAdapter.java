package io.yak.ops.business.dataset.gateway.datasource;

import io.yak.ops.spi.datasource.execution.DataSourceExecutionProvider;
import io.yak.ops.spi.datasource.execution.DataSourceSqlExecutor;
import io.yak.ops.spi.datasource.execution.DataSourceSqlRequest;
import io.yak.ops.spi.datasource.execution.DataSourceSqlResult;
import org.springframework.stereotype.Component;

/** Converts the datasource execution SPI into Dataset-owned schema/query values. */
@Component
public class DataSourceSchemaSqlAdapter implements DatasetSchemaSqlGateway {

  private final DataSourceExecutionProvider executionProvider;

  public DataSourceSchemaSqlAdapter(DataSourceExecutionProvider executionProvider) {
    this.executionProvider = executionProvider;
  }

  @Override
  public QueryResult execute(
      String dataSourceId, String sql, int maxRows, int timeoutSeconds) {
    DataSourceSqlResult result;
    try (DataSourceSqlExecutor executor = executionProvider.open(dataSourceId)) {
      result = executor.execute(new DataSourceSqlRequest(sql, maxRows, timeoutSeconds));
    }
    return new QueryResult(
        result.resultSet(),
        result.columns().stream()
            .map(
                column ->
                    new QueryColumn(
                        column.name(),
                        column.label(),
                        column.typeName(),
                        column.jdbcType(),
                        column.nullable()))
            .toList(),
        result.rows(),
        result.truncated());
  }
}
