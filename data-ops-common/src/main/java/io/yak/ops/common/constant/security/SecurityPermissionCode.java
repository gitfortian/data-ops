package io.yak.ops.common.constant.security;

/** Permission codes for the data-security module (classification/access/masking/audit/compliance). */
public final class SecurityPermissionCode {

  public static final String READ = "data-security:read";
  public static final String CREATE = "data-security:create";
  public static final String UPDATE = "data-security:update";
  public static final String DELETE = "data-security:delete";

  private SecurityPermissionCode() {}
}
