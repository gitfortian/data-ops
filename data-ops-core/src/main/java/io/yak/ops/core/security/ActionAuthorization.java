package io.yak.ops.core.security;

/**
 * Application-level action authorization port.
 *
 * <p>Business modules depend on this contract only. The security implementation belongs to the
 * boot/infrastructure layer so RBAC truth remains owned by the platform security subsystem.
 */
public interface ActionAuthorization {

  /** Requires an authenticated subject with the given permission. */
  void requirePermission(String permissionCode);

  /**
   * Requires the permission only when a platform subject is authenticated.
   *
   * <p>This is intended for execution surfaces that also support non-platform credentials, such as
   * public Data Service API-key invocation.
   */
  void requirePermissionIfAuthenticated(String permissionCode);
}
