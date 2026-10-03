package io.yak.ops.business.agent.conversation;

import io.yak.ops.business.agent.config.AgentProperties;
import io.yak.ops.business.agent.config.ConditionalOnAgentEnabled;
import io.yak.ops.business.agent.domain.AgentTurnRecord;
import io.yak.ops.business.agent.repository.AgentTurnRepository;
import jakarta.annotation.PreDestroy;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 轮次调度器：QUEUED 行即持久队列。启动时先把遗留 RUNNING 孤儿收敛为 INTERRUPTED
 * （诚实终态，不支持伪造断点续跑），再周期扫描入队执行；提交侧另有即时唤醒降低排队延迟。
 * 认领走条件 UPDATE CAS，天然幂等：同一轮被重复扫描只会被认领一次。
 */
@Slf4j
@ConditionalOnAgentEnabled
@Component
public class AgentTurnDispatcher {

  private final AgentTurnRepository turnRepository;
  private final AgentTurnExecutor executor;
  private final AgentProperties properties;

  /** 已投递、尚未完成或挂起的轮次；持久化认领仍由 CAS 兜底。 */
  private final Set<String> dispatched = ConcurrentHashMap.newKeySet();

  private final ExecutorService workers;
  private final ExecutorService wakeups = Executors.newSingleThreadExecutor(runnable -> {
    Thread thread = new Thread(runnable, "yak-agent-queue-wakeup");
    thread.setDaemon(true);
    return thread;
  });
  private final AtomicBoolean wakeupPending = new AtomicBoolean();

  public AgentTurnDispatcher(
      AgentTurnRepository turnRepository, AgentTurnExecutor executor, AgentProperties properties) {
    this.turnRepository = turnRepository;
    this.executor = executor;
    this.properties = properties;
    this.workers =
        new ThreadPoolExecutor(
            Math.max(1, properties.getTurn().getWorkerPoolSize()),
            Math.max(1, properties.getTurn().getWorkerPoolSize()),
            0L, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(Math.max(1, properties.getTurn().getQueueCapacity())),
            runnable -> {
              Thread thread = new Thread(runnable, "yak-agent-turn-worker");
              thread.setDaemon(true);
              return thread;
            });
  }

  @EventListener(ApplicationReadyEvent.class)
  public void bootstrap() {
    int orphans = turnRepository.interruptOrphanRunning();
    if (orphans > 0) {
      log.warn("interrupted {} orphan running turns from previous boot", orphans);
    }
    sweep();
  }

  /** 提交侧即时唤醒：把扫描挪到 worker 线程外执行，HTTP 线程不做任何推理准备。 */
  public void kick() {
    if (!wakeupPending.compareAndSet(false, true)) return;
    try {
      wakeups.execute(() -> {
        try { sweep(); }
        finally { wakeupPending.set(false); }
      });
    } catch (RejectedExecutionException rejected) {
      wakeupPending.set(false);
      // Durable queue and periodic scanner retain the work during shutdown or saturation.
      log.debug("Agent queue wakeup deferred");
    }
  }

  @Scheduled(
      fixedDelayString = "${yak.agent.turn.queue-poll-millis:2000}",
      initialDelayString = "${yak.agent.turn.queue-poll-millis:2000}")
  public void sweep() {
    int batch = Math.max(1, properties.getTurn().getWorkerPoolSize()) * 2;
    for (AgentTurnRecord record : turnRepository.listQueued(batch)) {
      if (!dispatched.add(record.turnId())) {
        continue;
      }
      try {
        workers.execute(
          () -> {
            try {
              executor.executeAndAwait(record);
            } finally {
              dispatched.remove(record.turnId());
            }
          });
      } catch (RejectedExecutionException rejected) {
        dispatched.remove(record.turnId());
        // No claim has occurred; leave QUEUED truth untouched for the next sweep.
        log.debug("Agent turn delivery deferred turn={}", record.turnId());
        break;
      }
    }
  }

  @PreDestroy
  void shutdown() {
    wakeups.shutdownNow();
    workers.shutdownNow();
    try {
      if (!workers.awaitTermination(5, TimeUnit.SECONDS)) {
        log.warn("Agent workers did not finish resource cleanup within the shutdown budget");
      }
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
    }
  }
}
