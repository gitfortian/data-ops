package io.yak.ops.business.quality.execution;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.yak.ops.business.quality.domain.QualityDomain.Execution;
import io.yak.ops.business.quality.domain.QualityDomain.RuleExecution;
import io.yak.ops.common.enums.quality.QualityEnums.CheckResult;
import io.yak.ops.core.security.ActionAccessDeniedException;
import io.yak.ops.core.security.ActionAuthorization;
import java.util.List;
import org.junit.jupiter.api.Test;

class QualityEvidenceQueryAdapterTest {
  private final QualityExecutionReader reader = mock(QualityExecutionReader.class);
  private final ActionAuthorization authorization = mock(ActionAuthorization.class);
  private final QualityEvidenceQueryAdapter adapter = new QualityEvidenceQueryAdapter(reader, authorization);

  @Test void failedAndSkippedRulesKeepTheirHistoricalSemanticsWithoutSqlOrRawErrors() throws Exception {
    Execution execution = mock(Execution.class);
    when(execution.executionNo()).thenReturn("exec-7");
    when(execution.checkResult()).thenReturn(CheckResult.ERROR);
    var error = new RuleExecution(1L, 2L, "异常规则", "COUNT", null, "region", CheckResult.ERROR,
        null, "0", "SELECT secret FROM private", "jdbc:credential", 3L, null);
    var skipped = new RuleExecution(2L, 3L, "跳过规则", "COUNT", null, "region", CheckResult.NOT_RUN,
        null, "0", null, null, 0L, null);
    when(execution.rules()).thenReturn(List.of(error, skipped));
    when(reader.require("exec-7")).thenReturn(execution);
    var evidence = adapter.require("exec-7");
    assertEquals("ERROR", evidence.rules().getFirst().result());
    assertEquals("NOT_RUN", evidence.rules().getLast().result());
    String json = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(evidence);
    assertFalse(json.contains("SELECT secret"));
    assertFalse(json.contains("jdbc:credential"));
    assertFalse(evidence.truncated());
  }

  @Test void permissionDenialPrecedesSourceRead() {
    doThrow(new ActionAccessDeniedException("quality:execution:read")).when(authorization).requirePermission("quality:execution:read");
    assertThrows(ActionAccessDeniedException.class, () -> adapter.require("exec-7"));
    verifyNoInteractions(reader);
  }
}
