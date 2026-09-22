package io.yak.ops.business.sync.offline.execution;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.yak.ops.business.sync.offline.domain.OfflineExecutionStatus;
import io.yak.ops.business.sync.offline.domain.OfflineJobExecution;
import io.yak.ops.business.sync.offline.engine.LinkUpClient.LinkUpJobResponse;
import java.util.List;
import org.springframework.util.StringUtils;

/** Builds the one logical parent snapshot exposed by a FAN_OUT Attempt. */
final class OfflineFanOutSnapshotAggregator {

  private static final String FAN_OUT = "FAN_OUT";
  private static final String FAN_OUT_RETRY_UNSUPPORTED = "FAN_OUT_RETRY_UNSUPPORTED";

  private final ObjectMapper objectMapper;

  OfflineFanOutSnapshotAggregator(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  LinkUpJobResponse aggregate(
      OfflineJobExecution execution,
      List<OfflineFanOutUnitState> states) {
    LinkUpJobResponse response = new LinkUpJobResponse();
    response.setExternalExecutionId(execution.getExternalExecutionId());
    response.setIdempotencyKey(execution.getIdempotencyKey());
    response.setDefinitionVersion(execution.getDefinitionVersion());
    response.setWorkerInstanceId(firstWorker(states, execution.getWorkerInstanceId()));
    response.setStatus(aggregateStatus(states).name());
    response.setStateVersion(value(execution.getStateVersion(), 0L) + 1L);
    response.setCancellationRequested(Boolean.TRUE.equals(execution.getCancellationRequested()));
    response.setStartTimeMillis(minPositive(states, "startTimeMillis"));
    response.setEndTimeMillis(maxPositive(states, "endTimeMillis"));
    Long start = response.getStartTimeMillis();
    Long end = response.getEndTimeMillis();
    response.setDurationMillis(
        start != null && end != null && end >= start
            ? end - start
            : maxPositive(states, "durationMillis"));
    response.setMetrics(aggregateMetrics(states));
    response.setCommitSummary(aggregateCommitSummary(states));
    response.setPipelines(aggregatePipelines(states));
    response.setTransitions(unitManifest(states));

    long failed =
        states.stream()
            .filter(
                state ->
                    OfflineExecutionStatus.parse(state.status())
                        == OfflineExecutionStatus.FAILED)
            .count();
    long unknown =
        states.stream()
            .filter(
                state ->
                    OfflineExecutionStatus.parse(state.status())
                        == OfflineExecutionStatus.UNKNOWN)
            .count();
    if (failed > 0L) {
      response.setErrorCode(FAN_OUT_RETRY_UNSUPPORTED);
      response.setErrorMessage(
          "FAN_OUT 有 " + failed + " 个子任务失败；已成功写入的表不会被自动整组重放");
    } else if (unknown > 0L) {
      response.setErrorCode("FAN_OUT_UNIT_UNKNOWN");
      response.setErrorMessage("FAN_OUT 有 " + unknown + " 个子任务的远端提交状态暂时无法确认");
    }
    return response;
  }

  private OfflineExecutionStatus aggregateStatus(List<OfflineFanOutUnitState> states) {
    List<OfflineExecutionStatus> statuses =
        states.stream()
            .map(OfflineFanOutUnitState::status)
            .map(OfflineExecutionStatus::parse)
            .toList();

    if (statuses.contains(OfflineExecutionStatus.UNKNOWN)) {
      return OfflineExecutionStatus.UNKNOWN;
    }
    if (statuses.contains(OfflineExecutionStatus.RUNNING)) {
      return OfflineExecutionStatus.RUNNING;
    }
    if (statuses.contains(OfflineExecutionStatus.QUEUED)) {
      return OfflineExecutionStatus.QUEUED;
    }
    if (statuses.contains(OfflineExecutionStatus.SUBMITTED)
        || statuses.contains(OfflineExecutionStatus.CREATED)) {
      return OfflineExecutionStatus.SUBMITTED;
    }
    if (statuses.contains(OfflineExecutionStatus.FAILED)) {
      return OfflineExecutionStatus.FAILED;
    }
    if (statuses.contains(OfflineExecutionStatus.CANCELED)) {
      return OfflineExecutionStatus.CANCELED;
    }
    return OfflineExecutionStatus.SUCCEEDED;
  }

  private ObjectNode aggregateMetrics(List<OfflineFanOutUnitState> states) {
    ObjectNode metrics = objectMapper.createObjectNode();
    sum(metrics, states, "sourceRecordCount");
    sum(metrics, states, "sinkAttemptedRecordCount");
    sum(metrics, states, "sinkSuccessRecordCount");
    sum(metrics, states, "sourceReadBytes");
    sum(metrics, states, "sinkWrittenBytes");
    sum(metrics, states, "failedRecordCount");
    sum(metrics, states, "skippedRecordCount");
    sum(metrics, states, "databaseCommitMillis");
    sum(metrics, states, "sqlExecutionMillis");
    sumDecimal(metrics, states, "sourceAverageQps");
    sumDecimal(metrics, states, "sinkAverageQps");
    return metrics;
  }

  private ObjectNode aggregateCommitSummary(List<OfflineFanOutUnitState> states) {
    ObjectNode commit = objectMapper.createObjectNode();
    long total = 0L;
    for (OfflineFanOutUnitState state : states) {
      total +=
          state
              .response()
              .path("commitSummary")
              .path("successfullyCommittedRecordCount")
              .asLong(0L);
    }
    commit.put("successfullyCommittedRecordCount", total);
    return commit;
  }

  private ArrayNode aggregatePipelines(List<OfflineFanOutUnitState> states) {
    ArrayNode result = objectMapper.createArrayNode();
    for (OfflineFanOutUnitState state : states) {
      JsonNode pipelines = state.response().path("pipelines");
      if (pipelines.isArray()) {
        pipelines.forEach(item -> result.add(item.deepCopy()));
      }
    }
    return result;
  }

  private ObjectNode unitManifest(List<OfflineFanOutUnitState> states) {
    ObjectNode transitions = objectMapper.createObjectNode();
    transitions.put("strategy", FAN_OUT);
    ArrayNode units = transitions.putArray("units");
    states.forEach(state -> units.add(state.toJson()));
    return transitions;
  }

  private void sum(
      ObjectNode metrics,
      List<OfflineFanOutUnitState> states,
      String field) {
    long total = 0L;
    for (OfflineFanOutUnitState state : states) {
      total += state.response().path("metrics").path(field).asLong(0L);
    }
    metrics.put(field, total);
  }

  private void sumDecimal(
      ObjectNode metrics,
      List<OfflineFanOutUnitState> states,
      String field) {
    double total = 0D;
    for (OfflineFanOutUnitState state : states) {
      total += state.response().path("metrics").path(field).asDouble(0D);
    }
    metrics.put(field, total);
  }

  private Long minPositive(List<OfflineFanOutUnitState> states, String field) {
    Long result = null;
    for (OfflineFanOutUnitState state : states) {
      long current = state.response().path(field).asLong(0L);
      if (current > 0L && (result == null || current < result)) {
        result = current;
      }
    }
    return result;
  }

  private Long maxPositive(List<OfflineFanOutUnitState> states, String field) {
    Long result = null;
    for (OfflineFanOutUnitState state : states) {
      long current = state.response().path(field).asLong(0L);
      if (current > 0L && (result == null || current > result)) {
        result = current;
      }
    }
    return result;
  }

  private String firstWorker(
      List<OfflineFanOutUnitState> states,
      String fallback) {
    for (OfflineFanOutUnitState state : states) {
      if (StringUtils.hasText(state.workerInstanceId())) {
        return state.workerInstanceId();
      }
    }
    return fallback;
  }

  private long value(Long value, long fallback) {
    return value == null ? fallback : value;
  }
}
