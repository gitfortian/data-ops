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
import io.yak.ops.business.consumption.product.discovery.ProductDiscoveryService;
import io.yak.ops.business.consumption.product.identity.ProductKey;
import io.yak.ops.business.consumption.product.model.ProductType;
import io.yak.ops.business.consumption.product.model.ProviderEvidenceState;
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

/** Phase 4 #104 Golden Path A: evidence gaps remain explicit and do not erase Product identity. */
class DatasetGoldenSourceEvidenceGapTest {

  @Test
  void missingSourceObservationAndUnavailableAssetEvidenceRemainExplicitWithoutHidingDataset() {
    DatasetReader reader = mock(DatasetReader.class);
    AssetSourceLookupService assetLookup = mock(AssetSourceLookupService.class);
    AssetAppService assetAppService = mock(AssetAppService.class);

    Dataset dataset = new Dataset(
        42L,
        7L,
        "Orders",
        "Published orders",
        DatasetStatus.ONLINE,
        101L,
        Instant.parse("2026-09-26T01:00:00Z"),
        null);
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
        Instant.parse("2026-09-26T01:00:00Z"));
    DatasetCatalogEntry entry = new DatasetCatalogEntry(dataset, version, List.of());

    when(reader.catalog(List.of(42L), false)).thenReturn(List.of(entry));
    when(assetLookup.lookup("DATASET", "42"))
        .thenThrow(new IllegalStateException("asset provider unavailable"));

    DatasetDataProductProvider provider = new DatasetDataProductProvider(reader, assetLookup);
    ProductDiscoveryService discovery =
        new ProductDiscoveryService(new DataProductRegistry(List.of(provider)));
    CanonicalProductService canonical = new CanonicalProductService(discovery, assetAppService);

    ProductKey key = new ProductKey(ProductType.DATASET, "42");
    CanonicalProductDetail detail = canonical.detail(key);

    assertEquals(ProductLookupState.FOUND, detail.state());
    assertNotNull(detail.product());
    assertEquals(key, detail.product().productKey());
    assertEquals("/dataset/42", detail.navigation().sourceHref());

    CanonicalProductDetail.GovernanceEvidence sourceEvidence = detail.governanceEvidence().stream()
        .filter(evidence -> "source-governance".equals(evidence.sectionKey()))
        .findFirst()
        .orElseThrow();
    assertEquals(ProviderEvidenceState.UNAVAILABLE, sourceEvidence.state());
    assertEquals("DATASET", sourceEvidence.ownerDomain());
    assertNull(sourceEvidence.observedAt());
    assertEquals(
        "Dataset source governance observation time is unavailable",
        sourceEvidence.reason());

    CanonicalProductDetail.GovernanceEvidence assetEvidence = detail.governanceEvidence().stream()
        .filter(evidence -> "asset".equals(evidence.sectionKey()))
        .findFirst()
        .orElseThrow();
    assertEquals(ProviderEvidenceState.UNAVAILABLE, assetEvidence.state());
    assertEquals("ASSET", assetEvidence.ownerDomain());
    assertNull(assetEvidence.observedAt());
    assertEquals("asset provider unavailable", assetEvidence.reason());
  }
}
