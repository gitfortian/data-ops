package io.yak.ops.business.consumption.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
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
import io.yak.ops.business.consumption.product.model.DatasetContractPayload;
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
import io.yak.ops.business.dataset.DatasetSourceType;
import io.yak.ops.business.dataset.DatasetStatus;
import io.yak.ops.business.dataset.DatasetVersion;
import io.yak.ops.business.dataset.definition.DatasetReader;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Phase 4 Golden Path A acceptance slices.
 *
 * <p>The path must start from the real Dataset owning projection rather than a fabricated
 * Consumption product. Query authorization, execution and usage evidence remain later #104 slices.
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
}
