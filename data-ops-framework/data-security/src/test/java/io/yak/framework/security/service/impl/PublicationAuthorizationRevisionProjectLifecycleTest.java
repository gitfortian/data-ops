package io.yak.framework.security.service.impl;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.yak.framework.security.dao.ProjectDao;
import io.yak.framework.security.dao.RoleDao;
import io.yak.framework.security.extend.ResourceExtend;
import io.yak.framework.security.service.DeptService;
import io.yak.framework.security.service.MessageService;
import io.yak.framework.security.service.OplogService;
import io.yak.framework.security.service.PermissionService;
import io.yak.framework.security.service.RolePermissionService;
import io.yak.framework.security.service.UserProjectService;
import io.yak.framework.security.service.UserRoleService;
import io.yak.framework.security.service.UserService;
import org.junit.jupiter.api.Test;

/** T3 write-side ordering; cross-data-source publication is still NO-GO. */
class PublicationAuthorizationRevisionProjectLifecycleTest {

  @Test
  void disablingProjectLocksProjectRevisionBeforeReadingCurrentState() {
    var dao = mock(ProjectDao.class);
    var revision = mock(PublicationAuthorizationRevisionService.class);
    var service = new ProjectServiceImpl(dao, mock(DeptService.class),
        mock(OplogService.class), mock(UserService.class),
        mock(UserProjectService.class), mock(ResourceExtend.class), revision);
    assertThrows(RuntimeException.class, () ->
        service.changeProjectStatus(701L, "reviewer"));
    var ordered = inOrder(revision, dao);
    ordered.verify(revision).advanceProjectMutation(701L);
    ordered.verify(dao).selectByProjectId(701L);
  }

  @Test
  void deletingRoleLocksGlobalRevisionBeforeQueryingRoleData() {
    var dao = mock(RoleDao.class);
    var revision = mock(PublicationAuthorizationRevisionService.class);
    var service = new RoleServiceImpl(dao, mock(PermissionService.class),
        mock(MessageService.class), mock(OplogService.class),
        mock(io.yak.framework.security.dao.UserDao.class),
        mock(RolePermissionService.class), mock(UserRoleService.class), revision);
    assertThrows(RuntimeException.class, () -> service.deleteRoleByRoleId(21L, "reviewer"));
    var ordered = inOrder(revision, dao);
    ordered.verify(revision).advanceGlobalMutation();
    ordered.verify(dao).selectByRoleId(21L);
  }
}
