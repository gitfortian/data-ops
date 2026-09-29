package io.yak.ops.business.consumption.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
import io.yak.ops.business.consumption.relationship.UsageEvidence;
import io.yak.ops.business.consumption.relationship.UsageEvidenceRepository;
import io.yak.ops.business.consumption.relationship.UsageEvidenceService;
import io.yak.ops.business.consumption.relationship.UsageNormalizationState;
import io.yak.ops.business.consumption.relationship.source.DatasetUsageEvidenceNormalizer;
import io.yak.ops.business.dataset.Dataset;
import io.yak.ops.business.dataset.DatasetCatalogEntry;
import io.yak.ops.business.dataset.DatasetQueryPerformance;
import io.yak.ops.business.dataset.DatasetQueryStatus;
import io.yak.ops.business.dataset.DatasetQuerySubject;
import io.yak.ops.business.dataset.DatasetSourceType;
import io.yak.ops.business.dataset.DatasetStatus;
import io.yak.ops.business.dataset.DatasetVersion;
import io.yak.ops.business.dataset.definition.DatasetReader;
import io.yak.ops.business.dataset.observability.DatasetQueryPerformanceRecorder;
import io.yak.ops.business.dataset.query.DatasetQueryCoordinator;
import io.yak.ops.business.dataset.query.DatasetSourceQueryAdapter;
import io.yak.ops.business.dataset.query.DatasetSourceQueryRegistry;
import io.yak.ops.business.dataset.repository.DatasetRepository;
import io.yak.ops.core.security.ActionAuthorization;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** Phase 4 #104 Golden Path A: Dataset execution failure remains diagnostic, not usage. */
class DatasetGoldenQueryFailureTest {

  @Test
  void failedQueryKeepsCanonicalProductIdentityAndDoesNotCreateUsageEvidence() {
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
    ProductKey productKey = new ProductKey(ProductType.DATASET, "42");

    CanonicalProductDetail beforeFailure = canonical.detail(productKey);
    assertNotNull(beforeFailure.product());
    assertEquals(productKey, beforeFailure.product().productKey());
    assertEquals("101", beforeFailure.product().activeVersion().identity());

    DatasetRepository datasetRepository = mock(DatasetRepository.class);
    DatasetSourceQueryRegistry registry = mock(DatasetSourceQueryRegistry.class);
    DatasetSourceQueryAdapter adapter = mock(DatasetSourceQueryAdapter.class);
    DatasetQueryPerformanceRecorder recorder = mock(DatasetQueryPerformanceRecorder.class);
    ActionAuthorization authorization = mock(ActionAuthorization.class);

    when(datasetRepository.findDataset(42L)).thenReturn(Optional.of(dataset));
    when(datasetRepository.findVersion(101L)).thenReturn(Optional.of(version));
    when(datasetRepository.listFields(101L)).thenReturn(List.of());
    when(registry.require(DatasetSourceType.QUERY_REVISION)).thenReturn(adapter);
    when(adapter.execute(dataset, version, List.of(), null))
        .thenThrow(new IllegalStateException("source unavailable"));

    DatasetQueryCoordinator coordinator =
        new DatasetQueryCoordinator(datasetRepository, registry, recorder, authorization);

    IllegalStateException failure = assertThrows(
        IllegalStateException.class,
        () -> coordinator.query(42L, null, DatasetQuerySubject.authenticatedUser("alice")));
    assertEquals("source unavailable", failure.getMessage());
    verify(authorization).requirePermission("dataset:query");

    ArgumentCaptor<DatasetQueryPerformance> diagnostic =
        ArgumentCaptor.forClass(DatasetQueryPerformance.class);
    verify(recorder).record(diagnostic.capture());
    DatasetQueryPerformance trace = diagnostic.getValue();
    assertEquals(DatasetQueryStatus.FAILED, trace.status());
    assertEquals("EXECUTE_SOURCE", trace.failureStage());
    assertEquals(42L, trace.datasetId());
    assertEquals(101L, trace.datasetVersionId());
    assertEquals(3, trace.datasetVersionNo());
    assertEquals("USER", trace.subjectType());
    assertEquals("SECURITY_PRINCIPAL", trace.subjectSourceDomain());
    assertEquals("alice", trace.subjectSourceIdentity());

    UsageEvidenceRepository usageRepository = mock(UsageEvidenceRepository.class);
    var normalization = new DatasetUsageEvidenceNormalizer(new UsageEvidenceService(usageRepository))
        .normalize(7L, trace);

    assertEquals(UsageNormalizationState.IGNORED, normalization.state());
    assertEquals("query:" + trace.queryId(), normalization.providerEvidenceRef());
    verify(usageRepository, never()).save(any(UsageEvidence.class));

    CanonicalProductDetail afterFailure = canonical.detail(productKey);
    assertNotNull(afterFailure.product());
    assertEquals(beforeFailure.product().productKey(), afterFailure.product().productKey());
    assertEquals(beforeFailure.product().sourceRef(), afterFailure.product().sourceRef());
    assertEquals(beforeFailure.product().activeVersion(), afterFailure.product().activeVersion());
  }
}
