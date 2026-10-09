package io.yak.framework.security.dao;

import io.yak.framework.security.common.entity.Permission;
import java.beans.Introspector;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Stable ABI for the moved Permission + PermissionDao persistence port. */
class PermissionPersistencePortContractTest {

  @Test
  void permissionDaoKeepsAllFourHistoricalMethodSignatures() throws Exception {
    assertEquals("io.yak.framework.security.dao.PermissionDao", PermissionDao.class.getName());
    assertTrue(PermissionDao.class.isInterface());
    assertEquals(4, PermissionDao.class.getDeclaredMethods().length);
    assertEquals(List.class,
        PermissionDao.class.getMethod("selectAllAndAscOrderByLevel").getReturnType());
    assertEquals(void.class,
        PermissionDao.class.getMethod("insertBatch", List.class).getReturnType());
    assertEquals(int.class,
        PermissionDao.class.getMethod("deleteById", Long.class).getReturnType());
    assertEquals(void.class,
        PermissionDao.class.getMethod("synchronizeDeclared", List.class).getReturnType());
  }

  @Test
  void movedPermissionBeanKeepsOriginalPropertiesAndTypes() throws Exception {
    assertEquals("io.yak.framework.security.common.entity.Permission", Permission.class.getName());
    Permission model = new Permission();
    model.setId(42L);
    model.setPermissionCode("security:project:read");
    model.setPermissionName("Read Project");
    model.setParentId(7L);
    model.setLeaf(true);
    model.setLevel(2);
    model.setDescription("Project-level access");
    model.setMenuCode("system-security-projects");
    model.setParentCode("security");
    model.setActive(true);
    model.setDeclared(true);
    assertEquals(42L, model.getId());
    assertEquals("security:project:read", model.getPermissionCode());
    assertEquals("Read Project", model.getPermissionName());
    assertEquals(7L, model.getParentId());
    assertEquals(true, model.getLeaf());
    assertEquals(2, model.getLevel());
    assertEquals("Project-level access", model.getDescription());
    assertEquals("system-security-projects", model.getMenuCode());
    assertEquals("security", model.getParentCode());
    assertEquals(true, model.getActive());
    assertEquals(true, model.getDeclared());

    Set<String> properties = Arrays.stream(Introspector.getBeanInfo(Permission.class)
        .getPropertyDescriptors()).map(bean -> bean.getName()).collect(Collectors.toSet());
    assertTrue(properties.containsAll(Set.of("permissionCode", "permissionName",
        "parentId", "parentCode", "leaf", "level", "menuCode", "active", "declared")));
    assertTrue(Modifier.isTransient(Permission.class.getDeclaredField("parentCode").getModifiers()));
    assertFalse(Modifier.isTransient(Permission.class.getDeclaredField("menuCode").getModifiers()));
  }

  @Test
  void permissionBeanRetainsLombokValueEqualityContract() {
    Permission a = new Permission();
    Permission b = new Permission();
    a.setPermissionCode("security:project:read");
    b.setPermissionCode("security:project:read");
    assertEquals(a, b);
    assertEquals(a.hashCode(), b.hashCode());
    b.setPermissionCode("security:project:write");
    assertNotEquals(a, b);
  }
}
