package io.yak.framework.security.service.impl;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.framework.security.dao.UserProjectDao;
import io.yak.framework.security.service.PermissionCache;
import java.util.List;
import org.junit.jupiter.api.Test;

class UserProjectServiceImplTest {

  @Test
  void invalidatesUsersWhenProjectMembershipIsAdded() {
    UserProjectDao userProjectDao = mock(UserProjectDao.class);
    PermissionCache permissionCache = mock(PermissionCache.class);
    UserProjectServiceImpl service = new UserProjectServiceImpl(
            userProjectDao,
            permissionCache);

    service.saveUserProject(9L, List.of(7L, 8L, 7L));

    verify(userProjectDao).insertBatch(anyList());
    verify(permissionCache).invalidateUser(7L);
    verify(permissionCache).invalidateUser(8L);
  }

  @Test
  void invalidatesExistingUsersWhenProjectMembershipIsCleared() {
    UserProjectDao userProjectDao = mock(UserProjectDao.class);
    PermissionCache permissionCache = mock(PermissionCache.class);
    when(userProjectDao.selectUserIdListByProjectId(9L, 0))
            .thenReturn(List.of(7L, 8L));
    UserProjectServiceImpl service = new UserProjectServiceImpl(
            userProjectDao,
            permissionCache);

    service.deleteUserProjectByProjectId(9L);

    verify(userProjectDao).deleteByProjectIdAndUserType(9L, 0);
    verify(permissionCache).invalidateUser(7L);
    verify(permissionCache).invalidateUser(8L);
  }

  @Test
  void migratedProjectModelDistinguishesOwnerFromOrdinaryMembership() {
    UserProjectDao dao = mock(UserProjectDao.class);
    PermissionCache cache = mock(PermissionCache.class);
    UserProjectServiceImpl service = new UserProjectServiceImpl(dao, cache);

    service.saveOwnerProject(9L, List.of(7L, 7L, 8L));

    @SuppressWarnings("unchecked")
    org.mockito.ArgumentCaptor<List<io.yak.framework.security.common.entity.UserProject>> captured =
        org.mockito.ArgumentCaptor.forClass(List.class);
    verify(dao).insertBatch(captured.capture());
    org.junit.jupiter.api.Assertions.assertEquals(2, captured.getValue().size());
    org.junit.jupiter.api.Assertions.assertEquals(List.of(7L, 8L),
        captured.getValue().stream().map(
            io.yak.framework.security.common.entity.UserProject::getUserId).toList());
    captured.getValue().forEach(member -> {
      org.junit.jupiter.api.Assertions.assertEquals(Long.valueOf(9L), member.getProjectId());
      org.junit.jupiter.api.Assertions.assertEquals(Integer.valueOf(1), member.getUserType());
    });
    verify(cache).invalidateUser(7L);
    verify(cache).invalidateUser(8L);
  }

  @Test
  void deletingProjectOwnersUsesOwnerTypeAndInvalidatesOnlyAffectedUsers() {
    UserProjectDao dao = mock(UserProjectDao.class);
    PermissionCache cache = mock(PermissionCache.class);
    when(dao.selectUserIdListByProjectId(9L, 1)).thenReturn(List.of(12L));
    new UserProjectServiceImpl(dao, cache).deleteOwnerProjectByProjectId(9L);

    verify(dao).deleteByProjectIdAndUserType(9L, 1);
    verify(cache).invalidateUser(12L);
    org.mockito.Mockito.verify(dao, org.mockito.Mockito.never())
        .deleteByProjectIdAndUserType(9L, 0);
  }

}
