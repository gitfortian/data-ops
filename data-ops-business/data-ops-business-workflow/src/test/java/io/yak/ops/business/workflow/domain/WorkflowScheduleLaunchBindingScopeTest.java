package io.yak.ops.business.workflow.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class WorkflowScheduleLaunchBindingScopeTest {

  @Test
  void shouldRestoreOuterTriggerAfterNestedLaunch() {
    assertThat(WorkflowScheduleLaunchBindingScope.currentTriggerId()).isNull();

    try (var outer = WorkflowScheduleLaunchBindingScope.open("trigger-outer")) {
      assertThat(WorkflowScheduleLaunchBindingScope.currentTriggerId()).isEqualTo("trigger-outer");
      try (var inner = WorkflowScheduleLaunchBindingScope.open("trigger-inner")) {
        assertThat(WorkflowScheduleLaunchBindingScope.currentTriggerId()).isEqualTo("trigger-inner");
      }
      assertThat(WorkflowScheduleLaunchBindingScope.currentTriggerId()).isEqualTo("trigger-outer");
    }

    assertThat(WorkflowScheduleLaunchBindingScope.currentTriggerId()).isNull();
  }
}
