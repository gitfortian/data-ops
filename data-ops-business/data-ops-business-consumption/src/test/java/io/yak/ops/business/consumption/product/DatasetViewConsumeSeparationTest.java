package io.yak.ops.business.consumption.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.yak.ops.business.asset.application.AssetAppService;
import io.yak.ops.business.asset.application.AssetSourceLookupService;
import io.yak.ops.business.consumption.product.discovery.CanonicalProductDetail;
import io.yak.ops.business.consumption.product.discovery.CanonicalProductService;
import io.yak.ops.business.consumption.product.discovery.DataProductRegistry;
import io.yak.ops.business.consumption.product.discovery.ProductDiscoveryService;
import io.yak.ops.business.consumption.product.identity.ProductKey;
import io.yak.ops.business.consumption.product.model.ProductType;
import io.yak.ops.business.consumption.product.provider.ProductLookupState;
import io.yak.ops.business.consumption.product.provider.source.DatasetDataProductProvider;
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
import io.yak.ops.business.dataset.query.DatasetSourceQueryRegistry;
import io.yak.ops.business.dataset.repository.DatasetRepository;
import io.yak.ops.core.security.ActionAccessDeniedException;
import io.yak.ops.core.security.ActionAuthorization;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** Phase 4 #104 Golden Path A, slice 2: view and consume remain independent decisions. */
class DatasetViewConsumeSeparationTest {

  @Test
  void viewableCanonicalDatasetRemainsVisibleWhenQueryActionIsForbidden() {
    ProductKey key = new ProductKey(ProductType.DATASET, "42");
    DatasetReader reader = mock(DatasetReader.class);
    AssetSourceLookupService assetLookup = mock(AssetSourceLookupService.class);
    AssetAppService assetAppService = mock(AssetAppService.class);

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
    DatasetCatalogEntry entry = new DatasetCatalogEntry(dataset, version, List.of());

    when(reader.catalog(List.of(42L), false)).thenReturn(List.of(entry));
    when(assetLookup.lookup("DATASET", "42")).thenReturn(new AssetSourceLookupService.SourceLookup(
        "NOT_INDEXED", "DATASET", "42", null, null, null, null));

    DatasetDataProductProvider provider = new DatasetDataProductProvider(reader, assetLookup);
    ProductDiscoveryService discovery =
        new ProductDiscoveryService(new DataProductRegistry(List.of(provider)));
    CanonicalProductService canonical =
        new CanonicalProductService(discovery, assetAppService);

    CanonicalProductDetail beforeConsume = canonical.detail(key);
    assertEquals(ProductLookupState.FOUND, beforeConsume.state());
    assertEquals(key, beforeConsume.product().productKey());

    DatasetRepository repository = mock(DatasetRepository.class);
    DatasetSourceQueryRegistry registry = mock(DatasetSourceQueryRegistry.class);
    DatasetQueryPerformanceRecorder recorder = mock(DatasetQueryPerformanceRecorder.class);
    ActionAuthorization authorization = mock(ActionAuthorization.class);
    doThrow(new ActionAccessDeniedException("dataset:query"))
        .when(authorization).requirePermission("dataset:query");

    DatasetQueryCoordinator coordinator =
        new DatasetQueryCoordinator(repository, registry, recorder, authorization);

    ActionAccessDeniedException denied = assertThrows(
        ActionAccessDeniedException.class,
        () -> coordinator.query(42L, null, DatasetQuerySubject.authenticatedUser("alice")));

    assertEquals("dataset:query", denied.getPermissionCode());
    verify(authorization).requirePermission("dataset:query");
    verifyNoInteractions(repository, registry);

    ArgumentCaptor<DatasetQueryPerformance> rejectedEvidence =
        ArgumentCaptor.forClass(DatasetQueryPerformance.class);
    verify(recorder).record(rejectedEvidence.capture());
    DatasetQueryPerformance trace = rejectedEvidence.getValue();
    assertEquals(42L, trace.datasetId());
    assertEquals(DatasetQueryStatus.REJECTED, trace.status());
    assertEquals("AUTHORIZE_ACTION", trace.failureStage());
    assertEquals("ActionAccessDeniedException", trace.errorType());
    assertEquals("USER", trace.subjectType());
    assertEquals("SECURITY_PRINCIPAL", trace.subjectSourceDomain());
    assertEquals("alice", trace.subjectSourceIdentity());

    CanonicalProductDetail afterConsumeDenied = canonical.detail(key);
    assertEquals(ProductLookupState.FOUND, afterConsumeDenied.state());
    assertEquals(key, afterConsumeDenied.product().productKey());
  }
}
