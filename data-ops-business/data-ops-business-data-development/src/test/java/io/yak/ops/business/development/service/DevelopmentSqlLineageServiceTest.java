package io.yak.ops.business.development.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.datasource.catalog.DataSourceCatalogReader;
import io.yak.ops.business.datasource.domain.catalog.CatalogColumn;
import io.yak.ops.business.development.domain.DevelopmentNode;
import io.yak.ops.business.development.domain.DevelopmentTaskRevision;
import io.yak.ops.business.lineage.domain.LineageAsset;
import io.yak.ops.business.lineage.domain.LineageAssetType;
import io.yak.ops.business.lineage.maintenance.LineageMaintenanceService;
import io.yak.ops.business.lineage.domain.LineageRelationType;
import io.yak.ops.business.lineage.registration.LineageRegistrationService;
import io.yak.ops.spi.task.model.TaskDefinition;
import java.sql.Types;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class DevelopmentSqlLineageServiceTest {

  @Test
  void replacesGeneratedRelationsWithTableAndColumnLineage() {
    LineageRegistrationService lineageService = mock(LineageRegistrationService.class);
    LineageMaintenanceService maintenanceService = mock(LineageMaintenanceService.class);
    when(maintenanceService.lockAndAcceptRevision(anyString(), anyInt())).thenReturn(true);
    SqlTableLineageParser tableParser = mock(SqlTableLineageParser.class);
    SqlColumnLineageParser columnParser = mock(SqlColumnLineageParser.class);
    ObjectMapper objectMapper = new ObjectMapper();
    stubAssetRegistration(lineageService);
    DevelopmentSqlLineageService service = new DevelopmentSqlLineageService(
        lineageService, maintenanceService, tableParser, columnParser, objectMapper);

    String sql =
        "INSERT INTO dws.sales (order_id, user_name) "
            + "SELECT o.id, u.name FROM ods.orders o JOIN dim.users u ON u.id=o.user_id";
    SqlTableLineageParser.TableRef orders = table("ods.orders", "ods", "orders");
    SqlTableLineageParser.TableRef users = table("dim.users", "dim", "users");
    SqlTableLineageParser.TableRef sales = table("dws.sales", "dws", "sales");
    when(tableParser.parse(sql)).thenReturn(new SqlTableLineageParser.ParseResult(
        List.of(users, orders), List.of(sales), 1));
    when(columnParser.parse(sql)).thenReturn(new SqlColumnLineageParser.ParseResult(
        List.of(
            mapping(orders, "id", sales, "order_id", 1, 1),
            mapping(orders, "id", sales, "order_id", 1, 1),
            mapping(users, "name", sales, "user_name", 2, 1)),
        1,
        2,
        0));

    service.syncPublished(node(), revision(sql));

    verify(maintenanceService).beginReplacement(
        DevelopmentSqlLineageService.EVIDENCE_SOURCE_TYPE, "42", "DATA_DEVELOPMENT", "42");
    verify(maintenanceService).finishReplacement(null);

    ArgumentCaptor<LineageRegistrationService.RegisterRelationCommand> relations =
        ArgumentCaptor.forClass(LineageRegistrationService.RegisterRelationCommand.class);
    verify(lineageService, times(3)).registerRelation(relations.capture());
    ArgumentCaptor<List<LineageRegistrationService.RegisterRelationCommand>> columnRelations =
        ArgumentCaptor.forClass(List.class);
    verify(lineageService).registerRelationsBatch(columnRelations.capture(), anyInt());
    List<LineageRegistrationService.RegisterRelationCommand> allRelations =
        new java.util.ArrayList<>(relations.getAllValues());
    allRelations.addAll(columnRelations.getValue());

    assertEquals(2, count(allRelations, LineageRelationType.READS_FROM));
    assertEquals(1, count(allRelations, LineageRelationType.WRITES_TO));
    assertEquals(2, count(allRelations, LineageRelationType.DERIVES_FROM));
    assertTrue(allRelations.stream()
        .filter(value -> value.relationType() == LineageRelationType.DERIVES_FROM)
        .allMatch(value -> "COLUMN".equals(value.properties().path("lineageLevel").asText())));
  }

  @Test
  void columnAssetsAreParentedByStableTableAssets() {
    LineageRegistrationService lineageService = mock(LineageRegistrationService.class);
    LineageMaintenanceService maintenanceService = mock(LineageMaintenanceService.class);
    when(maintenanceService.lockAndAcceptRevision(anyString(), anyInt())).thenReturn(true);
    SqlTableLineageParser tableParser = mock(SqlTableLineageParser.class);
    SqlColumnLineageParser columnParser = mock(SqlColumnLineageParser.class);
    ObjectMapper objectMapper = new ObjectMapper();
    stubAssetRegistration(lineageService);
    DevelopmentSqlLineageService service = new DevelopmentSqlLineageService(
        lineageService, maintenanceService, tableParser, columnParser, objectMapper);

    String sql = "INSERT INTO dws.sales (order_id) SELECT o.id FROM ods.orders o";
    SqlTableLineageParser.TableRef orders = table("ods.orders", "ods", "orders");
    SqlTableLineageParser.TableRef sales = table("dws.sales", "dws", "sales");
    when(tableParser.parse(sql)).thenReturn(new SqlTableLineageParser.ParseResult(
        List.of(orders), List.of(sales), 1));
    when(columnParser.parse(sql)).thenReturn(new SqlColumnLineageParser.ParseResult(
        List.of(mapping(orders, "id", sales, "order_id", 1, 1)), 1, 1, 0));

    service.syncPublished(node(), revision(sql));

    ArgumentCaptor<LineageRegistrationService.RegisterAssetCommand> assets =
        ArgumentCaptor.forClass(LineageRegistrationService.RegisterAssetCommand.class);
    verify(lineageService, times(3)).registerAsset(assets.capture());
    ArgumentCaptor<List<LineageRegistrationService.RegisterAssetCommand>> columnAssets =
        ArgumentCaptor.forClass(List.class);
    verify(lineageService).registerAssetsBatch(columnAssets.capture(), anyInt());

    Map<String, LineageRegistrationService.RegisterAssetCommand> byKey = new LinkedHashMap<>();
    for (LineageRegistrationService.RegisterAssetCommand command : assets.getAllValues()) {
      byKey.put(command.assetKey(), command);
    }
    for (LineageRegistrationService.RegisterAssetCommand command : columnAssets.getValue()) {
      byKey.put(command.assetKey(), command);
    }

    LineageRegistrationService.RegisterAssetCommand sourceColumn = byKey.get("column:12:.ods.orders.id");
    LineageRegistrationService.RegisterAssetCommand targetColumn = byKey.get("column:12:.dws.sales.order_id");
    assertNotNull(sourceColumn);
    assertNotNull(targetColumn);
    assertEquals(LineageAssetType.COLUMN, sourceColumn.assetType());
    assertEquals(LineageAssetType.COLUMN, targetColumn.assetType());
    assertNotNull(sourceColumn.parentAssetId());
    assertNotNull(targetColumn.parentAssetId());
  }

  @Test
  void catalogSchemaEnablesStarAndImplicitTargetColumnLineage() {
    LineageRegistrationService lineageService = mock(LineageRegistrationService.class);
    LineageMaintenanceService maintenanceService = mock(LineageMaintenanceService.class);
    when(maintenanceService.lockAndAcceptRevision(anyString(), anyInt())).thenReturn(true);
    SqlTableLineageParser tableParser = mock(SqlTableLineageParser.class);
    SqlColumnLineageParser columnParser = new SqlColumnLineageParser();
    DataSourceCatalogReader catalogReader = mock(DataSourceCatalogReader.class);
    ObjectMapper objectMapper = new ObjectMapper();
    stubAssetRegistration(lineageService);

    DevelopmentSqlLineageService service = new DevelopmentSqlLineageService(
        lineageService, maintenanceService, tableParser, columnParser, objectMapper);
    service.setDataSourceCatalogReader(catalogReader);

    String sql = "INSERT INTO dws.orders_copy SELECT * FROM ods.orders";
    SqlTableLineageParser.TableRef orders = table("ods.orders", "ods", "orders");
    SqlTableLineageParser.TableRef copy = table("dws.orders_copy", "dws", "orders_copy");
    when(tableParser.parse(sql)).thenReturn(new SqlTableLineageParser.ParseResult(
        List.of(orders), List.of(copy), 1));

    // Simulate a MySQL/Doris style two-part name. The first schema.table interpretation returns no
    // metadata; the service retries the same qualifier as database.table.
    when(catalogReader.listColumns(12L, null, "ods", "orders")).thenReturn(List.of());
    when(catalogReader.listColumns(12L, "ods", null, "orders")).thenReturn(List.of(
        catalogColumn("id", 1),
        catalogColumn("amount", 2)));
    when(catalogReader.listColumns(12L, null, "dws", "orders_copy")).thenReturn(List.of());
    when(catalogReader.listColumns(12L, "dws", null, "orders_copy")).thenReturn(List.of(
        catalogColumn("order_id", 1),
        catalogColumn("order_amount", 2)));

    service.syncPublished(node(), revision(sql));

    ArgumentCaptor<LineageRegistrationService.RegisterRelationCommand> relations =
        ArgumentCaptor.forClass(LineageRegistrationService.RegisterRelationCommand.class);
    verify(lineageService, times(2)).registerRelation(relations.capture());
    ArgumentCaptor<List<LineageRegistrationService.RegisterRelationCommand>> columnRelations =
        ArgumentCaptor.forClass(List.class);
    verify(lineageService).registerRelationsBatch(columnRelations.capture(), anyInt());
    List<LineageRegistrationService.RegisterRelationCommand> allRelations =
        new java.util.ArrayList<>(relations.getAllValues());
    allRelations.addAll(columnRelations.getValue());

    assertEquals(1, count(allRelations, LineageRelationType.READS_FROM));
    assertEquals(1, count(allRelations, LineageRelationType.WRITES_TO));
    assertEquals(2, count(allRelations, LineageRelationType.DERIVES_FROM));
    assertTrue(allRelations.stream()
        .filter(value -> value.relationType() == LineageRelationType.DERIVES_FROM)
        .anyMatch(value -> "id".equals(value.properties().path("sourceColumn").asText())
            && "order_id".equals(value.properties().path("targetColumn").asText())));
    assertTrue(allRelations.stream()
        .filter(value -> value.relationType() == LineageRelationType.DERIVES_FROM)
        .anyMatch(value -> "amount".equals(value.properties().path("sourceColumn").asText())
            && "order_amount".equals(value.properties().path("targetColumn").asText())));
  }

  @Test
  void columnParserFailureKeepsCurrentTableLineage() {
    LineageRegistrationService lineageService = mock(LineageRegistrationService.class);
    LineageMaintenanceService maintenanceService = mock(LineageMaintenanceService.class);
    when(maintenanceService.lockAndAcceptRevision(anyString(), anyInt())).thenReturn(true);
    SqlTableLineageParser tableParser = mock(SqlTableLineageParser.class);
    SqlColumnLineageParser columnParser = mock(SqlColumnLineageParser.class);
    ObjectMapper objectMapper = new ObjectMapper();
    stubAssetRegistration(lineageService);
    DevelopmentSqlLineageService service = new DevelopmentSqlLineageService(
        lineageService, maintenanceService, tableParser, columnParser, objectMapper);

    String sql = "INSERT INTO dws.sales (id) SELECT o.id FROM ods.orders o";
    SqlTableLineageParser.TableRef orders = table("ods.orders", "ods", "orders");
    SqlTableLineageParser.TableRef sales = table("dws.sales", "dws", "sales");
    when(tableParser.parse(sql)).thenReturn(new SqlTableLineageParser.ParseResult(
        List.of(orders), List.of(sales), 1));
    when(columnParser.parse(sql)).thenThrow(
        new SqlColumnLineageParser.SqlColumnLineageParseException(
            "column parse failed", new RuntimeException("column parse failed")));

    assertDoesNotThrow(() -> service.syncPublished(node(), revision(sql)));

    ArgumentCaptor<LineageRegistrationService.RegisterRelationCommand> relations =
        ArgumentCaptor.forClass(LineageRegistrationService.RegisterRelationCommand.class);
    verify(lineageService, times(2)).registerRelation(relations.capture());
    assertEquals(1, count(relations, LineageRelationType.READS_FROM));
    assertEquals(1, count(relations, LineageRelationType.WRITES_TO));
    assertEquals(0, count(relations, LineageRelationType.DERIVES_FROM));
  }

  @Test
  void tableParserFailureClearsStaleRelationsAndSkipsColumnParsing() {
    LineageRegistrationService lineageService = mock(LineageRegistrationService.class);
    LineageMaintenanceService maintenanceService = mock(LineageMaintenanceService.class);
    when(maintenanceService.lockAndAcceptRevision(anyString(), anyInt())).thenReturn(true);
    SqlTableLineageParser tableParser = mock(SqlTableLineageParser.class);
    SqlColumnLineageParser columnParser = mock(SqlColumnLineageParser.class);
    ObjectMapper objectMapper = new ObjectMapper();
    stubAssetRegistration(lineageService);
    DevelopmentSqlLineageService service = new DevelopmentSqlLineageService(
        lineageService, maintenanceService, tableParser, columnParser, objectMapper);

    String sql = "SELECT FROM";
    when(tableParser.parse(sql)).thenThrow(new SqlTableLineageParser.SqlLineageParseException(
        "table parse failed", new RuntimeException("table parse failed")));

    assertDoesNotThrow(() -> service.syncPublished(node(), revision(sql)));

    verify(maintenanceService).beginReplacement(
        DevelopmentSqlLineageService.EVIDENCE_SOURCE_TYPE, "42", "DATA_DEVELOPMENT", "42");
    verify(maintenanceService).finishReplacement(null);
    verify(columnParser, never()).parse(any());
    verify(lineageService, never()).registerRelation(any());
  }

  private static long count(
      ArgumentCaptor<LineageRegistrationService.RegisterRelationCommand> relations,
      LineageRelationType type) {
    return count(relations.getAllValues(), type);
  }

  private static long count(
      List<LineageRegistrationService.RegisterRelationCommand> relations, LineageRelationType type) {
    return relations.stream()
        .filter(value -> value.relationType() == type)
        .count();
  }

  private static SqlColumnLineageParser.ColumnMapping mapping(
      SqlTableLineageParser.TableRef sourceTable,
      String sourceColumn,
      SqlTableLineageParser.TableRef targetTable,
      String targetColumn,
      int outputOrdinal,
      int sourceOrdinal) {
    return new SqlColumnLineageParser.ColumnMapping(
        sourceTable,
        sourceColumn,
        targetTable,
        targetColumn,
        SqlColumnLineageParser.MappingKind.IDENTITY,
        sourceTable.tableName() + "." + sourceColumn,
        1,
        outputOrdinal,
        sourceOrdinal);
  }

  private static SqlTableLineageParser.TableRef table(
      String qualifiedName,
      String schema,
      String table) {
    return new SqlTableLineageParser.TableRef(
        qualifiedName, qualifiedName, null, schema, table);
  }

  private static CatalogColumn catalogColumn(String name, int ordinal) {
    return new CatalogColumn(
        name,
        "VARCHAR",
        Types.VARCHAR,
        null,
        null,
        true,
        ordinal,
        false,
        null);
  }

  private static void stubAssetRegistration(LineageRegistrationService lineageService) {
    AtomicLong ids = new AtomicLong(1);
    Map<String, Long> stableIds = new LinkedHashMap<>();
    when(lineageService.registerAsset(any())).thenAnswer(invocation -> {
      LineageRegistrationService.RegisterAssetCommand command = invocation.getArgument(0);
      long id = stableIds.computeIfAbsent(command.assetKey(), ignored -> ids.getAndIncrement());
      return new LineageAsset(
          id,
          command.assetKey(),
          command.assetType(),
          command.name(),
          command.sourceType(),
          command.sourceId(),
          command.parentAssetId(),
          command.dataSourceId(),
          command.databaseName(),
          command.schemaName(),
          command.tableName(),
          command.columnName(),
          command.properties(),
          Instant.parse("2026-08-20T00:00:00Z"),
          Instant.parse("2026-08-20T00:00:00Z"));
    });
    when(lineageService.registerAssetsBatch(any(), anyInt())).thenAnswer(invocation -> {
      List<LineageRegistrationService.RegisterAssetCommand> commands = invocation.getArgument(0);
      Map<String, LineageAsset> result = new LinkedHashMap<>();
      for (LineageRegistrationService.RegisterAssetCommand command : commands) {
        long id = stableIds.computeIfAbsent(command.assetKey(), ignored -> ids.getAndIncrement());
        result.put(command.assetKey(), new LineageAsset(
            id, command.assetKey(), command.assetType(), command.name(), command.sourceType(),
            command.sourceId(), command.parentAssetId(), command.dataSourceId(),
            command.databaseName(), command.schemaName(), command.tableName(), command.columnName(),
            command.properties(), Instant.parse("2026-08-20T00:00:00Z"),
            Instant.parse("2026-08-20T00:00:00Z")));
      }
      return result;
    });
  }

  private static DevelopmentNode node() {
    return new DevelopmentNode(
        42L,
        "sales sql",
        "SQL",
        7L,
        3L,
        true,
        Instant.parse("2026-08-20T00:00:00Z"),
        Instant.parse("2026-08-20T00:00:00Z"));
  }

  private static DevelopmentTaskRevision revision(String sql) {
    return new DevelopmentTaskRevision(
        100L,
        42L,
        3,
        9L,
        new TaskDefinition("SQL", 1, sql, "{\"dataSourceId\":\"12\"}"),
        "checksum",
        Instant.parse("2026-08-20T00:00:00Z"));
  }
}
