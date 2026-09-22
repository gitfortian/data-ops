package io.yak.ops.business.sync.realtime.domain;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.yak.ops.business.sync.realtime.domain.RealtimeJobState.DesiredState;
import io.yak.ops.business.sync.realtime.domain.RealtimeJobState.ObservedState;
import io.yak.ops.business.sync.realtime.domain.SyncExecution.EngineExecutionRef;
import org.junit.jupiter.api.Test;

class SyncExecutionStateMachineTest {

  private final SyncExecutionStateMachine stateMachine = new SyncExecutionStateMachine();

  @Test
  void terminalExecutionCannotBeResurrected() {
    SyncExecution failed = execution(DesiredState.STOPPED, ObservedState.FAILED);
    SyncExecution stopped = execution(DesiredState.STOPPED, ObservedState.STOPPED);

    assertThatThrownBy(
            () -> stateMachine.requireTransition(failed, ObservedState.STARTING))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("FAILED -> STARTING");
    assertThatThrownBy(
            () -> stateMachine.requireTransition(stopped, ObservedState.STARTING))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("STOPPED -> STARTING");

    assertThatCode(() -> stateMachine.requireNewExecutionAllowed(failed)).doesNotThrowAnyException();
    assertThatCode(() -> stateMachine.requireNewExecutionAllowed(stopped)).doesNotThrowAnyException();
  }

  @Test
  void stringCompatibilityOverloadKeepsExistingTransitionContract() {
    SyncExecution starting = execution(DesiredState.RUNNING, ObservedState.STARTING);

    assertThatCode(() -> stateMachine.requireTransition(starting, "RUNNING"))
        .doesNotThrowAnyException();
  }

  @Test
  void activeOrUncertainExecutionBlocksAnotherExecution() {
    for (ObservedState state :
        new ObservedState[] {
          ObservedState.STARTING,
          ObservedState.RUNNING,
          ObservedState.STOPPING,
          ObservedState.UNKNOWN,
          ObservedState.CONFLICT
        }) {
      SyncExecution current =
          execution(
              state == ObservedState.STOPPING ? DesiredState.STOPPED : DesiredState.RUNNING,
              state);
      assertThatThrownBy(() -> stateMachine.requireNewExecutionAllowed(current))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("请勿重复启动");
    }
  }

  @Test
  void definitionMutationPolicyReadsExecutionLifecycle() {
    assertThatCode(
            () ->
                stateMachine.requireDefinitionMutable(
                    execution(DesiredState.STOPPED, ObservedState.FAILED)))
        .doesNotThrowAnyException();

    assertThatThrownBy(
            () ->
                stateMachine.requireDefinitionMutable(
                    execution(DesiredState.RUNNING, ObservedState.RUNNING)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("只有已停止或明确失败");
  }

  private SyncExecution execution(DesiredState desired, ObservedState observed) {
    return new SyncExecution(
        19L,
        7L,
        31L,
        desired,
        observed,
        new EngineExecutionRef("FLINK_CDC", null),
        observed == ObservedState.UNKNOWN,
        null);
  }
}
