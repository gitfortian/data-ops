package io.yak.ops.business.workflow.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WorkflowRunInputScopeTest {

  @Test
  void shouldOverlayRuntimeInputWithoutMutatingPublishedRunSpec() {
    WorkflowRunSpec spec = new WorkflowRunSpec(
        "workflow",
        List.of(),
        List.of(),
        Map.of("tenant", "base", "keep", 1),
        0L,
        "CONTINUE_INDEPENDENT_BRANCHES");

    assertThat(spec.input()).containsEntry("tenant", "base");

    try (var ignored = WorkflowRunInputScope.open(
        Map.of("tenant", "schedule", "businessDate", "2026-08-14"))) {
      assertThat(spec.input())
          .containsEntry("tenant", "schedule")
          .containsEntry("businessDate", "2026-08-14")
          .containsEntry("keep", 1);

      try (var nested = WorkflowRunInputScope.open(Map.of("tenant", "backfill"))) {
        assertThat(spec.input())
            .containsEntry("tenant", "backfill")
            .doesNotContainKey("businessDate")
            .containsEntry("keep", 1);
      }

      assertThat(spec.input()).containsEntry("tenant", "schedule");
    }

    assertThat(spec.input())
        .containsEntry("tenant", "base")
        .doesNotContainKey("businessDate");
  }
}
