package io.yak.ops.platform.security.port;

import io.yak.framework.security.common.entity.UserProject;
import io.yak.framework.security.common.entity.UserRole;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SecurityMembershipPortsTest {

  @Test
  void projectMembershipHasEightStableDtoFreeOperations() throws Exception {
    Class<?> type = UserProjectMembershipPort.class;
    assertEquals(9, type.getDeclaredMethods().length);
    assertEquals(List.class, type.getMethod("selectUserIdListByProjectId", Long.class, int.class).getReturnType());
    assertEquals(List.class, type.getMethod("selectProjectIdListByUserIdList", List.class).getReturnType());
    assertEquals(void.class, type.getMethod("insertBatch", List.class).getReturnType());
    assertEquals(int.class, type.getMethod("deleteUserProject", List.class).getReturnType());
    assertEquals(void.class, type.getMethod("deleteByProjectId", Long.class).getReturnType());
    assertEquals(void.class, type.getMethod("deleteByUserId", Long.class).getReturnType());
    assertEquals(void.class, type.getMethod("deleteByProjectIdAndUserType", Long.class, int.class).getReturnType());
    assertEquals(List.class, type.getMethod("selectByProjectIds", List.class).getReturnType());
    assertEquals(List.class, type.getMethod("selectMembershipsByCriteria", UserProjectCriteria.class).getReturnType());
    assertEquals("io.yak.ops.platform.security.port.UserProjectMembershipPort", type.getName());
    assertDtoAndPoFree(type);
  }

  @Test
  void roleAssignmentHasFiveStableDtoFreeOperations() throws Exception {
    Class<?> type = UserRoleAssignmentPort.class;
    assertEquals(7, type.getDeclaredMethods().length);
    assertEquals(List.class, type.getMethod("selectUserIdListByRoleId", Long.class).getReturnType());
    assertEquals(List.class, type.getMethod("selectRoleIdListByUserId", Long.class).getReturnType());
    assertEquals(void.class, type.getMethod("insertBatch", List.class).getReturnType());
    assertEquals(int.class, type.getMethod("deleteByUserIdOrRoleId", Long.class, Long.class).getReturnType());
    assertEquals(int.class, type.getMethod("selectCountByRoleId", Long.class).getReturnType());
    assertEquals(List.class, type.getMethod("selectAssignmentsByRoleIds", List.class).getReturnType());
    assertEquals(List.class, type.getMethod("selectAssignmentsByUserIds", List.class).getReturnType());
    assertEquals("io.yak.ops.platform.security.port.UserRoleAssignmentPort", type.getName());
    assertDtoAndPoFree(type);
  }

  private static void assertDtoAndPoFree(Class<?> type) {
    for (Method method : type.getDeclaredMethods()) {
      for (Type parameter : method.getGenericParameterTypes()) {
        assertFalse(parameter.getTypeName().contains(".po."), method.toString());
        assertFalse(parameter.getTypeName().contains(".dto."), method.toString());
      }
      String result = method.getGenericReturnType().getTypeName();
      assertFalse(result.contains(".po."), method.toString());
      assertFalse(result.contains(".dto."), method.toString());
    }
  }
}
