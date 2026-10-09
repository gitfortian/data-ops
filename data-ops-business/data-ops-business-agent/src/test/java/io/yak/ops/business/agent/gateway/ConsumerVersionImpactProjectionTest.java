package io.yak.ops.business.agent.gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.agent.domain.ConsumerVersionImpactTarget;
import io.yak.ops.business.agent.domain.GovernanceEvidenceLedger;
import io.yak.ops.business.consumption.api.ConsumerVersionImpactQueryApi;
import io.yak.ops.business.consumption.api.ConsumerVersionImpactQueryApi.Consumer;
import io.yak.ops.business.consumption.api.ConsumerVersionImpactQueryApi.Result;
import io.yak.ops.business.consumption.api.ConsumerVersionImpactQueryApi.Window;
import io.yak.ops.core.security.ActionAccessDeniedException;
import io.yak.ops.spi.section.SectionStatus;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class ConsumerVersionImpactProjectionTest {
  private final ConsumerVersionImpactTarget target = new ConsumerVersionImpactTarget("DATASET", "9007199254740993", "999999999999999999999999999999");
  private Result result(Window subscriptions, Window usage) {
    return new Result(target.productType(), target.productIdentity(), target.sourceVersionIdentity(), SectionStatus.OK,
        "ACTIVE_SOURCE_REFERENCE", subscriptions, usage);
  }
  private Window empty() { return new Window(SectionStatus.EMPTY, 0, "WITHIN_LIMIT", List.of()); }
  private Window observed(String identity) { return new Window(SectionStatus.OK, 1, "WITHIN_LIMIT", List.of(
      new Consumer("DASHBOARD", "DASHBOARD", identity, 1, LocalDateTime.of(2026, 10, 9, 10, 0)))); }
  @Test void keepsIndependentEmptyAndUnavailableWindowsWithExactVersion() {
    var parts = ConsumerVersionImpactProjection.project(target,
        result(empty(), new Window(SectionStatus.UNAVAILABLE, 1, "private", observed("9").consumers())));
    assertEquals(List.of("OK", "EMPTY", "UNAVAILABLE"), parts.stream().map(ConsumerVersionImpactProjection.Part::status).toList());
    assertTrue(parts.getFirst().facts().contains(target.sourceVersionIdentity()));
    assertTrue(parts.get(1).facts().contains("NOT_PERFORMED"));
    assertEquals("{}", parts.get(2).facts());
  }
  @Test void rejectsWrongIdentityAndMissingMembershipWithoutLeakingRelations() {
    var invalid = new Result("DATASET", "9", target.sourceVersionIdentity(), SectionStatus.OK,
        "ACTIVE_SOURCE_REFERENCE", empty(), observed("private"));
    assertTrue(ConsumerVersionImpactProjection.project(target, invalid).stream().allMatch(part -> part.status().equals("UNAVAILABLE") && part.facts().equals("{}")));
    invalid = new Result(target.productType(), target.productIdentity(), target.sourceVersionIdentity(), SectionStatus.OK, null, empty(), observed("private"));
    assertTrue(ConsumerVersionImpactProjection.project(target, invalid).stream().allMatch(part -> part.facts().equals("{}")));
  }
  @Test void malformedOversizedAndDuplicateConsumerPayloadFailsOnlyItsSide() {
    for (Window invalid : List.of(observed("x".repeat(129)),
        new Window(SectionStatus.OK, 2, "WITHIN_LIMIT", List.of(observed("9").consumers().getFirst(), observed("9").consumers().getFirst())),
        new Window(SectionStatus.OK, 10, "WITHIN_LIMIT", observed("9").consumers()),
        new Window(SectionStatus.EMPTY, 1, "WITHIN_LIMIT", observed("9").consumers()))) {
      var parts = ConsumerVersionImpactProjection.project(target, result(empty(), invalid));
      assertEquals("EMPTY", parts.get(1).status()); assertEquals("UNAVAILABLE", parts.get(2).status());
    }
  }
  @Test void everyEncodedWindowStaysWithinBudgetAndMarksSaturation() {
    var consumers = java.util.stream.IntStream.range(0, 10).mapToObj(i -> new Consumer("JOB", "d".repeat(128),
        "x".repeat(127) + i, 1, LocalDateTime.of(2026, 10, 9, 10, 0))).toList();
    var parts = ConsumerVersionImpactProjection.project(target, result(empty(), new Window(SectionStatus.OK, 10, "LIMIT_REACHED", consumers)));
    assertEquals("OK", parts.get(2).status()); assertTrue(parts.get(2).facts().contains("LIMIT_REACHED"));
    assertTrue(parts.stream().allMatch(part -> part.facts().length() <= 6000));
  }
  @Test void jsonEscapeExpansionRejectsOversizedPartWithoutTruncatingIdentity() {
    var consumers = java.util.stream.IntStream.range(0, 10).mapToObj(i -> new Consumer("JOB", "d".repeat(128),
        "\u0001".repeat(127) + i, 1, LocalDateTime.of(2026, 10, 9, 10, 0))).toList();
    var parts = ConsumerVersionImpactProjection.project(target, result(empty(), new Window(SectionStatus.OK, 10, "LIMIT_REACHED", consumers)));
    assertEquals("EMPTY", parts.get(1).status());
    assertEquals("UNAVAILABLE", parts.get(2).status()); assertEquals("{}", parts.get(2).facts());
  }
  @SuppressWarnings("unchecked")
  private static <T> ObjectProvider<T> provider(T value) {
    ObjectProvider<T> provider = mock(ObjectProvider.class); when(provider.getIfAvailable()).thenReturn(value); return provider;
  }
  @Test void gatewayRechecksSourceAndRegistersThreeExactBacklinksWithoutRawErrors() {
    var api = mock(ConsumerVersionImpactQueryApi.class);
    when(api.read(target.productType(), target.productIdentity(), target.sourceVersionIdentity())).thenReturn(result(empty(), observed("9")));
    var gateway = new GovernanceEvidenceGateway(provider(null), provider(null), provider(null), provider(api));
    var ledger = new GovernanceEvidenceLedger();
    assertTrue(gateway.consumerVersionImpact(target, ledger).contains("NOT_PERFORMED"));
    assertEquals(3, ledger.entries().size());
    assertTrue(ledger.entries().stream().allMatch(ref -> ref.path().endsWith("?reviewVersion=" + target.sourceVersionIdentity())));
    when(api.read(target.productType(), target.productIdentity(), target.sourceVersionIdentity())).thenThrow(new ActionAccessDeniedException("private raw"));
    var fresh = new GovernanceEvidenceLedger();
    String denied = gateway.consumerVersionImpact(target, fresh);
    assertTrue(denied.contains("PERMISSION_DENIED")); assertFalse(denied.contains("private raw"));
    verify(api, times(2)).read(target.productType(), target.productIdentity(), target.sourceVersionIdentity());
  }
}
