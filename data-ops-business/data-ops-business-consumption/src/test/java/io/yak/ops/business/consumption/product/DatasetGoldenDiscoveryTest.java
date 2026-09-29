package io.yak.ops.business.consumption.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.yak.ops.business.asset.application.AssetAppService;
import io.yak.ops.business.asset.application.AssetSourceLookupService;
import io.yak.ops.business.consumption.product.discovery.CanonicalProductDetail;
import io.yak.ops.business.consumption.product.discovery.CanonicalProductService;
import io.yak.ops.business.consumption.product.discovery.DataProductRegistry;
import io.yak.ops.business.consumption.product.discovery.ProductDiscoveryResult;
import io.yak.ops.business.consumption.product.discovery.ProductDiscoveryService;
import io.yak.ops.business.consumption.product.identity.ProductKey;
import io.yak.ops.business.consumption.product.model.AvailabilityState;
import io.yak.ops.business.consumption.product.model.ProductType;
import io.yak.ops.business.consumption.product.model.ProviderEvidenceState;
import io.yak.ops.business.consumption.product.model.SourceLifecycleState;
import io.yak.ops.business.consumption.product.provider.ProductLookupState;
import io.yak.ops.business.consumption.product.provider.ProductSearchCriteria;
import io.yak.ops.business.consumption.product.provider.ProductSearchState;
import io.yak.ops.business.consumption.product.provider.source.DatasetDataProductProvider;
import io.yak.ops.business.dataset.Dataset;
import io.yak.ops.business.dataset.DatasetCatalogEntry;
import io.yak.ops.business.dataset.DatasetSourceType;
import io.yak.ops.business.dataset.DatasetStatus;
import io.yak.ops.business.dataset.DatasetVersion;
import io.yak.ops.business.dataset.definition.DatasetReader;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Phase 4 #104 Golden Path A, slice 1: discovery and canonical Dataset identity. */
class DatasetGoldenDiscoveryTest {

  @Test
  void discoverySourceShortcutAndCanonicalDetailResolveTheSamePublishedDataset() {
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

    when(reader.catalog(List.of(), false)).thenReturn(List.of(entry));
    when(reader.catalog(List.of(42L), false)).thenReturn(List.of(entry));
    when(assetLookup.lookup("DATASET", "42")).thenReturn(new AssetSourceLookupService.SourceLookup(
        "NOT_INDEXED", "DATASET", "42", null, null, null, null));

    DatasetDataProductProvider provider = new DatasetDataProductProvider(reader, assetLookup);
    ProductDiscoveryService discoveryService =
        new ProductDiscoveryService(new DataProductRegistry(List.of(provider)));
    CanonicalProductService canonicalService =
        new CanonicalProductService(discoveryService, assetAppService);

    ProductDiscoveryResult discovery = discoveryService.search(new ProductSearchCriteria(
        ProductType.DATASET,
        "orders",
        null,
        7L,
        null,
        SourceLifecycleState.PUBLISHED,
        null));

    assertEquals(ProductSearchState.READY, discovery.providerStates().get(ProductType.DATASET));
    assertEquals(1L, discovery.total());
    assertEquals(1, discovery.products().size());

    ProductKey discoveredKey = discovery.products().get(0).productKey();
    assertEquals("DATASET:42", discoveredKey.value());

    CanonicalProductService.NavigationResolution shortcut =
        canonicalService.fromSource(ProductType.DATASET, "42");
    assertEquals("FOUND", shortcut.state());
    assertEquals(discoveredKey, shortcut.productKey());
    assertEquals("/data-analysis/consumption/DATASET%3A42", shortcut.canonicalHref());

    CanonicalProductDetail detail = canonicalService.detail(discoveredKey);
    assertEquals(ProductLookupState.FOUND, detail.state());
    assertNotNull(detail.product());
    assertEquals(discoveredKey, detail.product().productKey());
    assertEquals("42", detail.product().sourceRef().sourceIdentity());
    assertEquals("101", detail.product().activeVersion().identity());
    assertEquals("v3", detail.product().activeVersion().displayVersion());
    assertEquals("/dataset/42", detail.navigation().sourceHref());

    CanonicalProductDetail.GovernanceEvidence sourceGovernance = detail.governanceEvidence().stream()
        .filter(evidence -> "source-governance".equals(evidence.sectionKey()))
        .findFirst()
        .orElseThrow();
    assertEquals(ProviderEvidenceState.UNAVAILABLE, sourceGovernance.state());
    assertEquals("DATASET", sourceGovernance.ownerDomain());
    assertNull(sourceGovernance.observedAt());
    assertNotNull(sourceGovernance.reason());
  }

  @Test
  void offlinePublishedDatasetRemainsDiscoverableWithUnavailableRuntime() {
    DatasetReader reader = mock(DatasetReader.class);
    AssetSourceLookupService assetLookup = mock(AssetSourceLookupService.class);
    AssetAppService assetAppService = mock(AssetAppService.class);

    Dataset dataset = new Dataset(
        43L,
        7L,
        "Offline orders",
        "Not published",
        DatasetStatus.OFFLINE,
        102L,
        Instant.parse("2026-09-26T01:00:00Z"),
        Instant.parse("2026-09-26T01:00:00Z"));
    DatasetVersion version = new DatasetVersion(
        102L,
        43L,
        1,
        DatasetSourceType.QUERY_REVISION,
        502L,
        602L,
        1,
        "ds-1",
        "select 1",
        "{}",
        Instant.parse("2026-09-26T01:00:00Z"));
    DatasetCatalogEntry entry = new DatasetCatalogEntry(dataset, version, List.of());

    when(reader.catalog(List.of(), false)).thenReturn(List.of(entry));
    when(reader.catalog(List.of(43L), false)).thenReturn(List.of(entry));

    DatasetDataProductProvider provider = new DatasetDataProductProvider(reader, assetLookup);
    ProductDiscoveryService discoveryService =
        new ProductDiscoveryService(new DataProductRegistry(List.of(provider)));
    CanonicalProductService canonicalService =
        new CanonicalProductService(discoveryService, assetAppService);

    ProductDiscoveryResult discovery = discoveryService.search(new ProductSearchCriteria(
        ProductType.DATASET,
        "offline",
        null,
        7L,
        null,
        SourceLifecycleState.PUBLISHED,
        null));

    assertEquals(ProductSearchState.READY, discovery.providerStates().get(ProductType.DATASET));
    assertEquals(1L, discovery.total());
    assertEquals(1, discovery.products().size());

    ProductKey productKey = new ProductKey(ProductType.DATASET, "43");
    CanonicalProductService.NavigationResolution shortcut =
        canonicalService.fromSource(ProductType.DATASET, "43");
    assertEquals("FOUND", shortcut.state());
    assertEquals(productKey, shortcut.productKey());
    assertEquals("/data-analysis/consumption/DATASET%3A43", shortcut.canonicalHref());

    CanonicalProductDetail detail = canonicalService.detail(productKey);
    assertEquals(ProductLookupState.FOUND, detail.state());
    assertEquals(SourceLifecycleState.PUBLISHED, detail.product().lifecycle());
    assertEquals(AvailabilityState.UNAVAILABLE, detail.product().availability());
    assertNotNull(detail.navigation());
  }
}
