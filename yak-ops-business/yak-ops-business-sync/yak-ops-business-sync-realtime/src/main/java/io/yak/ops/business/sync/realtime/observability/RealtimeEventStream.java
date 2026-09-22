package io.yak.ops.business.sync.realtime.observability;

import io.yak.ops.business.sync.realtime.domain.RealtimeJobChangeEvent;
import io.yak.ops.core.project.CurrentProject;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** Broadcasts committed realtime job changes only to subscribers of the same trusted Project. */
@Component
public class RealtimeEventStream {

  private static final Logger LOG = LoggerFactory.getLogger(RealtimeEventStream.class);
  private static final long STREAM_TIMEOUT_MILLIS = Duration.ofHours(1).toMillis();

  private final CurrentProject currentProject;
  private final CopyOnWriteArrayList<ProjectSubscriber> subscribers = new CopyOnWriteArrayList<>();
  private final ScheduledExecutorService heartbeat =
      Executors.newSingleThreadScheduledExecutor(
          runnable -> {
            Thread thread = new Thread(runnable, "yak-realtime-sse-heartbeat");
            thread.setDaemon(true);
            return thread;
          });

  public RealtimeEventStream(CurrentProject currentProject) {
    this.currentProject = currentProject;
    heartbeat.scheduleAtFixedRate(this::heartbeat, 15, 15, TimeUnit.SECONDS);
  }

  public SseEmitter subscribe() {
    long projectId = currentProject.requireProjectId();
    SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MILLIS);
    ProjectSubscriber subscriber = new ProjectSubscriber(projectId, emitter);
    subscribers.add(subscriber);
    Runnable cleanup = () -> subscribers.remove(subscriber);
    emitter.onCompletion(cleanup);
    emitter.onTimeout(cleanup);
    emitter.onError(ignored -> cleanup.run());
    send(subscriber, "connected", Map.of("timestamp", Instant.now().toString()));
    return emitter;
  }

  @TransactionalEventListener(
      phase = TransactionPhase.AFTER_COMMIT,
      fallbackExecution = true)
  public void publish(RealtimeJobChangeEvent event) {
    long projectId = currentProject.requireProjectId();
    for (ProjectSubscriber subscriber : subscribers) {
      if (subscriber.projectId() == projectId) {
        send(subscriber, "realtime", event);
      }
    }
  }

  private void heartbeat() {
    for (ProjectSubscriber subscriber : subscribers) {
      send(subscriber, "ping", Map.of("timestamp", Instant.now().toString()));
    }
  }

  private void send(ProjectSubscriber subscriber, String name, Object payload) {
    try {
      subscriber.emitter().send(
          SseEmitter.event()
              .name(name)
              .id(Long.toString(System.nanoTime()))
              .data(payload));
    } catch (IOException | IllegalStateException exception) {
      subscribers.remove(subscriber);
      LOG.debug("Realtime SSE client disconnected: {}", exception.getMessage());
    }
  }

  @PreDestroy
  void shutdown() {
    heartbeat.shutdownNow();
    subscribers.forEach(subscriber -> subscriber.emitter().complete());
    subscribers.clear();
  }

  private record ProjectSubscriber(long projectId, SseEmitter emitter) {}
}
