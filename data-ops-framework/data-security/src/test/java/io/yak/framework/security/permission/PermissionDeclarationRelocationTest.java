package io.yak.framework.security.permission;

import java.lang.reflect.Method;
import java.net.URL;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;

import static org.junit.jupiter.api.Assertions.*;

/** Security Starter must still discover the exact runtime annotation FQCN after its jar relocation. */
class PermissionDeclarationRelocationTest {

  @YakPermission(
      code = "security:project:read", name = "Read Project",
      group = "Security", groupCode = "security",
      menuCode = "system-security-projects", description = "Project access")
  static class PermissionControllerFixture {
    @YakPermission(code = "security:user:read", name = "Read User", group = "Security")
    void readUser() {}
  }

  @Test
  void originalFqcnClassAndRuntimeAnnotationRemainDiscoverable() throws Exception {
    Class<?> oldFqcn = Class.forName("io.yak.framework.security.permission.YakPermission");
    assertSame(YakPermission.class, oldFqcn);
    YakPermission type = AnnotatedElementUtils.findMergedAnnotation(
        PermissionControllerFixture.class, YakPermission.class);
    assertNotNull(type);
    assertEquals("security:project:read", type.code());
    assertEquals("security", type.groupCode());
    assertEquals("system-security-projects", type.menuCode());
    Method method = PermissionControllerFixture.class.getDeclaredMethod("readUser");
    YakPermission onMethod = AnnotatedElementUtils.findMergedAnnotation(method, YakPermission.class);
    assertNotNull(onMethod);
    assertEquals("security:user:read", onMethod.code());
    assertEquals("", onMethod.groupCode());
  }

  @Test
  void exactlyOneBytecodeOwnerForEachMigratedLegacyFqcn() throws Exception {
    ClassLoader loader = PermissionDeclarationRelocationTest.class.getClassLoader();
    for (String name : List.of("YakPermission", "PermissionDefinition",
        "PermissionDefinition$Item", "PermissionDefinitionProvider")) {
      String path = "io/yak/framework/security/permission/" + name + ".class";
      List<URL> resources = Collections.list(loader.getResources(path));
      assertEquals(1, resources.size(), path + " must have a single binary owner: " + resources);
    }
  }

  @Test
  void permissionDeclarationAndProviderRemainBinaryReachable() throws Exception {
    assertSame(PermissionDefinition.class,
        Class.forName("io.yak.framework.security.permission.PermissionDefinition"));
    assertSame(PermissionDefinition.Item.class,
        Class.forName("io.yak.framework.security.permission.PermissionDefinition$Item"));
    assertSame(PermissionDefinitionProvider.class,
        Class.forName("io.yak.framework.security.permission.PermissionDefinitionProvider"));
  }
}
