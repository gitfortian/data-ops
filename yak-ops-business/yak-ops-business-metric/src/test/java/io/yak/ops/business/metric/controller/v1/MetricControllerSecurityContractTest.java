package io.yak.ops.business.metric.controller.v1;

import static org.assertj.core.api.Assertions.assertThat;

import io.yak.framework.security.web.RequiresPermission;
import io.yak.ops.common.constant.metric.MetricPermissionCode;
import io.yak.ops.core.project.ProjectMigrationMode;
import io.yak.ops.core.project.ProjectScope;
import org.junit.jupiter.api.Test;

class MetricControllerSecurityContractTest {

  @Test
  void canonicalMetricAuthoringReadsRemainProjectScopedAndPermissionGuarded() {
    ProjectScope projectScope = MetricController.class.getAnnotation(ProjectScope.class);
    RequiresPermission readPermission = MetricController.class.getAnnotation(RequiresPermission.class);

    assertThat(projectScope).isNotNull();
    assertThat(projectScope.value()).isEqualTo(ProjectMigrationMode.PROJECT_REQUIRED);
    assertThat(readPermission).isNotNull();
    assertThat(readPermission.value()).isEqualTo(MetricPermissionCode.READ);
  }
}
