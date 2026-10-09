package io.yak.framework.security.web;

import io.yak.framework.security.authentication.AuthenticationManager;
import io.yak.framework.security.common.entity.user.User;
import io.yak.framework.security.config.YakSecurityProperties;
import io.yak.framework.security.extend.CurrentUserProvider;
import io.yak.framework.security.extend.PasswordEncoder;
import io.yak.framework.security.extend.impl.DefaultLoginExtendImpl;
import io.yak.framework.security.service.RbacPermissionService;
import io.yak.framework.security.service.UserService;
import io.yak.framework.security.service.impl.LoginServiceImpl;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Full Spring MVC dispatch through the real Starter LoginService and Security
 * interceptor. Verifies transport-level 401/403 semantics, not just status
 * constants returned by isolated handlers.
 */
class SecurityHttpAuthorizationBoundaryTest {

  private static final String PATH = "/a82-security-authorization-check";

  @Test
  void anonymousRequestReceivesActualHttp401WithoutPermissionLookup() throws Exception {
    AuthenticationManager authentication = mock(AuthenticationManager.class);
    RbacPermissionService permission = mock(RbacPermissionService.class);
    MockMvc mvc = mvc(authentication, mock(UserService.class), permission,
        mock(CurrentUserProvider.class));

    mvc.perform(get(PATH))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.code").exists());

    verify(permission, never()).hasPermission(any(), any());
  }

  @Test
  void authenticatedUserMissingPermissionReceivesActualHttp403() throws Exception {
    AuthenticationManager authentication = mock(AuthenticationManager.class);
    UserService users = mock(UserService.class);
    RbacPermissionService permissions = mock(RbacPermissionService.class);
    CurrentUserProvider currentUser = mock(CurrentUserProvider.class);
    loggedIn(authentication, users);
    when(currentUser.getCurrentUser(any(HttpServletRequest.class))).thenReturn("yak");

    mvc(authentication, users, permissions, currentUser)
        .perform(get(PATH))
        .andExpect(status().isForbidden())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.code").value(3001));

    verify(permissions).hasPermission("yak", "security:project:read");
  }

  @Test
  void authorizedRequestReachesControllerWithHttp200() throws Exception {
    AuthenticationManager authentication = mock(AuthenticationManager.class);
    UserService users = mock(UserService.class);
    RbacPermissionService permissions = mock(RbacPermissionService.class);
    CurrentUserProvider currentUser = mock(CurrentUserProvider.class);
    loggedIn(authentication, users);
    when(currentUser.getCurrentUser(any(HttpServletRequest.class))).thenReturn("yak");
    when(permissions.hasPermission("yak", "security:project:read")).thenReturn(true);

    mvc(authentication, users, permissions, currentUser)
        .perform(get(PATH))
        .andExpect(status().isOk())
        .andExpect(content().string("allowed"));
  }

  @Test
  void revokedOrMismatchedSessionFailsClosedWithHttp401() throws Exception {
    AuthenticationManager authentication = mock(AuthenticationManager.class);
    UserService users = mock(UserService.class);
    when(authentication.isLogin()).thenReturn(true);
    when(authentication.getLoginUserId()).thenReturn(42L);
    when(authentication.getLoginUsername()).thenReturn("yak");
    User user = mock(User.class);
    when(user.getId()).thenReturn(43L);
    when(users.getUserByUsername("yak")).thenReturn(user);

    mvc(authentication, users, mock(RbacPermissionService.class),
        mock(CurrentUserProvider.class))
        .perform(get(PATH))
        .andExpect(status().isUnauthorized());
    verify(authentication).logout();
  }

  private static void loggedIn(AuthenticationManager authentication, UserService users) {
    when(authentication.isLogin()).thenReturn(true);
    when(authentication.getLoginUserId()).thenReturn(42L);
    when(authentication.getLoginUsername()).thenReturn("yak");
    User user = mock(User.class);
    when(user.getId()).thenReturn(42L);
    when(users.getUserByUsername("yak")).thenReturn(user);
  }

  private static MockMvc mvc(AuthenticationManager authentication, UserService users,
      RbacPermissionService permissions, CurrentUserProvider currentUser) {
    YakSecurityProperties properties = new YakSecurityProperties();
    DefaultLoginExtendImpl login = new DefaultLoginExtendImpl(users,
        mock(PasswordEncoder.class), properties, authentication);
    YakAuthenticationInterceptor interceptor = new YakAuthenticationInterceptor(
        new LoginServiceImpl(login), properties, permissions, currentUser);
    return MockMvcBuilders.standaloneSetup(new ProtectedController())
        .addInterceptors(interceptor)
        .build();
  }

  @RestController
  static class ProtectedController {
    @RequiresPermission("security:project:read")
    @GetMapping(PATH)
    public String protectedGet() {
      return "allowed";
    }
  }
}
