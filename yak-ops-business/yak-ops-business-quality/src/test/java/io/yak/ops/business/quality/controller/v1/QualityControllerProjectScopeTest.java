package io.yak.ops.business.quality.controller.v1;

import static org.assertj.core.api.Assertions.assertThat;

import io.yak.ops.core.project.ProjectMigrationMode;
import io.yak.ops.core.project.ProjectScope;
import java.util.List;
import org.junit.jupiter.api.Test;

class QualityControllerProjectScopeTest {

  @Test
  void projectOwnedControllersRequireTrustedProjectContext() {
    for (Class<?> controller : projectControllers()) {
      ProjectScope scope = controller.getAnnotation(ProjectScope.class);
      assertThat(scope)
          .as("%s must declare ProjectScope", controller.getSimpleName())
          .isNotNull();
      assertThat(scope.value())
          .as("%s must reject requests without Project context", controller.getSimpleName())
          .isEqualTo(ProjectMigrationMode.PROJECT_REQUIRED);
    }
  }

  @Test
  void platformTemplateControllersRemainGlobal() {
    for (Class<?> controller : globalControllers()) {
      ProjectScope scope = controller.getAnnotation(ProjectScope.class);
      assertThat(scope)
          .as("%s must explicitly declare its global contract", controller.getSimpleName())
          .isNotNull();
      assertThat(scope.value())
          .isEqualTo(ProjectMigrationMode.LEGACY_GLOBAL);
    }
  }

  private List<Class<?>> projectControllers() {
    return List.of(
        QualityTableAssetController.class,
        QualityMonitorController.class,
        QualityExecutionController.class,
        QualityExecutionWorkspaceController.class,
        QualityOverviewController.class,
        QualityWorkspaceController.class);
  }

  private List<Class<?>> globalControllers() {
    return List.of(
        QualityTemplateController.class,
        CustomTemplateController.class);
  }
}
