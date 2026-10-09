package io.yak.framework.security.dao;

import io.yak.framework.security.common.entity.RolePermission;
import io.yak.framework.security.common.entity.UserProject;
import io.yak.framework.security.common.entity.UserRole;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;
import java.util.Arrays;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Public security relation FQCN, bean and permission DAO method ABI. */
class SecurityRelationPortContractTest {

  @Test
  void oldFullyQualifiedNamesAndPermissionPortSignaturesRemainStable() throws Exception {
    assertEquals("io.yak.framework.security.dao.RolePermissionDao", RolePermissionDao.class.getName());
    assertEquals(5, RolePermissionDao.class.getDeclaredMethods().length);
    assertEquals(void.class, RolePermissionDao.class.getMethod("insertBatch", List.class).getReturnType());
    assertEquals(void.class, RolePermissionDao.class.getMethod("deleteByRoleId", Long.class).getReturnType());
    assertEquals(void.class, RolePermissionDao.class.getMethod("deleteByPermissionId", Long.class).getReturnType());
    assertEquals(List.class, RolePermissionDao.class.getMethod("selectPermissionIdListByRoleId", Long.class).getReturnType());
    assertEquals(List.class, RolePermissionDao.class.getMethod("selectPermissionIdListByRoleIdList", List.class).getReturnType());
    assertEquals("io.yak.framework.security.common.entity.RolePermission", RolePermission.class.getName());
    assertEquals("io.yak.framework.security.common.entity.UserRole", UserRole.class.getName());
    assertEquals("io.yak.framework.security.common.entity.UserProject", UserProject.class.getName());
    for (Class<?> type : List.of(RolePermission.class, UserRole.class, UserProject.class)) {
      assertEquals(type, Class.forName(type.getName()));
    }
  }

  @Test
  void userProjectMembershipKeepsAllThreeIdentityDimensions() {
    UserProject membership = new UserProject();
    membership.setUserId(13L);
    membership.setUserType(2);
    membership.setProjectId(991L);
    assertEquals(13L, membership.getUserId());
    assertEquals(2, membership.getUserType());
    assertEquals(991L, membership.getProjectId());
    UserProject same = new UserProject();
    same.setUserId(13L);
    same.setUserType(2);
    same.setProjectId(991L);
    assertEquals(membership, same);
    same.setProjectId(992L);
    assertNotEquals(membership, same);
  }

  @Test
  void userRoleConstructorAndRoleGrantFieldsRetainLombokAbi() throws Exception {
    UserRole userRole = new UserRole(20L, 80L);
    assertEquals(20L, userRole.getUserId());
    assertEquals(80L, userRole.getRoleId());
    assertEquals(new UserRole(20L, 80L), userRole);
    assertEquals(new UserRole(), UserRole.class.getDeclaredConstructor().newInstance());
    RolePermission grant = new RolePermission();
    grant.setRoleId(80L);
    grant.setPermissionId(501L);
    assertEquals(80L, grant.getRoleId());
    assertEquals(501L, grant.getPermissionId());
    assertNotEquals(grant, new RolePermission());
    Set<String> publicMethods = Arrays.stream(RolePermissionDao.class.getMethods())
        .map(Method::getName).collect(Collectors.toSet());
    assertEquals(Set.of("insertBatch", "deleteByRoleId", "deleteByPermissionId",
        "selectPermissionIdListByRoleId", "selectPermissionIdListByRoleIdList"), publicMethods);
  }
}
