package io.yak.ops.business.semantic.controller.v1;

import static org.assertj.core.api.Assertions.assertThat;

import io.yak.framework.security.web.RequiresPermission;
import io.yak.ops.common.constant.semantic.SemanticPermissionCode;
import io.yak.ops.core.project.ProjectMigrationMode;
import io.yak.ops.core.project.ProjectScope;
import org.junit.jupiter.api.Test;

class SemanticStandardControllerSecurityContractTest {

  @Test
  void stableStandardReadsRemainProjectScopedAndPermissionGuarded() {
    ProjectScope projectScope = SemanticStandardController.class.getAnnotation(ProjectScope.class);
    RequiresPermission readPermission = SemanticStandardController.class.getAnnotation(RequiresPermission.class);

    assertThat(projectScope).isNotNull();
    assertThat(projectScope.value()).isEqualTo(ProjectMigrationMode.PROJECT_REQUIRED);
    assertThat(readPermission).isNotNull();
    assertThat(readPermission.value()).isEqualTo(SemanticPermissionCode.READ);
  }
}
