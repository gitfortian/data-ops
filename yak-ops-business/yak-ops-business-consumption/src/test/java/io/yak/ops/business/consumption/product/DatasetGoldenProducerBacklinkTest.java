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

/** Phase 4 #104 Golden Path A: Dataset canonical product keeps the producer backlink stable. */
class DatasetGoldenProducerBacklinkTest {

  @Test
  void canonicalDatasetKeepsProducerBacklinkAcrossNewPublishedRevision() {
    Instant observedAt = Instant.parse("2026-09-26T01:00:00Z");
    Dataset firstDataset = new Dataset(
        42L, 7L, "Orders", "Published orders", DatasetStatus.ONLINE, 101L,
        observedAt, observedAt);
    DatasetVersion firstVersion = new DatasetVersion(
        101L, 42L, 3, DatasetSourceType.QUERY_REVISION,
        501L, 601L, 9, "ds-1", "select 1", "{}", observedAt);

    Dataset nextDataset = new Dataset(
        42L, 7L, "Orders", "Published orders", DatasetStatus.ONLINE, 102L,
        observedAt, observedAt.plusSeconds(60));
    DatasetVersion nextVersion = new DatasetVersion(
        102L, 42L, 4, DatasetSourceType.QUERY_REVISION,
        501L, 602L, 10, "ds-1", "select 2", "{}", observedAt.plusSeconds(60));

    DatasetReader reader = mock(DatasetReader.class);
    AssetSourceLookupService assetLookup = mock(AssetSourceLookupService.class);
    AssetAppService assetAppService = mock(AssetAppService.class);
    when(reader.catalog(List.of(42L), false)).thenReturn(
        List.of(new DatasetCatalogEntry(firstDataset, firstVersion, List.of())),
        List.of(new DatasetCatalogEntry(nextDataset, nextVersion, List.of())));
    when(assetLookup.lookup("DATASET", "42")).thenReturn(new AssetSourceLookupService.SourceLookup(
        "NOT_INDEXED", "DATASET", "42", null, null, null, null));

    CanonicalProductService canonical = new CanonicalProductService(
        new ProductDiscoveryService(new DataProductRegistry(List.of(
            new DatasetDataProductProvider(reader, assetLookup)))),
        assetAppService);
    ProductKey productKey = new ProductKey(ProductType.DATASET, "42");

    CanonicalProductDetail first = canonical.detail(productKey);
    CanonicalProductDetail next = canonical.detail(productKey);

    assertNotNull(first.product());
    assertNotNull(next.product());
    assertEquals("TASK_ASSET", first.product().producerRef().domain());
    assertEquals("501", first.product().producerRef().identity());
    assertEquals(first.product().producerRef(), next.product().producerRef());
    assertEquals("/data-development/task/501", first.navigation().producerHref());
    assertEquals(first.navigation().producerHref(), next.navigation().producerHref());
    assertEquals(productKey, next.product().productKey());
    assertEquals("102", next.product().activeVersion().identity());
  }
}
