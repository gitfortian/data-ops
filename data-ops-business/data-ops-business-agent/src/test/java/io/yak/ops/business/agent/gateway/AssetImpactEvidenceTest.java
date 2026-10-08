package io.yak.ops.business.agent.gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyNoInteractions;

import io.yak.ops.business.agent.domain.GovernanceEvidenceLedger;
import io.yak.ops.business.asset.api.AssetGovernanceQueryApi;
import io.yak.ops.business.asset.api.AssetSectionResult;
import io.yak.ops.spi.section.SectionMapSummary;
import io.yak.ops.spi.section.SectionStatus;
import io.yak.ops.spi.section.SectionType;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.ObjectProvider;

class AssetImpactEvidenceTest {
  private final AssetGovernanceQueryApi api = mock(AssetGovernanceQueryApi.class);
  @SuppressWarnings("unchecked") private static <T> ObjectProvider<T> provider(T api) {
    ObjectProvider<T> provider = mock(ObjectProvider.class);
    when(provider.getIfAvailable()).thenReturn(api); return provider;
  }
  private GovernanceEvidenceGateway gateway() {
    return new GovernanceEvidenceGateway(provider(api), provider(null), provider(null));
  }
  private AssetSectionResult result(SectionStatus status, Map<String, Object> values) {
    return new AssetSectionResult(SectionType.USAGE, status, "FEDERATED", new SectionMapSummary(values),
        "jdbc:private reason", null, List.of(), List.of(), null, null);
  }
  private Map<String, Object> values(Object business) {
    return Map.of("pageActivity", Map.of("ownerDomain", "ASSET", "status", "OK", "windowDays", 30, "viewCount", 99,
            "views", List.of(Map.of("username", "private user")), "password", "private password"),
        "structuralUsage", Map.of("ownerDomain", "LINEAGE", "status", "OK", "direction", "DOWNSTREAM", "hop", 1, "downstreamReferenceCount", 4),
        "businessConsumption", business);
  }
  private Map<String, Object> business(String status) {
    return Map.of("ownerDomain", "CONSUMING_DOMAINS", "status", status, "scope", "可见 Dataset 消费", "successfulUsageCount", 2,
        "activeSubscriptionCount", 3, "coverageNote", "仅当前可见证据，非完整影响", "error", "private diagnostics");
  }

  @ParameterizedTest @EnumSource(SectionStatus.class)
  void childStatusRemainsIndependentAndOnlyReadableFactsCanBeVerified(SectionStatus status) {
    when(api.section(7, SectionType.USAGE)).thenReturn(result(SectionStatus.OK, values(business(status.name()))));
    var ledger = new GovernanceEvidenceLedger(); String output = gateway().impact(7, ledger);
    assertEquals(List.of("ASSET", "LINEAGE", "CONSUMING_DOMAINS"), ledger.entries().stream().map(e -> e.owner()).toList());
    assertEquals(status.name(), ledger.entries().get(2).status());
    assertEquals("99", ledger.verifyFact(ledger.entries().getFirst().id(), "viewCount").value());
    assertEquals("4", ledger.verifyFact(ledger.entries().get(1).id(), "downstreamReferenceCount").value());
    if (status == SectionStatus.OK) assertEquals("2", ledger.verifyFact(ledger.entries().get(2).id(), "successfulUsageCount").value());
    else assertThrows(IllegalArgumentException.class, () -> ledger.verifyFact(ledger.entries().get(2).id(), "successfulUsageCount"));
    assertTrue(ledger.entries().stream().allMatch(e -> e.sourceUpdatedAt().equals("unknown") && e.path().equals("/data-asset/detail/7")));
    assertFalse(output.contains("private")); assertFalse(output.contains("username")); assertFalse(output.contains("password"));
    verify(api).section(7, SectionType.USAGE); verifyNoMoreInteractions(api);
  }

  @ParameterizedTest @EnumSource(value = SectionStatus.class, names = {"UNAVAILABLE", "PERMISSION_DENIED", "NOT_APPLICABLE"})
  void unreadableTopLevelCannotLeakChildPayload(SectionStatus status) {
    when(api.section(7, SectionType.USAGE)).thenReturn(result(status, values(business("OK"))));
    var ledger = new GovernanceEvidenceLedger(); String output = gateway().impact(7, ledger);
    assertTrue(ledger.entries().stream().allMatch(e -> e.status().equals(status.name())));
    assertFalse(output.contains("\"viewCount\":99")); assertFalse(output.contains("successfulUsageCount"));
  }

  @Test void malformedOrOversizedSourceFailsIndependentlyWithoutTruncatingMeaning() {
    for (Object child : List.of(Map.of(), Map.of("ownerDomain", "UNKNOWN", "status", "OK"),
        Map.of("ownerDomain", "METRIC", "status", "OK", "scope", "x".repeat(513)),
        Map.of("ownerDomain", "METRIC", "status", "OK", "scope", "s", "totalCount", -1),
        Map.of("ownerDomain", "METRIC", "status", "OK", "scope", "s", "totalCount", "0"))) {
      when(api.section(7, SectionType.USAGE)).thenReturn(result(SectionStatus.OK, values(child)));
      var ledger = new GovernanceEvidenceLedger(); gateway().impact(7, ledger);
      assertEquals(List.of("OK", "OK", "UNAVAILABLE"), ledger.entries().stream().map(e -> e.status()).toList());
    }
  }

  @Test void oversizedHopCannotWrapAroundToOneHop() {
    var source = new java.util.HashMap<>(values(business("OK")));
    source.put("structuralUsage", Map.of("ownerDomain", "LINEAGE", "status", "OK", "direction", "DOWNSTREAM",
        "hop", 4294967297L, "downstreamReferenceCount", 4));
    when(api.section(7, SectionType.USAGE)).thenReturn(result(SectionStatus.OK, source));
    var ledger = new GovernanceEvidenceLedger(); gateway().impact(7, ledger);
    assertEquals("UNAVAILABLE", ledger.entries().get(1).status());
  }

  @Test void capacityCheckedBeforeAnySourceReadAndFailuresDoNotEraseExistingEvidence() {
    var ledger = new GovernanceEvidenceLedger();
    for (int i = 0; i < 38; i++) ledger.register("ASSET", "old", "OK", null, "/data-asset/detail/7");
    assertThrows(IllegalStateException.class, () -> gateway().impact(7, ledger)); verifyNoInteractions(api);
    var failedLedger = new GovernanceEvidenceLedger(); failedLedger.register("ASSET", "old", "OK", null, "/data-asset/detail/7");
    when(api.section(7, SectionType.USAGE)).thenThrow(new SecurityException("private"));
    assertFalse(gateway().impact(7, failedLedger).contains("private"));
    assertEquals(4, failedLedger.entries().size()); assertEquals("OK", failedLedger.entries().getFirst().status());
    assertEquals("PERMISSION_DENIED", failedLedger.entries().getLast().status());
  }
}
