package io.yak.ops.business.job.controller.v1;

import static org.assertj.core.api.Assertions.assertThat;

import io.yak.ops.core.project.ProjectMigrationMode;
import io.yak.ops.core.project.ProjectScope;
import org.junit.jupiter.api.Test;

class TaskControllerProjectScopeTest {

  @Test
  void taskDiscoveryRequiresProjectContext() {
    ProjectScope scope = TaskController.class.getAnnotation(ProjectScope.class);

    assertThat(scope).isNotNull();
    assertThat(scope.value()).isEqualTo(ProjectMigrationMode.PROJECT_REQUIRED);
  }
}
