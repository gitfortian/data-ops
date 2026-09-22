package io.yak.ops.common.constant.approval;

/** 通用审批流权限码(菜单注册见 yak-security V2033)。 */
public final class ApprovalPermissionCode {
  public static final String READ = "data-approval:read";
  public static final String CREATE = "data-approval:create";
  public static final String APPROVE = "data-approval:approve";
  public static final String MANAGE = "data-approval:manage";

  private ApprovalPermissionCode() {}
}
