package io.yak.framework.security.service.impl;

import io.yak.framework.security.common.entity.RolePermission;
import io.yak.framework.security.dao.RolePermissionDao;
import io.yak.framework.security.service.PermissionCache;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Downstream persistence execution still uses exactly the relocated RBAC port. */
class SecurityRelationPortCompatibilityTest {

  @Test
  void legacyRbacDaoImplementationAndServiceUseMigratedPort() {
    assertTrue(RolePermissionDao.class.isAssignableFrom(
        io.yak.framework.security.dao.impl.RolePermissionDaoImpl.class));
  }

  @Test
  void replaceRoleGrantsKeepsDeleteThenInsertAndRoleCacheInvalidation() {
    RolePermissionDao dao = mock(RolePermissionDao.class);
    PermissionCache cache = mock(PermissionCache.class);
    RolePermissionServiceImpl service = new RolePermissionServiceImpl(dao, cache);

    service.updateRolePermission(11L, List.of(30L, 30L, 42L));

    InOrder ordered = inOrder(dao);
    ordered.verify(dao).deleteByRoleId(11L);
    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<RolePermission>> captures = ArgumentCaptor.forClass(List.class);
    ordered.verify(dao).insertBatch(captures.capture());
    assertEquals(List.of(30L, 42L), captures.getValue().stream()
        .map(RolePermission::getPermissionId).toList());
    assertTrue(captures.getValue().stream().allMatch(value -> value.getRoleId().equals(11L)));
    verify(cache).invalidateRole(11L);
  }

  @Test
  void emptyRoleGrantReplacementStillRemovesPriorGrantsWithoutInsert() {
    RolePermissionDao dao = mock(RolePermissionDao.class);
    PermissionCache cache = mock(PermissionCache.class);
    new RolePermissionServiceImpl(dao, cache).updateRolePermission(11L, List.of());
    verify(dao).deleteByRoleId(11L);
    verify(dao, never()).insertBatch(anyList());
    verify(cache).invalidateRole(11L);
  }

  @Test
  void deletingGrantInvalidatesPermissionCachesWithoutChangingDaoSemantics() {
    RolePermissionDao dao = mock(RolePermissionDao.class);
    PermissionCache cache = mock(PermissionCache.class);
    var service = new RolePermissionServiceImpl(dao, cache);
    service.deleteRolePermissionByPermissionId(31L);
    verify(cache).invalidateAll();
    verify(dao).deleteByPermissionId(31L);
  }

  @Test
  void roleGrantMutationStillUsesDedicatedTransactionAndRollbackForException() throws Exception {
    for (String operation : List.of("saveRolePermission", "updateRolePermission")) {
      Method method = RolePermissionServiceImpl.class.getMethod(operation, Long.class, List.class);
      Transactional tx = method.getAnnotation(Transactional.class);
      assertNotNull(tx, operation + " must remain transactional");
      assertEquals("yakSecurityTransactionManager", tx.transactionManager());
      assertArrayEquals(new Class<?>[]{Exception.class}, tx.rollbackFor());
    }
    Transactional tx = RolePermissionServiceImpl.class
        .getMethod("deleteRolePermissionByPermissionId", Long.class)
        .getAnnotation(Transactional.class);
    assertNotNull(tx);
    assertEquals("yakSecurityTransactionManager", tx.transactionManager());
  }
}
