package io.yak.ops.business.consumption.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.yak.ops.business.consumption.product.identity.ProductKey;
import io.yak.ops.business.consumption.product.identity.SourceRef;
import io.yak.ops.business.consumption.product.model.AccessDecision;
import io.yak.ops.business.consumption.product.model.AccessProjection;
import io.yak.ops.business.consumption.product.model.AvailabilityState;
import io.yak.ops.business.consumption.product.model.DataProductView;
import io.yak.ops.business.consumption.product.model.ProductContractPayload;
import io.yak.ops.business.consumption.product.model.ProductType;
import io.yak.ops.business.consumption.product.model.ProviderEvidenceState;
import io.yak.ops.business.consumption.product.model.SourceLifecycleState;
import io.yak.ops.business.consumption.product.provider.ProductLookupResult;
import io.yak.ops.business.consumption.product.provider.ProductLookupState;
import io.yak.ops.business.consumption.product.provider.ProductSearchResult;
import io.yak.ops.business.consumption.product.provider.ProductSearchState;
import java.util.List;
import org.junit.jupiter.api.Test;

class ConsumptionContractFoundationTest {

  @Test
  void productKeyIsStableAndLimitedToFrozenProductTypes() {
    assertEquals("DATASET:42", new ProductKey(ProductType.DATASET, "42").value());
    assertEquals(new ProductKey(ProductType.DATA_SERVICE, "svc-1"), ProductKey.parse("DATA_SERVICE:svc-1"));
    assertThrows(IllegalArgumentException.class, () -> new ProductKey(ProductType.DATASET, " "));
    assertThrows(IllegalArgumentException.class, () -> ProductKey.parse("METRIC:gmv"));
  }

  @Test
  void accessDecisionAndProviderAvailabilityStayOrthogonal() {
    AccessProjection allowed = AccessProjection.ready(AccessDecision.ALLOWED);
    AccessProjection unavailable = AccessProjection.unavailable("security provider down");

    assertEquals(AccessDecision.ALLOWED, allowed.decision());
    assertEquals(ProviderEvidenceState.READY, allowed.providerState());
    assertEquals(null, unavailable.decision());
    assertEquals(ProviderEvidenceState.UNAVAILABLE, unavailable.providerState());
  }

  @Test
  void commonViewCannotDriftFromOwningSourceIdentityOrPayloadType() {
    ProductKey key = new ProductKey(ProductType.DATASET, "dataset-1");
    ProductContractPayload datasetPayload = () -> ProductType.DATASET;
    ProductContractPayload servicePayload = () -> ProductType.DATA_SERVICE;

    new DataProductView(
        key,
        new SourceRef(ProductType.DATASET, "dataset-1"),
        null,
        null,
        "Orders",
        null,
        "data-team",
        1L,
        "PROJECT",
        null,
        SourceLifecycleState.PUBLISHED,
        AvailabilityState.UNKNOWN,
        AccessProjection.unavailable("access provider not wired yet"),
        List.of(),
        datasetPayload);

    assertThrows(
        IllegalArgumentException.class,
        () -> new DataProductView(
            key,
            new SourceRef(ProductType.DATASET, "another-dataset"),
            null,
            null,
            "Orders",
            null,
            "data-team",
            1L,
            "PROJECT",
            null,
            SourceLifecycleState.PUBLISHED,
            AvailabilityState.UNKNOWN,
            AccessProjection.unavailable("access provider not wired yet"),
            List.of(),
            datasetPayload));

    assertThrows(
        IllegalArgumentException.class,
        () -> new DataProductView(
            key,
            new SourceRef(ProductType.DATASET, "dataset-1"),
            null,
            null,
            "Orders",
            null,
            "data-team",
            1L,
            "PROJECT",
            null,
            SourceLifecycleState.PUBLISHED,
            AvailabilityState.UNKNOWN,
            AccessProjection.unavailable("access provider not wired yet"),
            List.of(),
            servicePayload));
  }

  @Test
  void lookupNeverUsesNullToMeanDifferentFailureModes() {
    ProductLookupResult hidden = ProductLookupResult.notDiscoverable();
    ProductLookupResult unavailable = ProductLookupResult.unavailable("dataset provider down");

    assertEquals(ProductLookupState.NOT_DISCOVERABLE, hidden.state());
    assertEquals(ProductLookupState.UNAVAILABLE, unavailable.state());
  }

  @Test
  void confirmedEmptySearchIsDifferentFromProviderFailure() {
    ProductSearchResult empty = ProductSearchResult.ready(List.of(), 0);
    ProductSearchResult unavailable = ProductSearchResult.unavailable("provider down");

    assertEquals(ProductSearchState.READY, empty.state());
    assertEquals(List.of(), empty.products());
    assertEquals(ProductSearchState.UNAVAILABLE, unavailable.state());
  }
}
