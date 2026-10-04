package io.yak.ops.business.consumption.product.provider.source;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.yak.ops.business.asset.application.AssetSourceLookupService;
import io.yak.ops.business.consumption.product.identity.ProductKey;
import io.yak.ops.business.consumption.product.model.AvailabilityState;
import io.yak.ops.business.consumption.product.model.DatasetContractPayload;
import io.yak.ops.business.consumption.product.model.ProductType;
import io.yak.ops.business.consumption.product.provider.ProductLookupState;
import io.yak.ops.business.consumption.product.provider.ProductSearchCriteria;
import io.yak.ops.business.consumption.product.provider.ProductSearchState;
import io.yak.ops.business.dataset.Dataset;
import io.yak.ops.business.dataset.DatasetCatalogEntry;
import io.yak.ops.business.dataset.DatasetSourceType;
import io.yak.ops.business.dataset.DatasetStatus;
import io.yak.ops.business.dataset.DatasetVersion;
import io.yak.ops.business.dataset.definition.DatasetReader;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import io.yak.ops.core.security.ActionAuthorization;
import io.yak.ops.core.security.ActionAccessDeniedException;
import io.yak.ops.business.consumption.product.model.AccessDecision;
import io.yak.ops.business.consumption.product.model.ProviderEvidenceState;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.doThrow;

class DatasetDataProductProviderTest {

  @Test
  void standaloneSqlDatasetUsesOwningProvenanceForProducerBacklink() {
    var facade = mock(io.yak.ops.business.dataset.DevelopmentDatasetFacade.class);
    var version = new DatasetVersion(101L, 42L, 3, DatasetSourceType.SQL_QUERY,
        0L, 0L, 0, "2", "select order_id from orders", "{}", Instant.now());
    when(reader.catalog(List.of(42L), false)).thenReturn(List.of(
        new DatasetCatalogEntry(entry(DatasetStatus.ONLINE, 101L).dataset(), version, List.of())));
    when(assetLookup.lookup("DATASET", "42")).thenReturn(notIndexed("DATASET", "42"));
    when(facade.developmentSource(42L)).thenReturn(
        new io.yak.ops.business.dataset.DevelopmentDatasetFacade.DevelopmentSource(42L, 99L, 3, "FOUND"));
    var projected = new DatasetDataProductProvider(reader, assetLookup, null, null, facade)
        .get(new ProductKey(ProductType.DATASET, "42")).product();
    assertEquals("DATA_DEVELOPMENT_NODE", projected.producerRef().domain());
    assertEquals("99", projected.producerRef().identity());
    assertEquals("101", projected.activeVersion().identity());
  }

  private final DatasetReader reader = mock(DatasetReader.class);
  private final AssetSourceLookupService assetLookup = mock(AssetSourceLookupService.class);
  private final DatasetDataProductProvider provider = new DatasetDataProductProvider(reader, assetLookup);

  @Test
  void actionPermissionAloneCannotClaimPhysicalColumnConsumptionAllowed() {
    ActionAuthorization authorization = mock(ActionAuthorization.class);
    DatasetDataProductProvider secured = new DatasetDataProductProvider(reader, assetLookup, null, authorization);
    when(reader.catalog(List.of(42L), false)).thenReturn(List.of(entry(DatasetStatus.ONLINE, 101L)));
    when(assetLookup.lookup("DATASET", "42")).thenReturn(notIndexed("DATASET", "42"));

    var access = secured.get(new ProductKey(ProductType.DATASET, "42")).product().access();

    assertEquals(ProviderEvidenceState.UNAVAILABLE, access.providerState());
    assertNull(access.decision());
    assertEquals("QUERY", access.action());
    assertEquals("LOGGED_IN_DATASET_QUERY", access.plane());
  }

  @Test
  void forbiddenQueryActionKeepsProductVisibleAndIdentifiesCallPlane() {
    ActionAuthorization authorization = mock(ActionAuthorization.class);
    doThrow(new ActionAccessDeniedException("dataset:query")).when(authorization).requirePermission("dataset:query");
    DatasetDataProductProvider secured = new DatasetDataProductProvider(reader, assetLookup, null, authorization);
    when(reader.catalog(List.of(42L), false)).thenReturn(List.of(entry(DatasetStatus.ONLINE, 101L)));
    when(assetLookup.lookup("DATASET", "42")).thenReturn(notIndexed("DATASET", "42"));

    var result = secured.get(new ProductKey(ProductType.DATASET, "42"));

    assertEquals(ProductLookupState.FOUND, result.state());
    assertEquals(AccessDecision.FORBIDDEN, result.product().access().decision());
    assertEquals(ProviderEvidenceState.READY, result.product().access().providerState());
    assertEquals("QUERY", result.product().access().action());
  }

  @Test
  void projectsOnlyOnlineDatasetWithCurrentImmutableVersion() {
    DatasetCatalogEntry published = entry(DatasetStatus.ONLINE, 101L);
    when(reader.catalog(List.of(42L), false)).thenReturn(List.of(published));
    when(assetLookup.lookup("DATASET", "42")).thenReturn(notIndexed("DATASET", "42"));

    var result = provider.get(new ProductKey(ProductType.DATASET, "42"));

    assertEquals(ProductLookupState.FOUND, result.state());
    assertEquals("DATASET:42", result.product().productKey().value());
    assertEquals(AvailabilityState.UNKNOWN, result.product().availability());
    DatasetContractPayload payload =
        assertInstanceOf(DatasetContractPayload.class, result.product().contractPayload());
    assertEquals(101L, payload.versionId());
    assertEquals(3, payload.versionNo());
  }

  @Test
  void offlineDatasetWithPublishedVersionRemainsDiscoverableAsUnavailable() {
    when(reader.catalog(List.of(42L), false)).thenReturn(List.of(entry(DatasetStatus.OFFLINE, 101L)));
    var result = provider.get(new ProductKey(ProductType.DATASET, "42"));
    assertEquals(ProductLookupState.FOUND, result.state());
    assertEquals(AvailabilityState.UNAVAILABLE, result.product().availability());
  }

  @Test
  void ownerFilterIsUnavailableUntilOwningEvidenceExists() {
    var result = provider.search(new ProductSearchCriteria(
        ProductType.DATASET, null, "data-team", null, null, null, null));
    assertEquals(ProductSearchState.UNAVAILABLE, result.state());
  }

  @Test
  void confirmedEmptySearchStaysReady() {
    when(reader.catalog(any(), org.mockito.ArgumentMatchers.eq(false))).thenReturn(List.of());
    var result = provider.search(new ProductSearchCriteria(
        ProductType.DATASET, "missing", null, null, null, null, null));
    assertEquals(ProductSearchState.READY, result.state());
    assertEquals(0, result.total());
  }

  private DatasetCatalogEntry entry(DatasetStatus status, Long versionId) {
    Dataset dataset = new Dataset(
        42L, 7L, "Orders", "Published orders", status, versionId, Instant.now(), Instant.now());
    DatasetVersion version = versionId == null ? null : new DatasetVersion(
        versionId, 42L, 3, DatasetSourceType.QUERY_REVISION,
        501L, 601L, 9, "ds-1", "select 1", "{}", Instant.now());
    return new DatasetCatalogEntry(dataset, version, List.of());
  }

  private AssetSourceLookupService.SourceLookup notIndexed(String sourceType, String sourceId) {
    return new AssetSourceLookupService.SourceLookup(
        "NOT_INDEXED", sourceType, sourceId, null, null, null, null);
  }
}
