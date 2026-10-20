package io.yak.framework.security.service.impl;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.yak.framework.security.dao.UserProjectDao;
import io.yak.framework.security.service.PermissionCache;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * T3 development scope: project member/owner mutations must acquire the
 * Security-owned revision row before touching membership relations.
 * This does not establish a cross-datasource Modeling commit fence.
 */
class PublicationAuthorizationRevisionLockOrderTest {
  @Test
  void removingAllProjectMembersLocksSecurityRevisionBeforeRepositoryRead() {
    var dao = mock(UserProjectDao.class);
    var revision = mock(PublicationAuthorizationRevisionService.class);
    var service = new UserProjectServiceImpl(dao, mock(PermissionCache.class), revision);
    when(dao.selectUserIdListByProjectId(701L, 0)).thenReturn(List.of(11L));
    service.deleteUserProjectByProjectId(701L);
    var ordered = inOrder(revision, dao);
    ordered.verify(revision).advanceProjectMutation(701L);
    ordered.verify(dao).selectUserIdListByProjectId(701L, 0);
  }

  @Test
  void changingProjectOwnerMustLockRevisionBeforeReadingOrRemovingRelations() {
    var dao = mock(UserProjectDao.class);
    var revision = mock(PublicationAuthorizationRevisionService.class);
    var service = new UserProjectServiceImpl(dao, mock(PermissionCache.class), revision);
    when(dao.selectUserIdListByProjectId(701L, 1)).thenReturn(List.of(11L));
    service.deleteOwnerProjectByProjectId(701L);
    var ordered = inOrder(revision, dao);
    ordered.verify(revision).advanceProjectMutation(701L);
    ordered.verify(dao).selectUserIdListByProjectId(701L, 1);
  }

  @Test
  void unavailableAuthorizationRevisionCannotChangeMembership() {
    var dao = mock(UserProjectDao.class);
    var revision = mock(PublicationAuthorizationRevisionService.class);
    org.mockito.Mockito.doThrow(new IllegalStateException("Security transaction unavailable"))
        .when(revision).advanceProjectMutation(701L);
    var service = new UserProjectServiceImpl(dao, mock(PermissionCache.class), revision);
    assertThrows(IllegalStateException.class, () ->
        service.saveUserProject(701L, List.of(11L)));
    assertThrows(IllegalStateException.class, () ->
        service.delOwnerProject(701L, List.of(11L)));
    verifyNoInteractions(dao);
  }
}
