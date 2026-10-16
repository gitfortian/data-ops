package io.yak.ops.business.sync.offline.execution;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.yak.ops.business.sync.offline.domain.OfflineJobExecution;
import io.yak.ops.business.sync.offline.domain.core.BatchExecution;
import io.yak.ops.business.sync.offline.domain.core.ExecutionSnapshot;
import io.yak.ops.business.sync.offline.repository.OfflineBatchExecutionRepository;
import io.yak.ops.business.sync.offline.repository.OfflineJobExecutionRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class OfflineOdsExecutionEvidenceQueryTest {
  private final OfflineJobExecutionRepository executions = mock(OfflineJobExecutionRepository.class);
  private final OfflineBatchExecutionRepository batches = mock(OfflineBatchExecutionRepository.class);
  private final OfflineOdsExecutionEvidenceQuery query =
      new OfflineOdsExecutionEvidenceQuery(executions, batches);

  @Test void exactFrozenBatchProvenanceReturnsRealAttemptMetrics() {
    OfflineJobExecution attempt = new OfflineJobExecution();
    attempt.setId(100L);
    attempt.setBatchId(80L);
    attempt.setJobDefinitionId(60L);
    attempt.setAttemptNo(2);
    attempt.setStatus("SUCCEEDED");
    attempt.setSinkCommittedRecordCount(17L);
    when(executions.findById(100L)).thenReturn(Optional.of(attempt));
    BatchExecution batch = mock(BatchExecution.class);
    ExecutionSnapshot snapshot = mock(ExecutionSnapshot.class);
    when(batch.taskId()).thenReturn(60L);
    when(batch.snapshot()).thenReturn(snapshot);
    when(snapshot.definitionSnapshot()).thenReturn(
        "{\"odsHandoff\":{\"modelId\":50,\"versionNo\":2,\"targetObservationId\":99}}");
    when(snapshot.definitionRevision()).thenReturn(3);
    when(batches.findById(80L)).thenReturn(Optional.of(batch));
    var evidence = query.require(100L, 50L, 2, 99L);
    assertEquals(17L, evidence.sinkCommittedRows());
    assertEquals(3, evidence.jobRevisionNo());
    assertEquals(2, evidence.attemptNo());
    assertEquals("SUCCEEDED", evidence.status());
    assertThrows(IllegalArgumentException.class, () -> query.require(100L, 50L, 3, 99L));
    assertThrows(IllegalArgumentException.class, () -> query.require(100L, 50L, 2, 98L));
  }

  @Test void missingOrForeignExecutionIsNotDisclosed() {
    assertThrows(IllegalArgumentException.class, () -> query.require(0, 50, 2, 99));
    assertThrows(IllegalArgumentException.class, () -> query.require(100, 50, 2, 99));
    verify(executions).findById(100L);
    verifyNoInteractions(batches);
  }

  @Test void legacyAttemptWithoutBatchCannotClaimOdsEvidence() {
    OfflineJobExecution attempt = new OfflineJobExecution();
    attempt.setId(100L);
    attempt.setJobDefinitionId(60L);
    when(executions.findById(100L)).thenReturn(Optional.of(attempt));
    assertThrows(IllegalArgumentException.class, () -> query.require(100, 50, 2, 99));
    verifyNoInteractions(batches);
  }
}
