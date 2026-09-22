package io.yak.ops.business.dataset.query.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.dataset.Dataset;
import io.yak.ops.business.dataset.DatasetField;
import io.yak.ops.business.dataset.DatasetQueryRequest;
import io.yak.ops.business.dataset.DatasetQueryResult;
import io.yak.ops.business.dataset.DatasetSourceType;
import io.yak.ops.business.dataset.DatasetVersion;
import io.yak.ops.business.dataset.gateway.taskcatalog.DatasetTaskCatalogGateway;
import io.yak.ops.business.dataset.gateway.taskcatalog.DatasetTaskCatalogGateway.DatasetTaskRevisionSnapshot;
import io.yak.ops.business.dataset.query.DatasetQueryCompiler;
import io.yak.ops.business.dataset.query.DatasetSourceQueryAdapter;
import io.yak.ops.core.execution.sql.SqlExecutionCaller;
import io.yak.ops.core.execution.sql.SqlExecutionContext;
import io.yak.ops.core.execution.sql.SqlExecutionRequest;
import io.yak.ops.core.execution.sql.SqlExecutionResult;
import io.yak.ops.core.execution.sql.SqlExecutionRuntime;
import java.util.List;
import org.springframework.stereotype.Component;

/** Executes an exact QUERY_REVISION DatasetVersion without invoking task execution semantics. */
@Component
public class QueryRevisionDatasetSourceAdapter implements DatasetSourceQueryAdapter {

  private static final int DEFAULT_TIMEOUT_SECONDS = 30;
  private static final int MAX_TIMEOUT_SECONDS = 120;

  private final DatasetTaskCatalogGateway taskCatalogGateway;
  private final SqlExecutionRuntime sqlExecutionRuntime;
  private final ObjectMapper objectMapper;
  private final DatasetQueryCompiler compiler;

  public QueryRevisionDatasetSourceAdapter(
      DatasetTaskCatalogGateway taskCatalogGateway,
      SqlExecutionRuntime sqlExecutionRuntime,
      ObjectMapper objectMapper,
      DatasetQueryCompiler compiler) {
    this.taskCatalogGateway = taskCatalogGateway;
    this.sqlExecutionRuntime = sqlExecutionRuntime;
    this.objectMapper = objectMapper;
    this.compiler = compiler;
  }

  @Override
  public DatasetSourceType sourceType() {
    return DatasetSourceType.QUERY_REVISION;
  }

  @Override
  public ExecutionResult execute(
      Dataset dataset,
      DatasetVersion version,
      List<DatasetField> fields,
      DatasetQueryRequest request) {
    long prepareStartedAt = System.nanoTime();
    DatasetTaskRevisionSnapshot revision =
        taskCatalogGateway.resolveRevision(
            version.sourceTaskAssetId(), version.sourceTaskRevisionId());
    if (revision.revisionId() != version.sourceTaskRevisionId()
        || revision.revisionNo() != version.sourceTaskRevisionNo()) {
      throw new IllegalStateException("DatasetVersion 与来源 TaskRevision 快照不一致");
    }
    if (!"SQL".equalsIgnoreCase(revision.taskType())) {
      throw new IllegalStateException("QUERY_REVISION Dataset 来源必须是 SQL TaskRevision");
    }

    SourceConfig sourceConfig = sourceConfig(revision.configJson());
    DatasetQueryCompiler.CompiledQuery compiled =
        compiler.compile(revision.content(), fields, request);
    int timeoutSeconds = queryTimeout(request, sourceConfig.timeoutSeconds());
    long prepareMillis = elapsedMillis(prepareStartedAt);
    long runtimeStartedAt = System.nanoTime();

    SqlExecutionResult result =
        sqlExecutionRuntime.execute(
            new SqlExecutionRequest(
                sourceConfig.dataSourceId(),
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
        sourceConfig.dataSourceId(),
        compiled.sql(),
        prepareMillis,
        waitMillis,
        executeMillis,
        transferMillis);
  }

  private SourceConfig sourceConfig(String configJson) {
    String raw = configJson == null || configJson.isBlank() ? "{}" : configJson.trim();
    try {
      JsonNode root = objectMapper.readTree(raw);
      if (root == null || !root.isObject()) {
        throw new IllegalArgumentException("SQL configJson 必须是 JSON 对象");
      }
      JsonNode dataSourceNode = root.get("dataSourceId");
      String dataSourceId =
          dataSourceNode == null || dataSourceNode.isNull() ? null : dataSourceNode.asText();
      if (dataSourceId == null || dataSourceId.isBlank()) {
        throw new IllegalArgumentException("SQL TaskRevision 缺少 dataSourceId，无法执行 Dataset 查询");
      }
      int sourceTimeout = root.path("timeoutSeconds").asInt(DEFAULT_TIMEOUT_SECONDS);
      if (sourceTimeout < 1) {
        sourceTimeout = DEFAULT_TIMEOUT_SECONDS;
      }
      return new SourceConfig(
          dataSourceId.trim(), Math.min(sourceTimeout, MAX_TIMEOUT_SECONDS));
    } catch (IllegalArgumentException exception) {
      throw exception;
    } catch (Exception exception) {
      throw new IllegalArgumentException("SQL TaskRevision configJson 非法", exception);
    }
  }

  private int queryTimeout(DatasetQueryRequest request, int sourceTimeout) {
    Integer requested = request == null ? null : request.timeoutSeconds();
    if (requested == null) {
      return Math.max(1, Math.min(sourceTimeout, MAX_TIMEOUT_SECONDS));
    }
    if (requested < 1 || requested > MAX_TIMEOUT_SECONDS) {
      throw new IllegalArgumentException("timeoutSeconds 必须在 1~120 之间");
    }
    return requested;
  }

  private static long elapsedMillis(long startedAt) {
    return Math.max(0L, (System.nanoTime() - startedAt) / 1_000_000L);
  }

  private record SourceConfig(String dataSourceId, int timeoutSeconds) {}
}
