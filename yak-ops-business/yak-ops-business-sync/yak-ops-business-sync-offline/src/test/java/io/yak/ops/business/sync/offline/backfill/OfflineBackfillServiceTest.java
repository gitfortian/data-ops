package io.yak.ops.business.sync.offline.backfill;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.sync.offline.definition.OfflineJobDefinitionService;
import io.yak.ops.business.sync.offline.domain.OfflineJobDefinition;
import io.yak.ops.business.sync.offline.domain.core.BatchExecution;
import io.yak.ops.business.sync.offline.domain.core.BatchKey;
import io.yak.ops.business.sync.offline.domain.core.BatchScope;
import io.yak.ops.business.sync.offline.domain.core.BatchStatus;
import io.yak.ops.business.sync.offline.domain.core.BatchTrigger;
import io.yak.ops.business.sync.offline.domain.core.ExecutionSnapshot;
import io.yak.ops.business.sync.offline.domain.core.RetryPolicySnapshot;
import io.yak.ops.business.sync.offline.repository.OfflineBatchExecutionRepository;
import io.yak.ops.business.sync.offline.repository.OfflineJobDefinitionRepository;
import io.yak.ops.common.bean.dto.sync.offline.OfflineBackfillRequestDTO;
import io.yak.ops.common.bean.vo.sync.offline.OfflineBackfillVO;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OfflineBackfillServiceTest {

  @Mock private OfflineJobDefinitionService definitionService;
  @Mock private OfflineJobDefinitionRepository definitionRepository;
  @Mock private OfflineBatchExecutionRepository batchRepository;
  @Mock private OfflineBackfillPlanner planner;

  private OfflineBackfillService service;

  @BeforeEach
  void setUp() {
    service = new OfflineBackfillService(
        definitionService,
        definitionRepository,
        batchRepository,
        planner);
  }

  @Test
  void materializesPlannerScopesWithSharedSnapshot() {
    OfflineBackfillRequestDTO request = new OfflineBackfillRequestDTO();
    OfflineBackfillPlanner.PreparedRequest prepared =
        new OfflineBackfillPlanner.PreparedRequest("backfill-august", List.of());
    OfflineJobDefinition definition = new OfflineJobDefinition();
    definition.setId(10L);
    OfflineBackfillPlanner.ScopePlan first = new OfflineBackfillPlanner.ScopePlan(
        BatchScope.dataWindow(
            LocalDateTime.of(2026, 8, 1, 0, 0),
            LocalDateTime.of(2026, 8, 2, 0, 0)),
        null);
    OfflineBackfillPlanner.ScopePlan second = new OfflineBackfillPlanner.ScopePlan(
        BatchScope.dataWindow(
            LocalDateTime.of(2026, 8, 2, 0, 0),
            LocalDateTime.of(2026, 8, 3, 0, 0)),
        null);
    ExecutionSnapshot snapshot = snapshot();
    OfflineBackfillPlanner.Plan plan = new OfflineBackfillPlanner.Plan(
        "backfill-august",
        List.of(first, second),
        Map.of(),
        snapshot);

    when(planner.prepare(request)).thenReturn(prepared);
    when(definitionService.require(10L)).thenReturn(definition);
    when(planner.plan(10L, definition, prepared)).thenReturn(plan);
    AtomicLong sequence = new AtomicLong(100L);
    when(batchRepository.insert(any(BatchExecution.class)))
        .thenAnswer(invocation -> persisted(invocation.getArgument(0), sequence.incrementAndGet()));

    OfflineBackfillVO result = service.submit(10L, request);

    assertThat(result.getCreatedCount()).isEqualTo(2);
    assertThat(result.getReusedCount()).isZero();
    assertThat(result.getBatchIds()).containsExactly(101L, 102L);
    ArgumentCaptor<BatchExecution> captor = ArgumentCaptor.forClass(BatchExecution.class);
    verify(batchRepository, org.mockito.Mockito.times(2)).insert(captor.capture());
    assertThat(captor.getAllValues()).allMatch(batch -> batch.trigger() == BatchTrigger.BACKFILL);
    assertThat(captor.getAllValues()).allMatch(batch -> batch.status() == BatchStatus.PENDING);
    assertThat(captor.getAllValues()).allMatch(batch -> batch.snapshot().equals(snapshot));
    verify(definitionRepository).lock(10L);
    verify(planner).plan(10L, definition, prepared);
  }

  @Test
  void reusesExistingPlannerBatchWithoutCreatingDuplicate() {
    OfflineBackfillRequestDTO request = new OfflineBackfillRequestDTO();
    OfflineBackfillPlanner.PreparedRequest prepared =
        new OfflineBackfillPlanner.PreparedRequest("backfill-existing", List.of());
    OfflineJobDefinition definition = new OfflineJobDefinition();
    definition.setId(10L);
    OfflineBackfillPlanner.ScopePlan scope =
        new OfflineBackfillPlanner.ScopePlan(BatchScope.fullSelection(), null);
    BatchExecution existing = new BatchExecution(
        77L,
        10L,
        BatchKey.backfill("backfill-existing", scope.scope().fingerprint()),
        BatchTrigger.BACKFILL,
        scope.scope(),
        snapshot(),
        BatchStatus.SUCCEEDED,
        List.of());
    OfflineBackfillPlanner.Plan plan = new OfflineBackfillPlanner.Plan(
        "backfill-existing",
        List.of(scope),
        Map.of(scope.scope().fingerprint(), existing),
        existing.snapshot());

    when(planner.prepare(request)).thenReturn(prepared);
    when(definitionService.require(10L)).thenReturn(definition);
    when(planner.plan(10L, definition, prepared)).thenReturn(plan);

    OfflineBackfillVO result = service.submit(10L, request);

    assertThat(result.getCreatedCount()).isZero();
    assertThat(result.getReusedCount()).isEqualTo(1);
    assertThat(result.getBatchIds()).containsExactly(77L);
    verify(batchRepository, never()).insert(any(BatchExecution.class));
  }

  @Test
  void requestValidationHappensBeforeTaskLock() {
    OfflineBackfillRequestDTO request = new OfflineBackfillRequestDTO();
    when(planner.prepare(request)).thenThrow(new IllegalArgumentException("scopes 不能为空"));

    assertThatThrownBy(() -> service.submit(10L, request))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("scopes");

    verify(definitionRepository, never()).lock(10L);
  }

  private ExecutionSnapshot snapshot() {
    return new ExecutionSnapshot(
        "{\"definition\":1}",
        7,
        new RetryPolicySnapshot(2, 10),
        "digest-7",
        "{\"kind\":\"BatchSyncJob\"}");
  }

  private BatchExecution persisted(BatchExecution batch, long id) {
    return new BatchExecution(
        id,
        batch.taskId(),
        batch.batchKey(),
        batch.trigger(),
        batch.batchScope(),
        batch.snapshot(),
        batch.status(),
        batch.attempts());
  }
}
