package io.yak.ops.business.development.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.datasource.catalog.DataSourceCatalogReader;
import io.yak.ops.business.datasource.domain.catalog.CatalogColumn;
import io.yak.ops.business.development.domain.DevelopmentNode;
import io.yak.ops.business.development.domain.DevelopmentSqlLineagePreview;
import io.yak.ops.business.development.lineage.analysis.DevelopmentSqlProjectionLineageAnalyzer;
import io.yak.ops.business.development.repository.DevelopmentNodeRepository;
import io.yak.ops.business.lineage.domain.LineageRelationType;
import java.sql.Types;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DevelopmentSqlLineagePreviewServiceTest {

  @Test
  void previewsCurrentSelectWithoutPersistingAProductionTarget() {
    DevelopmentSqlLineagePreviewService service = service(sqlNode(1L, "订单汇总"));

    DevelopmentSqlLineagePreview preview = service.preview(
        1L,
        "SQL",
        """
            SELECT o.id, SUM(o.amount) AS total_amount
            FROM ods.orders o
            GROUP BY o.id
            """,
        "{\"dataSourceId\":\"12\"}",
        null,
        null);

    assertEquals("SUCCESS", preview.status());
    assertEquals(1, preview.inputTableCount());
    assertEquals(0, preview.outputTableCount());
    assertEquals(2, preview.columnMappingCount());
    assertEquals(2, preview.graph().nodes().size());
    assertEquals(1, preview.graph().relations().size());
    assertEquals(LineageRelationType.READS_FROM, preview.graph().relations().get(0).relationType());
    assertTrue(preview.graph().nodes().stream()
        .anyMatch(asset -> "table:12:ods.orders".equals(asset.assetKey())));
    assertTrue(preview.columnMappings().stream()
        .anyMatch(mapping -> "amount".equals(mapping.sourceColumn())
            && "total_amount".equals(mapping.targetColumn())
            && "AGGREGATION".equals(mapping.mappingKind())));
    assertNull(preview.columnMappings().get(0).targetTable());
  }

  @Test
  void previewsInsertAsInputTaskOutputAndKeepsColumnEvidence() {
    DevelopmentSqlLineagePreviewService service = service(sqlNode(7L, "写入汇总"));

    DevelopmentSqlLineagePreview preview = service.preview(
        7L,
        "SQL",
        "INSERT INTO dws.order_copy (id) SELECT s.id FROM ods.orders s",
        "{\"dataSourceId\":\"3\"}",
        null,
        null);

    assertEquals("SUCCESS", preview.status());
    assertEquals(1, preview.inputTableCount());
    assertEquals(1, preview.outputTableCount());
    assertEquals(3, preview.graph().nodes().size());
    assertEquals(2, preview.graph().relations().size());
    assertTrue(preview.graph().relations().stream()
        .anyMatch(relation -> relation.relationType() == LineageRelationType.READS_FROM));
    assertTrue(preview.graph().relations().stream()
        .anyMatch(relation -> relation.relationType() == LineageRelationType.WRITES_TO));
    assertTrue(preview.columnMappings().stream()
        .anyMatch(mapping -> "ods.orders".equals(mapping.sourceTable())
            && "id".equals(mapping.sourceColumn())
            && "dws.order_copy".equals(mapping.targetTable())
            && "id".equals(mapping.targetColumn())));
  }

  @Test
  void includesCatalogDataTypesInColumnMappings() {
    DevelopmentSqlLineagePreviewService service = service(sqlNode(8L, "类型血缘"));
    DataSourceCatalogReader catalogReader = mock(DataSourceCatalogReader.class);
    when(catalogReader.listColumns(3L, null, "ods", "orders"))
        .thenReturn(java.util.List.of(column("id", "BIGINT", 1)));
    when(catalogReader.listColumns(3L, null, "dws", "order_copy"))
        .thenReturn(java.util.List.of(column("id", "DECIMAL(20,0)", 1)));
    service.setDataSourceCatalogReader(catalogReader);

    DevelopmentSqlLineagePreview preview = service.preview(
        8L,
        "SQL",
        "INSERT INTO dws.order_copy (id) SELECT s.id FROM ods.orders s",
        "{\"dataSourceId\":\"3\"}",
        null,
        null);

    DevelopmentSqlLineagePreview.ColumnMapping mapping = preview.columnMappings().get(0);
    assertEquals("BIGINT", mapping.sourceDataType());
    assertEquals("DECIMAL(20,0)", mapping.targetDataType());
  }

  @Test
  void returnsFailedPreviewForInvalidSqlInsteadOfWritingStaleLineage() {
    DevelopmentSqlLineagePreviewService service = service(sqlNode(9L, "错误 SQL"));

    DevelopmentSqlLineagePreview preview = service.preview(
        9L,
        "SQL",
        "SELECT (",
        "{\"dataSourceId\":\"1\"}",
        null,
        null);

    assertEquals("FAILED", preview.status());
    assertTrue(preview.parseError() != null && !preview.parseError().isBlank());
    assertEquals(1, preview.graph().nodes().size());
    assertTrue(preview.graph().relations().isEmpty());
  }

  @Test
  void rejectsNonSqlNode() {
    DevelopmentSqlLineagePreviewService service = service(new DevelopmentNode(
        5L,
        "Python",
        "PYTHON",
        null,
        null,
        true,
        Instant.now(),
        Instant.now()));

    assertThrows(
        IllegalArgumentException.class,
        () -> service.preview(
            5L,
            "SQL",
            "SELECT 1",
            "{\"dataSourceId\":\"1\"}",
            null,
            null));
  }

  private static DevelopmentSqlLineagePreviewService service(DevelopmentNode node) {
    DevelopmentNodeRepository repository = mock(DevelopmentNodeRepository.class);
    when(repository.findById(node.id())).thenReturn(Optional.of(node));
    SqlColumnLineageParser columnParser = new DerivedAwareSqlColumnLineageParser();
    return new DevelopmentSqlLineagePreviewService(
        repository,
        new SqlTableLineageParser(),
        columnParser,
        new DevelopmentSqlProjectionLineageAnalyzer(columnParser),
        new ObjectMapper());
  }

  private static DevelopmentNode sqlNode(long id, String name) {
    return new DevelopmentNode(
        id,
        name,
        "SQL",
        null,
        null,
        true,
        Instant.now(),
        Instant.now());
  }

  private static CatalogColumn column(String name, String type, int ordinal) {
    return new CatalogColumn(
        name, type, Types.VARCHAR, null, null, true, ordinal, false, null);
  }
}
