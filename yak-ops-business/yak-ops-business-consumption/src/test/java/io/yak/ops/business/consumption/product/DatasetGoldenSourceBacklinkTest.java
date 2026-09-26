package io.yak.ops.business.consumption.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
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
import io.yak.ops.business.dataset.DatasetSourceType;
import io.yak.ops.business.dataset.DatasetStatus;
import io.yak.ops.business.dataset.DatasetVersion;
import io.yak.ops.business.dataset.definition.DatasetReader;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Phase 4 #104 Golden Path A: canonical product keeps a stable Dataset source backlink. */
class DatasetGoldenSourceBacklinkTest {

  @Test
  void canonicalDatasetBacklinkUsesStableSourceIdentityAcrossDisplayAndVersionChanges() {
    Instant observedAt = Instant.parse("2026-09-26T01:00:00Z");

    Dataset firstDataset = new Dataset(
        42L,
        7L,
        "Orders",
        "Published orders",
        DatasetStatus.ONLINE,
        101L,
        observedAt,
        observedAt);
    DatasetVersion firstVersion = new DatasetVersion(
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

    Dataset renamedDataset = new Dataset(
        42L,
        7L,
        "Orders curated",
        "Renamed without changing source identity",
        DatasetStatus.ONLINE,
        102L,
        observedAt,
        observedAt.plusSeconds(60));
    DatasetVersion nextVersion = new DatasetVersion(
        102L,
        42L,
        4,
        DatasetSourceType.QUERY_REVISION,
        501L,
        602L,
        10,
        "ds-1",
        "select 2",
        "{}",
        observedAt.plusSeconds(60));

    DatasetReader reader = mock(DatasetReader.class);
    AssetSourceLookupService assetLookup = mock(AssetSourceLookupService.class);
    AssetAppService assetAppService = mock(AssetAppService.class);

    when(reader.catalog(List.of(42L), false))
        .thenReturn(
            List.of(new DatasetCatalogEntry(firstDataset, firstVersion, List.of())),
            List.of(new DatasetCatalogEntry(renamedDataset, nextVersion, List.of())));
    when(assetLookup.lookup("DATASET", "42")).thenReturn(new AssetSourceLookupService.SourceLookup(
        "NOT_INDEXED", "DATASET", "42", null, null, null, null));

    DatasetDataProductProvider provider = new DatasetDataProductProvider(reader, assetLookup);
    CanonicalProductService canonical = new CanonicalProductService(
        new ProductDiscoveryService(new DataProductRegistry(List.of(provider))), assetAppService);
    ProductKey productKey = new ProductKey(ProductType.DATASET, "42");

    CanonicalProductDetail first = canonical.detail(productKey);
    CanonicalProductDetail afterRenameAndPublish = canonical.detail(productKey);

    assertEquals(ProductLookupState.FOUND, first.state());
    assertEquals(ProductLookupState.FOUND, afterRenameAndPublish.state());
    assertNotNull(first.product());
    assertNotNull(afterRenameAndPublish.product());

    assertEquals(productKey, first.product().productKey());
    assertEquals(productKey, afterRenameAndPublish.product().productKey());
    assertEquals(productKey, first.product().sourceRef().productKey());
    assertEquals(productKey, afterRenameAndPublish.product().sourceRef().productKey());
    assertEquals("42", first.product().sourceRef().sourceIdentity());
    assertEquals("42", afterRenameAndPublish.product().sourceRef().sourceIdentity());

    assertEquals("/dataset/42", first.navigation().sourceHref());
    assertEquals("/dataset/42", afterRenameAndPublish.navigation().sourceHref());
    assertEquals(
        "/data-analysis/consumption/DATASET%3A42", first.navigation().canonicalHref());
    assertEquals(
        "/data-analysis/consumption/DATASET%3A42",
        afterRenameAndPublish.navigation().canonicalHref());

    assertEquals("101", first.product().activeVersion().identity());
    assertEquals("v3", first.product().activeVersion().label());
    assertEquals("102", afterRenameAndPublish.product().activeVersion().identity());
    assertEquals("v4", afterRenameAndPublish.product().activeVersion().label());
  }
}
