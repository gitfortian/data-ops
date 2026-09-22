package io.yak.ops.business.sync.offline.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.sync.offline.dao.OfflineExecutionEventDao;
import io.yak.ops.business.sync.offline.domain.OfflineExecutionEvent;
import io.yak.ops.business.sync.offline.domain.OfflineExecutionFinalFailureEvent;
import io.yak.ops.business.sync.offline.domain.OfflineJobExecution;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

class OfflineExecutionEventRepositoryAdapterNotificationTest {

  @Test
  void emitsFinalFailureOnlyWhenNoRetryIsScheduled() {
    OfflineExecutionEventDao dao = mock(OfflineExecutionEventDao.class);
    OfflineJobExecutionRepository executions = mock(OfflineJobExecutionRepository.class);
    ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
    when(dao.insert(any())).thenReturn(true);

    OfflineJobExecution execution = execution();
    when(executions.findById(99L)).thenReturn(Optional.of(execution));

    OfflineExecutionEventRepositoryAdapter repository =
        new OfflineExecutionEventRepositoryAdapter(dao, executions, publisher);
    repository.append(event("RUNNING", "FAILED"));

    ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
    verify(publisher).publishEvent(captor.capture());
    assertThat(captor.getValue()).isInstanceOf(OfflineExecutionFinalFailureEvent.class);
    OfflineExecutionFinalFailureEvent failure =
        (OfflineExecutionFinalFailureEvent) captor.getValue();
    assertThat(failure.executionId()).isEqualTo(99L);
    assertThat(failure.jobDefinitionId()).isEqualTo(10L);
    assertThat(failure.errorMessage()).isEqualTo("engine down");
  }

  @Test
  void retryableFailureDoesNotEmitUserNotificationSignal() {
    OfflineExecutionEventDao dao = mock(OfflineExecutionEventDao.class);
    OfflineJobExecutionRepository executions = mock(OfflineJobExecutionRepository.class);
    ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
    when(dao.insert(any())).thenReturn(true);

    OfflineJobExecution execution = execution();
    execution.setNextRetryTime(LocalDateTime.now().plusMinutes(1));
    when(executions.findById(99L)).thenReturn(Optional.of(execution));

    OfflineExecutionEventRepositoryAdapter repository =
        new OfflineExecutionEventRepositoryAdapter(dao, executions, publisher);
    repository.append(event("RUNNING", "FAILED"));

    verify(publisher, never()).publishEvent(any(Object.class));
  }

  @Test
  void repeatedFailedStateDoesNotEmitDuplicateSignal() {
    OfflineExecutionEventDao dao = mock(OfflineExecutionEventDao.class);
    OfflineJobExecutionRepository executions = mock(OfflineJobExecutionRepository.class);
    ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
    when(dao.insert(any())).thenReturn(true);
    when(executions.findById(99L)).thenReturn(Optional.of(execution()));

    OfflineExecutionEventRepositoryAdapter repository =
        new OfflineExecutionEventRepositoryAdapter(dao, executions, publisher);
    repository.append(event("FAILED", "FAILED"));

    verify(publisher, never()).publishEvent(any(Object.class));
  }

  private OfflineJobExecution execution() {
    OfflineJobExecution execution = new OfflineJobExecution();
    execution.setId(99L);
    execution.setJobDefinitionId(10L);
    execution.setStatus("FAILED");
    execution.setErrorMessage("engine down");
    return execution;
  }

  private OfflineExecutionEvent event(String from, String to) {
    return OfflineExecutionEvent.builder()
        .executionId(99L)
        .fromStatus(from)
        .toStatus(to)
        .eventType("FAILED")
        .createTime(LocalDateTime.now())
        .build();
  }
}
