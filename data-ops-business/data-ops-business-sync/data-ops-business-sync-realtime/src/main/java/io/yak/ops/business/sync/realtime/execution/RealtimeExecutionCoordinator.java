package io.yak.ops.business.sync.realtime.execution;

import io.yak.ops.business.sync.realtime.domain.RealtimeJobView;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import org.springframework.stereotype.Component;

/** Serializes in-process execution commands and delegates to focused execution roles. */
@Component
public class RealtimeExecutionCoordinator {

  private final RealtimeExecutionStarter starter;
  private final RealtimeExecutionStateManager states;
  private final RealtimeExecutionReplacementManager replacements;
  private final ConcurrentHashMap<Long, ReentrantLock> lifecycleLocks = new ConcurrentHashMap<>();

  public RealtimeExecutionCoordinator(
      RealtimeExecutionStarter starter,
      RealtimeExecutionStateManager states,
      RealtimeExecutionReplacementManager replacements) {
    this.starter = starter;
    this.states = states;
    this.replacements = replacements;
  }

  public RealtimeJobView.Deployment start(long taskId, String idempotencyKey) {
    ReentrantLock lock = lifecycleLock(taskId);
    lock.lock();
    try {
      return starter.start(taskId, idempotencyKey);
    } finally {
      lock.unlock();
    }
  }

  public void stop(long taskId) {
    ReentrantLock lock = lifecycleLock(taskId);
    lock.lock();
    try {
      states.stop(taskId);
    } finally {
      lock.unlock();
    }
  }

  public RealtimeJobView.Deployment restartExecution(long taskId, String idempotencyKey) {
    ReentrantLock lock = lifecycleLock(taskId);
    lock.lock();
    try {
      return replacements.restartExecution(taskId, idempotencyKey);
    } finally {
      lock.unlock();
    }
  }

  public RealtimeJobView.Deployment applyPublishedVersion(
      long taskId, String idempotencyKey) {
    ReentrantLock lock = lifecycleLock(taskId);
    lock.lock();
    try {
      return replacements.applyPublishedVersion(taskId, idempotencyKey);
    } finally {
      lock.unlock();
    }
  }

  private ReentrantLock lifecycleLock(long taskId) {
    return lifecycleLocks.computeIfAbsent(taskId, ignored -> new ReentrantLock());
  }
}
