package io.yak.framework.security.controller.v1;

import io.yak.framework.security.common.constant.SecurityPermissionCode;
import io.yak.framework.security.common.vo.project.ProjectBriefVO;
import io.yak.framework.security.common.vo.role.RoleBriefVO;
import io.yak.framework.security.common.vo.user.CurrentUserVO;
import io.yak.framework.security.common.vo.user.UserBriefVO;
import io.yak.framework.security.context.CurrentUser;
import io.yak.framework.security.service.LoginService;
import io.yak.framework.security.service.RoleService;
import io.yak.framework.security.service.UserService;
import io.yak.framework.security.service.impl.CurrentUserProjectResolver;
import io.yak.framework.security.service.impl.UserMenuGrantService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LoginControllerCurrentUserTest {

  @Test
  void rootCurrentIdentityIncludesRolesAndSwitchableWorkspaces() {
    LoginService loginService = mock(LoginService.class);
    UserService userService = mock(UserService.class);
    RoleService roleService = mock(RoleService.class);
    CurrentUserProjectResolver projectResolver =
            mock(CurrentUserProjectResolver.class);
    CurrentUser currentUser = mock(CurrentUser.class);
    @SuppressWarnings("unchecked")
    ObjectProvider<UserMenuGrantService> menuGrantProvider =
            mock(ObjectProvider.class);

    RoleBriefVO role = new RoleBriefVO();
    role.setId(1L);
    role.setRoleName("系统管理员");

    ProjectBriefVO project = new ProjectBriefVO();
    project.setId(9L);
    project.setProjectCode("p9");
    project.setProjectName("默认空间");

    when(currentUser.isAuthenticated()).thenReturn(true);
    when(currentUser.getUsername()).thenReturn("root");
    UserBriefVO brief = new UserBriefVO();
    brief.setId(7L);
    brief.setUserName("root");
    when(userService.getUserBriefByUsername("root"))
            .thenReturn(brief);
    when(roleService.getRoleBriefListByUserId(7L))
            .thenReturn(List.of(role));
    when(currentUser.getPermissionCodes()).thenReturn(Set.of(
            SecurityPermissionCode.ROOT));
    when(currentUser.getMenuCodes()).thenReturn(List.of());
    when(currentUser.getProjectIds()).thenReturn(Set.of(9L));
    when(projectResolver.resolve(
            any(CurrentUserVO.class), eq(Set.of(9L))))
            .thenReturn(List.of(project));
    when(menuGrantProvider.getIfAvailable()).thenReturn(null);

    LoginController controller = new LoginController(
            loginService,
            userService,
            roleService,
            currentUser,
            menuGrantProvider,
            projectResolver);

    CurrentUserVO actual = controller.current().getData();

    assertThat(actual.getRoleList())
            .extracting(RoleBriefVO::getRoleName)
            .containsExactly("系统管理员");
    assertThat(actual.getProjectList())
            .extracting(ProjectBriefVO::getProjectName)
            .containsExactly("默认空间");
    assertThat(actual.getPermissionCodes())
            .containsExactly(SecurityPermissionCode.ROOT);
    verify(projectResolver).resolve(actual, Set.of(9L));
  }
}
