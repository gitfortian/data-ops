package io.yak.ops.business.consumption.product.provider.source;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.yak.ops.business.asset.application.AssetSourceLookupService;
import io.yak.ops.business.consumption.product.identity.ProductKey;
import io.yak.ops.business.consumption.product.model.AvailabilityState;
import io.yak.ops.business.consumption.product.model.DataServiceContractPayload;
import io.yak.ops.business.consumption.product.model.ProductType;
import io.yak.ops.business.consumption.product.provider.ProductLookupState;
import io.yak.ops.business.consumption.product.provider.ProductSearchCriteria;
import io.yak.ops.business.consumption.product.provider.ProductSearchState;
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

class DataServiceDataProductProviderTest {

  private final DataServiceReader reader = mock(DataServiceReader.class);
  private final DataServiceViewFactory viewFactory = mock(DataServiceViewFactory.class);
  private final AssetSourceLookupService assetLookup = mock(AssetSourceLookupService.class);
  private final DataServiceDataProductProvider provider =
      new DataServiceDataProductProvider(reader, viewFactory, assetLookup);

  @Test
  void projectsPinnedPublishedSourceRevisionAndRuntimeInterface() {
    DataServiceDefinition definition = definition(true);
    when(reader.require(88L)).thenReturn(definition);
    when(viewFactory.view(definition)).thenReturn(view(definition));
    when(assetLookup.lookup("DATA_SERVICE", "88")).thenReturn(notIndexed());

    var result = provider.get(new ProductKey(ProductType.DATA_SERVICE, "88"));

    assertEquals(ProductLookupState.FOUND, result.state());
    assertEquals("9001", result.product().activeVersion().identity());
    assertEquals("v12", result.product().activeVersion().displayVersion());
    assertEquals(AvailabilityState.UNKNOWN, result.product().availability());
    DataServiceContractPayload payload =
        assertInstanceOf(DataServiceContractPayload.class, result.product().contractPayload());
    assertEquals("/api/v1/data-service/runtime/orders", payload.runtimePath());
    assertEquals(List.of("tenantId"), payload.parameterNames());
    assertEquals(9001L, payload.sourceRevisionId());
  }

  @Test
  void disabledPublishedServiceIsUnavailableWithoutChangingLifecycle() {
    DataServiceDefinition definition = definition(false);
    when(reader.require(88L)).thenReturn(definition);
    when(viewFactory.view(definition)).thenReturn(view(definition));
    when(assetLookup.lookup("DATA_SERVICE", "88")).thenReturn(notIndexed());

    var result = provider.get(new ProductKey(ProductType.DATA_SERVICE, "88"));
    assertEquals(AvailabilityState.UNAVAILABLE, result.product().availability());
  }

  @Test
  void ownerFilterDoesNotPretendToBeAnEmptySearch() {
    var result = provider.search(new ProductSearchCriteria(
        ProductType.DATA_SERVICE, null, "integration-team", null, null, null, null));
    assertEquals(ProductSearchState.UNAVAILABLE, result.state());
  }

  private DataServiceDefinition definition(boolean enabled) {
    LocalDateTime now = LocalDateTime.now();
    return DataServiceDefinition.restore(
        88L,
        7L,
        4L,
        new DataServiceSettings("Orders API", "/orders", 500, 30, enabled, "Orders service", true),
        new PublishedRuntimeSnapshot(3L, "select * from orders where tenant_id=:tenantId"),
        new SourceReference("DEVELOPMENT_TASK", "task-55", 9001L, 12),
        RuntimePolicy.defaults(true),
        AuthMode.API_KEY,
        now,
        now);
  }

  private DataServiceView view(DataServiceDefinition definition) {
    return new DataServiceView(
        88L, "Orders API", "/orders", "/api/v1/data-service/runtime/orders",
        3L, "select * from orders where tenant_id=:tenantId", List.of("tenantId"),
        500, 30, definition.settings().enabled(), "API_KEY", "Orders service",
        "DEVELOPMENT_TASK", "task-55", 9001L, 12,
        definition.createTime(), definition.updateTime(), true);
  }

  private AssetSourceLookupService.SourceLookup notIndexed() {
    return new AssetSourceLookupService.SourceLookup(
        "NOT_INDEXED", "DATA_SERVICE", "88", null, null, null, null);
  }
}
