package io.yak.ops.business.resource.controller.v1;

import static org.assertj.core.api.Assertions.assertThat;

import io.yak.framework.security.web.RequiresPermission;
import io.yak.ops.common.constant.resource.ResourcePermissionCode;
import io.yak.ops.core.project.ProjectMigrationMode;
import io.yak.ops.core.project.ProjectScope;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

class ResourcesControllerContractTest {

  @Test
  void exposesVersionedResourceApiAndExistingPermissionContract() throws Exception {
    RequestMapping mapping = ResourcesController.class.getAnnotation(RequestMapping.class);
    RequiresPermission read = ResourcesController.class.getAnnotation(RequiresPermission.class);
    ProjectScope resourceScope = ResourcesController.class.getAnnotation(ProjectScope.class);
    Method download = ResourcesController.class.getMethod(
        "download", Long.class, jakarta.servlet.http.HttpServletResponse.class);
    RequiresPermission downloadPermission = download.getAnnotation(RequiresPermission.class);
    Method storagePlugins = ResourcesController.class.getMethod("storagePlugins");
    GetMapping storagePluginsMapping = storagePlugins.getAnnotation(GetMapping.class);
    ProjectScope storagePluginScope = storagePlugins.getAnnotation(ProjectScope.class);

    assertThat(mapping.value()).containsExactly("/api/v1/resources");
    assertThat(read.value()).isEqualTo(ResourcePermissionCode.READ);
    assertThat(resourceScope.value()).isEqualTo(ProjectMigrationMode.PROJECT_REQUIRED);
    assertThat(downloadPermission.value()).isEqualTo(ResourcePermissionCode.DOWNLOAD);
    assertThat(storagePluginsMapping.value()).containsExactly("/storage-plugins");
    assertThat(storagePluginScope.value()).isEqualTo(ProjectMigrationMode.LEGACY_GLOBAL);
  }
}
