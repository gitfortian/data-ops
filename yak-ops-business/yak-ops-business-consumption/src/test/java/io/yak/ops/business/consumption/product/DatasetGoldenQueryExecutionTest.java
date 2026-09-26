package io.yak.ops.business.consumption.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.asset.application.AssetAppService;
import io.yak.ops.business.asset.application.AssetSourceLookupService;
import io.yak.ops.business.consumption.product.discovery.CanonicalProductDetail;
import io.yak.ops.business.consumption.product.discovery.CanonicalProductService;
import io.yak.ops.business.consumption.product.discovery.DataProductRegistry;
import io.yak.ops.business.consumption.product.discovery.ProductDiscoveryService;
import io.yak.ops.business.consumption.product.identity.ProductKey;
import io.yak.ops.business.consumption.product.model.ProductType;
import io.yak.ops.business.consumption.product.provider.source.DatasetDataProductProvider;
import io.yak.ops.business.consumption.relationship.ConsumerType;
import io.yak.ops.business.consumption.relationship.UsageEvidence;
import io.yak.ops.business.consumption.relationship.UsageEvidenceRepository;
import io.yak.ops.business.consumption.relationship.UsageEvidenceService;
import io.yak.ops.business.consumption.relationship.UsageNormalizationState;
import io.yak.ops.business.consumption.relationship.source.DatasetUsageEvidenceNormalizer;
import io.yak.ops.business.dataset.Dataset;
import io.yak.ops.business.dataset.DatasetCatalogEntry;
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
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** Phase 4 #104 Golden Path A, slice 3: authorized query executes and normalizes stable usage evidence. */
class DatasetGoldenQueryExecutionTest {

  @Test
  void authorizedQueryUsesTheSameImmutableVersionExposedByCanonicalProduct() {
    Instant observedAt = Instant.parse("2026-09-26T01:00:00Z");
    Dataset dataset = new Dataset(
        42L,
        7L,
        "Orders",
        "Published orders",
        DatasetStatus.ONLINE,
        101L,
        observedAt,
        observedAt);
    DatasetVersion version = new DatasetVersion(
        101L,
        42L,
        3,
        DatasetSourceType.QUERY_REVISION,
        501L,
        601L,
        9,
        "ds-1",
        "select 1",
        "{}",
        observedAt);

    DatasetReader reader = mock(DatasetReader.class);
    AssetSourceLookupService assetLookup = mock(AssetSourceLookupService.class);
    AssetAppService assetAppService = mock(AssetAppService.class);
    when(reader.catalog(List.of(42L), false))
        .thenReturn(List.of(new DatasetCatalogEntry(dataset, version, List.of())));
    when(assetLookup.lookup("DATASET", "42")).thenReturn(new AssetSourceLookupService.SourceLookup(
        "NOT_INDEXED", "DATASET", "42", null, null, null, null));

    DatasetDataProductProvider provider = new DatasetDataProductProvider(reader, assetLookup);
    CanonicalProductService canonical = new CanonicalProductService(
        new ProductDiscoveryService(new DataProductRegistry(List.of(provider))), assetAppService);
    CanonicalProductDetail detail = canonical.detail(new ProductKey(ProductType.DATASET, "42"));
    assertNotNull(detail.product());
    assertEquals("101", detail.product().activeVersion().identity());
    assertEquals("v3", detail.product().activeVersion().label());

    DatasetRepository repository = mock(DatasetRepository.class);
    DatasetSourceQueryRegistry registry = mock(DatasetSourceQueryRegistry.class);
    DatasetSourceQueryAdapter adapter = mock(DatasetSourceQueryAdapter.class);
    DatasetQueryPerformanceRecorder recorder = mock(DatasetQueryPerformanceRecorder.class);
    ActionAuthorization authorization = mock(ActionAuthorization.class);

    when(repository.findDataset(42L)).thenReturn(Optional.of(dataset));
    when(repository.findVersion(101L)).thenReturn(Optional.of(version));
    when(repository.listFields(101L)).thenReturn(List.of());
    when(registry.require(DatasetSourceType.QUERY_REVISION)).thenReturn(adapter);
    when(adapter.execute(dataset, version, List.of(), null)).thenReturn(new ExecutionResult(
        new DatasetQueryResult(42L, 101L, 3, List.of(), List.of(), List.of(), 0, false, 5L),
        "ds-1",
        "select 1",
        0L,
        1L,
        2L,
        0L));

    DatasetQueryCoordinator coordinator =
        new DatasetQueryCoordinator(repository, registry, recorder, authorization);
    DatasetQueryResult result =
        coordinator.query(42L, null, DatasetQuerySubject.authenticatedUser("alice"));

    verify(authorization).requirePermission("dataset:query");
    verify(repository).findVersion(101L);
    verify(adapter).execute(dataset, version, List.of(), null);
    assertEquals(101L, result.datasetVersionId());
    assertEquals(3, result.datasetVersionNo());

    ArgumentCaptor<DatasetQueryPerformance> evidence =
        ArgumentCaptor.forClass(DatasetQueryPerformance.class);
    verify(recorder).record(evidence.capture());
    DatasetQueryPerformance trace = evidence.getValue();
    assertEquals(DatasetQueryStatus.SUCCESS, trace.status());
    assertEquals(101L, trace.datasetVersionId());
    assertEquals(3, trace.datasetVersionNo());
    assertEquals("USER", trace.subjectType());
    assertEquals("SECURITY_PRINCIPAL", trace.subjectSourceDomain());
    assertEquals("alice", trace.subjectSourceIdentity());

    UsageEvidenceRepository usageRepository = mock(UsageEvidenceRepository.class);
    when(usageRepository.findByDeduplicationId(
        7L, "DATASET_QUERY_PERFORMANCE:" + trace.queryId()))
        .thenReturn(Optional.empty());
    when(usageRepository.save(any(UsageEvidence.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    var normalization = new DatasetUsageEvidenceNormalizer(new UsageEvidenceService(usageRepository))
        .normalize(7L, trace);

    assertEquals(UsageNormalizationState.NORMALIZED, normalization.state());
    UsageEvidence usage = normalization.evidence();
    assertNotNull(usage);
    assertEquals(detail.product().productKey(), usage.productKey());
    assertEquals(detail.product().activeVersion().identity(), usage.sourceVersion().identity());
    assertEquals(detail.product().activeVersion().label(), usage.sourceVersion().displayVersion());
    assertEquals(ConsumerType.USER, usage.consumerRef().consumerType());
    assertEquals("SECURITY_PRINCIPAL", usage.consumerRef().sourceDomain());
    assertEquals("alice", usage.consumerRef().sourceIdentity());
    assertEquals("USER:SECURITY_PRINCIPAL:alice", usage.consumerRef().identityKey());
    assertEquals("query:" + result.queryId(), usage.providerEvidenceRef());
  }
}
