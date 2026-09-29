package io.yak.ops.business.dataset.observability;

import io.yak.ops.business.dataset.DatasetQueryPerformance;
import java.util.concurrent.ConcurrentLinkedDeque;
import org.springframework.stereotype.Component;

/** Bounded local fallback used only when persistent diagnostics are unavailable. */
@Component
class DatasetQueryPerformanceBuffer {

  static final int MAX_TRACES = 500;

  private final ConcurrentLinkedDeque<BufferedTrace> traces = new ConcurrentLinkedDeque<>();

  void add(DatasetQueryPerformance trace) {
    add(null, trace);
  }

  void add(Long projectId, DatasetQueryPerformance trace) {
    if (trace == null) return;
    traces.addFirst(new BufferedTrace(projectId, trace));
    while (traces.size() > MAX_TRACES) {
      traces.pollLast();
    }
  }

  Iterable<BufferedTrace> traces() {
    return traces;
  }

  int size() {
    return traces.size();
  }

  record BufferedTrace(Long projectId, DatasetQueryPerformance trace) {}
}
