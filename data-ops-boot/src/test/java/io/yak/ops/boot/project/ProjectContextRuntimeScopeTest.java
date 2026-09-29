package io.yak.ops.boot.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.core.project.ProjectContext;
import org.junit.jupiter.api.Test;

class ProjectContextRuntimeScopeTest {

  @Test
  void backgroundScopeRestoresPreviousContext() {
    ProjectContextRuntime runtime = new ProjectContextRuntime();
    runtime.bind(new ProjectContext(1L, "A"));

    Long inside = runtime.call(
        new ProjectContext(2L, "B"),
        () -> runtime.requireProjectId());

    assertEquals(2L, inside);
    assertEquals(1L, runtime.requireProjectId());
  }

  @Test
  void backgroundScopeClearsContextWhenThereWasNoPreviousBinding() {
    ProjectContextRuntime runtime = new ProjectContextRuntime();

    assertEquals(
        7L,
        runtime.call(new ProjectContext(7L, null), runtime::requireProjectId));
    assertTrue(runtime.current().isEmpty());
  }
}
