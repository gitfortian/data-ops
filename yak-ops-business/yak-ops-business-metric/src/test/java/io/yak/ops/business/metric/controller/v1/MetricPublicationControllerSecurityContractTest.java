package io.yak.ops.business.metric.controller.v1;

import static org.assertj.core.api.Assertions.assertThat;

import io.yak.framework.security.web.RequiresPermission;
import io.yak.ops.common.constant.metric.MetricPermissionCode;
import io.yak.ops.core.project.ProjectMigrationMode;
import io.yak.ops.core.project.ProjectScope;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

class MetricPublicationControllerSecurityContractTest {

  @Test
  void publicationReadsRemainProjectScopedAndReadGuarded() throws Exception {
    ProjectScope projectScope = MetricPublicationController.class.getAnnotation(ProjectScope.class);
    RequiresPermission readPermission =
        MetricPublicationController.class.getAnnotation(RequiresPermission.class);

    assertThat(projectScope).isNotNull();
    assertThat(projectScope.value()).isEqualTo(ProjectMigrationMode.PROJECT_REQUIRED);
    assertThat(readPermission).isNotNull();
    assertThat(permissionValues(readPermission)).contains(MetricPermissionCode.READ);
  }

  @Test
  void publishAndWithdrawRequireDedicatedPublicationPermission() throws Exception {
    Method publish = MetricPublicationController.class.getMethod(
        "publish", Long.class, int.class, jakarta.servlet.http.HttpServletRequest.class);
    Method withdraw = MetricPublicationController.class.getMethod(
        "withdraw", Long.class, jakarta.servlet.http.HttpServletRequest.class);

    assertThat(permissionValues(publish.getAnnotation(RequiresPermission.class)))
        .contains(MetricPermissionCode.PUBLISH);
    assertThat(permissionValues(withdraw.getAnnotation(RequiresPermission.class)))
        .contains(MetricPermissionCode.PUBLISH);
  }

  private static String[] permissionValues(RequiresPermission permission) throws Exception {
    assertThat(permission).isNotNull();
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
