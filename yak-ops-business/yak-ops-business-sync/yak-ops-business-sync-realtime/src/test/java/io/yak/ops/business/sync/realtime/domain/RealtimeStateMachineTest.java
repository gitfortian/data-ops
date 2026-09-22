package io.yak.ops.business.sync.realtime.domain;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class RealtimeStateMachineTest {

  private final RealtimeStateMachine stateMachine = new RealtimeStateMachine();

  @Test
  void acceptsLifecycleAndRecoveryTransitions() {
    assertThatCode(() -> stateMachine.requireTransition("STOPPED", "STARTING"))
        .doesNotThrowAnyException();
    assertThatCode(() -> stateMachine.requireTransition("UNKNOWN", "RUNNING"))
        .doesNotThrowAnyException();
    assertThatCode(() -> stateMachine.requireTransition("STOPPING", "STOPPED"))
        .doesNotThrowAnyException();
    assertThatCode(() -> stateMachine.requireTransition("CONFLICT", "RUNNING"))
        .doesNotThrowAnyException();
    assertThatCode(() -> stateMachine.requireTransition("FAILED", "STOPPING"))
        .doesNotThrowAnyException();
  }

  @Test
  void acceptsRecoveryEvidenceSettlingFailureAndConflict() {
    assertThatCode(() -> stateMachine.requireTransition("FAILED", "UNKNOWN"))
        .doesNotThrowAnyException();
    assertThatCode(() -> stateMachine.requireTransition("FAILED", "CONFLICT"))
        .doesNotThrowAnyException();
    assertThatCode(() -> stateMachine.requireTransition("CONFLICT", "FAILED"))
        .doesNotThrowAnyException();
  }

  @Test
  void rejectsImpossibleDirectTransition() {
    assertThatThrownBy(() -> stateMachine.requireTransition("STOPPED", "RUNNING"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("非法实时任务状态迁移");
  }

  @Test
  void allowsDefinitionMutationOnlyWhenRuntimeIsStable() {
    assertThatCode(() -> stateMachine.requireDefinitionMutable("STOPPED", "STOPPED"))
        .doesNotThrowAnyException();
    assertThatCode(() -> stateMachine.requireDefinitionMutable("STOPPED", "FAILED"))
        .doesNotThrowAnyException();
  }

  @Test
  void rejectsDefinitionMutationForActiveOrUncertainRuntime() {
    assertThatThrownBy(() -> stateMachine.requireDefinitionMutable("RUNNING", "RUNNING"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("运行态未稳定");
    assertThatThrownBy(() -> stateMachine.requireDefinitionMutable("STOPPED", "UNKNOWN"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("运行态未稳定");
    assertThatThrownBy(() -> stateMachine.requireDefinitionMutable("STOPPED", "CONFLICT"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("运行态未稳定");
  }

  @Test
  void allowsStartOnlyFromDefinitelyInactiveStates() {
    assertThatCode(() -> stateMachine.requireStartable("STOPPED", "STOPPED"))
        .doesNotThrowAnyException();
    assertThatCode(() -> stateMachine.requireStartable("STOPPED", "FAILED"))
        .doesNotThrowAnyException();
  }

  @Test
  void rejectsDuplicateOrUncertainStartReservations() {
    assertThatThrownBy(() -> stateMachine.requireStartable("RUNNING", "STARTING"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("请勿重复启动");
    assertThatThrownBy(() -> stateMachine.requireStartable("RUNNING", "RUNNING"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("请勿重复启动");
    assertThatThrownBy(() -> stateMachine.requireStartable("STOPPED", "STOPPING"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("请勿重复启动");
    assertThatThrownBy(() -> stateMachine.requireStartable("STOPPED", "UNKNOWN"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("请勿重复启动");
    assertThatThrownBy(() -> stateMachine.requireStartable("STOPPED", "CONFLICT"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("请勿重复启动");
  }
}
