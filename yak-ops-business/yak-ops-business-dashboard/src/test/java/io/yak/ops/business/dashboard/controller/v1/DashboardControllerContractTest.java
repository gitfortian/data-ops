package io.yak.ops.business.dashboard.controller.v1;

import static io.yak.ops.core.project.ProjectMigrationMode.PROJECT_REQUIRED;
import static org.assertj.core.api.Assertions.assertThat;

import io.yak.ops.core.project.ProjectScope;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

class DashboardControllerContractTest {

  @Test
  void baseRestPathRemainsStableInsideRequiredProjectScope() {
    RequestMapping mapping = DashboardController.class.getAnnotation(RequestMapping.class);
    ProjectScope controllerScope = DashboardController.class.getAnnotation(ProjectScope.class);
    ProjectScope overviewScope = DashboardOverviewController.class.getAnnotation(ProjectScope.class);

    assertThat(mapping).isNotNull();
    assertThat(mapping.value()).containsExactly("/api/v1/dashboards");
    assertThat(controllerScope).isNotNull();
    assertThat(controllerScope.value()).isEqualTo(PROJECT_REQUIRED);
    assertThat(overviewScope).isNotNull();
    assertThat(overviewScope.value()).isEqualTo(PROJECT_REQUIRED);
  }

  @Test
  void deprecatedActivateRouteRemainsCompatibilityAlias() throws Exception {
    Method method = DashboardController.class.getMethod("activateVersion", long.class, int.class);
    assertThat(method.getAnnotation(Deprecated.class)).isNotNull();
    assertThat(method.getAnnotation(PostMapping.class).value())
        .containsExactly("/{dashboardId}/activate/{versionNo}");
  }
}
