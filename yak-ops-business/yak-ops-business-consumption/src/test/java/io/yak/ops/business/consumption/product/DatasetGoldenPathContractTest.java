package io.yak.ops.business.consumption.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.asset.application.AssetAppService;
import io.yak.ops.business.asset.application.AssetSourceLookupService;
import io.yak.ops.business.consumption.product.discovery.CanonicalProductDetail;
import io.yak.ops.business.consumption.product.discovery.CanonicalProductService;
import io.yak.ops.business.consumption.product.discovery.DataProductRegistry;
import io.yak.ops.business.consumption.product.discovery.ProductDiscoveryResult;
import io.yak.ops.business.consumption.product.discovery.ProductDiscoveryService;
import io.yak.ops.business.consumption.product.identity.ProductKey;
import io.yak.ops.business.consumption.product.model.DatasetContractPayload;
import io.yak.ops.business.consumption.product.model.ProductType;
import io.yak.ops.business.consumption.product.provider.DataProductProvider;
import io.yak.ops.business.consumption.product.provider.ProductLookupState;
import io.yak.ops.business.consumption.product.provider.ProductSearchCriteria;
import io.yak.ops.business.consumption.product.provider.source.DatasetDataProductProvider;
import io.yak.ops.business.consumption.relationship.ConsumerRef;
import io.yak.ops.business.consumption.relationship.UsageEvidence;
import io.yak.ops.business.consumption.relationship.UsageEvidenceRepository;
import io.yak.ops.business.consumption.relationship.UsageEvidenceService;
import io.yak.ops.business.consumption.relationship.UsageNormalizationState;
import io.yak.ops.business.consumption.relationship.source.DatasetUsageEvidenceNormalizer;
import io.yak.ops.business.dataset.Dataset;
import io.yak.ops.business.dataset.DatasetCatalogEntry;
import io.yak.ops.business.dataset.DatasetField;
import io.yak.ops.business.dataset.DatasetFieldDataType;
import io.yak.ops.business.dataset.DatasetFieldRole;
import io.yak.ops.business.dataset.DatasetQueryPerformance;
import io.yak.ops.business.dataset.DatasetQueryResult;
import io.yak.ops.business.dataset.DatasetQueryStatus;
import io.yak.ops.business.dataset.DatasetQuerySubject;
import io.yak.ops.business.dataset.DatasetSourceType;
import io.yak.ops.business.dataset.DatasetStatus;
import io.yak.ops.business.dataset.DatasetVersion;
import io.yak.ops.business.dataset.definition.DatasetReader;
import io.yak.ops.business.dataset.observability.DatasetQueryPerformanceRecorder;
import io.yak.ops.business.dataset.query.DatasetQueryCoordinator;
import io.yak.ops.business.dataset.query.DatasetSourceQueryAdapter;
import io.yak.ops.business.dataset.query.DatasetSourceQueryAdapter.ExecutionResult;
import io.yak.ops.business.dataset.query.DatasetSourceQueryRegistry;
import io.yak.ops.business.dataset.repository.DatasetRepository;
import io.yak.ops.core.security.ActionAuthorization;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Phase 4 Golden Path A acceptance slices.
 *
 * <p>The path starts from the real Dataset owning projection, crosses the real Dataset query
 * coordinator boundary, and normalizes its successful source evidence into Consumption Usage
 * Evidence. Consumer/Impact remains a later #104 slice.
 */
class DatasetGoldenPathContractTest {

  @Test
  void publishedDatasetOwningProjectionKeepsStableIdentityThroughDiscoveryAndCanonicalDetail() {
    ProductKey key = new ProductKey(ProductType.DATASET, "42");
    DatasetReader reader = mock(DatasetReader.class);
    AssetSourceLookupService assetLookup = mock(AssetSourceLookupService.class);
    DatasetCatalogEntry sourceEntry = publishedDataset();
    when(reader.catalog(List.of(), true)).thenReturn(List.of(sourceEntry));
    when(reader.catalog(List.of(42L), false)).thenReturn(List.of(sourceEntry));
    when(assetLookup.lookup("DATASET", "42")).thenReturn(new AssetSourceLookupService.SourceLookup(
        "NOT_INDEXED", "DATASET", "42", null, null, null, null));

    DataProductProvider datasetProvider = new DatasetDataProductProvider(reader, assetLookup);
    ProductDiscoveryService discovery =
        new ProductDiscoveryService(new DataProductRegistry(List.of(datasetProvider)));
    CanonicalProductService canonical =
        new CanonicalProductService(discovery, mock(AssetAppService.class));

    ProductDiscoveryResult discovered = discovery.search(new ProductSearchCriteria(
        ProductType.DATASET, null, null, null, null, null, null));
    CanonicalProductDetail detail = canonical.detail(key);

    assertEquals(1, discovered.products().size());
    assertEquals(key, discovered.products().get(0).productKey());
    assertEquals("101", discovered.products().get(0).activeVersion().identity());
    assertEquals("v3", discovered.products().get(0).activeVersion().displayVersion());

    DatasetContractPayload discoveredContract = assertInstanceOf(
        DatasetContractPayload.class, discovered.products().get(0).contractPayload());
    assertEquals(101L, discoveredContract.versionId());
    assertEquals(3, discoveredContract.versionNo());
    assertEquals(2, discoveredContract.columns().size());
    assertEquals(new DatasetContractPayload.DatasetColumnContract(
        "order_id", "order_id", "Order ID", "NUMBER", false,
        "Stable order identifier", "DIMENSION", 1), discoveredContract.columns().get(0));
    assertEquals(new DatasetContractPayload.DatasetColumnContract(
        "amount", "amount", "Amount", "NUMBER", true,
        "Order amount", "MEASURE", 2), discoveredContract.columns().get(1));

    assertEquals(ProductLookupState.FOUND, detail.state());
    assertEquals(key, detail.product().productKey());
    assertEquals("101", detail.product().activeVersion().identity());
    DatasetContractPayload canonicalContract = assertInstanceOf(
        DatasetContractPayload.class, detail.product().contractPayload());
    assertEquals(discoveredContract, canonicalContract);
    assertEquals("/data-analysis/consumption/DATASET%3A42", detail.navigation().canonicalHref());
    assertEquals("/dataset/42", detail.navigation().sourceHref());
  }

  @Test
  void publishedDatasetQueryCrossesActionGateAndNormalizesStableUsageEvidence() {
    DatasetCatalogEntry sourceEntry = publishedDataset();
    Dataset dataset = sourceEntry.dataset();
    DatasetVersion version = sourceEntry.currentVersion();
    List<DatasetField> fields = sourceEntry.fields();

    DatasetRepository repository = mock(DatasetRepository.class);
    DatasetSourceQueryRegistry registry = mock(DatasetSourceQueryRegistry.class);
    DatasetSourceQueryAdapter adapter = mock(DatasetSourceQueryAdapter.class);
    DatasetQueryPerformanceRecorder recorder = mock(DatasetQueryPerformanceRecorder.class);
    ActionAuthorization authorization = mock(ActionAuthorization.class);

    when(repository.findDataset(42L)).thenReturn(Optional.of(dataset));
    when(repository.findVersion(101L)).thenReturn(Optional.of(version));
    when(repository.listFields(101L)).thenReturn(fields);
    when(registry.require(DatasetSourceType.QUERY_REVISION)).thenReturn(adapter);
    when(adapter.execute(dataset, version, fields, null)).thenReturn(new ExecutionResult(
        new DatasetQueryResult(
            42L, 101L, 3, List.of(), List.of(), List.of(List.of(1001L, 88.5d)),
            1, false, 4L),
        "ds-1", "select order_id, amount from orders", 0L, 1L, 2L, 1L));

    DatasetQueryCoordinator coordinator =
        new DatasetQueryCoordinator(repository, registry, recorder, authorization);
    DatasetQuerySubject subject = DatasetQuerySubject.authenticatedUser("alice");

    DatasetQueryResult result = coordinator.query(42L, null, subject);

    verify(authorization).requirePermission("dataset:query");
    verify(adapter).execute(dataset, version, fields, null);
    assertNotNull(result.queryId());
    assertEquals(42L, result.datasetId());
    assertEquals(101L, result.datasetVersionId());
    assertEquals(3, result.datasetVersionNo());
    assertEquals(1, result.returnedRows());

    ArgumentCaptor<DatasetQueryPerformance> evidence =
        ArgumentCaptor.forClass(DatasetQueryPerformance.class);
    verify(recorder).record(evidence.capture());
    DatasetQueryPerformance trace = evidence.getValue();
    assertEquals(result.queryId(), trace.queryId());
    assertEquals(42L, trace.datasetId());
    assertEquals(101L, trace.datasetVersionId());
    assertEquals(3, trace.datasetVersionNo());
    assertEquals(DatasetQueryStatus.SUCCESS, trace.status());
    assertNull(trace.failureStage());
    assertEquals("USER", trace.subjectType());
    assertEquals("SECURITY_PRINCIPAL", trace.subjectSourceDomain());
    assertEquals("alice", trace.subjectSourceIdentity());

    InMemoryUsageEvidenceRepository usageRepository = new InMemoryUsageEvidenceRepository();
    var normalization = new DatasetUsageEvidenceNormalizer(new UsageEvidenceService(usageRepository))
        .normalize(7L, trace);

    assertEquals(UsageNormalizationState.NORMALIZED, normalization.state());
    UsageEvidence usage = normalization.evidence();
    assertNotNull(usage);
    assertEquals(new ProductKey(ProductType.DATASET, "42"), usage.productKey());
    assertEquals("101", usage.sourceVersion().identity());
    assertEquals("v3", usage.sourceVersion().displayVersion());
    assertEquals("USER:SECURITY_PRINCIPAL:alice", usage.consumerRef().identityKey());
    assertEquals("query:" + result.queryId(), usage.providerEvidenceRef());
    assertEquals("DATASET_QUERY_PERFORMANCE:" + result.queryId(), usage.deduplicationId());
    assertEquals(1, usageRepository.values.size());
  }

  private DatasetCatalogEntry publishedDataset() {
    Instant now = Instant.now();
    Dataset dataset = new Dataset(
        42L, 7L, "Orders", "Published orders", DatasetStatus.ONLINE, 101L, now, now);
    DatasetVersion version = new DatasetVersion(
        101L, 42L, 3, DatasetSourceType.QUERY_REVISION,
        501L, 601L, 9, "ds-1", "select 1", "{}", now);
    List<DatasetField> fields = List.of(
        new DatasetField(
            "order_id", 101L, "order_id", "Order ID", DatasetFieldDataType.NUMBER,
            false, "Stable order identifier", DatasetFieldRole.DIMENSION, 1),
        new DatasetField(
            "amount", 101L, "amount", "Amount", DatasetFieldDataType.NUMBER,
            true, "Order amount", DatasetFieldRole.MEASURE, 2));
    return new DatasetCatalogEntry(dataset, version, fields);
  }

  private static final class InMemoryUsageEvidenceRepository implements UsageEvidenceRepository {
    private final AtomicLong sequence = new AtomicLong(1);
    private final List<UsageEvidence> values = new ArrayList<>();

    @Override
    public Optional<UsageEvidence> findByDeduplicationId(Long projectId, String deduplicationId) {
      return values.stream()
          .filter(value -> value.projectId().equals(projectId)
              && value.deduplicationId().equals(deduplicationId))
          .findFirst();
    }

    @Override
    public UsageEvidence save(UsageEvidence evidence) {
      UsageEvidence saved = new UsageEvidence(
          sequence.getAndIncrement(),
          evidence.projectId(),
          evidence.productKey(),
          evidence.sourceVersion(),
          evidence.consumerRef(),
          evidence.observedAt(),
          evidence.consumptionMode(),
          evidence.outcome(),
          evidence.provider(),
          evidence.providerEvidenceRef(),
          evidence.deduplicationId(),
          evidence.normalizedAt());
      values.add(saved);
      return saved;
    }

    @Override
    public List<UsageEvidence> list(
        Long projectId, ProductKey productKey, ConsumerRef consumerRef, int limit) {
      return values.stream()
          .filter(value -> value.projectId().equals(projectId))
          .filter(value -> productKey == null || value.productKey().equals(productKey))
          .filter(value -> consumerRef == null || value.consumerRef().equals(consumerRef))
          .limit(limit)
          .toList();
    }
  }
}
