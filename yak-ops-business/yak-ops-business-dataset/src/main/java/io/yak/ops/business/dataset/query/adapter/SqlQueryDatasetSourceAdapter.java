package io.yak.ops.business.dataset.query.adapter;

import io.yak.ops.business.dataset.Dataset;
import io.yak.ops.business.dataset.DatasetField;
import io.yak.ops.business.dataset.DatasetQueryRequest;
import io.yak.ops.business.dataset.DatasetQueryResult;
import io.yak.ops.business.dataset.DatasetSourceType;
import io.yak.ops.business.dataset.DatasetVersion;
import io.yak.ops.business.dataset.query.DatasetQueryCompiler;
import io.yak.ops.business.dataset.query.DatasetSourceQueryAdapter;
import io.yak.ops.core.execution.sql.SqlExecutionCaller;
import io.yak.ops.core.execution.sql.SqlExecutionContext;
import io.yak.ops.core.execution.sql.SqlExecutionRequest;
import io.yak.ops.core.execution.sql.SqlExecutionResult;
import io.yak.ops.core.execution.sql.SqlExecutionRuntime;
import java.util.List;
import org.springframework.stereotype.Component;

/** Query runtime for immutable DatasetVersions that own datasource + SQL directly. */
@Component
public class SqlQueryDatasetSourceAdapter implements DatasetSourceQueryAdapter {

  private static final int DEFAULT_TIMEOUT_SECONDS = 30;
  private static final int MAX_TIMEOUT_SECONDS = 120;

  private final SqlExecutionRuntime sqlExecutionRuntime;
  private final DatasetQueryCompiler compiler;

  public SqlQueryDatasetSourceAdapter(
      SqlExecutionRuntime sqlExecutionRuntime, DatasetQueryCompiler compiler) {
    this.sqlExecutionRuntime = sqlExecutionRuntime;
    this.compiler = compiler;
  }

  @Override
  public DatasetSourceType sourceType() {
    return DatasetSourceType.SQL_QUERY;
  }

  @Override
  public ExecutionResult execute(
      Dataset dataset,
      DatasetVersion version,
      List<DatasetField> fields,
      DatasetQueryRequest request) {
    long prepareStartedAt = System.nanoTime();
    if (version.dataSourceId() == null || version.dataSourceId().isBlank()) {
      throw new IllegalStateException("SQL_QUERY DatasetVersion 缺少 dataSourceId");
    }
    if (version.sql() == null || version.sql().isBlank()) {
      throw new IllegalStateException("SQL_QUERY DatasetVersion 缺少 SQL");
    }

    DatasetQueryCompiler.CompiledQuery compiled = compiler.compile(version.sql(), fields, request);
    int timeoutSeconds = queryTimeout(request);
    long prepareMillis = elapsedMillis(prepareStartedAt);
    long runtimeStartedAt = System.nanoTime();

    SqlExecutionResult result =
        sqlExecutionRuntime.execute(
            new SqlExecutionRequest(
                version.dataSourceId(),
                compiled.sql(),
                compiled.fetchRows(),
                timeoutSeconds,
                SqlExecutionContext.of(
                    SqlExecutionCaller.DATASET, String.valueOf(dataset.id()))));
    long waitMillis = result.timing().openMillis();
    long executeMillis = result.timing().executeMillis();

    long transferStartedAt = System.nanoTime();
    if (!result.resultSet()) {
      throw new IllegalStateException("Dataset Query Runtime 只接受结果集查询");
    }
    boolean overflow = result.rows().size() > compiled.limit();
    List<List<Object>> rows =
        overflow ? result.rows().subList(0, compiled.limit()) : result.rows();
    long transferMillis = elapsedMillis(transferStartedAt);
    long elapsedMillis = elapsedMillis(runtimeStartedAt);

    DatasetQueryResult queryResult =
        new DatasetQueryResult(
            dataset.id(),
            version.id(),
            version.versionNo(),
            compiled.bindings(),
            result.columns(),
            rows,
            rows.size(),
            result.truncated() || overflow,
            elapsedMillis);
    return new ExecutionResult(
        queryResult,
        version.dataSourceId(),
        compiled.sql(),
        prepareMillis,
        waitMillis,
        executeMillis,
        transferMillis);
  }

  private int queryTimeout(DatasetQueryRequest request) {
    Integer requested = request == null ? null : request.timeoutSeconds();
    if (requested == null) {
      return DEFAULT_TIMEOUT_SECONDS;
    }
    if (requested < 1 || requested > MAX_TIMEOUT_SECONDS) {
      throw new IllegalArgumentException("timeoutSeconds 必须在 1~120 之间");
    }
    return requested;
  }

  private static long elapsedMillis(long startedAt) {
    return Math.max(0L, (System.nanoTime() - startedAt) / 1_000_000L);
  }
}
