package io.yak.ops.business.development.lineage;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.datasource.catalog.DataSourceCatalogReader;
import io.yak.ops.business.development.domain.DevelopmentNode;
import io.yak.ops.business.development.lineage.model.DevelopmentSqlLineagePreview;
import io.yak.ops.business.development.lineage.model.DevelopmentSqlLineagePreview.ColumnMapping;
import io.yak.ops.business.development.lineage.model.DevelopmentSqlLineagePreview.PreviewAsset;
import io.yak.ops.business.development.lineage.model.DevelopmentSqlLineagePreview.PreviewGraph;
import io.yak.ops.business.development.lineage.model.DevelopmentSqlLineagePreview.PreviewRelation;
import io.yak.ops.business.development.repository.DevelopmentNodeRepository;
import io.yak.ops.business.development.service.DevelopmentSqlLineageService;
import io.yak.ops.business.development.service.SqlColumnLineageParser;
import io.yak.ops.business.development.service.SqlTableLineageParser;
import io.yak.ops.business.development.service.TableIdentityResolver;
import io.yak.ops.business.lineage.analysis.sql.SqlProjectionLineageAnalyzer;
import io.yak.ops.business.lineage.domain.LineageAssetType;
import io.yak.ops.business.lineage.domain.LineageDirection;
import io.yak.ops.business.lineage.domain.LineageRelationType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Builds editor-time SQL lineage without registering assets or relations in Lineage Core. */
@Service
public class DevelopmentSqlLineagePreviewService {

  private static final String PREVIEW_SOURCE_TYPE = "DATA_DEVELOPMENT_SQL_PREVIEW";

  private final DevelopmentNodeRepository nodeRepository;
  private final SqlTableLineageParser tableParser;
  private final SqlColumnLineageParser columnParser;
  private final SqlProjectionLineageAnalyzer projectionAnalyzer;
  private final ObjectMapper objectMapper;
  private final TableIdentityResolver identityResolver = new TableIdentityResolver();

  private DataSourceCatalogReader dataSourceCatalogReader;

  public DevelopmentSqlLineagePreviewService(
      DevelopmentNodeRepository nodeRepository,
      SqlTableLineageParser tableParser,
      SqlColumnLineageParser columnParser,
      SqlProjectionLineageAnalyzer projectionAnalyzer,
      ObjectMapper objectMapper) {
    this.nodeRepository = nodeRepository;
    this.tableParser = tableParser;
    this.columnParser = columnParser;
    this.projectionAnalyzer = projectionAnalyzer;
    this.objectMapper = objectMapper;
  }

  @Autowired(required = false)
  void setDataSourceCatalogReader(DataSourceCatalogReader dataSourceCatalogReader) {
    this.dataSourceCatalogReader = dataSourceCatalogReader;
  }

  public DevelopmentSqlLineagePreview preview(
      Long nodeId,
      String taskType,
      String sql,
      String configJson,
      String databaseName,
      String schemaName) {
    DevelopmentNode node = requireSqlNode(nodeId, taskType);
    if (sql == null || sql.isBlank()) {
      throw new IllegalArgumentException("SQL 不能为空");
    }
    SqlContext persisted = sqlContext(configJson);
    String dataSourceId = persisted.dataSourceId();
    // Explicit preview parameters override persisted revision context.
    String defaultDatabase = firstNonBlank(databaseName, persisted.databaseName());
    String defaultSchema = firstNonBlank(schemaName, persisted.schemaName());
    SqlContext context = new SqlContext(dataSourceId, defaultDatabase, defaultSchema, persisted.dialect());
    PreviewAsset task = taskAsset(node, dataSourceId);

    final SqlTableLineageParser.ParseResult tableParsed;
    try {
      tableParsed = tableParser.parse(sql);
    } catch (RuntimeException exception) {
      return failedPreview(task, dataSourceId, safeMessage(exception));
    }

    ColumnAnalysis columnAnalysis = analyzeColumns(
        sql,
        dataSourceId,
        tableParsed.outputs().isEmpty(),
        defaultDatabase,
        defaultSchema);
    Map<String, PreviewAsset> nodes = new LinkedHashMap<>();
    Map<String, PreviewRelation> relations = new LinkedHashMap<>();
    nodes.put(task.id(), task);

    for (SqlTableLineageParser.TableRef input : tableParsed.inputs()) {
      PreviewAsset table = tableAsset(context, input);
      nodes.putIfAbsent(table.id(), table);
      PreviewRelation relation = tableRelation(
          table,
          task,
          LineageRelationType.READS_FROM,
          "INPUT",
          node.id());
      relations.putIfAbsent(relation.id(), relation);
    }
    for (SqlTableLineageParser.TableRef output : tableParsed.outputs()) {
      PreviewAsset table = tableAsset(context, output);
      nodes.putIfAbsent(table.id(), table);
      PreviewRelation relation = tableRelation(
          task,
          table,
          LineageRelationType.WRITES_TO,
          "OUTPUT",
          node.id());
      relations.putIfAbsent(relation.id(), relation);
    }

    String status;
    if (columnAnalysis.error() != null) {
      status = "PARTIAL";
    } else if (columnAnalysis.unresolvedReferenceCount() > 0) {
      status = columnAnalysis.mappings().isEmpty() ? "UNRESOLVED" : "PARTIAL";
    } else {
      status = "SUCCESS";
    }

    return new DevelopmentSqlLineagePreview(
        status,
        dataSourceId,
        tableParsed.statementCount(),
        tableParsed.inputs().size(),
        tableParsed.outputs().size(),
        columnAnalysis.mappings().size(),
        columnAnalysis.candidateOutputCount(),
        columnAnalysis.unresolvedReferenceCount(),
        null,
        columnAnalysis.error(),
        new PreviewGraph(
            task,
            LineageDirection.BOTH,
            1,
            List.copyOf(nodes.values()),
            List.copyOf(relations.values())),
        columnAnalysis.mappings());
  }

  private DevelopmentNode requireSqlNode(Long nodeId, String taskType) {
    if (nodeId == null || nodeId <= 0L) throw new IllegalArgumentException("节点 ID 非法");
    DevelopmentNode node = nodeRepository.findById(nodeId)
        .orElseThrow(() -> new IllegalArgumentException("节点不存在：" + nodeId));
    if (!"SQL".equalsIgnoreCase(node.type())) {
      throw new IllegalArgumentException("只有 SQL 节点支持血缘预览：" + node.type());
    }
    if (taskType == null || !"SQL".equalsIgnoreCase(taskType.trim())) {
      throw new IllegalArgumentException("血缘预览 taskType 必须为 SQL");
    }
    return node;
  }

  private ColumnAnalysis analyzeColumns(
      String sql,
      String dataSourceId,
      boolean projectionOnly,
      String defaultDatabase,
      String defaultSchema) {
    if (projectionOnly) {
      try {
        SqlProjectionLineageAnalyzer.ProjectionResult result = projectionAnalyzer.analyze(
            sql,
            projectionSchemaProvider(dataSourceId, defaultDatabase, defaultSchema));
        List<ColumnMapping> mappings = result.mappings().stream().map(mapping -> {
          String sourceType = catalogDataType(
              dataSourceId,
              mapping.sourceTable().databaseName(),
              mapping.sourceTable().schemaName(),
              mapping.sourceTable().tableName(),
              mapping.sourceColumnName(),
              defaultDatabase,
              defaultSchema);
          return new ColumnMapping(
              mapping.sourceTable().qualifiedName(),
              mapping.sourceColumnName(),
              sourceType,
              null,
              mapping.outputColumnName(),
              inferredTargetDataType(
                  sourceType, mapping.mappingKind().name(), mapping.expression()),
              mapping.mappingKind().name(),
              mapping.expression(),
              mapping.outputOrdinal(),
              mapping.sourceOrdinal());
        }).toList();
        return new ColumnAnalysis(
            mappings,
            result.candidateOutputCount(),
            result.unresolvedReferenceCount(),
            null);
      } catch (RuntimeException projectionFailure) {
        // Non-projection statements can also have no write target. Fall through to the baseline
        // parser before surfacing a column-level preview failure.
        try {
          return analyzePhysicalColumns(
              sql,
              dataSourceId,
              defaultDatabase,
              defaultSchema);
        } catch (RuntimeException ignored) {
          return new ColumnAnalysis(List.of(), 0, 0, safeMessage(projectionFailure));
        }
      }
    }

    try {
      return analyzePhysicalColumns(sql, dataSourceId, defaultDatabase, defaultSchema);
    } catch (RuntimeException exception) {
      return new ColumnAnalysis(List.of(), 0, 0, safeMessage(exception));
    }
  }

  private ColumnAnalysis analyzePhysicalColumns(
      String sql,
      String dataSourceId,
      String defaultDatabase,
      String defaultSchema) {
    SqlColumnLineageParser.ParseResult result = columnParser.parse(
        sql,
        columnSchemaProvider(dataSourceId, defaultDatabase, defaultSchema));
    List<ColumnMapping> mappings = result.mappings().stream().map(mapping -> {
      String sourceType = catalogDataType(
          dataSourceId,
          mapping.sourceTable().databaseName(),
          mapping.sourceTable().schemaName(),
          mapping.sourceTable().tableName(),
          mapping.sourceColumnName(),
          defaultDatabase,
          defaultSchema);
      String targetType = catalogDataType(
          dataSourceId,
          mapping.targetTable().databaseName(),
          mapping.targetTable().schemaName(),
          mapping.targetTable().tableName(),
          mapping.targetColumnName(),
          defaultDatabase,
          defaultSchema);
      return new ColumnMapping(
          mapping.sourceTable().qualifiedName(),
          mapping.sourceColumnName(),
          sourceType,
          mapping.targetTable().qualifiedName(),
          mapping.targetColumnName(),
          targetType == null
              ? inferredTargetDataType(sourceType, mapping.mappingKind().name(), mapping.expression())
              : targetType,
          mapping.mappingKind().name(),
          mapping.expression(),
          mapping.outputOrdinal(),
          mapping.sourceOrdinal());
    }).toList();
    return new ColumnAnalysis(
        mappings,
        result.candidateOutputCount(),
        result.unresolvedReferenceCount(),
        null);
  }

  private SqlColumnLineageParser.SchemaProvider columnSchemaProvider(
      String dataSourceId,
      String defaultDatabase,
      String defaultSchema) {
    Map<String, List<CatalogColumn>> cache = new LinkedHashMap<>();
    return table -> cache.computeIfAbsent(
            schemaCacheKey(table.canonicalName(), defaultDatabase, defaultSchema),
            ignored -> catalogColumnsForTable(
                dataSourceId,
                table.databaseName(),
                table.schemaName(),
                table.tableName(),
                defaultDatabase,
                defaultSchema))
        .stream()
        .map(column -> new SqlColumnLineageParser.SchemaColumn(
            column.name(),
            column.ordinalPosition()))
        .toList();
  }

  private SqlProjectionLineageAnalyzer.SchemaProvider projectionSchemaProvider(
      String dataSourceId,
      String defaultDatabase,
      String defaultSchema) {
    Map<String, List<CatalogColumn>> cache = new LinkedHashMap<>();
    return table -> cache.computeIfAbsent(
            schemaCacheKey(table.canonicalName(), defaultDatabase, defaultSchema),
            ignored -> catalogColumnsForTable(
                dataSourceId,
                table.databaseName(),
                table.schemaName(),
                table.tableName(),
                defaultDatabase,
                defaultSchema))
        .stream()
        .map(column -> new SqlProjectionLineageAnalyzer.SchemaColumn(
            column.name(),
            column.ordinalPosition()))
        .toList();
  }

  /**
   * Only unqualified table references inherit the editor database/schema context. Two-part names
   * remain parser-owned so the existing PostgreSQL schema.table / MySQL database.table fallback is
   * preserved.
   */
  private List<CatalogColumn> catalogColumnsForTable(
      String dataSourceId,
      String explicitDatabase,
      String explicitSchema,
      String table,
      String defaultDatabase,
      String defaultSchema) {
    String database = explicitDatabase;
    String schema = explicitSchema;
    if (database == null && schema == null) {
      database = defaultDatabase;
      schema = defaultSchema;
    }
    return catalogColumns(dataSourceId, database, schema, table);
  }

  private List<CatalogColumn> catalogColumns(
      String dataSourceId,
      String database,
      String schema,
      String table) {
    DataSourceCatalogReader catalogReader = this.dataSourceCatalogReader;
    if (catalogReader == null) return List.of();

    final Long numericDataSourceId;
    try {
      numericDataSourceId = Long.valueOf(dataSourceId);
    } catch (NumberFormatException exception) {
      return List.of();
    }

    List<io.yak.ops.business.datasource.domain.catalog.CatalogColumn> columns = listColumns(
        catalogReader,
        numericDataSourceId,
        database,
        schema,
        table);
    if (columns.isEmpty() && database == null && schema != null) {
      columns = listColumns(catalogReader, numericDataSourceId, schema, null, table);
    }

    List<CatalogColumn> result = new ArrayList<>();
    for (io.yak.ops.business.datasource.domain.catalog.CatalogColumn column : columns) {
      if (column == null || column.name() == null || column.name().isBlank()) continue;
      result.add(new CatalogColumn(
          column.name(), column.typeName(), column.ordinalPosition()));
    }
    return List.copyOf(result);
  }

  private List<io.yak.ops.business.datasource.domain.catalog.CatalogColumn> listColumns(
      DataSourceCatalogReader catalogReader,
      Long dataSourceId,
      String database,
      String schema,
      String table) {
    try {
      List<io.yak.ops.business.datasource.domain.catalog.CatalogColumn> columns =
          catalogReader.listColumns(dataSourceId, database, schema, table);
      return columns == null ? List.of() : columns;
    } catch (RuntimeException exception) {
      return List.of();
    }
  }

  private String catalogDataType(
      String dataSourceId,
      String explicitDatabase,
      String explicitSchema,
      String table,
      String columnName,
      String defaultDatabase,
      String defaultSchema) {
    return catalogColumnsForTable(
            dataSourceId, explicitDatabase, explicitSchema, table, defaultDatabase, defaultSchema)
        .stream()
        .filter(column -> column.name().equalsIgnoreCase(columnName))
        .map(CatalogColumn::dataType)
        .filter(type -> type != null && !type.isBlank())
        .findFirst()
        .orElse(null);
  }

  private static String inferredTargetDataType(
      String sourceDataType, String mappingKind, String expression) {
    if ("AGGREGATION".equals(mappingKind)
        && expression != null
        && expression.stripLeading().toUpperCase(java.util.Locale.ROOT).startsWith("COUNT(")) {
      return "BIGINT";
    }
    return sourceDataType;
  }

  private PreviewAsset taskAsset(DevelopmentNode node, String dataSourceId) {
    String key = DevelopmentSqlLineageService.sqlTaskAssetKey(node.id());
    return new PreviewAsset(
        key,
        key,
        LineageAssetType.SQL_TASK,
        node.name(),
        "DATA_DEVELOPMENT",
        String.valueOf(node.id()),
        null,
        dataSourceId,
        null,
        null,
        null,
        null,
        Map.of("preview", true));
  }

  private PreviewAsset tableAsset(SqlContext context, SqlTableLineageParser.TableRef table) {
    TableIdentityResolver.PhysicalTableIdentity identity = identityResolver.resolve(
        table, new TableIdentityResolver.ResolutionContext(
            context.dataSourceId(), context.databaseName(), context.schemaName(), context.dialect()));
    String key = identity.assetKey();
    return new PreviewAsset(
        key,
        key,
        LineageAssetType.TABLE,
        table.qualifiedName(),
        "DATASOURCE",
        context.dataSourceId(),
        null,
        context.dataSourceId(),
        emptyToNull(identity.databaseName()),
        emptyToNull(identity.schemaName()),
        identity.tableName(),
        null,
        Map.of("qualifiedName", table.qualifiedName(), "preview", true));
  }

  private PreviewRelation tableRelation(
      PreviewAsset source,
      PreviewAsset target,
      LineageRelationType relationType,
      String role,
      long nodeId) {
    String id = source.id() + "->" + target.id() + ":" + relationType.name();
    return new PreviewRelation(
        id,
        source.id(),
        target.id(),
        relationType,
        PREVIEW_SOURCE_TYPE,
        String.valueOf(nodeId),
        null,
        Map.of("lineageLevel", "TABLE", "tableRole", role, "preview", true));
  }

  private DevelopmentSqlLineagePreview failedPreview(
      PreviewAsset task,
      String dataSourceId,
      String parseError) {
    return new DevelopmentSqlLineagePreview(
        "FAILED",
        dataSourceId,
        0,
        0,
        0,
        0,
        0,
        0,
        parseError,
        null,
        new PreviewGraph(task, LineageDirection.BOTH, 1, List.of(task), List.of()),
        List.of());
  }

  private SqlContext sqlContext(String configJson) {
    try {
      JsonNode root = objectMapper.readTree(
          configJson == null || configJson.isBlank() ? "{}" : configJson);
      JsonNode value = root == null ? null : root.get("dataSourceId");
      String dataSourceId = value == null || value.isNull() ? null : value.asText();
      if (dataSourceId == null || dataSourceId.isBlank()) {
        throw new IllegalArgumentException("SQL task dataSourceId 不能为空");
      }
      return new SqlContext(
          dataSourceId.trim(),
          text(root, "databaseName", "database", "catalog"),
          text(root, "schemaName", "schema"),
          TableIdentityResolver.SqlDialect.from(text(root, "dialect", "dbType")));
    } catch (JsonProcessingException exception) {
      throw new IllegalArgumentException("SQL task configJson 不是合法 JSON", exception);
    }
  }

  private static String normalizeContext(String value) {
    if (value == null || value.isBlank()) return null;
    return value.trim();
  }

  private static String firstNonBlank(String explicit, String persisted) {
    String normalized = normalizeContext(explicit);
    return normalized == null ? normalizeContext(persisted) : normalized;
  }

  private static String text(JsonNode root, String... names) {
    for (String name : names) {
      JsonNode value = root == null ? null : root.get(name);
      if (value != null && !value.isNull() && !value.asText().isBlank()) return value.asText().trim();
    }
    return null;
  }

  private static String emptyToNull(String value) {
    return value == null || value.isEmpty() ? null : value;
  }

  private static String schemaCacheKey(
      String canonicalName,
      String defaultDatabase,
      String defaultSchema) {
    return canonicalName
        + "|"
        + (defaultDatabase == null ? "" : defaultDatabase)
        + "|"
        + (defaultSchema == null ? "" : defaultSchema);
  }

  private static String safeMessage(Throwable throwable) {
    String message = throwable == null ? null : throwable.getMessage();
    if (message == null || message.isBlank()) {
      return throwable == null ? "unknown parser error" : throwable.getClass().getSimpleName();
    }
    return message.length() > 1000 ? message.substring(0, 1000) : message;
  }

  private record SqlContext(
      String dataSourceId,
      String databaseName,
      String schemaName,
      TableIdentityResolver.SqlDialect dialect) {
  }

  private record CatalogColumn(String name, String dataType, Integer ordinalPosition) {
  }

  private record ColumnAnalysis(
      List<ColumnMapping> mappings,
      int candidateOutputCount,
      int unresolvedReferenceCount,
      String error) {
  }
}
