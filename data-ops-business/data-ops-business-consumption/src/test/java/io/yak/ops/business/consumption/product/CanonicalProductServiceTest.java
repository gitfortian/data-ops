package io.yak.ops.business.consumption.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.yak.ops.business.asset.application.AssetAppService;
import io.yak.ops.business.asset.application.AssetAppService.AssetView;
import io.yak.ops.business.consumption.product.discovery.CanonicalProductDetail;
import io.yak.ops.business.consumption.product.discovery.CanonicalProductService;
import io.yak.ops.business.consumption.product.discovery.ProductDiscoveryService;
import io.yak.ops.business.consumption.product.identity.DomainRef;
import io.yak.ops.business.consumption.product.identity.ProductKey;
import io.yak.ops.business.consumption.product.model.DataProductView;
import io.yak.ops.business.consumption.product.model.ProductType;
import io.yak.ops.business.consumption.product.model.ProviderEvidenceState;
import io.yak.ops.business.consumption.product.provider.ProductLookupResult;
import io.yak.ops.business.consumption.product.provider.ProductLookupState;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class CanonicalProductServiceTest {

  @Test
  void canonicalDetailUsesStableProductKeyAndSourceIdentityForNavigation() {
    ProductDiscoveryService discovery = mock(ProductDiscoveryService.class);
    AssetAppService assets = mock(AssetAppService.class);
    CanonicalProductService service = new CanonicalProductService(discovery, assets);
    ProductKey key = new ProductKey(ProductType.DATASET, "42");
    DataProductView product = mock(DataProductView.class);
    when(product.productKey()).thenReturn(key);
    when(product.sections()).thenReturn(List.of());
    when(discovery.get(key)).thenReturn(ProductLookupResult.found(product));

    CanonicalProductDetail detail = service.detail(key);

    assertEquals(ProductLookupState.FOUND, detail.state());
    assertEquals("/data-analysis/consumption/DATASET%3A42", detail.navigation().canonicalHref());
    assertEquals("/dataset/42", detail.navigation().sourceHref());
    assertNull(detail.navigation().assetHref());
  }

  @Test
  void assetGovernanceEvidenceComesFromAssetOwnerDomainWithoutReplacingSourceTruth() {
    ProductDiscoveryService discovery = mock(ProductDiscoveryService.class);
    AssetAppService assets = mock(AssetAppService.class);
    CanonicalProductService service = new CanonicalProductService(discovery, assets);
    ProductKey key = new ProductKey(ProductType.DATASET, "42");
    DataProductView product = mock(DataProductView.class);
    when(product.productKey()).thenReturn(key);
    when(product.sections()).thenReturn(List.of());
    when(product.assetRef()).thenReturn(new DomainRef("ASSET", "9"));
    when(discovery.get(key)).thenReturn(ProductLookupResult.found(product));

    AssetView asset = mock(AssetView.class);
    when(asset.status()).thenReturn("PUBLISHED");
    when(asset.owner()).thenReturn("data-team");
    when(asset.securityLevelCode()).thenReturn("L2");
    when(asset.healthScore()).thenReturn(91);
    when(asset.healthGrade()).thenReturn("A");
    when(asset.updateTime()).thenReturn(LocalDateTime.of(2026, 9, 24, 10, 0));
    when(assets.get(9L)).thenReturn(asset);

    CanonicalProductDetail detail = service.detail(key);
    CanonicalProductDetail.GovernanceEvidence evidence = detail.governanceEvidence().get(0);

    assertEquals("asset-governance", evidence.sectionKey());
    assertEquals(ProviderEvidenceState.READY, evidence.state());
    assertEquals("ASSET", evidence.ownerDomain());
    assertEquals("data-team", evidence.facts().get("owner"));
    assertEquals("L2", evidence.facts().get("securityLevel"));
    assertEquals(91, evidence.facts().get("healthScore"));
    assertEquals("/data-asset/detail/9", detail.navigation().assetHref());
  }

  @Test
  void unavailableProviderNeverDegradesToNotFoundNavigation() {
    ProductDiscoveryService discovery = mock(ProductDiscoveryService.class);
    AssetAppService assets = mock(AssetAppService.class);
    CanonicalProductService service = new CanonicalProductService(discovery, assets);
    ProductKey key = new ProductKey(ProductType.DATA_SERVICE, "7");
    when(discovery.get(key)).thenReturn(ProductLookupResult.unavailable("provider down"));

    CanonicalProductDetail detail = service.detail(key);
    CanonicalProductService.NavigationResolution resolution =
        service.fromSource(ProductType.DATA_SERVICE, "7");

    assertEquals(ProductLookupState.UNAVAILABLE, detail.state());
    assertEquals("UNAVAILABLE", resolution.state());
    assertNull(resolution.canonicalHref());
  }
}
