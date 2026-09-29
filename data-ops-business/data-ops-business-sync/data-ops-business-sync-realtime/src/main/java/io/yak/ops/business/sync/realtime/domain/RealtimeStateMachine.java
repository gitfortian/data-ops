package io.yak.ops.business.sync.realtime.domain;

import io.yak.ops.business.sync.realtime.domain.RealtimeJobState.DesiredState;
import io.yak.ops.business.sync.realtime.domain.RealtimeJobState.ObservedState;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Explicit transition and mutation policy for realtime job state axes. */
@Component
public class RealtimeStateMachine {

  private static final EnumSet<ObservedState> DEFINITION_MUTABLE_STATES =
      EnumSet.of(ObservedState.STOPPED, ObservedState.FAILED);
  private static final EnumSet<ObservedState> STARTABLE_STATES =
      EnumSet.of(ObservedState.STOPPED, ObservedState.FAILED);

  private final Map<ObservedState, EnumSet<ObservedState>> transitions =
      new EnumMap<>(ObservedState.class);

  public RealtimeStateMachine() {
    allow(
        ObservedState.STOPPED,
        ObservedState.STARTING,
        ObservedState.STOPPING,
        ObservedState.FAILED,
        ObservedState.UNKNOWN);
    allow(
        ObservedState.STARTING,
        ObservedState.RUNNING,
        ObservedState.STOPPING,
        ObservedState.FAILED,
        ObservedState.UNKNOWN,
        ObservedState.CONFLICT);
    allow(
        ObservedState.RUNNING,
        ObservedState.STOPPING,
        ObservedState.FAILED,
        ObservedState.UNKNOWN,
        ObservedState.CONFLICT);
    allow(
        ObservedState.STOPPING, ObservedState.STOPPED, ObservedState.FAILED, ObservedState.UNKNOWN);
    allow(
        ObservedState.FAILED,
        ObservedState.STOPPED,
        ObservedState.STARTING,
        ObservedState.STOPPING,
        ObservedState.UNKNOWN,
        ObservedState.CONFLICT);
    allow(
        ObservedState.UNKNOWN,
        ObservedState.RUNNING,
        ObservedState.STOPPED,
        ObservedState.STOPPING,
        ObservedState.FAILED,
        ObservedState.CONFLICT);
    allow(
        ObservedState.CONFLICT,
        ObservedState.RUNNING,
        ObservedState.STOPPING,
        ObservedState.STOPPED,
        ObservedState.FAILED,
        ObservedState.UNKNOWN);
  }

  public void requireTransition(String fromValue, String toValue) {
    ObservedState from = ObservedState.valueOf(fromValue);
    ObservedState to = ObservedState.valueOf(toValue);
    if (from == to) {
      return;
    }
    if (!transitions.getOrDefault(from, EnumSet.noneOf(ObservedState.class)).contains(to)) {
      throw new IllegalStateException("非法实时任务状态迁移：" + from + " -> " + to);
    }
  }

  /**
   * Definition changes are release-axis operations and must not be allowed while runtime state is
   * active or uncertain. A definite FAILED runtime is safe to edit because desired state has
   * already returned to STOPPED and no active Flink job is expected.
   */
  public void requireDefinitionMutable(String desiredValue, String observedValue) {
    DesiredState desired = DesiredState.valueOf(desiredValue);
    ObservedState observed = ObservedState.valueOf(observedValue);
    if (desired != DesiredState.STOPPED || !DEFINITION_MUTABLE_STATES.contains(observed)) {
      throw new IllegalStateException(
          "任务运行态未稳定，只有已停止或明确失败的任务才能编辑、发布或删除");
    }
  }

  /** A new deployment may only reserve a task that is definitely not active. */
  public void requireStartable(String desiredValue, String observedValue) {
    DesiredState desired = DesiredState.valueOf(desiredValue);
    ObservedState observed = ObservedState.valueOf(observedValue);
    if (desired != DesiredState.STOPPED || !STARTABLE_STATES.contains(observed)) {
      throw new IllegalStateException("任务已在启动、运行或停止过程中，请勿重复启动");
    }
  }

  private void allow(ObservedState from, ObservedState... targets) {
    transitions.put(from, EnumSet.copyOf(Arrays.asList(targets)));
  }
}
