package io.yak.framework.security.service.impl;

import io.yak.framework.security.common.entity.UserProject;
import io.yak.framework.security.common.entity.UserRole;
import io.yak.framework.security.dao.UserProjectDao;
import io.yak.framework.security.dao.UserRoleDao;
import io.yak.framework.security.dao.impl.UserProjectDaoImpl;
import io.yak.framework.security.dao.impl.UserRoleDaoImpl;
import io.yak.framework.security.service.PermissionCache;
import io.yak.ops.platform.security.port.UserProjectMembershipPort;
import io.yak.ops.platform.security.port.UserRoleAssignmentPort;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Runtime compatibility of Platform ports with the unchanged legacy DAO constructors. */
class MembershipPortWiringTest {

  @Test
  void oldDaosPreserveDeclaredJavaMethodsAndImplementNewPlatformPorts() {
    assertTrue(UserProjectMembershipPort.class.isAssignableFrom(UserProjectDao.class));
    assertTrue(UserRoleAssignmentPort.class.isAssignableFrom(UserRoleDao.class));
    assertTrue(UserProjectMembershipPort.class.isAssignableFrom(UserProjectDaoImpl.class));
    assertTrue(UserRoleAssignmentPort.class.isAssignableFrom(UserRoleDaoImpl.class));
    assertEquals(10, UserProjectDao.class.getDeclaredMethods().length);
    assertEquals(7, UserRoleDao.class.getDeclaredMethods().length);
  }

  @Test
  void projectServiceUsesSameLegacyDaoInstanceAsPlatformPort() throws Exception {
    UserProjectDao dao = mock(UserProjectDao.class);
    UserProjectServiceImpl service = new UserProjectServiceImpl(dao, mock(PermissionCache.class));
    Field port = UserProjectServiceImpl.class.getDeclaredField("membershipPort");
    port.setAccessible(true);
    assertSame(dao, port.get(service));
    assertEquals(UserProjectMembershipPort.class, port.getType());
  }

  @Test
  void roleServiceUsesSameLegacyDaoInstanceAsPlatformPort() throws Exception {
    UserRoleDao dao = mock(UserRoleDao.class);
    UserRoleServiceImpl service = new UserRoleServiceImpl(dao, mock(PermissionCache.class));
    Field port = UserRoleServiceImpl.class.getDeclaredField("assignmentPort");
    port.setAccessible(true);
    assertSame(dao, port.get(service));
    assertEquals(UserRoleAssignmentPort.class, port.getType());
  }

  @Test
  void serviceConstructorsKeepLegacyBinarySignaturesWithoutDuplicateDaoState() throws Exception {
    assertNotNull(UserProjectServiceImpl.class.getConstructor(
        UserProjectDao.class, PermissionCache.class));
    assertNotNull(UserRoleServiceImpl.class.getConstructor(
        UserRoleDao.class, PermissionCache.class));
    assertEquals(1, java.util.Arrays.stream(UserProjectServiceImpl.class.getDeclaredFields())
        .filter(field -> UserProjectMembershipPort.class.isAssignableFrom(field.getType())).count());
    assertEquals(1, java.util.Arrays.stream(UserRoleServiceImpl.class.getDeclaredFields())
        .filter(field -> UserRoleAssignmentPort.class.isAssignableFrom(field.getType())).count());
    assertThrows(NoSuchFieldException.class,
        () -> UserProjectServiceImpl.class.getDeclaredField("userProjectDao"));
    assertThrows(NoSuchFieldException.class,
        () -> UserRoleServiceImpl.class.getDeclaredField("userRoleDao"));
  }

  @Test
  void ownerProjectWritesRetainOwnerTypeAndCacheInvalidation() {
    UserProjectDao dao = mock(UserProjectDao.class);
    PermissionCache cache = mock(PermissionCache.class);
    new UserProjectServiceImpl(dao, cache).saveOwnerProject(71L, List.of(5L, 5L, 9L));
    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<UserProject>> capture = ArgumentCaptor.forClass(List.class);
    verify(dao).insertBatch(capture.capture());
    List<UserProject> saved = capture.getValue();
    assertEquals(List.of(5L, 9L), saved.stream().map(UserProject::getUserId).toList());
    assertTrue(saved.stream().allMatch(row -> Long.valueOf(71L).equals(row.getProjectId())
        && Integer.valueOf(1).equals(row.getUserType())));
    verify(cache).invalidateUser(5L);
    verify(cache).invalidateUser(9L);
  }

  @Test
  void roleUpdateDeletesOldAssignmentBeforeAddingDistinctNewGrants() {
    UserRoleDao dao = mock(UserRoleDao.class);
    PermissionCache cache = mock(PermissionCache.class);
    new UserRoleServiceImpl(dao, cache).updateUserRoleByUserId(5L, List.of(17L, 17L, 19L));
    InOrder sequence = inOrder(dao);
    sequence.verify(dao).deleteByUserIdOrRoleId(5L, null);
    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<UserRole>> capture = ArgumentCaptor.forClass(List.class);
    sequence.verify(dao).insertBatch(capture.capture());
    assertEquals(List.of(17L, 19L), capture.getValue().stream().map(UserRole::getRoleId).toList());
    assertTrue(capture.getValue().stream().allMatch(row -> Long.valueOf(5L).equals(row.getUserId())));
    verify(cache).invalidateUser(5L);
  }

  @Test
  void emptyRoleUpdateRevokesWithoutAccidentallyRestoringRoles() {
    UserRoleDao dao = mock(UserRoleDao.class);
    PermissionCache cache = mock(PermissionCache.class);
    new UserRoleServiceImpl(dao, cache).updateUserRoleByRoleId(19L, List.of());
    verify(dao).deleteByUserIdOrRoleId(null, 19L);
    verify(dao, never()).insertBatch(anyList());
    verify(cache).invalidateRole(19L);
  }

  @Test
  void legacyMutationMethodsRetainSecurityTransactionManagerAndRollback() throws Exception {
    assertTransaction(UserProjectServiceImpl.class, "saveOwnerProject");
    assertTransaction(UserProjectServiceImpl.class, "updateOwnerProject");
    assertTransaction(UserRoleServiceImpl.class, "updateUserRoleByUserId");
    assertTransaction(UserRoleServiceImpl.class, "updateUserRoleByRoleId");
  }

  private static void assertTransaction(Class<?> type, String name) throws Exception {
    Method method = type.getDeclaredMethod(name, Long.class, List.class);
    Transactional tx = method.getAnnotation(Transactional.class);
    assertNotNull(tx, name);
    assertEquals("yakSecurityTransactionManager", tx.transactionManager(), name);
    assertArrayEquals(new Class<?>[]{Exception.class}, tx.rollbackFor(), name);
  }
}
