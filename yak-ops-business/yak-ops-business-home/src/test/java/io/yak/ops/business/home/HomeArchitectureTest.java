package io.yak.ops.business.home;

import static org.assertj.core.api.Assertions.assertThat;

import io.yak.framework.security.web.RequiresPermission;
import io.yak.ops.business.home.asset.HomeAssetOverviewReader;
import io.yak.ops.business.home.cockpit.HomeCockpitReader;
import io.yak.ops.business.home.controller.v1.HomeAssetOverviewController;
import io.yak.ops.business.home.controller.v1.HomeCockpitController;
import io.yak.ops.business.home.controller.v1.HomeDataCenterController;
import io.yak.ops.business.home.controller.v1.HomeQualityOverviewController;
import io.yak.ops.business.home.controller.v1.HomeResourceCenterController;
import io.yak.ops.business.home.controller.v1.HomeScheduleCenterController;
import io.yak.ops.business.home.datacenter.HomeDataCenterReader;
import io.yak.ops.business.home.quality.HomeQualityOverviewReader;
import io.yak.ops.business.home.resource.HomeResourceCenterReader;
import io.yak.ops.business.home.schedule.HomeScheduleCenterReader;
import io.yak.ops.business.quality.QualityPermissionCode;
import io.yak.ops.common.constant.resource.ResourcePermissionCode;
import io.yak.ops.core.project.ProjectMigrationMode;
import io.yak.ops.core.project.ProjectScope;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestMapping;

class HomeArchitectureTest {

  @Test
  void shouldKeepHomeReadersAsExplicitInternalReadRoles() {
    for (Class<?> reader : List.of(
        HomeCockpitReader.class,
        HomeDataCenterReader.class,
        HomeAssetOverviewReader.class,
        HomeQualityOverviewReader.class,
        HomeResourceCenterReader.class,
        HomeScheduleCenterReader.class)) {
      assertThat(reader.getAnnotation(Component.class))
          .as("%s must be an internal @Component read role", reader.getSimpleName())
          .isNotNull();
      assertThat(reader.getAnnotation(Service.class))
          .as("%s must not drift back to a generic @Service role", reader.getSimpleName())
          .isNull();
      assertThat(reader.getSimpleName()).endsWith("Reader");
      assertThat(reader.getPackageName()).doesNotContain(".service");
    }
  }

  @Test
  void shouldKeepExternalHomeRoutesAndProjectScopeStable() {
    Map<Class<?>, String> expectedRoutes = new LinkedHashMap<>();
    expectedRoutes.put(HomeCockpitController.class, "/api/v1/home/cockpit");
    expectedRoutes.put(HomeDataCenterController.class, "/api/v1/home/data-center");
    expectedRoutes.put(HomeAssetOverviewController.class, "/api/v1/home/assets");
    expectedRoutes.put(HomeQualityOverviewController.class, "/api/v1/home/quality");
    expectedRoutes.put(HomeResourceCenterController.class, "/api/v1/home/resources");
    expectedRoutes.put(HomeScheduleCenterController.class, "/api/v1/home/schedule-center");

    for (Map.Entry<Class<?>, String> entry : expectedRoutes.entrySet()) {
      Class<?> controller = entry.getKey();
      RequestMapping mapping = controller.getAnnotation(RequestMapping.class);
      ProjectScope scope = controller.getAnnotation(ProjectScope.class);

      assertThat(mapping).isNotNull();
      assertThat(mapping.value()).containsExactly(entry.getValue());
      assertThat(scope).isNotNull();
      assertThat(scope.value()).isEqualTo(ProjectMigrationMode.PROJECT_REQUIRED);
    }

    RequiresPermission qualityPermission =
        HomeQualityOverviewController.class.getAnnotation(RequiresPermission.class);
    assertThat(qualityPermission).isNotNull();
    assertThat(qualityPermission.value()).isEqualTo(QualityPermissionCode.EXECUTION_READ);

    RequiresPermission resourcePermission =
        HomeResourceCenterController.class.getAnnotation(RequiresPermission.class);
    assertThat(resourcePermission).isNotNull();
    assertThat(resourcePermission.value()).isEqualTo(ResourcePermissionCode.READ);
  }
}
