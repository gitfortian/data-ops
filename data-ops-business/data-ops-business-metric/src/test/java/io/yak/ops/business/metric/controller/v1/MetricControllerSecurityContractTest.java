package io.yak.ops.business.metric.controller.v1;

import static org.assertj.core.api.Assertions.assertThat;

import io.yak.framework.security.web.RequiresPermission;
import io.yak.ops.common.constant.metric.MetricPermissionCode;
import io.yak.ops.core.project.ProjectMigrationMode;
import io.yak.ops.core.project.ProjectScope;
import java.lang.reflect.Array;
import org.junit.jupiter.api.Test;

class MetricControllerSecurityContractTest {

  @Test
  void canonicalMetricAuthoringReadsRemainProjectScopedAndPermissionGuarded() throws Exception {
    ProjectScope projectScope = MetricController.class.getAnnotation(ProjectScope.class);
    RequiresPermission readPermission = MetricController.class.getAnnotation(RequiresPermission.class);

    assertThat(projectScope).isNotNull();
    assertThat(projectScope.value()).isEqualTo(ProjectMigrationMode.PROJECT_REQUIRED);
    assertThat(readPermission).isNotNull();
    assertThat(permissionValues(readPermission)).contains(MetricPermissionCode.READ);
  }

  private static String[] permissionValues(RequiresPermission permission) throws Exception {
    Object raw = permission.annotationType().getMethod("value").invoke(permission);
    if (raw instanceof String value) {
      return new String[] {value};
    }
    int length = Array.getLength(raw);
    String[] values = new String[length];
    for (int index = 0; index < length; index++) {
      values[index] = String.valueOf(Array.get(raw, index));
    }
    return values;
  }
}
