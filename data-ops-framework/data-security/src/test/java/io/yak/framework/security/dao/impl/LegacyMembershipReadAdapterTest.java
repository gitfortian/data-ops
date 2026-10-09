package io.yak.framework.security.dao.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import io.yak.framework.security.common.dto.user.UserProjectDTO;
import io.yak.framework.security.common.entity.UserProject;
import io.yak.framework.security.common.entity.UserRole;
import io.yak.framework.security.common.po.UserProjectPO;
import io.yak.framework.security.common.po.UserRolePO;
import io.yak.framework.security.dao.UserProjectDao;
import io.yak.framework.security.dao.UserRoleDao;
import io.yak.framework.security.dao.mapper.UserProjectMapper;
import io.yak.framework.security.dao.mapper.UserRoleMapper;
import io.yak.framework.security.service.PermissionCache;
import io.yak.framework.security.service.impl.UserProjectServiceImpl;
import io.yak.framework.security.service.impl.UserRoleServiceImpl;
import io.yak.ops.platform.security.port.UserProjectCriteria;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Real legacy DTO/PO adapters exercised without changing MyBatis SQL or service constructors. */
class LegacyMembershipReadAdapterTest {

  @Test
  void projectCriteriaPreservesAllFiveLegacyQueryFieldsAndReturnsRelationBean() {
    UserProjectMapper mapper = mock(UserProjectMapper.class);
    UserProjectPO row = new UserProjectPO();
    row.setUserId(10L);
    row.setUserType(1);
    row.setProjectId(77L);
    when(mapper.selectList(any(Wrapper.class))).thenReturn(List.of(row));
    var dao = new UserProjectDaoImpl(mapper);
    UserProjectCriteria criteria = new UserProjectCriteria(5L, 10L, 1, 77L, false);
    List<UserProject> members = dao.selectMembershipsByCriteria(criteria);
    assertEquals(1, members.size());
    assertEquals(Long.valueOf(10), members.get(0).getUserId());
    assertEquals(Integer.valueOf(1), members.get(0).getUserType());
    assertEquals(Long.valueOf(77), members.get(0).getProjectId());
    verify(mapper).selectList(any(Wrapper.class));
  }

  @Test
  void projectServiceMapsItsExistingDtoToPlatformCriteria() {
    UserProjectDao dao = mock(UserProjectDao.class);
    var service = new UserProjectServiceImpl(dao, mock(PermissionCache.class));
    UserProjectDTO dto = new UserProjectDTO();
    dto.setId(5L);
    dto.setUserId(10L);
    dto.setUserType(1);
    dto.setProjectId(77L);
    dto.setIsDelete(false);
    service.lisUserProjectByUserProjectDTO(dto);
    ArgumentCaptor<UserProjectCriteria> capture = ArgumentCaptor.forClass(UserProjectCriteria.class);
    verify(dao).selectMembershipsByCriteria(capture.capture());
    assertEquals(new UserProjectCriteria(5L, 10L, 1, 77L, false), capture.getValue());
    verify(dao, never()).select(any(UserProjectDTO.class));
  }

  @Test
  void roleAdapterConvertsPoToExistingLombokRelationFields() {
    UserRoleMapper mapper = mock(UserRoleMapper.class);
    UserRolePO po = new UserRolePO();
    po.setUserId(10L);
    po.setRoleId(19L);
    when(mapper.selectList(any(Wrapper.class))).thenReturn(List.of(po));
    var dao = new UserRoleDaoImpl(mapper);
    assertEquals(List.of(new UserRole(10L, 19L)), dao.selectAssignmentsByRoleIds(List.of(19L)));
    assertEquals(List.of(new UserRole(10L, 19L)), dao.selectAssignmentsByUserIds(List.of(10L)));
    verify(mapper, times(2)).selectList(any(Wrapper.class));
  }

  @Test
  void roleServiceReadsPlatformProjectionsRatherThanOldPoDao() {
    UserRoleDao dao = mock(UserRoleDao.class);
    var service = new UserRoleServiceImpl(dao, mock(PermissionCache.class));
    when(dao.selectAssignmentsByRoleIds(List.of(19L)))
        .thenReturn(List.of(new UserRole(10L, 19L)));
    when(dao.selectAssignmentsByUserIds(List.of(10L)))
        .thenReturn(List.of(new UserRole(10L, 19L)));
    assertEquals(List.of(new UserRole(10L, 19L)), service.getByRoleIds(List.of(19L)));
    assertEquals(List.of(new UserRole(10L, 19L)), service.getRoleIdListByUserIds(List.of(10L)));
    verify(dao, never()).selectByRoleIds(anyList());
    verify(dao, never()).getRoleIdListByUserIds(anyList());
  }
}
