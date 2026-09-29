package io.yak.ops.business.dataset.schema;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.dataset.DatasetFieldDataType;
import io.yak.ops.business.dataset.DatasetFieldRole;
import io.yak.ops.business.dataset.DatasetSqlSafety;
import io.yak.ops.business.dataset.gateway.datasource.DatasetSchemaSqlGateway;
import io.yak.ops.business.dataset.gateway.datasource.DatasetSchemaSqlGateway.QueryColumn;
import io.yak.ops.business.dataset.gateway.datasource.DatasetSchemaSqlGateway.QueryResult;
import io.yak.ops.business.dataset.gateway.taskcatalog.DatasetTaskCatalogGateway;
import io.yak.ops.business.dataset.gateway.taskcatalog.DatasetTaskCatalogGateway.DatasetTaskAssetSnapshot;
import io.yak.ops.business.dataset.gateway.taskcatalog.DatasetTaskCatalogGateway.DatasetTaskRevisionSnapshot;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** Discovers Dataset output schemas without owning Dataset publication state. */
@Component
public class DatasetSchemaDiscovery {

  private static final int DEFAULT_TIMEOUT_SECONDS = 30;
  private static final int MAX_DISCOVERY_TIMEOUT_SECONDS = 30;
  private static final int DEFAULT_PREVIEW_MAX_ROWS = 1_000;
  private static final Pattern SAFE_IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_$]*");

  private final DatasetTaskCatalogGateway taskCatalogGateway;
  private final DatasetSchemaSqlGateway sqlGateway;
  private final DatasetFieldIdentity fieldIdentity;
  private final ObjectMapper objectMapper;

  public DatasetSchemaDiscovery(
      DatasetTaskCatalogGateway taskCatalogGateway,
      DatasetSchemaSqlGateway sqlGateway,
      DatasetFieldIdentity fieldIdentity,
      ObjectMapper objectMapper) {
    this.taskCatalogGateway = taskCatalogGateway;
    this.sqlGateway = sqlGateway;
    this.fieldIdentity = fieldIdentity;
    this.objectMapper = objectMapper;
  }

  public List<DatasetFieldSpec> discover(long datasetId, DatasetTaskAssetSnapshot asset) {
    return toFields(datasetId, discoverColumns(asset));
  }

  public List<DatasetFieldSpec> preview(DatasetTaskAssetSnapshot asset) {
    return toPreviewFields(discoverColumns(asset));
  }

  public List<DatasetFieldSpec> discover(long datasetId, String dataSourceId, String sql) {
    return toFields(datasetId, discoverColumns(dataSourceId, sql, DEFAULT_TIMEOUT_SECONDS));
  }

  /** Preview has no persistent field identity; ids are assigned when a version is saved. */
  public List<DatasetFieldSpec> preview(String dataSourceId, String sql) {
    return toPreviewFields(discoverColumns(dataSourceId, sql, DEFAULT_TIMEOUT_SECONDS));
  }

  public QueryPreview previewQuery(String dataSourceId, String sql) {
    String normalizedDataSourceId = requireDataSourceId(dataSourceId);
    String baseSql = DatasetSqlSafety.requireReadOnlyQuery(sql);
    long startedAt = System.nanoTime();
    QueryResult result =
        sqlGateway.execute(
            normalizedDataSourceId,
            baseSql,
            DEFAULT_PREVIEW_MAX_ROWS,
            DEFAULT_TIMEOUT_SECONDS);
    long durationMs = elapsedMillis(startedAt);
    requireResultSet(result);
    return new QueryPreview(toPreviewFields(result.columns()), result, durationMs);
  }

  private List<QueryColumn> discoverColumns(DatasetTaskAssetSnapshot asset) {
    DatasetTaskRevisionSnapshot revision =
        taskCatalogGateway.resolveRevision(asset.id(), asset.currentRevisionId());
    if (revision.revisionId() != asset.currentRevisionId()
        || revision.revisionNo() != asset.currentRevisionNo()) {
      throw new IllegalStateException("Schema discovery 解析到的 TaskRevision 与发布快照不一致");
    }
    if (!"SQL".equalsIgnoreCase(revision.taskType())) {
      throw new IllegalArgumentException("Schema discovery 仅支持 SQL TaskRevision");
    }
    SourceConfig config = sourceConfig(revision.configJson());
    return discoverColumns(config.dataSourceId(), revision.content(), config.timeoutSeconds());
  }

  private List<QueryColumn> discoverColumns(
      String dataSourceId, String sql, int timeoutSeconds) {
    String normalizedDataSourceId = requireDataSourceId(dataSourceId);
    String baseSql = DatasetSqlSafety.requireReadOnlyQuery(sql);
    String discoverySql =
        "SELECT yak_dataset_source.* FROM (" + baseSql + ") yak_dataset_source LIMIT 1";
    QueryResult result =
        sqlGateway.execute(
            normalizedDataSourceId,
            discoverySql,
            1,
            Math.min(Math.max(timeoutSeconds, 1), MAX_DISCOVERY_TIMEOUT_SECONDS));
    requireResultSet(result);
    return result.columns();
  }

  private void requireResultSet(QueryResult result) {
    if (!result.resultSet()) {
      throw new IllegalStateException("Dataset schema/query preview 没有返回结果集");
    }
    if (result.columns().isEmpty()) {
      throw new IllegalArgumentException("Dataset 来源查询没有可发现的输出字段");
    }
  }

  private List<DatasetFieldSpec> toFields(long datasetId, List<QueryColumn> columns) {
    return toPreviewFields(columns).stream()
        .map(
            field ->
                new DatasetFieldSpec(
                    fieldIdentity.stableFieldId(datasetId, field.physicalName()),
                    field.physicalName(),
                    field.displayName(),
                    field.dataType(),
                    field.nullable(),
                    field.description(),
                    field.defaultRole()))
        .toList();
  }

  private List<DatasetFieldSpec> toPreviewFields(List<QueryColumn> columns) {
    List<DatasetFieldSpec> fields = new ArrayList<>(columns.size());
    Set<String> names = new HashSet<>();
    for (QueryColumn column : columns) {
      String physicalName = column.label();
      if (physicalName == null || physicalName.isBlank()) {
        physicalName = column.name();
      }
      if (physicalName == null || physicalName.isBlank()) {
        throw new IllegalArgumentException("Dataset 来源查询存在无名称字段，请在 SQL 中显式设置别名");
      }
      physicalName = physicalName.trim();
      if (!SAFE_IDENTIFIER.matcher(physicalName).matches()) {
        throw new IllegalArgumentException(
            "Dataset 输出字段必须使用简单安全别名 [A-Za-z_][A-Za-z0-9_$]*：" + physicalName);
      }
      if (!names.add(physicalName.toLowerCase(Locale.ROOT))) {
        throw new IllegalArgumentException("Dataset 来源查询存在重复输出字段：" + physicalName);
      }
      DatasetFieldDataType dataType = dataType(column.jdbcType());
      DatasetFieldRole role =
          dataType == DatasetFieldDataType.NUMBER
              ? DatasetFieldRole.MEASURE
              : DatasetFieldRole.DIMENSION;
      fields.add(
          new DatasetFieldSpec(
              null, physicalName, physicalName, dataType, column.nullable(), null, role));
    }
    return List.copyOf(fields);
  }

  private DatasetFieldDataType dataType(int jdbcType) {
    return switch (jdbcType) {
      case Types.BOOLEAN, Types.BIT -> DatasetFieldDataType.BOOLEAN;
      case Types.TINYINT,
          Types.SMALLINT,
          Types.INTEGER,
          Types.BIGINT,
          Types.FLOAT,
          Types.REAL,
          Types.DOUBLE,
          Types.NUMERIC,
          Types.DECIMAL -> DatasetFieldDataType.NUMBER;
      case Types.DATE -> DatasetFieldDataType.DATE;
      case Types.TIME,
          Types.TIME_WITH_TIMEZONE,
          Types.TIMESTAMP,
          Types.TIMESTAMP_WITH_TIMEZONE -> DatasetFieldDataType.DATETIME;
      case Types.CHAR,
          Types.VARCHAR,
          Types.LONGVARCHAR,
          Types.NCHAR,
          Types.NVARCHAR,
          Types.LONGNVARCHAR,
          Types.CLOB,
          Types.NCLOB -> DatasetFieldDataType.STRING;
      default -> DatasetFieldDataType.UNKNOWN;
    };
  }

  private SourceConfig sourceConfig(String configJson) {
    String raw = configJson == null || configJson.isBlank() ? "{}" : configJson.trim();
    try {
      JsonNode root = objectMapper.readTree(raw);
      if (root == null || !root.isObject()) {
        throw new IllegalArgumentException("SQL configJson 必须是 JSON 对象");
      }
      String dataSourceId = root.path("dataSourceId").asText(null);
      int sourceTimeout = root.path("timeoutSeconds").asInt(DEFAULT_TIMEOUT_SECONDS);
      if (sourceTimeout < 1) {
        sourceTimeout = DEFAULT_TIMEOUT_SECONDS;
      }
      return new SourceConfig(
          requireDataSourceId(dataSourceId),
          Math.min(sourceTimeout, MAX_DISCOVERY_TIMEOUT_SECONDS));
    } catch (IllegalArgumentException exception) {
      throw exception;
    } catch (Exception exception) {
      throw new IllegalArgumentException("SQL TaskRevision configJson 非法", exception);
    }
  }

  private String requireDataSourceId(String value) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("Dataset 必须选择数据源");
    }
    return value.trim();
  }

  private long elapsedMillis(long startedAt) {
    return Math.max(0L, (System.nanoTime() - startedAt) / 1_000_000L);
  }

  public record QueryPreview(
      List<DatasetFieldSpec> fields, QueryResult result, long durationMs) {
    public QueryPreview {
      fields = fields == null ? List.of() : List.copyOf(fields);
    }
  }

  private record SourceConfig(String dataSourceId, int timeoutSeconds) {}
}
