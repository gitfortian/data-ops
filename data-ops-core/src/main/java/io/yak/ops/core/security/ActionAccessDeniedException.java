package io.yak.ops.core.security;

/** Raised when the current platform subject is not allowed to execute an action. */
public class ActionAccessDeniedException extends RuntimeException {

  private final String permissionCode;

  public ActionAccessDeniedException(String permissionCode) {
    super("缺少执行权限：" + permissionCode);
    this.permissionCode = permissionCode;
  }

  public String getPermissionCode() {
    return permissionCode;
  }
}
