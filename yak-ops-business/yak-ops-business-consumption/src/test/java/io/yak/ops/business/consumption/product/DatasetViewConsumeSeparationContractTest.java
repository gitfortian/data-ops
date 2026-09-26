package io.yak.ops.business.consumption.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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
import io.yak.ops.business.consumption.product.discovery.ProductDiscoveryResult;
import io.yak.ops.business.consumption.product.discovery.ProductDiscoveryService;
import io.yak.ops.business.consumption.product.identity.ProductKey;
import io.yak.ops.business.consumption.product.model.ProductType;
import io.yak.ops.business.consumption.product.provider.DataProductProvider;
import io.yak.ops.business.consumption.product.provider.ProductLookupState;
import io.yak.ops.business.consumption.product.provider.ProductSearchCriteria;
import io.yak.ops.business.consumption.product.provider.source.DatasetDataProductProvider;
import io.yak.ops.business.dataset.Dataset;
import io.yak.ops.business.dataset.DatasetCatalogEntry;
import io.yak.ops.business.dataset.DatasetField;
import io.yak.ops.business.dataset.DatasetFieldDataType;
import io.yak.ops.business.dataset.DatasetFieldRole;
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

/** Verifies that Dataset view/discovery and consume authorization remain independent decisions. */
class DatasetViewConsumeSeparationContractTest {

  @Test
  void canonicalDatasetCanRemainViewableWhileQueryConsumptionIsForbidden() {
    ProductKey key = new ProductKey(ProductType.DATASET, "42");
    DatasetReader reader = mock(DatasetReader.class);
    AssetSourceLookupService assetLookup = mock(AssetSourceLookupService.class);
    DatasetCatalogEntry sourceEntry = publishedDataset();
    when(reader.catalog(List.of(42L), false)).thenReturn(List.of(sourceEntry));
    when(assetLookup.lookup("DATASET", "42")).thenReturn(new AssetSourceLookupService.SourceLookup(
        "NOT_INDEXED", "DATASET", "42", null, null, null, null));

    DataProductProvider provider = new DatasetDataProductProvider(reader, assetLookup);
    ProductDiscoveryService discovery =
        new ProductDiscoveryService(new DataProductRegistry(List.of(provider)));
    CanonicalProductService canonical =
        new CanonicalProductService(discovery, mock(AssetAppService.class));

    CanonicalProductDetail detail = canonical.detail(key);

    assertEquals(ProductLookupState.FOUND, detail.state());
    assertEquals(key, detail.product().productKey());
    assertEquals("/data-analysis/consumption/DATASET%3A42", detail.navigation().canonicalHref());
    assertEquals("/dataset/42", detail.navigation().sourceHref());

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

    // The consume denial must not erase or reclassify the independently viewable product.
    CanonicalProductDetail detailAfterDenial = canonical.detail(key);
    assertEquals(ProductLookupState.FOUND, detailAfterDenial.state());
    assertEquals(key, detailAfterDenial.product().productKey());
  }

  @Test
  void offlineDatasetIsExcludedFromDiscoveryAndReportedAsNotDiscoverableByStableId() {
    ProductKey key = new ProductKey(ProductType.DATASET, "42");
    DatasetReader reader = mock(DatasetReader.class);
    AssetSourceLookupService assetLookup = mock(AssetSourceLookupService.class);
    DatasetCatalogEntry sourceEntry = offlineDataset();
    when(reader.catalog(List.of(), true)).thenReturn(List.of(sourceEntry));
    when(reader.catalog(List.of(42L), false)).thenReturn(List.of(sourceEntry));

    DataProductProvider provider = new DatasetDataProductProvider(reader, assetLookup);
    ProductDiscoveryService discovery =
        new ProductDiscoveryService(new DataProductRegistry(List.of(provider)));
    CanonicalProductService canonical =
        new CanonicalProductService(discovery, mock(AssetAppService.class));

    ProductDiscoveryResult discovered = discovery.search(new ProductSearchCriteria(
        ProductType.DATASET, null, null, null, null, null, null));
    CanonicalProductDetail detail = canonical.detail(key);

    assertEquals(0, discovered.products().size());
    assertEquals(ProductLookupState.NOT_DISCOVERABLE, detail.state());
    assertNull(detail.product());
    assertNull(detail.navigation());
    verify(reader).catalog(List.of(), true);
    verify(reader).catalog(List.of(42L), false);
    verifyNoInteractions(assetLookup);
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

  private DatasetCatalogEntry offlineDataset() {
    DatasetCatalogEntry published = publishedDataset();
    Dataset source = published.dataset();
    Dataset offline = new Dataset(
        source.id(),
        source.projectId(),
        source.name(),
        source.description(),
        DatasetStatus.OFFLINE,
        source.currentVersionId(),
        source.createTime(),
        source.updateTime());
    return new DatasetCatalogEntry(offline, published.currentVersion(), published.fields());
  }
}
