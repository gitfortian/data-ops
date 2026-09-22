package io.yak.ops.business.lineage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.business.lineage.dao.support.LineageBatchSupport;
import io.yak.ops.business.lineage.domain.LineageAsset;
import io.yak.ops.business.lineage.domain.LineageAssetDraft;
import io.yak.ops.business.lineage.domain.LineageAssetType;
import io.yak.ops.business.lineage.domain.LineageGraph;
import io.yak.ops.business.lineage.domain.LineageRelation;
import io.yak.ops.business.lineage.domain.LineageRelationDraft;
import io.yak.ops.business.lineage.domain.LineageRelationType;
import io.yak.ops.business.lineage.repository.LineageRepository;
import io.yak.ops.business.lineage.query.LineageAssetReader;
import io.yak.ops.business.lineage.query.LineageGraphReader;
import io.yak.ops.business.lineage.query.LineageQueryService;
import io.yak.ops.business.lineage.registration.LineageAssetRegistrar;
import io.yak.ops.business.lineage.registration.LineageRegistrationDraftFactory;
import io.yak.ops.business.lineage.registration.LineageRegistrationService;
import io.yak.ops.business.lineage.registration.LineageRelationRegistrar;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class LineageServicesTest {

  @Test
  void traversesMultiHopGraphAndStopsAtVisitedAssets() {
    InMemoryLineageRepository repository = new InMemoryLineageRepository();
    LineageRegistrationService writeService = registrationService(repository);
    LineageQueryService queryService = queryService(repository);

    LineageAsset tableA = writeService.registerAsset(asset("table:a", LineageAssetType.TABLE));
    LineageAsset taskB = writeService.registerAsset(asset("sql:b", LineageAssetType.SQL_TASK));
    LineageAsset tableC = writeService.registerAsset(asset("table:c", LineageAssetType.TABLE));
    LineageAsset datasetD = writeService.registerAsset(asset("dataset:d", LineageAssetType.DATASET));
    LineageAsset chartE = writeService.registerAsset(asset("chart:e", LineageAssetType.CHART));

    writeService.registerRelation(relation(tableA.id(), taskB.id(), LineageRelationType.READS_FROM));
    writeService.registerRelation(relation(taskB.id(), tableC.id(), LineageRelationType.WRITES_TO));
    writeService.registerRelation(relation(tableC.id(), datasetD.id(), LineageRelationType.DERIVES_FROM));
    writeService.registerRelation(relation(datasetD.id(), chartE.id(), LineageRelationType.CONSUMES));
    writeService.registerRelation(relation(chartE.id(), taskB.id(), LineageRelationType.CONSUMES));

    LineageGraph downstream = queryService.downstream(tableA.id(), 10);
    assertEquals(
        Set.of(tableA.id(), taskB.id(), tableC.id(), datasetD.id(), chartE.id()),
        ids(downstream));
    assertEquals(5, downstream.relations().size());

    LineageGraph twoHops = queryService.downstream(tableA.id(), 2);
    assertEquals(Set.of(tableA.id(), taskB.id(), tableC.id()), ids(twoHops));
    assertEquals(2, twoHops.relations().size());

    LineageGraph upstream = queryService.upstream(chartE.id(), 4);
    assertTrue(ids(upstream).contains(tableA.id()));
    assertTrue(ids(upstream).contains(datasetD.id()));
  }

  @Test
  void searchesAssetsByKeywordTypeAndLimit() {
    InMemoryLineageRepository repository = new InMemoryLineageRepository();
    LineageRegistrationService writeService = registrationService(repository);
    LineageQueryService queryService = queryService(repository);

    writeService.registerAsset(new LineageRegistrationService.RegisterAssetCommand(
        "dataset:1", LineageAssetType.DATASET, "Sales Dataset", "TEST", "1", null,
        null, null, null, null, null, null));
    writeService.registerAsset(new LineageRegistrationService.RegisterAssetCommand(
        "dataset:2", LineageAssetType.DATASET, "Inventory", "TEST", "2", null,
        null, null, null, null, null, null));
    writeService.registerAsset(new LineageRegistrationService.RegisterAssetCommand(
        "chart:1", LineageAssetType.CHART, "Sales Chart", "TEST", "3", null,
        null, null, null, null, null, null));

    List<LineageAsset> values = queryService.searchAssets("sales", LineageAssetType.DATASET, 10);

    assertEquals(1, values.size());
    assertEquals("dataset:1", values.get(0).assetKey());
    assertEquals(1, queryService.searchAssets(null, LineageAssetType.DATASET, 1).size());
  }

  @Test
  void batchSizingMakesThousandColumnLineageWritesBounded() {
    assertEquals(25, 2 * LineageBatchSupport.batchExecutionCount(2_000, 200)
        + LineageBatchSupport.batchExecutionCount(1_000, 200));
    assertEquals(5, LineageBatchSupport.batchExecutionCount(1_000, 256));
    assertEquals(0, LineageBatchSupport.batchExecutionCount(0, 200));
  }

  @Test
  void batchApiDeduplicatesInputsAndSkipsEmptyCollections() {
    InMemoryLineageRepository repository = new InMemoryLineageRepository();
    LineageRegistrationService writeService = registrationService(repository);
    LineageQueryService queryService = queryService(repository);

    Map<String, LineageAsset> assets = writeService.registerAssetsBatch(
        List.of(asset("column:a", LineageAssetType.COLUMN),
            asset("column:a", LineageAssetType.COLUMN),
            asset("column:b", LineageAssetType.COLUMN)), 1);
    writeService.registerRelationsBatch(List.of(
        relation(assets.get("column:a").id(), assets.get("column:b").id(),
            LineageRelationType.DERIVES_FROM),
        relation(assets.get("column:a").id(), assets.get("column:b").id(),
            LineageRelationType.DERIVES_FROM)), 7);

    assertEquals(2, assets.size());
    assertEquals(1, repository.relations.size());
    assertTrue(writeService.registerAssetsBatch(List.of(), 10).isEmpty());
    writeService.registerRelationsBatch(List.of(), 10);
    assertEquals(1, repository.relations.size());
  }

  private static LineageRegistrationService registrationService(
    LineageRepository repository) {
  LineageRegistrationDraftFactory draftFactory =
      new LineageRegistrationDraftFactory(repository);
  return new LineageRegistrationService(
      new LineageAssetRegistrar(repository, draftFactory),
      new LineageRelationRegistrar(repository, draftFactory));
}

private static LineageQueryService queryService(LineageRepository repository) {
  LineageAssetReader assetReader = new LineageAssetReader(repository);
  return new LineageQueryService(
      assetReader, new LineageGraphReader(repository, assetReader));
}

  private static Set<Long> ids(LineageGraph graph) {
    return graph.nodes().stream().map(LineageAsset::id).collect(Collectors.toSet());
  }

  private static LineageRegistrationService.RegisterAssetCommand asset(
      String assetKey, LineageAssetType assetType) {
    return new LineageRegistrationService.RegisterAssetCommand(
        assetKey, assetType, assetKey, "TEST", assetKey, null,
        null, null, null, null, null, null);
  }

  private static LineageRegistrationService.RegisterRelationCommand relation(
      long sourceAssetId, long targetAssetId, LineageRelationType relationType) {
    return new LineageRegistrationService.RegisterRelationCommand(
        sourceAssetId, targetAssetId, relationType, "TEST", "case", null,
        null, "v1", Instant.parse("2026-08-20T00:00:00Z"), null);
  }

  private static final class InMemoryLineageRepository implements LineageRepository {
    private final AtomicLong assetIds = new AtomicLong(1);
    private final AtomicLong relationIds = new AtomicLong(1);
    private final Map<Long, LineageAsset> assets = new LinkedHashMap<>();
    private final Map<String, Long> assetKeys = new LinkedHashMap<>();
    private final Map<Long, LineageRelation> relations = new LinkedHashMap<>();

    @Override
    public LineageAsset upsertAsset(LineageAssetDraft draft) {
      Long existingId = assetKeys.get(draft.assetKey());
      long id = existingId == null ? assetIds.getAndIncrement() : existingId;
      Instant now = Instant.parse("2026-08-20T00:00:00Z");
      LineageAsset existing = assets.get(id);
      LineageAsset asset = new LineageAsset(
          id, draft.assetKey(), draft.assetType(), draft.name(), draft.sourceType(), draft.sourceId(),
          draft.parentAssetId(), draft.dataSourceId(), draft.databaseName(), draft.schemaName(),
          draft.tableName(), draft.columnName(), draft.properties(),
          existing == null ? now : existing.createTime(), now);
      assets.put(id, asset);
      assetKeys.put(draft.assetKey(), id);
      return asset;
    }

    @Override
    public LineageRelation upsertRelation(LineageRelationDraft draft) {
      long id = relationIds.getAndIncrement();
      Instant now = Instant.parse("2026-08-20T00:00:00Z");
      LineageRelation relation = new LineageRelation(
          id, draft.sourceAssetId(), draft.targetAssetId(), draft.relationType(),
          draft.sourceType(), draft.sourceId(), draft.expression(), draft.confidence(),
          draft.version(), draft.observedAt(), draft.properties(), now, now);
      relations.put(id, relation);
      return relation;
    }

    @Override
    public Map<String, LineageAsset> upsertAssets(List<LineageAssetDraft> drafts, int batchSize) {
      Map<String, LineageAsset> result = new LinkedHashMap<>();
      for (LineageAssetDraft draft : drafts) result.put(draft.assetKey(), upsertAsset(draft));
      return result;
    }

    @Override
    public void upsertRelations(List<LineageRelationDraft> drafts, int batchSize) {
      for (LineageRelationDraft draft : drafts) upsertRelation(draft);
    }

    @Override
    public int deleteRelationsByEvidence(String sourceType, String sourceId) {
      return 0;
    }

    @Override
    public Set<Long> findAssetIdsByEvidence(String sourceType, String sourceId) {
      return Set.of();
    }

    @Override
    public int deleteUnreferencedOwnedAssets(Set<Long> assetIds, String ownerType, String ownerId) {
      return 0;
    }

    @Override
    public Optional<LineageAsset> lockAssetByKey(String assetKey) {
      return findAssetByKey(assetKey);
    }

    @Override
    public Optional<LineageAsset> findAsset(long assetId) {
      return Optional.ofNullable(assets.get(assetId));
    }

    @Override
    public Optional<LineageAsset> findAssetByKey(String assetKey) {
      Long id = assetKeys.get(assetKey);
      return id == null ? Optional.empty() : findAsset(id);
    }

    @Override
    public List<LineageAsset> searchAssets(String keyword, LineageAssetType assetType, int limit) {
      String normalized = keyword == null ? "" : keyword.toLowerCase(Locale.ROOT);
      return assets.values().stream()
          .filter(asset -> assetType == null || asset.assetType() == assetType)
          .filter(asset -> normalized.isBlank()
              || asset.name().toLowerCase(Locale.ROOT).contains(normalized)
              || asset.assetKey().toLowerCase(Locale.ROOT).contains(normalized))
          .limit(limit)
          .toList();
    }

    @Override
    public List<LineageAsset> findAssetsByIds(Set<Long> assetIds) {
      return assetIds.stream().map(assets::get).filter(java.util.Objects::nonNull).toList();
    }

    @Override
    public List<LineageRelation> findOutgoingRelations(Set<Long> sourceAssetIds) {
      return relations.values().stream()
          .filter(relation -> sourceAssetIds.contains(relation.sourceAssetId()))
          .toList();
    }

    @Override
    public List<LineageRelation> findIncomingRelations(Set<Long> targetAssetIds) {
      return relations.values().stream()
          .filter(relation -> targetAssetIds.contains(relation.targetAssetId()))
          .toList();
    }
  }
}
