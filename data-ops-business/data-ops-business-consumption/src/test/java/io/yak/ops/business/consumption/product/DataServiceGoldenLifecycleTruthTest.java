package io.yak.ops.business.consumption.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.yak.ops.business.asset.application.AssetSourceLookupService;
import io.yak.ops.business.consumption.product.identity.ProductKey;
import io.yak.ops.business.consumption.product.model.AvailabilityState;
import io.yak.ops.business.consumption.product.model.DataServiceContractPayload;
import io.yak.ops.business.consumption.product.model.ProductType;
import io.yak.ops.business.consumption.product.model.SourceLifecycleState;
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
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Phase 4 #104: runtime availability must not fabricate a source lifecycle state. */
class DataServiceGoldenLifecycleTruthTest {

  @Test
  void disabledPublishedServiceRemainsPublishedButIsUnavailableForConsumption() {
    DataServiceReader reader = mock(DataServiceReader.class);
    DataServiceViewFactory viewFactory = mock(DataServiceViewFactory.class);
    AssetSourceLookupService assetLookup = mock(AssetSourceLookupService.class);

    LocalDateTime observedAt = LocalDateTime.of(2026, 9, 26, 18, 0);
    DataServiceDefinition definition = DataServiceDefinition.restore(
        88L,
        7L,
        4L,
        new DataServiceSettings(
            "Orders API", "/orders", 500, 30, false, "Temporarily disabled published service", true),
        new PublishedRuntimeSnapshot(3L, "select * from orders where tenant_id=:tenantId"),
        new SourceReference("DEVELOPMENT_TASK", "task-55", 9001L, 12),
        RuntimePolicy.defaults(true),
        AuthMode.API_KEY,
        observedAt,
        observedAt);
    DataServiceView view = new DataServiceView(
        88L,
        "Orders API",
        "/orders",
        "/api/v1/data-service/runtime/orders",
        3L,
        "select * from orders where tenant_id=:tenantId",
        List.of("tenantId"),
        500,
        30,
        false,
        "API_KEY",
        "Temporarily disabled published service",
        "DEVELOPMENT_TASK",
        "task-55",
        9001L,
        12,
        observedAt,
        observedAt,
        true);

    when(reader.require(88L)).thenReturn(definition);
    when(viewFactory.view(definition)).thenReturn(view);
    when(assetLookup.lookup("DATA_SERVICE", "88")).thenReturn(
        new AssetSourceLookupService.SourceLookup(
            "NOT_INDEXED", "DATA_SERVICE", "88", null, null, null, null));

    DataServiceDataProductProvider provider =
        new DataServiceDataProductProvider(reader, viewFactory, assetLookup);

    ProductLookupResult result =
        provider.get(new ProductKey(ProductType.DATA_SERVICE, "88"));

    assertEquals(ProductLookupState.FOUND, result.state());
    assertEquals("DATA_SERVICE:88", result.product().productKey().value());
    assertEquals(SourceLifecycleState.PUBLISHED, result.product().lifecycle());
    assertEquals(AvailabilityState.UNAVAILABLE, result.product().availability());

    DataServiceContractPayload contract = assertInstanceOf(
        DataServiceContractPayload.class, result.product().contractPayload());
    assertEquals(false, contract.enabled());
  }
}
