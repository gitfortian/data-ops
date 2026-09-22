package io.yak.ops.business.sync.offline.backfill;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.sync.offline.config.OfflineSyncProperties;
import io.yak.ops.business.sync.offline.cursor.OfflineCursorGateway;
import io.yak.ops.business.sync.offline.definition.OfflineJobDefinitionService;
import io.yak.ops.business.sync.offline.domain.OfflineJobDefinition;
import io.yak.ops.business.sync.offline.domain.core.BatchExecution;
import io.yak.ops.business.sync.offline.domain.core.BatchKey;
import io.yak.ops.business.sync.offline.domain.core.BatchScope;
import io.yak.ops.business.sync.offline.domain.core.BatchStatus;
import io.yak.ops.business.sync.offline.domain.core.BatchTrigger;
import io.yak.ops.business.sync.offline.domain.core.ExecutionSnapshot;
import io.yak.ops.business.sync.offline.domain.core.RetryPolicySnapshot;
import io.yak.ops.business.sync.offline.execution.OfflineExecutionScopeValidator;
import io.yak.ops.business.sync.offline.repository.OfflineBatchExecutionRepository;
import io.yak.ops.business.sync.offline.repository.OfflineScheduleRepository;
import io.yak.ops.common.bean.dto.sync.offline.OfflineBackfillRequestDTO;
import io.yak.ops.common.bean.dto.sync.offline.OfflineBackfillScopeDTO;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OfflineBackfillPlannerTest {

  @Mock private OfflineJobDefinitionService definitionService;
  @Mock private OfflineBatchExecutionRepository batchRepository;
  @Mock private OfflineScheduleRepository scheduleRepository;
  @Mock private OfflineCursorGateway cursorGateway;
  @Mock private OfflineExecutionScopeValidator scopeValidator;

  private OfflineBackfillPlanner planner;

  @BeforeEach
  void setUp() {
    planner = new OfflineBackfillPlanner(
        definitionService,
        batchRepository,
        scheduleRepository,
        cursorGateway,
        scopeValidator,
        new OfflineSyncProperties());
  }

  @Test
  void plansMultipleScopesWithOneFrozenSnapshot() {
    OfflineJobDefinition definition = definition("ONLINE");
    when(definitionService.resolveLogicalJobSpec(definition))
        .thenReturn("{\"kind\":\"BatchSyncJob\"}");
    when(batchRepository.findByTaskIdAndBatchKey(anyLong(), any()))
        .thenReturn(Optional.empty());
    OfflineBackfillRequestDTO request = new OfflineBackfillRequestDTO();
    request.setRequestId("backfill-august");
    request.setScopes(List.of(
        window(LocalDateTime.of(2026, 8, 1, 0, 0), LocalDateTime.of(2026, 8, 2, 0, 0)),
        window(LocalDateTime.of(2026, 8, 2, 0, 0), LocalDateTime.of(2026, 8, 3, 0, 0))));

    OfflineBackfillPlanner.PreparedRequest prepared = planner.prepare(request);
    OfflineBackfillPlanner.Plan plan = planner.plan(10L, definition, prepared);

    assertThat(plan.scopes()).hasSize(2);
    assertThat(plan.snapshot().logicalJobSpec())
        .isEqualTo("{\"kind\":\"BatchSyncJob\"}");
    verify(scopeValidator, org.mockito.Mockito.times(2)).validate(anyLong(), any(), any());
  }

  @Test
  void continuousCursorRangesInitializeOneRoute() {
    OfflineJobDefinition definition = definition("ONLINE");
    when(definitionService.resolveLogicalJobSpec(definition)).thenReturn("{}");
    when(batchRepository.findByTaskIdAndBatchKey(anyLong(), any()))
        .thenReturn(Optional.empty());
    when(cursorGateway.find(10L, "orders")).thenReturn(Optional.empty());
    OfflineBackfillRequestDTO request = new OfflineBackfillRequestDTO();
    request.setRequestId("cursor-bf");
    request.setScopes(List.of(
        cursor("orders", "updated_at", "100", "200"),
        cursor("orders", "updated_at", "200", "300")));

    OfflineBackfillPlanner.PreparedRequest prepared = planner.prepare(request);
    planner.plan(10L, definition, prepared);

    verify(cursorGateway).initializeIfAbsent(10L, "orders", "updated_at", "100");
  }

  @Test
  void cursorGapFailsBeforeExecutionScopeValidation() {
    OfflineJobDefinition definition = definition("ONLINE");
    when(definitionService.resolveLogicalJobSpec(definition)).thenReturn("{}");
    when(batchRepository.findByTaskIdAndBatchKey(anyLong(), any()))
        .thenReturn(Optional.empty());
    OfflineBackfillRequestDTO request = new OfflineBackfillRequestDTO();
    request.setRequestId("cursor-gap");
    request.setScopes(List.of(
        cursor("orders", "updated_at", "100", "200"),
        cursor("orders", "updated_at", "250", "300")));

    OfflineBackfillPlanner.PreparedRequest prepared = planner.prepare(request);

    assertThatThrownBy(() -> planner.plan(10L, definition, prepared))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("连续");
    verify(scopeValidator, never()).validate(anyLong(), any(), any());
  }

  @Test
  void invalidExecutionProjectionFailsBeforeMaterialization() {
    OfflineJobDefinition definition = definition("ONLINE");
    when(definitionService.resolveLogicalJobSpec(definition)).thenReturn("{}");
    when(batchRepository.findByTaskIdAndBatchKey(anyLong(), any()))
        .thenReturn(Optional.empty());
    doThrow(new IllegalStateException("Wave 5 scoped Batch V1 仅支持单表 source"))
        .when(scopeValidator)
        .validate(anyLong(), any(), any());
    OfflineBackfillRequestDTO request = new OfflineBackfillRequestDTO();
    request.setRequestId("invalid-scope");
    request.setScopes(List.of(
        window(LocalDateTime.of(2026, 8, 1, 0, 0), LocalDateTime.of(2026, 8, 2, 0, 0))));

    OfflineBackfillPlanner.PreparedRequest prepared = planner.prepare(request);

    assertThatThrownBy(() -> planner.plan(10L, definition, prepared))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("单表");
  }

  @Test
  void fullyExistingRequestReusesFrozenSnapshotEvenWhenTaskIsOffline() {
    OfflineJobDefinition definition = definition("OFFLINE");
    BatchScope scope = BatchScope.fullSelection();
    ExecutionSnapshot snapshot = new ExecutionSnapshot(
        "{\"definition\":\"frozen\"}",
        7,
        new RetryPolicySnapshot(2, 10),
        "digest-7",
        "{\"kind\":\"BatchSyncJob\"}");
    BatchExecution existing = new BatchExecution(
        77L,
        10L,
        BatchKey.backfill("existing", scope.fingerprint()),
        BatchTrigger.BACKFILL,
        scope,
        snapshot,
        BatchStatus.SUCCEEDED,
        List.of());
    when(batchRepository.findByTaskIdAndBatchKey(
            10L, BatchKey.backfill("existing", scope.fingerprint())))
        .thenReturn(Optional.of(existing));
    OfflineBackfillRequestDTO request = new OfflineBackfillRequestDTO();
    request.setRequestId("existing");
    OfflineBackfillScopeDTO full = new OfflineBackfillScopeDTO();
    full.setType("FULL_SELECTION");
    request.setScopes(List.of(full));

    OfflineBackfillPlanner.Plan plan = planner.plan(10L, definition, planner.prepare(request));

    assertThat(plan.snapshot()).isEqualTo(snapshot);
    assertThat(plan.existing(plan.scopes().get(0))).isSameAs(existing);
    verify(definitionService, never()).resolveLogicalJobSpec(definition);
  }

  private OfflineJobDefinition definition(String releaseState) {
    OfflineJobDefinition definition = new OfflineJobDefinition();
    definition.setId(10L);
    definition.setReleaseState(releaseState);
    definition.setDefinitionJson("{\"definition\":1}");
    definition.setVersion(7);
    definition.setConfigDigest("digest-7");
    return definition;
  }

  private OfflineBackfillScopeDTO window(LocalDateTime start, LocalDateTime end) {
    OfflineBackfillScopeDTO scope = new OfflineBackfillScopeDTO();
    scope.setType("DATA_WINDOW");
    scope.setStartInclusive(start);
    scope.setEndExclusive(end);
    return scope;
  }

  private OfflineBackfillScopeDTO cursor(
      String cursorId, String column, String after, String through) {
    OfflineBackfillScopeDTO scope = new OfflineBackfillScopeDTO();
    scope.setType("CURSOR_RANGE");
    scope.setCursorId(cursorId);
    scope.setCursorColumn(column);
    scope.setAfterExclusive(after);
    scope.setThroughInclusive(through);
    return scope;
  }
}
