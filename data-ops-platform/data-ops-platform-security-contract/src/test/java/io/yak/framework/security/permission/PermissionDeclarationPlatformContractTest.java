package io.yak.framework.security.permission;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** ABI and validation contracts for the relocated A8.2c permission declarations. */
class PermissionDeclarationPlatformContractTest {

  @Test
  void annotationRuntimeRetentionTargetsAndDefaultsStayCompatible() throws Exception {
    assertEquals(RetentionPolicy.RUNTIME, YakPermission.class.getAnnotation(Retention.class).value());
    assertEquals(Set.of(ElementType.METHOD, ElementType.TYPE),
        Set.copyOf(Arrays.asList(YakPermission.class.getAnnotation(Target.class).value())));
    assertEquals("io.yak.framework.security.permission.YakPermission", YakPermission.class.getName());
    assertEquals(Set.of("code", "name", "group", "groupCode", "menuCode", "description"),
        Arrays.stream(YakPermission.class.getDeclaredMethods())
            .map(method -> method.getName()).collect(Collectors.toSet()));
    assertNull(YakPermission.class.getDeclaredMethod("code").getDefaultValue());
    assertNull(YakPermission.class.getDeclaredMethod("name").getDefaultValue());
    assertNull(YakPermission.class.getDeclaredMethod("group").getDefaultValue());
    for (String optional : List.of("groupCode", "menuCode", "description")) {
      assertEquals("", YakPermission.class.getDeclaredMethod(optional).getDefaultValue());
    }
  }

  @Test
  void immutablePermissionDefinitionsKeepTrimValidationAndMenuMetadata() {
    PermissionDefinition.Item item = PermissionDefinition.Item.ofMenu(
        " view:read ", " View ", " Detail ", " view-menu ");
    PermissionDefinition group = PermissionDefinition.of(" view ", " View Group ", item);
    assertEquals("view", group.getCode());
    assertEquals("View Group", group.getName());
    assertSame(item, group.getPermissions().get(0));
    assertEquals("view:read", item.getCode());
    assertEquals("View", item.getName());
    assertEquals("Detail", item.getDescription());
    assertEquals("view-menu", item.getMenuCode());
    assertThrows(UnsupportedOperationException.class,
        () -> group.getPermissions().add(PermissionDefinition.Item.of("x", "X")));
    assertThrows(IllegalArgumentException.class,
        () -> PermissionDefinition.of("  ", "group", "code"));
    assertThrows(IllegalArgumentException.class,
        () -> PermissionDefinition.of("group", " ", "code"));
    assertThrows(IllegalArgumentException.class,
        () -> PermissionDefinition.Item.of(" ", "permission"));
    assertThrows(IllegalArgumentException.class,
        () -> PermissionDefinition.Item.of("code", " "));
    assertThrows(IllegalArgumentException.class,
        () -> PermissionDefinition.of("group", "Group", (PermissionDefinition.Item) null));
    assertNull(PermissionDefinition.Item.ofMenu("a", "A", " ", "").getDescription());
    assertNull(PermissionDefinition.Item.ofMenu("a", "A", "", " ").getMenuCode());
  }

  @Test
  void permissionProviderFactoryStillPreservesOrderAndNullBehavior() {
    PermissionDefinition a = PermissionDefinition.of("a", "A", "a:read");
    PermissionDefinition b = PermissionDefinition.of("b", "B", "b:read");
    PermissionDefinitionProvider provider = PermissionDefinitionProvider.of(a, b);
    assertEquals(List.of(a, b), provider.getPermissionDefinitions());
    assertThrows(UnsupportedOperationException.class,
        () -> provider.getPermissionDefinitions().clear());
    assertTrue(PermissionDefinitionProvider.of((PermissionDefinition[]) null)
        .getPermissionDefinitions().isEmpty());
  }
}
