package io.yak.ops.business.agent.conversation;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.agent.config.AgentProperties;
import io.yak.ops.business.agent.domain.AgentTurnRecord;
import io.yak.ops.business.agent.repository.AgentTurnRepository;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class AgentTurnDispatcherTest {
  @Test
  void rejectedDeliveryRemainsEligibleForTheNextDurableQueueSweep() throws Exception {
    AgentTurnRepository repository = mock(AgentTurnRepository.class);
    AgentTurnExecutor executor = mock(AgentTurnExecutor.class);
    AgentProperties properties = new AgentProperties();
    properties.getTurn().setWorkerPoolSize(1);
    properties.getTurn().setQueueCapacity(1);
    AgentTurnRecord first = turn("first"), second = turn("second"), third = turn("third");
    CountDownLatch started = new CountDownLatch(1), release = new CountDownLatch(1);
    CountDownLatch queuedFinished = new CountDownLatch(1), retriedFinished = new CountDownLatch(1);
    doAnswer(call -> { started.countDown(); assertTrue(release.await(2, TimeUnit.SECONDS)); return null; }).when(executor).executeAndAwait(first);
    doAnswer(call -> { queuedFinished.countDown(); return null; }).when(executor).executeAndAwait(second);
    doAnswer(call -> { retriedFinished.countDown(); return null; }).when(executor).executeAndAwait(third);
    when(repository.listQueued(anyInt())).thenReturn(List.of(first), List.of(second, third), List.of(third));
    AgentTurnDispatcher dispatcher = new AgentTurnDispatcher(repository, executor, properties);
    try {
      dispatcher.sweep();
      assertTrue(started.await(2, TimeUnit.SECONDS));
      dispatcher.sweep();
      release.countDown();
      assertTrue(queuedFinished.await(2, TimeUnit.SECONDS));
      dispatcher.sweep();
      assertTrue(retriedFinished.await(2, TimeUnit.SECONDS));
      verify(executor, times(1)).executeAndAwait(third);
    } finally {
      release.countDown();
      dispatcher.shutdown();
    }
  }

  private static AgentTurnRecord turn(String id) {
    AgentTurnRecord record = mock(AgentTurnRecord.class);
    when(record.turnId()).thenReturn(id);
    return record;
  }
}
