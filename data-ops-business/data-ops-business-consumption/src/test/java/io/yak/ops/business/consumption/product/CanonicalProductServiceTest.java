package io.yak.ops.business.consumption.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.business.asset.application.AssetAppService;
import io.yak.ops.business.asset.application.AssetAppService.AssetView;
import io.yak.ops.business.asset.application.AssetDiscoverService;
import io.yak.ops.business.asset.application.AssetDiscoverService.SectionView;
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
import java.util.Map;
import io.yak.ops.spi.section.SectionStatus;
import io.yak.ops.business.consumption.product.model.ProductSectionState;
import org.junit.jupiter.api.Test;

class CanonicalProductServiceTest {

  @Test
  void canonicalSectionsReusePermissionCheckedEvidenceAndIsolateProviderFailure() {
    ProductDiscoveryService discovery = mock(ProductDiscoveryService.class);
    AssetAppService assets = mock(AssetAppService.class);
    AssetDiscoverService sections = mock(AssetDiscoverService.class);
    ProductKey key = new ProductKey(ProductType.DATASET, "42");
    DataProductView product = mock(DataProductView.class);
    when(product.productKey()).thenReturn(key);
    when(product.assetRef()).thenReturn(new DomainRef("ASSET", "9"));
    when(product.sections()).thenReturn(List.of(
        new ProductSectionState("ownership", ProviderEvidenceState.UNAVAILABLE,
            "DATASET", null, "Source owner is unavailable"),
        new ProductSectionState("quality", ProviderEvidenceState.UNAVAILABLE,
            "QUALITY", null, "Not connected")));
    when(discovery.get(key)).thenReturn(ProductLookupResult.found(product));
    when(assets.get(9L)).thenReturn(mock(AssetView.class));
    when(sections.section(9L, "QUALITY", "alice")).thenThrow(new IllegalStateException("down"));
    // Even if a faulty provider supplies data on denial, Consumption must not expose it.
    when(sections.section(9L, "SECURITY", "alice")).thenReturn(
        new SectionView(SectionStatus.PERMISSION_DENIED, "No permission", Map.of("secret", "hidden")));
    when(sections.section(9L, "LINEAGE", "alice")).thenReturn(
        new SectionView(SectionStatus.OK, null, Map.of("downstreamCount", 2)));

    var detail = new CanonicalProductService(discovery, assets, sections).detail(key, "alice");
    var quality = section(detail, "quality");
    var security = section(detail, "security");
    var lineage = section(detail, "lineage");

    assertEquals(ProductLookupState.FOUND, detail.state());
    assertEquals(ProviderEvidenceState.UNAVAILABLE, quality.state());
    assertEquals(Map.of(), quality.facts());
    assertEquals(ProviderEvidenceState.FORBIDDEN, security.state());
    assertEquals(Map.of(), security.facts());
    assertNull(security.observedAt());
    assertEquals(ProviderEvidenceState.READY, lineage.state());
    assertEquals(2, lineage.facts().get("downstreamCount"));
    assertEquals("LINEAGE", lineage.ownerDomain());
    assertEquals("INDEXED_SOURCE_ASSET", lineage.facts().get("evidenceScope"));
    assertNotNull(lineage.observedAt());
    assertEquals(ProviderEvidenceState.UNAVAILABLE, section(detail, "ownership").state());
    assertNull(detail.product().owner());
    assertEquals(1, detail.governanceEvidence().stream()
        .filter(evidence -> "quality".equals(evidence.sectionKey())).count());
    verify(sections).section(9L, "SECURITY", "alice");
  }

  @Test
  void nonApplicableAndHealthyEmptySectionsStayDistinctFromUnavailable() {
    ProductDiscoveryService discovery = mock(ProductDiscoveryService.class);
    AssetAppService assets = mock(AssetAppService.class);
    AssetDiscoverService sections = mock(AssetDiscoverService.class);
    ProductKey key = new ProductKey(ProductType.DATA_SERVICE, "7");
    DataProductView product = mock(DataProductView.class);
    when(product.productKey()).thenReturn(key);
    when(product.assetRef()).thenReturn(new DomainRef("ASSET", "9"));
    when(product.sections()).thenReturn(List.of());
    when(discovery.get(key)).thenReturn(ProductLookupResult.found(product));
    when(assets.get(9L)).thenReturn(mock(AssetView.class));
    when(sections.section(9L, "QUALITY", "alice")).thenReturn(
        new SectionView(SectionStatus.NOT_APPLICABLE, "Physical tables only", null));
    when(sections.section(9L, "SECURITY", "alice")).thenReturn(
        new SectionView(SectionStatus.EMPTY, "No classification", Map.of()));
    when(sections.section(9L, "LINEAGE", "alice")).thenReturn(
        new SectionView(SectionStatus.EMPTY, "No relationships", Map.of()));

    var detail = new CanonicalProductService(discovery, assets, sections).detail(key, "alice");

    assertEquals(ProviderEvidenceState.NOT_APPLICABLE, section(detail, "quality").state());
    assertTrue(section(detail, "quality").facts().isEmpty());
    assertEquals(ProviderEvidenceState.EMPTY, section(detail, "security").state());
    assertNotNull(section(detail, "security").observedAt());
    assertEquals("No classification", section(detail, "security").reason());
  }

  private static CanonicalProductDetail.GovernanceEvidence section(
      CanonicalProductDetail detail, String key) {
    return detail.governanceEvidence().stream().filter(item -> key.equals(item.sectionKey()))
        .findFirst().orElseThrow();
  }

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
