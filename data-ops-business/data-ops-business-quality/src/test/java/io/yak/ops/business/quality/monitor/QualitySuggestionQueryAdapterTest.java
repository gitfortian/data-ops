package io.yak.ops.business.quality.monitor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.yak.ops.business.quality.api.QualitySuggestionQueryApi;
import io.yak.ops.business.quality.domain.QualityDomain;
import io.yak.ops.business.quality.domain.QualityDefinitionFingerprint;
import io.yak.ops.business.quality.gateway.datasource.QualityDataCatalogGateway;
import io.yak.ops.business.quality.repository.*;
import io.yak.ops.common.enums.quality.QualityEnums.*;
import io.yak.ops.core.security.ActionAuthorization;
import io.yak.ops.core.security.ActionAccessDeniedException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class QualitySuggestionQueryAdapterTest {
  private final QualityMonitorReader reader = mock(QualityMonitorReader.class);
  private final QualityTableAssetRepository tables = mock(QualityTableAssetRepository.class);
  private final QualityTemplateRepository templates = mock(QualityTemplateRepository.class);
  private final QualityDataCatalogGateway catalog = mock(QualityDataCatalogGateway.class);
  private final ActionAuthorization authorization = mock(ActionAuthorization.class);
  private final QualityRulePolicy rules = new QualityRulePolicy(templates);
  private final QualitySuggestionQueryAdapter adapter = new QualitySuggestionQueryAdapter(
      reader, tables, templates, catalog, rules, authorization);
  private static final String DEFINITION = "a".repeat(64);

  private static QualityDomain.Monitor monitor(String owner, CheckResult result) {
    return new QualityDomain.Monitor(7L, "orders", null, 3L, "db", "db", "public", "orders",
        null, owner, true, result, "run", LocalDateTime.now(), null, LocalDateTime.now(), 0, List.of());
  }

  private void available() {
    when(reader.editableSnapshot(7)).thenReturn(new QualityMonitorReader.EditableSnapshot(
        monitor("owner", CheckResult.PASSED), null, DEFINITION));
    when(tables.existsTableAssetTarget(3L, "db", "public", "orders")).thenReturn(true);
    when(catalog.listColumns(3L, "db", "public", "orders")).thenReturn(
        List.of(new QualityDataCatalogGateway.QualityColumn("id", "BIGINT", null)));
    var template = new QualityDomain.Template(1L, "not_null", "非空", null, RuleType.COLUMN_NOT_NULL,
        RuleScope.COLUMN, "COMPLETENESS", null, true, true, 0, 0);
    when(templates.listTemplates(any())).thenReturn(List.of(template));
    when(templates.findTemplate(1L)).thenReturn(Optional.of(template));
  }

  private static QualitySuggestionQueryApi.Candidate candidate(String field, String threshold) {
    return new QualitySuggestionQueryApi.Candidate(1L, "检查非空", field, "GTE",
        new BigDecimal(threshold), null, List.of());
  }

  @Test void permissionDenialPrecedesAllSourceReads() {
    doThrow(new ActionAccessDeniedException("data-quality:monitor:read"))
        .when(authorization).requirePermission(anyString());
    assertThrows(ActionAccessDeniedException.class, () -> adapter.require(7));
    verifyNoInteractions(reader, tables, templates, catalog);
  }

  @Test void candidateUsesRealPolicyAndDoesNotWriteDefinitions() {
    available();
    var valid = adapter.validate(7, DEFINITION, List.of(candidate("id", "99")));
    assertEquals("GTE", valid.getFirst().operator());
    assertEquals(new BigDecimal("99"), valid.getFirst().threshold());
    verify(templates).findTemplate(1L);
    verify(reader).editableSnapshot(7);
  }

  @Test void unavailableFieldTemplateInvalidPercentAndOldDefinitionAreRejected() {
    available();
    assertThrows(IllegalArgumentException.class, () -> adapter.validate(7, DEFINITION, List.of(candidate("gone", "99"))));
    assertThrows(IllegalArgumentException.class, () -> adapter.validate(7, DEFINITION, List.of(candidate("id", "101"))));
    assertThrows(IllegalArgumentException.class, () -> adapter.validate(7, "b".repeat(64), List.of(candidate("id", "99"))));
    assertThrows(IllegalArgumentException.class, () -> adapter.validate(7, DEFINITION, List.of(
        new QualitySuggestionQueryApi.Candidate(999, "SQL", "id", "EQ", BigDecimal.ONE, null, List.of()))));
  }

  @Test void adoptionRereadsPhysicalFieldsAndRejectsDdlDrift() {
    available();
    adapter.validate(7, DEFINITION, List.of(candidate("id", "99")));
    when(catalog.listColumns(3L, "db", "public", "orders")).thenReturn(List.of());
    assertThrows(IllegalArgumentException.class, () -> adapter.validate(7, DEFINITION, List.of(candidate("id", "99"))));
    var command = new QualityMonitorCommand.Save("orders", null, 3L, "db", "db", "public",
        "orders", null, "owner", true, null, List.of(new QualityMonitorCommand.Rule(
            1L, "检查非空", "id", "GTE", BigDecimal.valueOf(99), null, List.of(), null, false)));
    assertThrows(IllegalArgumentException.class, () -> adapter.validateEditedFields(command));
  }

  @Test void fingerprintIncludesOwnerButExcludesRuntimeResultsAndTimestamps() {
    String base = QualityDefinitionFingerprint.of(monitor("owner", CheckResult.PASSED), null);
    assertEquals(base, QualityDefinitionFingerprint.of(monitor("owner", CheckResult.NOT_PASSED), null));
    assertNotEquals(base, QualityDefinitionFingerprint.of(monitor("other", CheckResult.PASSED), null));
  }

  @Test void staleUpdateLocksAndReloadsBeforeRejectingAllWriteSideEffects() {
    var repository = mock(QualityMonitorRepository.class);
    var executions = mock(QualityExecutionRepository.class);
    var policy = mock(QualityMonitorPolicy.class);
    var rulePolicy = mock(QualityRulePolicy.class);
    var settingsPolicy = mock(QualityMonitorSettingsPolicy.class);
    var schedules = mock(io.yak.ops.business.quality.schedule.QualityScheduleLifecycle.class);
    var tasks = mock(io.yak.ops.business.quality.task.QualityTaskPublisher.class);
    var original = monitor("owner", CheckResult.PASSED);
    var manager = new QualityMonitorManager(repository, executions, policy, rulePolicy, settingsPolicy, schedules, tasks);
    when(repository.findMonitor(7)).thenReturn(Optional.of(monitor("new owner", CheckResult.PASSED)));
    assertThrows(IllegalStateException.class, () -> manager.update(7, null, QualityDefinitionFingerprint.of(original, null)));
    var order = inOrder(repository);
    order.verify(repository).lockMonitor(7);
    order.verify(repository).findMonitor(7);
    verifyNoInteractions(policy, rulePolicy, settingsPolicy, schedules, tasks, executions);
    verify(repository, never()).updateMonitor(anyLong(), any());
    verify(repository, never()).replaceRules(anyLong(), any());
  }
}
