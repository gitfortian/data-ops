package io.yak.framework.security.service.impl;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import io.yak.framework.security.dao.PermissionDao;
import io.yak.framework.security.dao.mapper.MenuMapper;
import io.yak.framework.security.dao.mapper.RoleMenuMapper;
import io.yak.framework.security.service.PermissionCache;
import io.yak.framework.security.service.UserRoleService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

/**
 * Direct menu-only grants also change effective user permissions, even when
 * no ordinary role-permission row changes.
 */
class PublicationAuthorizationRevisionMenuLockOrderTest {
  @Test
  void clearingRoleMenusLocksGlobalRevisionBeforeDeletingRelations() {
    var mapper = mock(RoleMenuMapper.class);
    var revision = mock(PublicationAuthorizationRevisionService.class);
    var service = new MenuAuthorizationService(
        mock(MenuMapper.class), mapper, mock(PermissionDao.class),
        mock(UserRoleService.class), mock(PermissionCache.class), revision);
    service.updateRoleMenus(21L, List.of());
    var ordered = inOrder(revision, mapper);
    ordered.verify(revision).advanceGlobalMutation();
    ordered.verify(mapper).delete(ArgumentMatchers.any());
  }

  @Test
  void unavailableRevisionCannotDeleteRoleMenuGrants() {
    var mapper = mock(RoleMenuMapper.class);
    var revision = mock(PublicationAuthorizationRevisionService.class);
    org.mockito.Mockito.doThrow(new IllegalStateException("Security unavailable"))
        .when(revision).advanceGlobalMutation();
    var service = new MenuAuthorizationService(
        mock(MenuMapper.class), mapper, mock(PermissionDao.class),
        mock(UserRoleService.class), mock(PermissionCache.class), revision);
    assertThrows(IllegalStateException.class, () -> service.deleteRoleMenus(21L));
    verifyNoInteractions(mapper);
  }
}
