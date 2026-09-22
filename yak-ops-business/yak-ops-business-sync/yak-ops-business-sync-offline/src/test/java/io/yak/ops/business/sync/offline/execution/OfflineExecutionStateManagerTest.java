package io.yak.ops.business.sync.offline.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.sync.offline.domain.OfflineJobDefinition;
import io.yak.ops.business.sync.offline.domain.OfflineJobExecution;
import io.yak.ops.business.sync.offline.domain.core.BatchExecution;
import io.yak.ops.business.sync.offline.domain.core.BatchKey;
import io.yak.ops.business.sync.offline.domain.core.BatchScope;
import io.yak.ops.business.sync.offline.domain.core.BatchStatus;
import io.yak.ops.business.sync.offline.domain.core.BatchTrigger;
import io.yak.ops.business.sync.offline.domain.core.ExecutionSnapshot;
import io.yak.ops.business.sync.offline.domain.core.RetryPolicySnapshot;
import io.yak.ops.business.sync.offline.repository.OfflineBatchExecutionRepository;
import io.yak.ops.business.sync.offline.repository.OfflineExecutionEventRepository;
import io.yak.ops.business.sync.offline.repository.OfflineJobDefinitionRepository;
import io.yak.ops.business.sync.offline.repository.OfflineJobExecutionRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OfflineExecutionStateManagerTest {

  @Mock private OfflineJobDefinitionRepository definitionRepository;
  @Mock private OfflineJobExecutionRepository executionRepository;
  @Mock private OfflineBatchExecutionRepository batchRepository;
  @Mock private OfflineExecutionEventRepository eventRepository;
  @Mock private OfflineBatchRuntime batchRuntime;

  private OfflineExecutionStateManager stateManager;

  @BeforeEach
  void setUp() {
    stateManager = new OfflineExecutionStateManager(
        definitionRepository,
        executionRepository,
        batchRepository,
        eventRepository,
        batchRuntime,
        new ObjectMapper());
  }

  @Test
  void failedAttemptSchedulesRetryFromFrozenBatchPolicy() {
    OfflineJobExecution execution = execution(99L, "SUBMITTED");
    BatchExecution batch = frozenBatch(BatchStatus.FAILED);
    OfflineJobDefinition definition = new OfflineJobDefinition();
    definition.setId(10L);
    when(batchRepository.findById(77L)).thenReturn(Optional.of(batch));
    when(executionRepository.findByBatchId(77L)).thenReturn(List.of(execution));
    when(definitionRepository.findById(10L)).thenReturn(Optional.of(definition));

    stateManager.markFailed(execution, "engine down", true);

    assertThat(execution.getStatus()).isEqualTo("FAILED");
    assertThat(execution.getNextRetryTime()).isNotNull();
    verify(batchRuntime).persistAttempt(execution);
    verify(definitionRepository).update(definition);
    verify(eventRepository).append(any());
  }

  @Test
  void unknownClearsRetryWindowAndEndTime() {
    OfflineJobExecution execution = execution(99L, "RUNNING");
    execution.setStateVersion(3L);
    execution.setNextRetryTime(LocalDateTime.now().plusMinutes(1));
    execution.setEndTime(LocalDateTime.now());
    when(executionRepository.findByBatchId(77L)).thenReturn(List.of(execution));
    when(batchRepository.findById(77L)).thenReturn(Optional.of(frozenBatch(BatchStatus.UNKNOWN)));
    when(definitionRepository.findById(10L)).thenReturn(Optional.empty());

    stateManager.markUnknown(execution, "状态无法确认");

    assertThat(execution.getStatus()).isEqualTo("UNKNOWN");
    assertThat(execution.getNextRetryTime()).isNull();
    assertThat(execution.getEndTime()).isNull();
    verify(batchRuntime).persistAttempt(execution);
    verify(eventRepository).append(any());
  }

  @Test
  void oldAttemptLateEventDoesNotOverwriteTaskProjection() {
    OfflineJobExecution oldAttempt = execution(99L, "RUNNING");
    oldAttempt.setAttemptNo(1);
    OfflineJobExecution latestAttempt = execution(100L, "RUNNING");
    latestAttempt.setAttemptNo(2);
    when(executionRepository.findByBatchId(77L)).thenReturn(List.of(oldAttempt, latestAttempt));

    stateManager.markUnknown(oldAttempt, "旧 Attempt 晚到对账结果");

    verify(batchRuntime).persistAttempt(oldAttempt);
    verify(definitionRepository, never()).findById(10L);
    verify(definitionRepository, never()).update(any(OfflineJobDefinition.class));
  }

  @Test
  void cancellationIntentIsPersistedAndRecorded() {
    OfflineJobExecution execution = execution(99L, "RUNNING");

    stateManager.markCancellationRequested(execution);

    assertThat(execution.getCancellationRequested()).isTrue();
    verify(batchRuntime).persistAttempt(execution);
    verify(eventRepository).append(any());
  }

  @Test
  void batchlessLegacyExecutionCannotEnterUnknownReconcile() {
    OfflineJobExecution history = execution(99L, "RUNNING");
    history.setBatchId(null);

    assertThatThrownBy(() -> stateManager.markUnknown(history, "legacy timeout"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("未绑定 Batch")
        .hasMessageContaining("仅支持查询");

    verifyNoInteractions(batchRuntime, eventRepository, definitionRepository);
  }

  private OfflineJobExecution execution(long id, String status) {
    OfflineJobExecution execution = new OfflineJobExecution();
    execution.setId(id);
    execution.setJobDefinitionId(10L);
    execution.setBatchId(77L);
    execution.setStatus(status);
    execution.setStateVersion(1L);
    execution.setAttemptNo(1);
    return execution;
  }

  private BatchExecution frozenBatch(BatchStatus status) {
    return new BatchExecution(
        77L,
        10L,
        new BatchKey("manual:test"),
        BatchTrigger.MANUAL,
        BatchScope.fullSelection(),
        new ExecutionSnapshot(
            "{}",
            1,
            new RetryPolicySnapshot(3, 30),
            "digest",
            "logical"),
        status,
        List.of());
  }
}
