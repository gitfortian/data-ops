package io.yak.ops.business.agent.gateway;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.yak.ops.business.agent.domain.GovernanceEvidenceLedger;
import io.yak.ops.business.quality.api.QualityExecutionComparisonQueryApi;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.ObjectProvider;

class QualityExecutionComparisonGatewayTest {
  @SuppressWarnings("unchecked") private static <T> ObjectProvider<T> provider(T value) {
    var result = mock(ObjectProvider.class); when(result.getIfAvailable()).thenReturn(value); return result;
  }
  private static QualityExecutionComparisonQueryApi.Side side(String no) {
    return new QualityExecutionComparisonQueryApi.Side(no, 7L, "monitor", "table", "SUCCESS", "PASSED",
        0, 0, 0, 0, null, null, List.of(), false);
  }
  @ParameterizedTest @ValueSource(strings = {"denied", "failed", "missing", "mismatch", "oversized"})
  void comparisonFailureCannotPublishPartialTrustedFactsOrRawDiagnostics(String mode) {
    var api = mock(QualityExecutionComparisonQueryApi.class);
    if (mode.equals("mismatch")) when(api.compare("before", "after")).thenReturn(
        new QualityExecutionComparisonQueryApi.Comparison(side("before"), side("forged"), List.of()));
    else if (mode.equals("oversized")) when(api.compare("before", "after")).thenReturn(
        new QualityExecutionComparisonQueryApi.Comparison(side("before"), side("after"),
            java.util.Collections.nCopies(41, new QualityExecutionComparisonQueryApi.Alignment(7L, 0, 0, true))));
    else when(api.compare("before", "after")).thenThrow(mode.equals("denied")
        ? new SecurityException("private") : new IllegalStateException("private"));
    var gateway = new GovernanceEvidenceGateway(provider(null), provider(null), provider(mode.equals("missing") ? null : api));
    var ledger = new GovernanceEvidenceLedger();
    String value = gateway.comparison("before", "after", ledger);
    assertFalse(value.contains("private")); assertFalse(value.contains("status=OK"));
    assertEquals(2, ledger.entries().size());
    assertEquals(List.of("/data-quality/execution/before", "/data-quality/execution/after"), ledger.entries().stream().map(e -> e.path()).toList());
    assertTrue(ledger.entries().stream().allMatch(e -> e.status().equals(mode.equals("denied") ? "PERMISSION_DENIED" : "UNAVAILABLE")));
    assertThrows(IllegalArgumentException.class, () -> ledger.verifyFact(ledger.entries().getFirst().id(), "executionNo"));
  }
  @Test void insufficientLedgerCapacityCannotRegisterHalfAPair() {
    var api = mock(QualityExecutionComparisonQueryApi.class);
    var gateway = new GovernanceEvidenceGateway(provider(null), provider(null), provider(api));
    var ledger = new GovernanceEvidenceLedger();
    for (int i = 0; i < 38; i++) ledger.register("QUALITY", "old", "UNAVAILABLE", null, "/old");
    assertThrows(IllegalStateException.class, () -> gateway.comparison("before", "after", ledger));
    assertEquals(38, ledger.entries().size()); verifyNoInteractions(api);
  }

  @Test void allBoundedSideAndAlignmentFieldsRemainVerifiableWithoutEnvelopeTruncation() {
    var api = mock(QualityExecutionComparisonQueryApi.class);
    var rules = java.util.stream.LongStream.rangeClosed(1, 20).mapToObj(id ->
        new QualityExecutionComparisonQueryApi.Rule(id, "rule", "COUNT", "RULE", "amount", "PASSED", "12", "20")).toList();
    var before = new QualityExecutionComparisonQueryApi.Side("before", 7L, "monitor", "table", "SUCCESS", "PASSED",
        20, 20, 0, 0, null, java.time.LocalDateTime.of(2026, 10, 8, 12, 0), rules, true);
    var currentRules = rules.stream().map(rule -> new QualityExecutionComparisonQueryApi.Rule(rule.ruleId() + 20,
        rule.name(), rule.templateCode(), rule.ruleType(), rule.columnName(), rule.result(), rule.metricValue(), rule.expectedValue())).toList();
    var after = new QualityExecutionComparisonQueryApi.Side("after", 7L, "monitor", "table", "FAILED", "ERROR",
        20, 0, 0, 20, null, before.finishedAt(), currentRules, false);
    var alignment = java.util.stream.LongStream.rangeClosed(1, 40).mapToObj(id ->
        new QualityExecutionComparisonQueryApi.Alignment(id, id <= 20 ? (int) id - 1 : null,
            id > 20 ? (int) id - 21 : null, false)).toList();
    when(api.compare("before", "after")).thenReturn(new QualityExecutionComparisonQueryApi.Comparison(before, after, alignment));
    var ledger = new GovernanceEvidenceLedger();
    var gateway = new GovernanceEvidenceGateway(provider(null), provider(null), provider(api));
    String value = gateway.comparison("before", "after", ledger);
    assertFalse(value.contains("内容已截断")); assertEquals(3, ledger.entries().size());
    for (int i = 0; i < 2; i++) {
      assertEquals("20", ledger.verifyFact(ledger.entries().get(i).id(), "rules[19].expectedValue").value());
      assertEquals("2026-10-08T12:00:00", ledger.verifyFact(ledger.entries().get(i).id(), "finishedAt").value());
    }
    assertEquals("40", ledger.verifyFact(ledger.entries().getLast().id(), "alignment[39].ruleId").value());
  }
}
