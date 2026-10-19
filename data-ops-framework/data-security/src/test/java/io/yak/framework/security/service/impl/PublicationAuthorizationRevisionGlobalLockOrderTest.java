package io.yak.framework.security.service.impl;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.yak.framework.security.dao.RolePermissionDao;
import io.yak.framework.security.dao.UserRoleDao;
import io.yak.framework.security.service.PermissionCache;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The Global authorization revision is acquired before grants/revocations.
 * All callers use Security-owned transactions; no Modeling publication is
 * authorized by these tests.
 */
class PublicationAuthorizationRevisionGlobalLockOrderTest {

  @Test
  void rolePermissionRevokeLocksGlobalBeforeDaoMutation() {
    var dao = mock(RolePermissionDao.class);
    var epoch = mock(PublicationAuthorizationRevisionService.class);
    var service = new RolePermissionServiceImpl(dao, mock(PermissionCache.class), epoch);
    service.deleteRolePermissionByRoleId(21L);
    var ordered = inOrder(epoch, dao);
    ordered.verify(epoch).advanceGlobalMutation();
    ordered.verify(dao).deleteByRoleId(21L);
  }

  @Test
  void rolePermissionClearLocksGlobalBeforeDaoMutation() {
    var dao = mock(RolePermissionDao.class);
    var epoch = mock(PublicationAuthorizationRevisionService.class);
    var service = new RolePermissionServiceImpl(dao, mock(PermissionCache.class), epoch);
    service.updateRolePermission(21L, List.of());
    var ordered = inOrder(epoch, dao);
    ordered.verify(epoch).advanceGlobalMutation();
    ordered.verify(dao).deleteByRoleId(21L);
  }

  @Test
  void userRoleRevokeLocksGlobalBeforeDaoMutation() {
    var dao = mock(UserRoleDao.class);
    var epoch = mock(PublicationAuthorizationRevisionService.class);
    var service = new UserRoleServiceImpl(dao, mock(PermissionCache.class), epoch);
    service.deleteByUserIdOrRoleId(11L, null);
    var ordered = inOrder(epoch, dao);
    ordered.verify(epoch).advanceGlobalMutation();
    ordered.verify(dao).deleteByUserIdOrRoleId(11L, null);
  }

  @Test
  void userRoleReplacementLocksGlobalBeforeDeletingOldRoles() {
    var dao = mock(UserRoleDao.class);
    var epoch = mock(PublicationAuthorizationRevisionService.class);
    var service = new UserRoleServiceImpl(dao, mock(PermissionCache.class), epoch);
    service.updateUserRoleByUserId(11L, List.of());
    var ordered = inOrder(epoch, dao);
    ordered.verify(epoch).advanceGlobalMutation();
    ordered.verify(dao).deleteByUserIdOrRoleId(11L, null);
  }

  @Test
  void unavailableEpochPreventsGrantOrRevocationWrites() {
    var roles = mock(RolePermissionDao.class);
    var userRoles = mock(UserRoleDao.class);
    var epoch = mock(PublicationAuthorizationRevisionService.class);
    org.mockito.Mockito.doThrow(new IllegalStateException("Security unavailable"))
        .when(epoch).advanceGlobalMutation();
    var roleService = new RolePermissionServiceImpl(
        roles, mock(PermissionCache.class), epoch);
    var userService = new UserRoleServiceImpl(
        userRoles, mock(PermissionCache.class), epoch);
    assertThrows(IllegalStateException.class,
        () -> roleService.updateRolePermission(21L, List.of(88L)));
    assertThrows(IllegalStateException.class,
        () -> userService.updateUserRoleByRoleId(21L, List.of(11L)));
    verifyNoInteractions(roles, userRoles);
  }
}
