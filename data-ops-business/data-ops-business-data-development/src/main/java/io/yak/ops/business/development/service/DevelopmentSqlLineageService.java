package io.yak.ops.business.development.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.yak.ops.business.datasource.catalog.DataSourceCatalogReader;
import io.yak.ops.business.datasource.domain.catalog.CatalogColumn;
import io.yak.ops.business.development.domain.DevelopmentNode;
import io.yak.ops.business.development.domain.DevelopmentTaskRevision;
import io.yak.ops.business.lineage.domain.LineageAsset;
import io.yak.ops.business.lineage.domain.LineageAssetType;
import io.yak.ops.business.lineage.maintenance.LineageMaintenanceService;
import io.yak.ops.business.lineage.domain.LineageRelationType;
import io.yak.ops.business.lineage.registration.LineageRegistrationService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Publishes authoritative table- and column-level lineage for immutable SQL task revisions. */
@Service
public class DevelopmentSqlLineageService {

  static final String EVIDENCE_SOURCE_TYPE = "DATA_DEVELOPMENT_SQL_PARSE";

  /** SQL_TASK 资产键全仓唯一出处(asset 台账与血缘同源,D6;禁止在别处复制字面量)。 */
  public static String sqlTaskAssetKey(long nodeId) {
    return "sql-task:data-development:" + nodeId;
  }

  private static final Logger LOGGER = LoggerFactory.getLogger(DevelopmentSqlLineageService.class);
  private static final int MAX_EVIDENCE_SQL_LENGTH = 16000;

  private final LineageRegistrationService lineageRegistrationService;
  private final LineageMaintenanceService maintenanceService;
  private final SqlTableLineageParser tableParser;
  private final SqlColumnLineageParser columnParser;
  private final ObjectMapper objectMapper;
  private final TableIdentityResolver identityResolver = new TableIdentityResolver();

  private DataSourceCatalogReader dataSourceCatalogReader;
  private int lineageBatchSize = 200;

  public DevelopmentSqlLineageService(
      LineageRegistrationService lineageRegistrationService,
      LineageMaintenanceService maintenanceService,
      SqlTableLineageParser tableParser,
      SqlColumnLineageParser columnParser,
      ObjectMapper objectMapper) {
    this.lineageRegistrationService = lineageRegistrationService;
    this.maintenanceService = maintenanceService;
    this.tableParser = tableParser;
    this.columnParser = columnParser;
    this.objectMapper = objectMapper;
  }

  /**
   * Catalog is optional because datasource support itself can be disabled. Lineage must continue to
   * publish table-level facts and conservative column facts when Catalog metadata is unavailable.
   */
  @Autowired(required = false)
  void setDataSourceCatalogReader(DataSourceCatalogReader dataSourceCatalogReader) {
    this.dataSourceCatalogReader = dataSourceCatalogReader;
  }

  @Value("${yak.lineage.write-batch-size:200}")
  void setLineageBatchSize(int lineageBatchSize) {
    if (lineageBatchSize < 1) {
      throw new IllegalArgumentException("yak.lineage.write-batch-size 必须大于 0");
    }
    this.lineageBatchSize = lineageBatchSize;
  }

  /** Performs parsing and optional catalog lookups without opening a database transaction. */
  public PreparedLineage prepare(DevelopmentNode node, DevelopmentTaskRevision revision) {
    if (node == null || revision == null || revision.definition() == null
        || !"SQL".equalsIgnoreCase(revision.definition().taskType())) {
      return null;
    }
    SqlContext context = sqlContext(revision.definition().configJson());
    try {
      SqlTableLineageParser.ParseResult tables = tableParser.parse(revision.definition().content());
      try {
        SqlColumnLineageParser.ParseResult columns = dataSourceCatalogReader == null
            ? columnParser.parse(revision.definition().content())
            : columnParser.parse(revision.definition().content(), schemaProvider(context.dataSourceId()));
        return new PreparedLineage(context, tables, columns, null, null);
      } catch (SqlColumnLineageParser.SqlColumnLineageParseException failure) {
        return new PreparedLineage(context, tables, null, null, failure);
      }
    } catch (SqlTableLineageParser.SqlLineageParseException failure) {
      return new PreparedLineage(context, null, null, failure, null);
    }
  }

  /** Compatibility entry point; production outbox uses prepare then an independent write transaction. */
  @Transactional
  public void syncPublished(DevelopmentNode node, DevelopmentTaskRevision revision) {
    PreparedLineage prepared = prepare(node, revision);
    if (prepared != null) applyPrepared(node, revision, prepared);
  }

  /** Writes and reclaims one snapshot atomically. */
  @Transactional
  public void applyPrepared(DevelopmentNode node, DevelopmentTaskRevision revision,
      PreparedLineage prepared) {
    String evidenceId = String.valueOf(node.id());
    String dataSourceId = prepared.context().dataSourceId();
    if (!maintenanceService.lockAndAcceptRevision(
        sqlTaskAssetKey(node.id()), revision.revisionNo())) return;
    LineageMaintenanceService.CleanupScope cleanup = maintenanceService.beginReplacement(
        EVIDENCE_SOURCE_TYPE, evidenceId, "DATA_DEVELOPMENT", evidenceId);
    if (prepared.tableFailure() != null) {
      registerTaskAsset(node, revision, dataSourceId, null, null, prepared.tableFailure(), null);
      maintenanceService.finishReplacement(cleanup);
      return;
    }
    SqlTableLineageParser.ParseResult tableParsed = prepared.tables();
    SqlColumnLineageParser.ParseResult columnParsed = prepared.columns();
    LineageAsset task = registerTaskAsset(node, revision, dataSourceId, tableParsed, columnParsed,
        null, prepared.columnFailure());
    String evidenceSql = truncate(revision.definition().content(), MAX_EVIDENCE_SQL_LENGTH);
    Instant observedAt = revision.createTime() == null ? Instant.now() : revision.createTime();
    Map<String, LineageAsset> tableAssets = new LinkedHashMap<>();
    for (SqlTableLineageParser.TableRef input : tableParsed.inputs()) {
      LineageAsset table = registerTableAsset(prepared.context(), input, tableAssets);
      lineageRegistrationService.registerRelation(new LineageRegistrationService.RegisterRelationCommand(table.id(), task.id(),
          LineageRelationType.READS_FROM, EVIDENCE_SOURCE_TYPE, evidenceId, evidenceSql,
          BigDecimal.ONE, Integer.toString(revision.revisionNo()), observedAt,
          tableRelationProperties(revision, "INPUT")));
    }
    for (SqlTableLineageParser.TableRef output : tableParsed.outputs()) {
      LineageAsset table = registerTableAsset(prepared.context(), output, tableAssets);
      lineageRegistrationService.registerRelation(new LineageRegistrationService.RegisterRelationCommand(task.id(), table.id(),
          LineageRelationType.WRITES_TO, EVIDENCE_SOURCE_TYPE, evidenceId, evidenceSql,
          BigDecimal.ONE, Integer.toString(revision.revisionNo()), observedAt,
          tableRelationProperties(revision, "OUTPUT")));
    }
    if (columnParsed != null) registerColumnLineage(prepared.context(), task, revision, columnParsed,
        evidenceId, observedAt, tableAssets);
    maintenanceService.finishReplacement(cleanup);
  }

  public record PreparedLineage(SqlContext context, SqlTableLineageParser.ParseResult tables,
      SqlColumnLineageParser.ParseResult columns, RuntimeException tableFailure,
      RuntimeException columnFailure) {}

  private SqlColumnLineageParser.SchemaProvider schemaProvider(String dataSourceId) {
    DataSourceCatalogReader catalogReader = this.dataSourceCatalogReader;
    if (catalogReader == null) {
      return SqlColumnLineageParser.SchemaProvider.none();
    }

    final Long numericDataSourceId;
    try {
      numericDataSourceId = Long.valueOf(dataSourceId);
    } catch (NumberFormatException exception) {
      LOGGER.debug("Skip schema-aware lineage because datasource id is not numeric: {}", dataSourceId);
      return SqlColumnLineageParser.SchemaProvider.none();
    }

    Map<String, List<SqlColumnLineageParser.SchemaColumn>> cache = new LinkedHashMap<>();
    return table -> cache.computeIfAbsent(
        table.canonicalName(),
        ignored -> loadSchemaColumns(catalogReader, numericDataSourceId, table));
  }

  private List<SqlColumnLineageParser.SchemaColumn> loadSchemaColumns(
      DataSourceCatalogReader catalogReader,
      Long dataSourceId,
      SqlTableLineageParser.TableRef table) {
    List<CatalogColumn> columns = listColumns(
        catalogReader,
        dataSourceId,
        table.databaseName(),
        table.schemaName(),
        table.tableName());

    // A two-part SQL name is dialect-dependent: PostgreSQL usually means schema.table while
    // MySQL/Doris usually means database.table. Try the alternate interpretation only when the
    // primary lookup produced no metadata, keeping the resolver dialect-tolerant without leaking
    // datasource-plugin details into the SQL parser.
    if (columns.isEmpty() && table.databaseName() == null && table.schemaName() != null) {
      columns = listColumns(
          catalogReader,
          dataSourceId,
          table.schemaName(),
          null,
          table.tableName());
    }

    List<SqlColumnLineageParser.SchemaColumn> result = new ArrayList<>();
    for (CatalogColumn column : columns) {
      if (column == null || column.name() == null || column.name().isBlank()) continue;
      result.add(new SqlColumnLineageParser.SchemaColumn(
          column.name(),
          column.ordinalPosition()));
    }
    return List.copyOf(result);
  }

  private List<CatalogColumn> listColumns(
      DataSourceCatalogReader catalogReader,
      Long dataSourceId,
      String database,
      String schema,
      String table) {
    try {
      List<CatalogColumn> columns =
          catalogReader.listColumns(dataSourceId, database, schema, table);
      return columns == null ? List.of() : columns;
    } catch (RuntimeException exception) {
      LOGGER.debug(
          "Catalog column lookup failed for datasource {} table {}.{}.{}: {}",
          dataSourceId,
          database,
          schema,
          table,
          exception.getMessage());
      return List.of();
    }
  }

  private void registerColumnLineage(
      SqlContext sqlContext,
      LineageAsset task,
      DevelopmentTaskRevision revision,
      SqlColumnLineageParser.ParseResult parsed,
      String evidenceId,
      Instant observedAt,
      Map<String, LineageAsset> tableAssets) {
    Map<String, LineageRegistrationService.RegisterAssetCommand> columnCommands = new LinkedHashMap<>();
    Map<String, PendingColumnRelation> pendingRelations = new LinkedHashMap<>();
    int mappingIndex = 0;
    for (SqlColumnLineageParser.ColumnMapping mapping : parsed.mappings()) {
      mappingIndex++;
      LineageAsset sourceTable = registerTableAsset(sqlContext, mapping.sourceTable(), tableAssets);
      LineageAsset targetTable = registerTableAsset(sqlContext, mapping.targetTable(), tableAssets);
      LineageRegistrationService.RegisterAssetCommand sourceColumn = columnAssetCommand(
          sqlContext, sourceTable, mapping.sourceTable(), mapping.sourceColumnName());
      LineageRegistrationService.RegisterAssetCommand targetColumn = columnAssetCommand(
          sqlContext, targetTable, mapping.targetTable(), mapping.targetColumnName());
      columnCommands.putIfAbsent(sourceColumn.assetKey(), sourceColumn);
      columnCommands.putIfAbsent(targetColumn.assetKey(), targetColumn);

      String relationVersion = revision.revisionNo()
          + ":column:"
          + mapping.statementIndex()
          + ":"
          + mapping.outputOrdinal()
          + ":"
          + mapping.sourceOrdinal()
          + ":"
          + mappingIndex;
      String edgeKey = sourceColumn.assetKey() + "\u0000" + targetColumn.assetKey() + "\u0000"
          + mapping.statementIndex() + "\u0000" + mapping.outputOrdinal() + "\u0000"
          + mapping.sourceOrdinal();
      pendingRelations.putIfAbsent(edgeKey, new PendingColumnRelation(
          sourceColumn.assetKey(), targetColumn.assetKey(), mapping, relationVersion));
    }

    Map<String, LineageAsset> columns = lineageRegistrationService.registerAssetsBatch(
        List.copyOf(columnCommands.values()), lineageBatchSize);
    List<LineageRegistrationService.RegisterRelationCommand> relations = new ArrayList<>();
    for (PendingColumnRelation pending : pendingRelations.values()) {
      LineageAsset sourceColumn = columns.get(pending.sourceAssetKey());
      LineageAsset targetColumn = columns.get(pending.targetAssetKey());
      if (sourceColumn == null || targetColumn == null) {
        throw new IllegalStateException("批量保存字段资产后缺少返回的资产身份");
      }
      // UPDATE a = a + 1 is a valid dependency, but Lineage Core intentionally forbids self edges.
      if (sourceColumn.id() == targetColumn.id()) continue;
      relations.add(new LineageRegistrationService.RegisterRelationCommand(
          sourceColumn.id(), targetColumn.id(),
          LineageRelationType.DERIVES_FROM,
          EVIDENCE_SOURCE_TYPE,
          evidenceId,
          truncate(pending.mapping().expression(), MAX_EVIDENCE_SQL_LENGTH),
          BigDecimal.ONE,
          pending.relationVersion(),
          observedAt,
          columnRelationProperties(task, revision, pending.mapping())));
    }
    lineageRegistrationService.registerRelationsBatch(relations, lineageBatchSize);
  }

  private record PendingColumnRelation(String sourceAssetKey, String targetAssetKey,
      SqlColumnLineageParser.ColumnMapping mapping, String relationVersion) {}

  private LineageAsset registerTaskAsset(
      DevelopmentNode node,
      DevelopmentTaskRevision revision,
      String dataSourceId,
      SqlTableLineageParser.ParseResult tableParsed,
      SqlColumnLineageParser.ParseResult columnParsed,
      RuntimeException tableFailure,
      RuntimeException columnFailure) {
    ObjectNode properties = objectMapper.createObjectNode();
    if (node.projectId() != null) properties.put("projectId", String.valueOf(node.projectId()));
    properties.put("revisionId", String.valueOf(revision.id()));
    properties.put("revisionNo", revision.revisionNo());
    properties.put("checksum", revision.checksum());
    properties.put("dataSourceId", dataSourceId);
    properties.put(
        "columnSchemaResolution",
        dataSourceCatalogReader == null ? "NONE" : "DATASOURCE_CATALOG");

    if (tableFailure == null && tableParsed != null) {
      properties.put("parseStatus", "SUCCESS");
      properties.put("statementCount", tableParsed.statementCount());
      properties.put("inputTableCount", tableParsed.inputs().size());
      properties.put("outputTableCount", tableParsed.outputs().size());
    } else {
      properties.put("parseStatus", "FAILED");
      properties.put("parseError", truncate(
          tableFailure == null ? "Unknown SQL table lineage parser failure" : tableFailure.getMessage(),
          1000));
    }

    if (tableFailure != null) {
      properties.put("columnParseStatus", "SKIPPED");
    } else if (columnFailure != null) {
      properties.put("columnParseStatus", "FAILED");
      properties.put("columnParseError", truncate(columnFailure.getMessage(), 1000));
    } else if (columnParsed != null) {
      properties.put("columnMappingCount", columnParsed.mappings().size());
      properties.put("candidateOutputColumnCount", columnParsed.candidateOutputCount());
      properties.put("unresolvedColumnReferenceCount", columnParsed.unresolvedReferenceCount());
      if (columnParsed.unresolvedReferenceCount() > 0 && !columnParsed.mappings().isEmpty()) {
        properties.put("columnParseStatus", "PARTIAL");
      } else if (columnParsed.unresolvedReferenceCount() > 0) {
        properties.put("columnParseStatus", "UNRESOLVED");
      } else {
        properties.put("columnParseStatus", "SUCCESS");
      }
    } else {
      properties.put("columnParseStatus", "SKIPPED");
    }

    return lineageRegistrationService.registerAsset(new LineageRegistrationService.RegisterAssetCommand(
        sqlTaskAssetKey(node.id()),
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
        properties));
  }

  private LineageAsset registerTableAsset(
      SqlContext sqlContext,
      SqlTableLineageParser.TableRef table,
      Map<String, LineageAsset> cache) {
    TableIdentityResolver.PhysicalTableIdentity identity = resolve(table, sqlContext);
    String key = identity.assetKey();
    LineageAsset cached = cache.get(key);
    if (cached != null) return cached;

    ObjectNode properties = objectMapper.createObjectNode();
    properties.put("qualifiedName", table.qualifiedName());
    LineageAsset registered = lineageRegistrationService.registerAsset(new LineageRegistrationService.RegisterAssetCommand(
        key,
        LineageAssetType.TABLE,
        table.qualifiedName(),
        "DATASOURCE",
        sqlContext.dataSourceId(),
        null,
        sqlContext.dataSourceId(),
        emptyToNull(identity.databaseName()),
        emptyToNull(identity.schemaName()),
        identity.tableName(),
        null,
        properties));
    cache.put(key, registered);
    return registered;
  }

  private LineageRegistrationService.RegisterAssetCommand columnAssetCommand(
      SqlContext sqlContext,
      LineageAsset tableAsset,
      SqlTableLineageParser.TableRef table,
      String columnName) {
    ObjectNode properties = objectMapper.createObjectNode();
    properties.put("qualifiedName", table.qualifiedName() + "." + columnName);

    return new LineageRegistrationService.RegisterAssetCommand(
        columnAssetKey(resolve(table, sqlContext), columnName),
        LineageAssetType.COLUMN,
        columnName,
        "DATASOURCE",
        sqlContext.dataSourceId(),
        tableAsset.id(),
        sqlContext.dataSourceId(),
        emptyToNull(resolve(table, sqlContext).databaseName()),
        emptyToNull(resolve(table, sqlContext).schemaName()),
        resolve(table, sqlContext).tableName(),
        columnName,
        properties);
  }

  private JsonNode tableRelationProperties(DevelopmentTaskRevision revision, String role) {
    ObjectNode properties = objectMapper.createObjectNode();
    properties.put("revisionId", String.valueOf(revision.id()));
    properties.put("revisionNo", revision.revisionNo());
    properties.put("lineageLevel", "TABLE");
    properties.put("tableRole", role);
    return properties;
  }

  private JsonNode columnRelationProperties(
      LineageAsset task,
      DevelopmentTaskRevision revision,
      SqlColumnLineageParser.ColumnMapping mapping) {
    ObjectNode properties = objectMapper.createObjectNode();
    properties.put("revisionId", String.valueOf(revision.id()));
    properties.put("revisionNo", revision.revisionNo());
    properties.put("lineageLevel", "COLUMN");
    properties.put("sqlTaskAssetId", String.valueOf(task.id()));
    properties.put("mappingKind", mapping.mappingKind().name());
    properties.put("statementIndex", mapping.statementIndex());
    properties.put("outputOrdinal", mapping.outputOrdinal());
    properties.put("sourceOrdinal", mapping.sourceOrdinal());
    properties.put("sourceTable", mapping.sourceTable().qualifiedName());
    properties.put("sourceColumn", mapping.sourceColumnName());
    properties.put("targetTable", mapping.targetTable().qualifiedName());
    properties.put("targetColumn", mapping.targetColumnName());
    return properties;
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

  private TableIdentityResolver.PhysicalTableIdentity resolve(
      SqlTableLineageParser.TableRef table, SqlContext context) {
    return identityResolver.resolve(table, new TableIdentityResolver.ResolutionContext(
        context.dataSourceId(), context.databaseName(), context.schemaName(), context.dialect()));
  }

  private static String columnAssetKey(
      TableIdentityResolver.PhysicalTableIdentity table,
      String columnName) {
    return table.assetKey().replaceFirst("^table:", "column:")
        + "." + columnName.toLowerCase(java.util.Locale.ROOT);
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

  record SqlContext(
      String dataSourceId,
      String databaseName,
      String schemaName,
      TableIdentityResolver.SqlDialect dialect) {
  }

  private static String truncate(String value, int maxLength) {
    if (value == null) return null;
    return value.length() <= maxLength ? value : value.substring(0, maxLength);
  }
}
