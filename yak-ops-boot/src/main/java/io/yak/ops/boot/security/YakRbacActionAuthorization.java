package io.yak.ops.boot.security;

import io.yak.framework.security.context.YakSecurityContext;
import io.yak.framework.security.service.RbacPermissionService;
import io.yak.ops.core.security.ActionAccessDeniedException;
import io.yak.ops.core.security.ActionAuthorization;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** Bridges the core action-authorization port to Yak Security's RBAC truth. */
@Component
@RequiredArgsConstructor
public class YakRbacActionAuthorization implements ActionAuthorization {

  private final RbacPermissionService rbacPermissionService;

  @Override
  public void requirePermission(String permissionCode) {
    if (!YakSecurityContext.isAuthenticated()) {
      throw new ActionAccessDeniedException(permissionCode);
    }
    String username = YakSecurityContext.getCurrentUsername();
    if (!StringUtils.hasText(username)
        || !rbacPermissionService.hasPermission(username, permissionCode)) {
      throw new ActionAccessDeniedException(permissionCode);
    }
  }

  @Override
  public void requirePermissionIfAuthenticated(String permissionCode) {
    if (YakSecurityContext.isAuthenticated()) {
      requirePermission(permissionCode);
    }
  }
}
