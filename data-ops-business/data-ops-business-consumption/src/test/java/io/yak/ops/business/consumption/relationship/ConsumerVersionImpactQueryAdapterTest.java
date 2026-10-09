package io.yak.ops.business.consumption.relationship;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.yak.ops.business.consumption.product.discovery.ProductDiscoveryService;
import io.yak.ops.business.consumption.product.identity.ProductKey;
import io.yak.ops.business.consumption.product.identity.SourceVersionRef;
import io.yak.ops.business.consumption.product.model.DataProductView;
import io.yak.ops.business.consumption.product.provider.ProductLookupResult;
import io.yak.ops.core.project.CurrentProject;
import io.yak.ops.core.security.ActionAuthorization;
import io.yak.ops.core.security.ActionAccessDeniedException;
import io.yak.ops.spi.section.SectionStatus;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ConsumerVersionImpactQueryAdapterTest {
  private final ActionAuthorization authorization = mock(ActionAuthorization.class);
  private final CurrentProject project = mock(CurrentProject.class);
  private final ProductDiscoveryService discovery = mock(ProductDiscoveryService.class);
  private final SubscriptionRepository subscriptions = mock(SubscriptionRepository.class);
  private final UsageEvidenceRepository usage = mock(UsageEvidenceRepository.class);
  private final ConsumerVersionImpactQueryAdapter api = new ConsumerVersionImpactQueryAdapter(authorization, project, discovery, subscriptions, usage);
  private final ProductKey key = ProductKey.parse("DATASET:101");
  private final DataProductView product = mock(DataProductView.class);
  private final LocalDateTime at = LocalDateTime.of(2026, 10, 9, 10, 0);
  @BeforeEach void setup() {
    when(project.requireProjectId()).thenReturn(42L);
    when(product.projectId()).thenReturn(42L); when(product.productKey()).thenReturn(key);
    when(product.activeVersion()).thenReturn(new SourceVersionRef("9001", "v1"));
    when(discovery.get(key)).thenReturn(ProductLookupResult.found(product));
    when(usage.listByVersion(42L, key, "9001", 10)).thenReturn(List.of());
    when(subscriptions.listRecentActive(42L, key, 10)).thenReturn(List.of());
  }
  private UsageEvidence observed(long projectId, String version) {
    return new UsageEvidence(2L, projectId, key, new SourceVersionRef(version, "v1"),
        new ConsumerRef(ConsumerType.DASHBOARD, "DASHBOARD", "9", "private display"), at,
        ConsumptionMode.QUERY, UsageOutcome.SUCCESS, "DATASET_QUERY_PERFORMANCE", "private ref", "dedup", at);
  }
  @Test void activeVersionReadsOnlyBoundedPersistedWindowsWithoutRawPayload() {
    when(usage.listByVersion(42L, key, "9001", 10)).thenReturn(List.of(observed(42, "9001"), observed(42, "9001")));
    var result = api.read("DATASET", "101", "9001");
    assertEquals("ACTIVE_SOURCE_REFERENCE", result.membershipBasis());
    assertEquals(2, result.usage().recordCount()); assertEquals(1, result.usage().consumers().size());
    assertEquals(SectionStatus.EMPTY, result.subscriptions().status());
    assertFalse(result.toString().contains("private"));
    verify(authorization).requirePermission("data-asset:read");
    verify(usage).listByVersion(42L, key, "9001", 10);
    verify(subscriptions).listRecentActive(42L, key, 10);
    verifyNoMoreInteractions(usage, subscriptions);
  }
  @Test void unknownHistoricalVersionStopsBeforeSubscriptionsButSuccessProvesMembership() {
    var unknown = api.read("DATASET", "101", "8001");
    assertEquals(SectionStatus.NOT_APPLICABLE, unknown.status());
    verifyNoInteractions(subscriptions);
    when(usage.listByVersion(42L, key, "8001", 10)).thenReturn(List.of(observed(42, "8001")));
    assertEquals("NORMALIZED_SUCCESS_REFERENCE", api.read("DATASET", "101", "8001").membershipBasis());
    verify(subscriptions).listRecentActive(42L, key, 10);
  }
  @Test void partialFailureKeepsKnownMembershipAndOtherWindow() {
    when(usage.listByVersion(42L, key, "9001", 10)).thenThrow(new IllegalStateException("private jdbc"));
    var result = api.read("DATASET", "101", "9001");
    assertEquals(SectionStatus.OK, result.status());
    assertEquals(SectionStatus.UNAVAILABLE, result.usage().status());
    assertEquals(SectionStatus.EMPTY, result.subscriptions().status());
    assertFalse(result.toString().contains("private"));
  }
  @Test void permissionsAndProjectAreCheckedBeforeEvidence() {
    doThrow(new ActionAccessDeniedException("denied")).when(authorization).requirePermission("data-asset:read");
    assertThrows(ActionAccessDeniedException.class, () -> api.read("DATASET", "101", "9001"));
    verifyNoInteractions(discovery, usage, subscriptions);
    reset(authorization);
    when(product.projectId()).thenReturn(43L);
    assertEquals(SectionStatus.PERMISSION_DENIED, api.read("DATASET", "101", "9001").status());
    verifyNoInteractions(usage, subscriptions);
  }
  @Test void mismatchedRowsAreUnknownAndWindowSaturationIsExplicit() {
    when(usage.listByVersion(42L, key, "9001", 10)).thenReturn(List.of(observed(43, "9001")));
    assertEquals(SectionStatus.UNAVAILABLE, api.read("DATASET", "101", "9001").usage().status());
    when(usage.listByVersion(42L, key, "9001", 10)).thenReturn(java.util.Collections.nCopies(10, observed(42, "9001")));
    assertEquals("LIMIT_REACHED", api.read("DATASET", "101", "9001").usage().windowState());
    when(usage.listByVersion(42L, key, "9001", 10)).thenReturn(List.of(observed(42, "8001")));
    assertEquals(SectionStatus.UNAVAILABLE, api.read("DATASET", "101", "9001").usage().status());
  }
  @Test void rejectsMalformedTargetsBeforeAnySourceAccess() {
    assertThrows(IllegalArgumentException.class, () -> api.read(null, "101", "9001"));
    assertThrows(IllegalArgumentException.class, () -> api.read("DATASET", "01", "9001"));
    assertThrows(IllegalArgumentException.class, () -> api.read("DATASET", "9223372036854775808", "9001"));
    assertThrows(IllegalArgumentException.class, () -> api.read("DATASET", "101", "../1"));
    verifyNoInteractions(authorization, project, discovery, usage, subscriptions);
  }
  @Test void taggedIdentityGroupingDoesNotCollapseDelimiterCollisions() {
    var first = observed(42, "9001");
    var a = new UsageEvidence(first.id(), first.projectId(), first.productKey(), first.sourceVersion(),
        new ConsumerRef(ConsumerType.JOB, "A:B", "C", "private"), first.observedAt(), first.consumptionMode(),
        first.outcome(), first.provider(), first.providerEvidenceRef(), first.deduplicationId(), first.normalizedAt());
    var b = new UsageEvidence(first.id(), first.projectId(), first.productKey(), first.sourceVersion(),
        new ConsumerRef(ConsumerType.JOB, "A", "B:C", "private"), first.observedAt(), first.consumptionMode(),
        first.outcome(), first.provider(), first.providerEvidenceRef(), first.deduplicationId(), first.normalizedAt());
    when(usage.listByVersion(42L, key, "9001", 10)).thenReturn(List.of(a, b));
    assertEquals(2, api.read("DATASET", "101", "9001").usage().consumers().size());
  }
}
