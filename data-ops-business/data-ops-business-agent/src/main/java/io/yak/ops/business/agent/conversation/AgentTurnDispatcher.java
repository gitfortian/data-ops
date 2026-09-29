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

  /** 已投递给 worker、尚未完成认领的轮次：周期重叠时避免重复提交任务（认领本身仍由 CAS 兜底）。 */
  private final Set<String> dispatched = ConcurrentHashMap.newKeySet();

  private final ExecutorService workers;

  public AgentTurnDispatcher(
      AgentTurnRepository turnRepository, AgentTurnExecutor executor, AgentProperties properties) {
    this.turnRepository = turnRepository;
    this.executor = executor;
    this.properties = properties;
    this.workers =
        Executors.newFixedThreadPool(
            Math.max(1, properties.getTurn().getWorkerPoolSize()),
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
    workers.execute(this::sweep);
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
      workers.execute(
          () -> {
            try {
              executor.execute(record);
            } finally {
              dispatched.remove(record.turnId());
            }
          });
    }
  }

  @PreDestroy
  void shutdown() {
    workers.shutdownNow();
  }
}
