package io.yak.ops.business.sync.offline.execution;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.yak.ops.business.sync.offline.config.ConditionalOnOfflineSyncEnabled;
import io.yak.ops.business.sync.offline.definition.OfflineJobDefinitionService;
import io.yak.ops.business.sync.offline.domain.OfflineExecutionStatus;
import io.yak.ops.business.sync.offline.domain.OfflineJobExecution;
import io.yak.ops.business.sync.offline.domain.core.BatchExecution;
import io.yak.ops.business.sync.offline.engine.LinkUpClient;
import io.yak.ops.business.sync.offline.engine.LinkUpClient.LinkUpJobResponse;
import io.yak.ops.business.sync.offline.engine.LinkUpClient.LinkUpNodeResponse;
import io.yak.ops.business.sync.offline.engine.LinkUpClient.LinkUpRequestException;
import io.yak.ops.business.sync.offline.engine.LinkUpClient.LinkUpTransportException;
import io.yak.ops.business.sync.offline.engine.plan.OfflineExecutionPlan;
import io.yak.ops.business.sync.offline.engine.plan.OfflineExecutionPlanCodec;
import io.yak.ops.business.sync.offline.repository.OfflineBatchExecutionRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** Executes one logical FAN_OUT Attempt as deterministic child single-table Link-Up jobs. */
@ConditionalOnOfflineSyncEnabled
@Component
public class OfflineFanOutExecutionCoordinator {

  private static final String FAN_OUT = "FAN_OUT";

  private final OfflineBatchExecutionRepository batchRepository;
  private final OfflineJobDefinitionService definitionService;
  private final OfflineExecutionStateManager stateManager;
  private final LinkUpClient linkUpClient;
  private final OfflineExecutionPlanCodec planCodec;
  private final ObjectMapper objectMapper;
  private final OfflineFanOutSnapshotAggregator snapshotAggregator;

  public OfflineFanOutExecutionCoordinator(
      OfflineBatchExecutionRepository batchRepository,
      OfflineJobDefinitionService definitionService,
      OfflineExecutionStateManager stateManager,
      LinkUpClient linkUpClient,
      OfflineExecutionPlanCodec planCodec,
      @Qualifier("offlineSyncJsonMapper") ObjectMapper objectMapper) {
    this.batchRepository = batchRepository;
    this.definitionService = definitionService;
    this.stateManager = stateManager;
    this.linkUpClient = linkUpClient;
    this.planCodec = planCodec;
    this.objectMapper = objectMapper;
    this.snapshotAggregator = new OfflineFanOutSnapshotAggregator(objectMapper);
  }

  public boolean isFanOut(OfflineJobExecution execution) {
    return frozenPlan(execution).map(OfflineExecutionPlan::isFanOut).orElse(false);
  }

  public OfflineJobExecution submit(
      OfflineJobExecution execution,
      OfflineExecutionPlan plan) {
    requireFanOut(plan);

    List<PreparedUnit> prepared;
    try {
      // Resolve every child first so schema/target validation cannot create a half-submitted plan.
      prepared = prepare(plan);
    } catch (RuntimeException exception) {
      stateManager.markFailed(execution, safeMessage(exception), false);
      throw exception;
    }

    try {
      LinkUpNodeResponse node = linkUpClient.node();
      stateManager.bindWorker(execution, node == null ? null : node.getInstanceId());
      stateManager.markSubmitting(execution);
    } catch (RuntimeException exception) {
      stateManager.markFailed(execution, safeMessage(exception), false);
      throw exception;
    }

    List<OfflineFanOutUnitState> states = initialStates(execution, plan);
    // Persist the full child manifest before the first mutating request. From this point onward a
    // persisted CREATED child truly means that no dispatch intent has been recorded for that unit.
    applyAggregate(execution, states, "FAN_OUT_PLAN_READY");
    for (PreparedUnit unit : prepared) {
      OfflineFanOutUnitState state = requireState(states, unit.unit().unitKey());
      if (!OfflineExecutionStatus.isCreated(state.status())) {
        continue;
      }
      markDispatching(execution, states, state);
      submitUnit(execution, unit, state);
      applyAggregate(execution, states, "FAN_OUT_UNIT_SUBMITTED");
    }
    applyAggregate(execution, states, "FAN_OUT_SUBMITTED");
    return execution;
  }

  /**
   * Reconciles every active child. A CREATED child proven absent may be resumed from the frozen
   * plan. Any child whose previous submission may have reached the Worker is never blindly replayed.
   */
  public OfflineJobExecution reconcile(OfflineJobExecution execution) {
    OfflineExecutionPlan plan = requireFrozenPlan(execution);
    List<OfflineFanOutUnitState> states = currentStates(execution, plan);
    Map<String, PreparedUnit> prepared = new LinkedHashMap<>();
    boolean cancellationRequested = Boolean.TRUE.equals(execution.getCancellationRequested());

    for (OfflineExecutionPlan.Unit unit : plan.units()) {
      OfflineFanOutUnitState state = requireState(states, unit.unitKey());
      if (!OfflineExecutionStatus.isActive(state.status())) {
        continue;
      }

      // A persisted cancellation intent must never create a new remote write. CREATED is safe to
      // cancel locally because FAN_OUT records SUBMITTED before every mutating child request.
      if (cancellationRequested && OfflineExecutionStatus.isCreated(state.status())) {
        state.apply(state.localResponse(OfflineExecutionStatus.CANCELED, null, null));
        continue;
      }

      LinkUpJobResponse response;
      try {
        response = queryUnit(state);
      } catch (LinkUpRequestException exception) {
        if (exception.getStatusCode() == 404) {
          state.apply(
              state.localResponse(
                  OfflineExecutionStatus.UNKNOWN,
                  "FAN_OUT_CHILD_NOT_FOUND",
                  "已提交的 FAN_OUT 子任务暂时无法通过 externalExecutionId 确认"));
          continue;
        }
        throw exception;
      }

      if (response == null) {
        if (cancellationRequested) {
          state.apply(state.localResponse(OfflineExecutionStatus.CANCELED, null, null));
          continue;
        }
        PreparedUnit child =
            prepared.computeIfAbsent(unit.unitKey(), ignored -> prepare(unit));
        markDispatching(execution, states, state);
        submitUnit(execution, child, state);
      } else {
        state.apply(normalizeResponse(state, response));
      }

      if (cancellationRequested
          && OfflineExecutionStatus.isConfirmedActive(state.status())
          && StringUtils.hasText(state.engineJobId())) {
        state.apply(normalizeResponse(state, linkUpClient.cancel(state.engineJobId())));
      }
    }

    applyAggregate(execution, states, "FAN_OUT_RECONCILED");
    return execution;
  }

  public OfflineJobExecution cancel(OfflineJobExecution execution) {
    OfflineExecutionPlan plan = requireFrozenPlan(execution);
    List<OfflineFanOutUnitState> states = currentStates(execution, plan);

    for (OfflineFanOutUnitState state : states) {
      if (!OfflineExecutionStatus.isActive(state.status())) {
        continue;
      }

      if (OfflineExecutionStatus.isCreated(state.status())) {
        state.apply(state.localResponse(OfflineExecutionStatus.CANCELED, null, null));
        continue;
      }

      String jobId = state.engineJobId();
      if (!StringUtils.hasText(jobId)) {
        try {
          LinkUpJobResponse found =
              linkUpClient.findByExternalExecutionId(state.externalExecutionId());
          state.apply(normalizeResponse(state, found));
          jobId = state.engineJobId();
        } catch (LinkUpRequestException exception) {
          if (exception.getStatusCode() == 404) {
            // Non-CREATED means a mutating request may already have reached Link-Up. Preserve that
            // uncertainty instead of manufacturing a successful cancellation locally.
            state.apply(
                state.localResponse(
                    OfflineExecutionStatus.UNKNOWN,
                    "FAN_OUT_CANCEL_UNCONFIRMED",
                    "FAN_OUT 子任务提交状态不确定，取消结果无法确认"));
            continue;
          }
          throw exception;
        }
      }

      if (StringUtils.hasText(jobId)
          && OfflineExecutionStatus.isConfirmedActive(state.status())) {
        state.apply(normalizeResponse(state, linkUpClient.cancel(jobId)));
      }
    }

    applyAggregate(execution, states, "FAN_OUT_CANCELED");
    return execution;
  }

  private void markDispatching(
      OfflineJobExecution execution,
      List<OfflineFanOutUnitState> states,
      OfflineFanOutUnitState state) {
    // This write-ahead state is the crash-safety boundary. Once SUBMITTED is durable, recovery and
    // cancellation must reconcile by externalExecutionId instead of assuming the child is unsent.
    state.apply(state.localResponse(OfflineExecutionStatus.SUBMITTED, null, null));
    applyAggregate(execution, states, "FAN_OUT_UNIT_DISPATCHING");
  }

  private void submitUnit(
      OfflineJobExecution execution,
      PreparedUnit unit,
      OfflineFanOutUnitState state) {
    try {
      LinkUpJobResponse response =
          linkUpClient.submit(
              state.externalExecutionId(),
              state.idempotencyKey(),
              execution.getDefinitionVersion() == null ? 1 : execution.getDefinitionVersion(),
              unit.executionJobSpec());
      state.apply(normalizeResponse(state, response));
    } catch (LinkUpRequestException exception) {
      state.apply(
          state.localResponse(
              OfflineExecutionStatus.FAILED,
              exception.getCode(),
              safeMessage(exception)));
    } catch (LinkUpTransportException exception) {
      state.apply(
          state.localResponse(
              exception.isUncertain()
                  ? OfflineExecutionStatus.UNKNOWN
                  : OfflineExecutionStatus.FAILED,
              exception.isUncertain()
                  ? "FAN_OUT_SUBMIT_UNCERTAIN"
                  : "FAN_OUT_TRANSPORT_FAILED",
              safeMessage(exception)));
    } catch (RuntimeException exception) {
      state.apply(
          state.localResponse(
              OfflineExecutionStatus.FAILED,
              "FAN_OUT_SUBMIT_FAILED",
              safeMessage(exception)));
    }
  }

  /** Returns null only when a CREATED child is proven absent and may be submitted safely. */
  private LinkUpJobResponse queryUnit(OfflineFanOutUnitState state) {
    if (StringUtils.hasText(state.engineJobId())) {
      return linkUpClient.getJob(state.engineJobId());
    }

    try {
      return linkUpClient.findByExternalExecutionId(state.externalExecutionId());
    } catch (LinkUpRequestException exception) {
      if (exception.getStatusCode() == 404
          && OfflineExecutionStatus.isCreated(state.status())) {
        return null;
      }
      throw exception;
    }
  }

  private List<PreparedUnit> prepare(OfflineExecutionPlan plan) {
    List<PreparedUnit> prepared = new ArrayList<>();
    for (OfflineExecutionPlan.Unit unit : plan.units()) {
      prepared.add(prepare(unit));
    }
    return prepared;
  }

  private PreparedUnit prepare(OfflineExecutionPlan.Unit unit) {
    String resolved = definitionService.resolveExecutionJobSpec(write(unit.logicalJobSpec()));
    return new PreparedUnit(unit, parseObject(resolved));
  }

  private List<OfflineFanOutUnitState> initialStates(
      OfflineJobExecution execution,
      OfflineExecutionPlan plan) {
    List<OfflineFanOutUnitState> states = new ArrayList<>();
    for (OfflineExecutionPlan.Unit unit : plan.units()) {
      states.add(OfflineFanOutUnitState.initial(execution, unit, objectMapper));
    }
    return states;
  }

  private List<OfflineFanOutUnitState> currentStates(
      OfflineJobExecution execution,
      OfflineExecutionPlan plan) {
    JsonNode root = read(execution.getEngineSnapshotJson());
    JsonNode transitions = root == null ? null : root.path("transitions");
    JsonNode encoded = transitions == null ? null : transitions.path("units");
    if (transitions == null
        || !FAN_OUT.equals(transitions.path("strategy").asText())
        || encoded == null
        || !encoded.isArray()) {
      return initialStates(execution, plan);
    }

    Map<String, OfflineFanOutUnitState> persisted = new LinkedHashMap<>();
    for (JsonNode item : encoded) {
      OfflineFanOutUnitState state = OfflineFanOutUnitState.fromJson(item, objectMapper);
      persisted.put(state.unitKey(), state);
    }

    List<OfflineFanOutUnitState> states = new ArrayList<>();
    for (OfflineExecutionPlan.Unit unit : plan.units()) {
      OfflineFanOutUnitState state = persisted.get(unit.unitKey());
      if (state == null) {
        state = OfflineFanOutUnitState.initial(execution, unit, objectMapper);
      }
      if (!unit.sourceTable().equals(state.sourceTable())
          || !unit.sinkTable().equals(state.sinkTable())) {
        throw new IllegalStateException("FAN_OUT persisted unit no longer matches frozen plan");
      }
      states.add(state);
    }
    return states;
  }

  private void applyAggregate(
      OfflineJobExecution execution,
      List<OfflineFanOutUnitState> states,
      String eventType) {
    stateManager.applySnapshot(execution, snapshotAggregator.aggregate(execution, states), eventType);
  }

  private LinkUpJobResponse normalizeResponse(
      OfflineFanOutUnitState state,
      LinkUpJobResponse response) {
    LinkUpJobResponse value = response == null ? new LinkUpJobResponse() : response;
    if (!StringUtils.hasText(value.getExternalExecutionId())) {
      value.setExternalExecutionId(state.externalExecutionId());
    }
    if (!StringUtils.hasText(value.getIdempotencyKey())) {
      value.setIdempotencyKey(state.idempotencyKey());
    }
    if (!StringUtils.hasText(value.getStatus())) {
      value.setStatus(state.status());
    }
    return value;
  }

  private Optional<OfflineExecutionPlan> frozenPlan(OfflineJobExecution execution) {
    if (execution == null || execution.getBatchId() == null || execution.getBatchId() <= 0L) {
      return Optional.empty();
    }
    BatchExecution batch = batchRepository.findById(execution.getBatchId()).orElse(null);
    if (batch == null || !Objects.equals(batch.taskId(), execution.getJobDefinitionId())) {
      return Optional.empty();
    }
    return planCodec.decode(batch.snapshot().logicalJobSpec());
  }

  private OfflineExecutionPlan requireFrozenPlan(OfflineJobExecution execution) {
    OfflineExecutionPlan plan =
        frozenPlan(execution)
            .orElseThrow(
                () -> new IllegalStateException("当前 Attempt 不包含 FAN_OUT 冻结执行计划"));
    requireFanOut(plan);
    return plan;
  }

  private void requireFanOut(OfflineExecutionPlan plan) {
    if (plan == null || !plan.isFanOut()) {
      throw new IllegalArgumentException("OfflineFanOutExecutionCoordinator 仅接受 FAN_OUT 计划");
    }
  }

  private OfflineFanOutUnitState requireState(
      List<OfflineFanOutUnitState> states,
      String unitKey) {
    return states.stream()
        .filter(state -> state.unitKey().equals(unitKey))
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("FAN_OUT unit state missing: " + unitKey));
  }

  private JsonNode read(String value) {
    if (!StringUtils.hasText(value)) {
      return null;
    }
    try {
      return objectMapper.readTree(value);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("FAN_OUT execution snapshot JSON is corrupted", exception);
    }
  }

  private ObjectNode parseObject(String value) {
    try {
      JsonNode node = objectMapper.readTree(value);
      if (node == null || !node.isObject()) {
        throw new IllegalStateException("FAN_OUT child execution JobSpec must be a JSON object");
      }
      return (ObjectNode) node;
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("FAN_OUT child execution JobSpec JSON is corrupted", exception);
    }
  }

  private String write(JsonNode value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Failed to serialize FAN_OUT child JobSpec", exception);
    }
  }

  private String safeMessage(RuntimeException exception) {
    return exception.getMessage() == null || exception.getMessage().isBlank()
        ? exception.getClass().getSimpleName()
        : exception.getMessage();
  }

  private record PreparedUnit(
      OfflineExecutionPlan.Unit unit,
      ObjectNode executionJobSpec) {}
}
