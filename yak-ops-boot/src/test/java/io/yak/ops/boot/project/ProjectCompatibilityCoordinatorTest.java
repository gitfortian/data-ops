package io.yak.ops.boot.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.framework.security.common.dto.dept.DeptSaveDTO;
import io.yak.framework.security.common.dto.project.ProjectSaveDTO;
import io.yak.framework.security.common.vo.project.ProjectVO;
import io.yak.framework.security.common.vo.user.UserBriefVO;
import io.yak.framework.security.service.DeptService;
import io.yak.framework.security.service.ProjectService;
import io.yak.framework.security.service.UserService;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ProjectCompatibilityCoordinatorTest {

  @Test
  void createsRootDepartmentAndKeepsOwnerOutOfNormalMembers() {
    ProjectSpaceProperties properties = new ProjectSpaceProperties();
    ProjectService projectService = mock(ProjectService.class);
    UserService userService = mock(UserService.class);
    DeptService deptService = mock(DeptService.class);

    UserBriefVO owner = new UserBriefVO();
    owner.setId(1L);
    owner.setUserName("root");
    owner.setDeptId(null);

    UserBriefVO member = new UserBriefVO();
    member.setId(2L);
    member.setUserName("member");

    when(projectService.getProjectBriefList()).thenReturn(Collections.emptyList());
    when(userService.getUserBriefByUsername("root")).thenReturn(owner);
    when(userService.getAllUserBriefList()).thenReturn(List.of(owner, member, owner));
    when(deptService.getDeptIdListByParentIdAndDeptName(0L, "默认部门"))
        .thenReturn(Collections.emptyList(), List.of(7L));

    ProjectVO created = mock(ProjectVO.class);
    when(created.getId()).thenReturn(11L);
    when(projectService.createProject(any(ProjectSaveDTO.class), eq("root"))).thenReturn(created);

    ProjectCompatibilityCoordinator coordinator =
        new ProjectCompatibilityCoordinator(properties, projectService, userService, deptService);

    assertThat(coordinator.ensureRequiredDefaultProject()).isEqualTo(11L);

    ArgumentCaptor<DeptSaveDTO> departmentCaptor = ArgumentCaptor.forClass(DeptSaveDTO.class);
    verify(deptService).createDept(departmentCaptor.capture());
    assertThat(departmentCaptor.getValue().getDeptName()).isEqualTo("默认部门");
    assertThat(departmentCaptor.getValue().getParentId()).isZero();

    ArgumentCaptor<ProjectSaveDTO> projectCaptor = ArgumentCaptor.forClass(ProjectSaveDTO.class);
    verify(projectService).createProject(projectCaptor.capture(), eq("root"));
    assertThat(projectCaptor.getValue().getProjectName()).isEqualTo("默认空间");
    assertThat(projectCaptor.getValue().getDeptId()).isEqualTo(7L);
    assertThat(projectCaptor.getValue().getOwnerIdList()).containsExactly(1L);
    assertThat(projectCaptor.getValue().getUserIdList()).containsExactly(2L);
  }
}
