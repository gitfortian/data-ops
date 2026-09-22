package io.yak.ops.business.sync.realtime.domain;

import io.yak.ops.business.sync.realtime.domain.RealtimeJobState.DesiredState;
import io.yak.ops.business.sync.realtime.domain.RealtimeJobState.ObservedState;

/**
 * Lifecycle core of one realtime-sync execution.
 *
 * <p>One execution represents exactly one run. STOPPED and FAILED executions are historical facts
 * and are never resurrected. {@code definitionVersionId} may be absent only for legacy historical
 * records created before immutable DefinitionVersion evidence existed; every new execution must be
 * bound to one immutable DefinitionVersion before external submission.
 */
public record SyncExecution(
    long id,
    long taskId,
    Long definitionVersionId,
    DesiredState desiredState,
    ObservedState observedState,
    EngineExecutionRef engineExecutionRef,
    boolean resultUncertain,
    String errorMessage) {

  public SyncExecution {
    if (id <= 0) {
      throw new IllegalArgumentException("ExecutionId 必须大于 0");
    }
    if (taskId <= 0) {
      throw new IllegalArgumentException("TaskId 必须大于 0");
    }
    if (definitionVersionId != null && definitionVersionId <= 0) {
      throw new IllegalArgumentException("DefinitionVersionId 必须大于 0");
    }
    if (desiredState == null) {
      throw new IllegalArgumentException("Execution DesiredState 不能为空");
    }
    if (observedState == null) {
      throw new IllegalArgumentException("Execution ObservedState 不能为空");
    }
    if (engineExecutionRef == null) {
      throw new IllegalArgumentException("EngineExecutionRef 不能为空");
    }
  }

  public boolean terminal() {
    return observedState == ObservedState.STOPPED || observedState == ObservedState.FAILED;
  }

  public boolean activeOrUncertain() {
    return !terminal();
  }

  public boolean versioned() {
    return definitionVersionId != null;
  }

  /** Engine-neutral execution reference. externalExecutionId is absent before the engine binds it. */
  public record EngineExecutionRef(String engineType, String externalExecutionId) {
    public EngineExecutionRef {
      if (!hasText(engineType)) {
        throw new IllegalArgumentException("EngineType 不能为空");
      }
      engineType = engineType.trim();
      externalExecutionId = hasText(externalExecutionId) ? externalExecutionId.trim() : null;
    }

    public boolean bound() {
      return hasText(externalExecutionId);
    }

    private static boolean hasText(String value) {
      return value != null && !value.trim().isEmpty();
    }
  }
}
