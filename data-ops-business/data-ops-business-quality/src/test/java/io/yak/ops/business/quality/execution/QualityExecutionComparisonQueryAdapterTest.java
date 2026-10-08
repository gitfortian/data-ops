package io.yak.ops.business.quality.execution;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.yak.ops.business.quality.domain.QualityDomain.Execution;
import io.yak.ops.business.quality.domain.QualityDomain.RuleExecution;
import io.yak.ops.common.enums.quality.QualityEnums.CheckResult;
import io.yak.ops.common.enums.quality.QualityEnums.ExecutionStatus;
import io.yak.ops.common.enums.quality.QualityEnums.RuleType;
import io.yak.ops.core.security.ActionAuthorization;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class QualityExecutionComparisonQueryAdapterTest {
  private final QualityExecutionReader reader = mock(QualityExecutionReader.class);
  private final ActionAuthorization authorization = mock(ActionAuthorization.class);
  private final QualityExecutionComparisonQueryAdapter adapter = new QualityExecutionComparisonQueryAdapter(reader, authorization);

  private Execution execution(long id, String no) {
    var value = mock(Execution.class);
    when(value.id()).thenReturn(id); when(value.executionNo()).thenReturn(no);
    when(value.monitorId()).thenReturn(7L); when(value.dataSourceId()).thenReturn(9L);
    when(value.databaseName()).thenReturn("db"); when(value.tableName()).thenReturn("orders");
    when(value.executionStatus()).thenReturn(ExecutionStatus.SUCCESS);
    when(value.checkResult()).thenReturn(CheckResult.NOT_PASSED);
    when(value.finishedAt()).thenReturn(LocalDateTime.of(2026, 10, 8, 12, 0));
    when(reader.requireComparisonSummary(no)).thenReturn(value);
    when(reader.rulesForComparison(id)).thenReturn(List.of());
    return value;
  }

  private RuleExecution rule(long id, CheckResult result, String expected) {
    return new RuleExecution(id, id, "历史规则", "COUNT", RuleType.values()[0], "amount", result,
        "12", expected, "SELECT secret FROM private", "jdbc:password", 3L, null);
  }

  @Test void alignsStableIdsKeepsChangedThresholdAndExcludesSqlAndDiagnostics() throws Exception {
    execution(1, "before"); execution(2, "after");
    when(reader.rulesForComparison(1)).thenReturn(List.of(rule(7, CheckResult.NOT_PASSED, "0"), rule(3, CheckResult.ERROR, "1")));
    when(reader.rulesForComparison(2)).thenReturn(List.of(rule(3, CheckResult.NOT_RUN, "1"), rule(7, CheckResult.PASSED, "20")));
    var result = adapter.compare("before", "after");
    assertEquals(List.of(3L, 7L), result.alignment().stream().map(item -> item.ruleId()).toList());
    assertEquals(1, result.alignment().getFirst().baselineIndex());
    assertEquals(0, result.alignment().getFirst().currentIndex());
    assertTrue(result.alignment().getFirst().recordedDefinitionMatches());
    assertFalse(result.alignment().getLast().recordedDefinitionMatches());
    assertEquals("NOT_RUN", result.current().rules().getFirst().result());
    String json = new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules().writeValueAsString(result);
    assertFalse(json.contains("SELECT")); assertFalse(json.contains("password"));
    verify(reader, never()).require(anyString());
    var order = inOrder(authorization, reader);
    order.verify(authorization).requirePermission("quality:execution:read");
    order.verify(authorization).requirePermission("quality:monitor:read");
    order.verify(reader).requireComparisonSummary("before");
  }

  @ParameterizedTest @ValueSource(strings = {"quality:execution:read", "quality:monitor:read"})
  void authorizationPrecedesEverySourceRead(String permission) {
    doThrow(new SecurityException("private")).when(authorization).requirePermission(permission);
    assertThrows(SecurityException.class, () -> adapter.compare("before", "after"));
    verifyNoInteractions(reader);
  }

  @ParameterizedTest @ValueSource(strings = {"monitor", "datasource", "database", "schema", "table", "waiting", "running", "unfinished", "identity", "missing"})
  void rejectsDifferentHistoricalTargetsAndIncompleteExecutionsBeforeRuleReads(String mode) {
    execution(1, "before"); var after = execution(2, "after");
    switch (mode) {
      case "monitor" -> when(after.monitorId()).thenReturn(8L);
      case "datasource" -> when(after.dataSourceId()).thenReturn(10L);
      case "database" -> when(after.databaseName()).thenReturn("other");
      case "schema" -> when(after.schemaName()).thenReturn("other");
      case "table" -> when(after.tableName()).thenReturn("other");
      case "waiting" -> when(after.executionStatus()).thenReturn(ExecutionStatus.WAITING);
      case "running" -> when(after.checkResult()).thenReturn(CheckResult.RUNNING);
      case "unfinished" -> when(after.finishedAt()).thenReturn(null);
      case "identity" -> when(after.executionNo()).thenReturn("forged");
      case "missing" -> when(reader.requireComparisonSummary("after")).thenReturn(null);
      default -> fail();
    }
    assertThrows(IllegalArgumentException.class, () -> adapter.compare("before", "after"));
    verify(reader, never()).rulesForComparison(anyLong());
  }

  @Test void boundedRulesDeclareTruncationAndMissingVisibleSideWithoutInventingDeletion() {
    execution(1, "before"); execution(2, "after");
    when(reader.rulesForComparison(1)).thenReturn(LongStream.rangeClosed(1, 21).mapToObj(id -> rule(id, CheckResult.PASSED, "0")).toList());
    when(reader.rulesForComparison(2)).thenReturn(List.of(rule(21, CheckResult.ERROR, "0")));
    var result = adapter.compare("before", "after");
    assertEquals(20, result.baseline().rules().size()); assertTrue(result.baseline().truncated());
    assertNull(result.alignment().getLast().baselineIndex());
    assertEquals(0, result.alignment().getLast().currentIndex());
    assertFalse(result.alignment().getLast().recordedDefinitionMatches());
  }

  @Test void rejectsDuplicatesAndOversizedValuesRatherThanTruncatingHistory() {
    execution(1, "before"); execution(2, "after");
    when(reader.rulesForComparison(1)).thenReturn(List.of(rule(7, CheckResult.PASSED, "0"), rule(7, CheckResult.ERROR, "0")));
    assertThrows(IllegalArgumentException.class, () -> adapter.compare("before", "after"));
    when(reader.rulesForComparison(1)).thenReturn(List.of(rule(7, CheckResult.PASSED, "x".repeat(513))));
    assertThrows(IllegalArgumentException.class, () -> adapter.compare("before", "after"));
    when(reader.rulesForComparison(1)).thenReturn(LongStream.rangeClosed(1, 20).mapToObj(id -> rule(id, CheckResult.PASSED, "x".repeat(512))).toList());
    assertThrows(IllegalArgumentException.class, () -> adapter.compare("before", "after"));
  }

  @Test void acceptsCanceledAndFailedExecutionsAndTreatsUnrecordedDefinitionsAsUnknown() {
    var before = execution(1, "before"); var after = execution(2, "after");
    when(before.executionStatus()).thenReturn(ExecutionStatus.CANCELED);
    when(after.executionStatus()).thenReturn(ExecutionStatus.FAILED);
    when(before.schemaName()).thenReturn("");
    when(reader.rulesForComparison(1)).thenReturn(List.of(rule(7, CheckResult.ERROR, null)));
    when(reader.rulesForComparison(2)).thenReturn(List.of(rule(7, CheckResult.NOT_RUN, null)));
    assertFalse(adapter.compare("before", "after").alignment().getFirst().recordedDefinitionMatches());
  }
}
