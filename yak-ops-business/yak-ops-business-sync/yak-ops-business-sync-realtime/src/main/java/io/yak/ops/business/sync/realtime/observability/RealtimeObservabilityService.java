package io.yak.ops.business.sync.realtime.observability;

import io.yak.ops.business.sync.realtime.domain.RealtimeJobEventView;
import io.yak.ops.business.sync.realtime.domain.RealtimeObservabilityView;
import io.yak.ops.business.sync.realtime.domain.RealtimeObservabilityView.RuntimeLog;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** Stable application entry for realtime observability and event read-side use-cases. */
@Service("realtimeObservabilityApplicationService")
public class RealtimeObservabilityService {

  private final RealtimeObservabilityReader reader;
  private final RealtimeEventQuery events;
  private final RealtimeEventStream eventStream;

  public RealtimeObservabilityService(
      RealtimeObservabilityReader reader,
      RealtimeEventQuery events,
      RealtimeEventStream eventStream) {
    this.reader = reader;
    this.events = events;
    this.eventStream = eventStream;
  }

  public SseEmitter subscribe() {
    return eventStream.subscribe();
  }

  public List<RealtimeJobEventView> events(long id) {
    return events.events(id);
  }

  public RealtimeObservabilityView snapshot(long id) {
    return reader.snapshot(id);
  }

  public String submissionLog(long id, int tailLines) {
    return reader.submissionLog(id, tailLines);
  }

  public RuntimeLog runtimeLog(long id, int maxExceptions) {
    return reader.runtimeLog(id, maxExceptions);
  }
}
