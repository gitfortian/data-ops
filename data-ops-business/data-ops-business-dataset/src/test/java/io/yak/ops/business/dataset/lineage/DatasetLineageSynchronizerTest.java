package io.yak.ops.business.dataset.lineage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.dataset.Dataset;
import io.yak.ops.business.dataset.DatasetDetail;
import io.yak.ops.business.dataset.DatasetField;
import io.yak.ops.business.dataset.DatasetFieldDataType;
import io.yak.ops.business.dataset.DatasetFieldRole;
import io.yak.ops.business.dataset.DatasetSourceType;
import io.yak.ops.business.dataset.DatasetStatus;
import io.yak.ops.business.dataset.DatasetVersion;
import io.yak.ops.business.dataset.gateway.lineage.DatasetLineageGraphGateway;
import io.yak.ops.business.dataset.gateway.lineage.DatasetLineageGraphGateway.Asset;
import io.yak.ops.business.dataset.gateway.lineage.DatasetLineageGraphGateway.AssetSpec;
import io.yak.ops.business.dataset.gateway.lineage.DatasetLineageGraphGateway.RelationSpec;
import io.yak.ops.business.dataset.gateway.lineage.DatasetLineageGraphGateway.RelationType;
import io.yak.ops.business.dataset.gateway.lineage.DatasetProjectionAnalyzerGateway;
import io.yak.ops.business.dataset.gateway.lineage.DatasetProjectionAnalyzerGateway.Analysis;
import io.yak.ops.business.dataset.gateway.lineage.DatasetProjectionAnalyzerGateway.MappingKind;
import io.yak.ops.business.dataset.gateway.lineage.DatasetProjectionAnalyzerGateway.ProjectionMapping;
import io.yak.ops.business.dataset.gateway.lineage.DatasetProjectionAnalyzerGateway.ProjectionResult;
import io.yak.ops.business.dataset.gateway.lineage.DatasetProjectionAnalyzerGateway.TableRef;
import io.yak.ops.business.dataset.gateway.taskcatalog.DatasetTaskCatalogGateway;
import io.yak.ops.business.dataset.gateway.taskcatalog.DatasetTaskCatalogGateway.DatasetTaskAssetSnapshot;
import io.yak.ops.business.dataset.gateway.taskcatalog.DatasetTaskCatalogGateway.DatasetTaskRevisionSnapshot;
import io.yak.ops.business.dataset.gateway.taskcatalog.DatasetTaskCatalogGateway.SourceAvailability;
import io.yak.ops.business.dataset.gateway.taskcatalog.DatasetTaskCatalogGateway.SourceOrigin;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class DatasetLineageSynchronizerTest {

  @Test
  void queryRevisionUsesFrozenRevisionAndConnectsPhysicalColumnsToStableDatasetFields() {
    Fixture fixture = fixture();
    DatasetTaskAssetSnapshot asset =
        new DatasetTaskAssetSnapshot(
            11L,
            "sales.sql",
            "101",
            SourceOrigin.DATA_DEVELOPMENT,
            SourceAvailability.ONLINE,
            "SQL",
            99L,
            5);
    when(fixture.taskCatalog.get(11L)).thenReturn(asset);
    when(fixture.taskCatalog.resolveRevision(11L, 71L))
        .thenReturn(
            new DatasetTaskRevisionSnapshot(
                11L,
                71L,
                3,
                "SQL",
                "SELECT o.id AS order_id, o.amount FROM ods.orders o",
                "{\"dataSourceId\":\"12\"}"));

    TableRef orders = table("ods.orders", "ods", "orders");
    when(fixture.analyzer.analyze(anyString(), anyString()))
        .thenReturn(
            Analysis.available(
                new ProjectionResult(
                    List.of(
                        mapping(orders, "id", "order_id", 1),
                        mapping(orders, "amount", "amount", 2)),
                    2,
                    0)));

    fixture.synchronizer.syncCurrent(queryRevisionDataset());

    verify(fixture.taskCatalog).resolveRevision(11L, 71L);
    verify(fixture.graph)
        .clearRelationsByEvidence(DatasetLineageSynchronizer.EVIDENCE_SOURCE_TYPE, "21");

    AssetSpec dataset = fixture.assets.specs.get("dataset:21");
    AssetSpec orderField = fixture.assets.specs.get("dataset-field:21:field-order");
    AssetSpec amountField = fixture.assets.specs.get("dataset-field:21:field-amount");
    assertNotNull(dataset);
    assertNotNull(orderField);
    assertNotNull(amountField);
    assertEquals(
        fixture.assets.assets.get("dataset:21").id(), orderField.parentAssetId());
    assertEquals("71", dataset.properties().path("sourceTaskRevisionId").asText());
    assertEquals("SUCCESS", dataset.properties().path("lineageParseStatus").asText());

    assertEquals(2, count(fixture.assets.relations, RelationType.CONTAINS));
    assertEquals(4, count(fixture.assets.relations, RelationType.DERIVES_FROM));
    assertTrue(
        fixture.assets.relations.stream()
            .anyMatch(
                relation ->
                    fixture.assets.key(relation.sourceAssetId())
                            .equals("dataset-field:21:field-order")
                        && fixture.assets.key(relation.targetAssetId()).equals("dataset:21")
                        && relation.relationType() == RelationType.CONTAINS));
    assertTrue(
        fixture.assets.relations.stream()
            .anyMatch(
                relation ->
                    fixture.assets.key(relation.sourceAssetId())
                            .equals("column:12:ods.orders.id")
                        && fixture.assets.key(relation.targetAssetId())
                            .equals("dataset-field:21:field-order")
                        && relation.relationType() == RelationType.DERIVES_FROM));
    assertTrue(fixture.assets.assets.containsKey("sql-task:data-development:101"));
  }

  @Test
  void standaloneSqlDatasetBuildsLineageWithoutTaskCatalog() {
    Fixture fixture = fixture();
    TableRef orders = table("ods.orders", "ods", "orders");
    when(fixture.analyzer.analyze(anyString(), anyString()))
        .thenReturn(
            Analysis.available(
                new ProjectionResult(
                    List.of(mapping(orders, "id", "order_id", 1)), 1, 0)));

    Dataset dataset =
        new Dataset(
            22L, "standalone", null, DatasetStatus.ONLINE, 41L, Instant.EPOCH, Instant.EPOCH);
    DatasetVersion version =
        new DatasetVersion(
            41L,
            22L,
            1,
            DatasetSourceType.SQL_QUERY,
            0L,
            0L,
            0,
            "12",
            "SELECT o.id AS order_id FROM ods.orders o",
            "[]",
            Instant.EPOCH);
    DatasetField field =
        new DatasetField(
            "field-order",
            41L,
            "order_id",
            "order_id",
            DatasetFieldDataType.NUMBER,
            false,
            null,
            DatasetFieldRole.DIMENSION,
            1);

    fixture.synchronizer.syncCurrent(
        new DatasetDetail(dataset, version, List.of(version), List.of(field)));

    verifyNoInteractions(fixture.taskCatalog);
    assertTrue(fixture.assets.assets.containsKey("dataset:22"));
    assertTrue(fixture.assets.assets.containsKey("column:12:ods.orders.id"));
    assertTrue(
        fixture.assets.assets.keySet().stream().noneMatch(key -> key.startsWith("sql-task:")));
    assertEquals(1, count(fixture.assets.relations, RelationType.CONTAINS));
    assertEquals(2, count(fixture.assets.relations, RelationType.DERIVES_FROM));
  }

  @Test
  void analyzerFailureReplacesStaleFactsButKeepsDatasetStructure() {
    Fixture fixture = fixture();
    when(fixture.analyzer.analyze(anyString(), anyString()))
        .thenThrow(new IllegalStateException("parse failed"));

    Dataset dataset =
        new Dataset(
            23L, "broken", null, DatasetStatus.ONLINE, 42L, Instant.EPOCH, Instant.EPOCH);
    DatasetVersion version =
        new DatasetVersion(
            42L,
            23L,
            1,
            DatasetSourceType.SQL_QUERY,
            0L,
            0L,
            0,
            "12",
            "SELECT broken",
            "[]",
            Instant.EPOCH);
    DatasetField field =
        new DatasetField(
            "field-one",
            42L,
            "one",
            "one",
            DatasetFieldDataType.NUMBER,
            true,
            null,
            DatasetFieldRole.DIMENSION,
            1);

    fixture.synchronizer.syncCurrent(
        new DatasetDetail(dataset, version, List.of(version), List.of(field)));

    verify(fixture.graph)
        .clearRelationsByEvidence(DatasetLineageSynchronizer.EVIDENCE_SOURCE_TYPE, "23");
    assertEquals(
        "FAILED",
        fixture.assets.specs.get("dataset:23").properties().path("lineageParseStatus").asText());
    assertEquals(1, fixture.assets.relations.size());
    assertEquals(RelationType.CONTAINS, fixture.assets.relations.get(0).relationType());
  }

  private Fixture fixture() {
    DatasetLineageGraphGateway graph = mock(DatasetLineageGraphGateway.class);
    DatasetProjectionAnalyzerGateway analyzer = mock(DatasetProjectionAnalyzerGateway.class);
    DatasetTaskCatalogGateway taskCatalog = mock(DatasetTaskCatalogGateway.class);
    AssetStore assets = stubAssets(graph);
    DatasetLineageSourceResolver sourceResolver =
        new DatasetLineageSourceResolver(taskCatalog, new ObjectMapper());
    DatasetLineageSynchronizer synchronizer =
        new DatasetLineageSynchronizer(
            graph, analyzer, sourceResolver, new ObjectMapper());
    return new Fixture(graph, analyzer, taskCatalog, synchronizer, assets);
  }

  private AssetStore stubAssets(DatasetLineageGraphGateway graph) {
    AssetStore store = new AssetStore();
    when(graph.registerAsset(any()))
        .thenAnswer(
            invocation -> {
              AssetSpec spec = invocation.getArgument(0);
              Asset existing = store.assets.get(spec.assetKey());
              long id = existing == null ? store.ids.getAndIncrement() : existing.id();
              Asset asset = new Asset(id, spec.assetKey());
              store.assets.put(spec.assetKey(), asset);
              store.specs.put(spec.assetKey(), spec);
              return asset;
            });
    when(graph.requireAssetByKey(anyString()))
        .thenAnswer(
            invocation -> {
              String key = invocation.getArgument(0);
              Asset asset = store.assets.get(key);
              if (asset == null) {
                throw new IllegalArgumentException("missing: " + key);
              }
              return asset;
            });
    doAnswer(
            invocation -> {
              store.relations.add(invocation.getArgument(0));
              return null;
            })
        .when(graph)
        .registerRelation(any());
    return store;
  }

  private static DatasetDetail queryRevisionDataset() {
    Dataset dataset =
        new Dataset(
            21L,
            "sales",
            "sales dataset",
            DatasetStatus.ONLINE,
            31L,
            Instant.EPOCH,
            Instant.EPOCH);
    DatasetVersion version =
        new DatasetVersion(
            31L,
            21L,
            1,
            DatasetSourceType.QUERY_REVISION,
            11L,
            71L,
            3,
            "[]",
            Instant.EPOCH);
    DatasetField orderField =
        new DatasetField(
            "field-order",
            31L,
            "order_id",
            "订单 ID",
            DatasetFieldDataType.NUMBER,
            false,
            null,
            DatasetFieldRole.DIMENSION,
            1);
    DatasetField amountField =
        new DatasetField(
            "field-amount",
            31L,
            "amount",
            "销售额",
            DatasetFieldDataType.NUMBER,
            true,
            null,
            DatasetFieldRole.MEASURE,
            2);
    return new DatasetDetail(
        dataset, version, List.of(version), List.of(orderField, amountField));
  }

  private static TableRef table(String qualifiedName, String schema, String table) {
    return new TableRef(qualifiedName, qualifiedName, null, schema, table);
  }

  private static ProjectionMapping mapping(
      TableRef sourceTable, String sourceColumn, String outputColumn, int outputOrdinal) {
    return new ProjectionMapping(
        sourceTable,
        sourceColumn,
        outputColumn,
        MappingKind.IDENTITY,
        sourceTable.tableName() + "." + sourceColumn,
        outputOrdinal,
        1);
  }

  private static long count(List<RelationSpec> relations, RelationType type) {
    return relations.stream().filter(value -> value.relationType() == type).count();
  }

  private record Fixture(
      DatasetLineageGraphGateway graph,
      DatasetProjectionAnalyzerGateway analyzer,
      DatasetTaskCatalogGateway taskCatalog,
      DatasetLineageSynchronizer synchronizer,
      AssetStore assets) {}

  private static final class AssetStore {
    private final AtomicLong ids = new AtomicLong(1);
    private final Map<String, Asset> assets = new LinkedHashMap<>();
    private final Map<String, AssetSpec> specs = new LinkedHashMap<>();
    private final List<RelationSpec> relations = new ArrayList<>();

    private String key(long id) {
      return assets.values().stream()
          .filter(asset -> asset.id() == id)
          .map(Asset::assetKey)
          .findFirst()
          .orElse("<missing:" + id + ">");
    }
  }
}
