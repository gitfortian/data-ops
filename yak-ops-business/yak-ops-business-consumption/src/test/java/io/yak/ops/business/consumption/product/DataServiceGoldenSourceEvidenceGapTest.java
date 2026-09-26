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
import io.yak.ops.business.consumption.product.provider.source.DataServiceDataProductProvider;
import io.yak.ops.business.dataservice.domain.DataServiceDefinition;
import io.yak.ops.business.dataservice.domain.DataServiceSettings;
import io.yak.ops.business.dataservice.domain.PublishedRuntimeSnapshot;
import io.yak.ops.business.dataservice.domain.RuntimePolicy;
import io.yak.ops.business.dataservice.domain.SourceReference;
import io.yak.ops.business.dataservice.domain.access.AuthMode;
import io.yak.ops.business.dataservice.query.DataServiceReader;
import io.yak.ops.business.dataservice.query.DataServiceView;
import io.yak.ops.business.dataservice.query.DataServiceViewFactory;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Phase 4 #104 Golden Path B: evidence gaps remain explicit and do not erase Product identity. */
class DataServiceGoldenSourceEvidenceGapTest {

  @Test
  void missingSourceObservationAndUnavailableAssetEvidenceRemainExplicitWithoutHidingService() {
    DataServiceReader reader = mock(DataServiceReader.class);
    DataServiceViewFactory viewFactory = mock(DataServiceViewFactory.class);
    AssetSourceLookupService assetLookup = mock(AssetSourceLookupService.class);
    AssetAppService assetAppService = mock(AssetAppService.class);

    DataServiceDefinition definition = definition();
    when(reader.require(88L)).thenReturn(definition);
    when(viewFactory.view(definition)).thenReturn(view());
    when(assetLookup.lookup("DATA_SERVICE", "88"))
        .thenThrow(new IllegalStateException("asset provider unavailable"));

    DataServiceDataProductProvider provider =
        new DataServiceDataProductProvider(reader, viewFactory, assetLookup);
    ProductDiscoveryService discovery =
        new ProductDiscoveryService(new DataProductRegistry(List.of(provider)));
    CanonicalProductService canonical = new CanonicalProductService(discovery, assetAppService);

    ProductKey key = new ProductKey(ProductType.DATA_SERVICE, "88");
    CanonicalProductDetail detail = canonical.detail(key);

    assertEquals(ProductLookupState.FOUND, detail.state());
    assertNotNull(detail.product());
    assertEquals(key, detail.product().productKey());
    assertEquals("/data-service/88", detail.navigation().sourceHref());

    CanonicalProductDetail.GovernanceEvidence sourceEvidence = detail.governanceEvidence().stream()
        .filter(evidence -> "source-governance".equals(evidence.sectionKey()))
        .findFirst()
        .orElseThrow();
    assertEquals(ProviderEvidenceState.UNAVAILABLE, sourceEvidence.state());
    assertEquals("DATA_SERVICE", sourceEvidence.ownerDomain());
    assertNull(sourceEvidence.observedAt());
    assertEquals(
        "Data Service source governance observation time is unavailable",
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

  private DataServiceDefinition definition() {
    return DataServiceDefinition.restore(
        88L,
        7L,
        4L,
        new DataServiceSettings(
            "Orders API", "/orders", 500, 30, true, "Published orders service", true),
        new PublishedRuntimeSnapshot(3L, "select * from orders where tenant_id=:tenantId"),
        new SourceReference("DEVELOPMENT_TASK", "task-55", 9001L, 12),
        RuntimePolicy.defaults(true),
        AuthMode.API_KEY,
        LocalDateTime.of(2026, 9, 26, 14, 0),
        null);
  }

  private DataServiceView view() {
    return new DataServiceView(
        88L,
        "Orders API",
        "/orders",
        "/api/v1/data-service/runtime/orders",
        3L,
        "select * from orders where tenant_id=:tenantId",
        List.of("tenantId"),
        500,
        30,
        true,
        "API_KEY",
        "Published orders service",
        "DEVELOPMENT_TASK",
        "task-55",
        9001L,
        12,
        LocalDateTime.of(2026, 9, 26, 14, 0),
        null,
        true);
  }
}
