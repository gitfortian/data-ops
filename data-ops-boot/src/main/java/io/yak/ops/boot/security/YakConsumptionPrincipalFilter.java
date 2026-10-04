package io.yak.ops.boot.security;

import io.yak.framework.security.context.CurrentUser;
import io.yak.framework.security.service.RoleService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.Principal;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Projects Yak's verified console identity into MVC Principal for the two consumption boundaries. */
@Component
@RequiredArgsConstructor
public class YakConsumptionPrincipalFilter extends OncePerRequestFilter {
  private final CurrentUser currentUser;
  private final RoleService roleService;

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String path = request.getRequestURI().substring(request.getContextPath().length());
    return !inside(path, "/api/v1/datasets") && !inside(path, "/api/v1/consumption");
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    chain.doFilter(new HttpServletRequestWrapper(request) {
      private Principal resolved;

      @Override
      public Principal getUserPrincipal() {
        // Resolve lazily: Yak's context filter may run later in the servlet filter chain.
        if (!currentUser.isAuthenticated()) return null;
        if (resolved != null) return resolved;
        String username = currentUser.getUsername();
        if (currentUser.getUserId() == null || username == null || username.isBlank()) {
          throw new IllegalStateException("Verified consumption principal is incomplete");
        }
        var authorities = Objects.requireNonNull(currentUser.getRoleIds(), "Verified roles unavailable")
            .stream().distinct().map(id -> {
              var role = roleService.getRoleDetailByRoleId(Objects.requireNonNull(id));
              if (role == null || !id.equals(role.getId()) || role.getRoleCode() == null
                  || role.getRoleCode().isBlank()) {
                throw new IllegalStateException("Verified consumption role is unavailable");
              }
              return new SimpleGrantedAuthority(role.getRoleCode());
            }).toList();
        resolved = UsernamePasswordAuthenticationToken.authenticated(username, null, authorities);
        return resolved;
      }
    }, response);
  }

  private static boolean inside(String path, String prefix) {
    return path.equals(prefix) || path.startsWith(prefix + "/");
  }
}
