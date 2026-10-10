package io.yak.ops.business.agent.conversation;

import io.yak.ops.business.agent.config.ConditionalOnAgentEnabled;
import io.yak.ops.business.agent.domain.AgentTurnRecord;
import io.yak.ops.core.security.UserExecutionScope;
import jakarta.annotation.PreDestroy;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * F-039 task follow-up, NOT a new turn executor. The original turn must reach its
 * durable COMPLETED state and finalize its assistant message before this is called.
 * Every next slice repeats LIVE user/project authorization, current DataSource read
 * access, Metadata coverage, PLAN.md SHA and CAS budget in the existing Facade.
 *
 * A crash between a completed turn and this wakeup leaves READY state recoverable
 * by taskId, never guesses or duplicates a model run. Normal success continues
 * server-side without a connected browser.
 */
@Slf4j
@Component
@ConditionalOnAgentEnabled
@ConditionalOnProperty(prefix="yak.agent.source-semantic", name="enabled", havingValue="true")
public class SourceSemanticAutoContinuation {
  private final UserExecutionScope users;
  private final SourceSemanticTaskFacade tasks;
  private final ThreadPoolExecutor workers;

  @Autowired
  public SourceSemanticAutoContinuation(UserExecutionScope users, SourceSemanticTaskFacade tasks) {
    this.users = Objects.requireNonNull(users);
    this.tasks = Objects.requireNonNull(tasks);
    this.workers = new ThreadPoolExecutor(2, 2, 0, TimeUnit.SECONDS,
        new ArrayBlockingQueue<>(64), job -> {
          Thread thread = new Thread(job, "yak-f039-next-chunk");
          thread.setDaemon(true);
          return thread;
        });
  }

  /**
   * Never call next in a response callback or in an HTTP/UI timer. Enqueue a bounded
   * authorization recovery only after the original executor commits its terminal row.
   */
  public void completed(AgentTurnRecord original, String taskId) {
    if (taskId == null || !taskId.matches("[a-f0-9-]{36}")) return;
    try {
      workers.execute(() -> {
        try {
          users.call(original.userId(), original.projectId(), () -> {
            var view = tasks.read(taskId); // verifies original COMPLETED + immutable receipt
            if ("READY".equals(view.status()) && view.nextChunkId() != null
                && view.completedTurnIds().containsValue(original.turnId())
                && original.sessionId().equals(view.sessionId())) {
              tasks.next(taskId); // all authorization + scope + budget checks repeated
            }
            return null;
          });
        } catch (RuntimeException blocked) {
          // Lost ACL, drift, quota, uncertain insert, or storage outage: NEVER
          // synthesize success/retry the original turn. Task remains inspectable.
          log.warn("F-039 automatic follow-up stopped, task={}, turn={}, cause={}",
              taskId, original.turnId(), blocked.getClass().getSimpleName());
        }
      });
    } catch (RejectedExecutionException overloaded) {
      log.warn("F-039 automatic follow-up queue full, task={} remains refreshable", taskId);
    }
  }

  @PreDestroy
  void shutdown() {
    workers.shutdownNow();
  }
}
