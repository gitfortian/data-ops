package io.yak.ops.business.consumption.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.yak.ops.business.asset.application.AssetSourceLookupService;
import io.yak.ops.business.consumption.product.identity.ProductKey;
import io.yak.ops.business.consumption.product.model.ProductSectionState;
import io.yak.ops.business.consumption.product.model.ProductType;
import io.yak.ops.business.consumption.product.model.ProviderEvidenceState;
import io.yak.ops.business.consumption.product.provider.ProductLookupResult;
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
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Golden Path B contract for source-owned Data Service governance evidence. */
class DataServiceGoldenGovernanceEvidenceTest {

  @Test
  void canonicalProductCarriesTimedSourceGovernanceEvidenceFromOwningDefinition() {
    DataServiceReader reader = mock(DataServiceReader.class);
    DataServiceViewFactory viewFactory = mock(DataServiceViewFactory.class);
    AssetSourceLookupService assetLookup = mock(AssetSourceLookupService.class);

    LocalDateTime updatedAt = LocalDateTime.of(2026, 9, 26, 14, 30);
    DataServiceDefinition definition = definition(updatedAt);
    when(reader.require(88L)).thenReturn(definition);
    when(viewFactory.view(definition)).thenReturn(view(updatedAt));
    when(assetLookup.lookup("DATA_SERVICE", "88")).thenReturn(
        new AssetSourceLookupService.SourceLookup(
            "NOT_INDEXED", "DATA_SERVICE", "88", null, null, null, null));

    DataServiceDataProductProvider provider =
        new DataServiceDataProductProvider(reader, viewFactory, assetLookup);

    ProductLookupResult result = provider.get(new ProductKey(ProductType.DATA_SERVICE, "88"));

    assertEquals(ProductLookupState.FOUND, result.state());
    assertNotNull(result.product());
    ProductSectionState governance = result.product().sections().stream()
        .filter(section -> "source-governance".equals(section.sectionKey()))
        .findFirst()
        .orElseThrow();
    assertEquals(ProviderEvidenceState.READY, governance.state());
    assertEquals("DATA_SERVICE", governance.ownerDomain());
    assertEquals(Instant.parse("2026-09-26T14:30:00Z"), governance.observedAt());
    assertEquals(null, governance.reason());
  }

  @Test
  void missingOwningTimestampRemainsExplicitlyUnavailableInsteadOfFakeReady() {
    DataServiceReader reader = mock(DataServiceReader.class);
    DataServiceViewFactory viewFactory = mock(DataServiceViewFactory.class);
    AssetSourceLookupService assetLookup = mock(AssetSourceLookupService.class);

    DataServiceDefinition definition = definition(null);
    when(reader.require(88L)).thenReturn(definition);
    when(viewFactory.view(definition)).thenReturn(view(null));
    when(assetLookup.lookup("DATA_SERVICE", "88")).thenReturn(
        new AssetSourceLookupService.SourceLookup(
            "NOT_INDEXED", "DATA_SERVICE", "88", null, null, null, null));

    DataServiceDataProductProvider provider =
        new DataServiceDataProductProvider(reader, viewFactory, assetLookup);

    ProductLookupResult result = provider.get(new ProductKey(ProductType.DATA_SERVICE, "88"));

    assertEquals(ProductLookupState.FOUND, result.state());
    ProductSectionState governance = result.product().sections().stream()
        .filter(section -> "source-governance".equals(section.sectionKey()))
        .findFirst()
        .orElseThrow();
    assertEquals(ProviderEvidenceState.UNAVAILABLE, governance.state());
    assertEquals("DATA_SERVICE", governance.ownerDomain());
    assertEquals(null, governance.observedAt());
    assertNotNull(governance.reason());
  }

  private DataServiceDefinition definition(LocalDateTime updatedAt) {
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
        updatedAt);
  }

  private DataServiceView view(LocalDateTime updatedAt) {
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
        updatedAt,
        true);
  }
}
