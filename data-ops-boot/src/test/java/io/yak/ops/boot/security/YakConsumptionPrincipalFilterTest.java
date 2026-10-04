package io.yak.ops.boot.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.yak.framework.security.common.vo.role.RoleVO;
import io.yak.framework.security.context.CurrentUser;
import io.yak.framework.security.service.RoleService;
import jakarta.servlet.http.HttpServletRequest;
import java.security.Principal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

class YakConsumptionPrincipalFilterTest {
  private final CurrentUser user = mock(CurrentUser.class);
  private final RoleService roles = mock(RoleService.class);
  private final YakConsumptionPrincipalFilter filter = new YakConsumptionPrincipalFilter(user, roles);

  @Test
  void projectsLateBoundIdentityAndOwningRoleCodesInsteadOfClientHeaders() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/datasets/1");
    request.addHeader("X-User", "forged");
    filter.doFilter(request, new MockHttpServletResponse(), (wrapped, response) -> {
      assertThat(((HttpServletRequest) wrapped).getUserPrincipal()).isNull();
      verifiedUser();
      var principal = (Authentication) ((HttpServletRequest) wrapped).getUserPrincipal();
      assertThat(principal.getName()).isEqualTo("alice");
      assertThat(principal.getCredentials()).isNull();
      assertThat(principal.getAuthorities()).extracting("authority").containsExactly("ANALYST");
    });
  }

  @Test
  void anonymousConsoleRequestCannotBorrowServletPrincipal() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/consumption/products/DATASET:1");
    request.setUserPrincipal(() -> "forged");
    filter.doFilter(request, new MockHttpServletResponse(), (wrapped, response) ->
        assertThat(((HttpServletRequest) wrapped).getUserPrincipal()).isNull());
    verifyNoInteractions(roles);
  }

  @Test
  void missingRoleTruthFailsInsteadOfDroppingRolePolicies() {
    verifiedUser();
    when(roles.getRoleDetailByRoleId(9L)).thenReturn(null);
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/datasets/1/query");
    assertThatThrownBy(() -> filter.doFilter(request, new MockHttpServletResponse(), (wrapped, response) ->
        ((HttpServletRequest) wrapped).getUserPrincipal()))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void publicInvocationKeepsItsOriginalRequestAndAuthenticationPlane() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/data-service/invoke/orders");
    filter.doFilter(request, new MockHttpServletResponse(), (wrapped, response) ->
        assertThat(wrapped).isSameAs(request));
    verifyNoInteractions(user, roles);
  }

  @Test
  void mvcPrincipalArgumentReceivesVerifiedUsername() throws Exception {
    verifiedUser();
    MockMvcBuilders.standaloneSetup(new PrincipalEndpoint()).addFilters(filter).build()
        .perform(get("/api/v1/consumption/probe"))
        .andExpect(status().isOk()).andExpect(content().string("alice"));
  }

  private void verifiedUser() {
    when(user.isAuthenticated()).thenReturn(true);
    when(user.getUserId()).thenReturn(42L);
    when(user.getUsername()).thenReturn("alice");
    when(user.getRoleIds()).thenReturn(List.of(9L));
    RoleVO role = new RoleVO();
    role.setId(9L);
    role.setRoleCode("ANALYST");
    when(roles.getRoleDetailByRoleId(9L)).thenReturn(role);
  }

  @RestController
  static class PrincipalEndpoint {
    @GetMapping("/api/v1/consumption/probe")
    String probe(Principal principal) {
      return principal.getName();
    }
  }
}
