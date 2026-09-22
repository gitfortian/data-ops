package io.yak.ops.common.constant.lifecycle;

/** 数据生命周期权限码(菜单注册见 yak-security V2031)。 */
public final class LifecyclePermissionCode {
  public static final String READ = "data-lifecycle:read";
  public static final String CREATE = "data-lifecycle:create";
  public static final String UPDATE = "data-lifecycle:update";
  public static final String DELETE = "data-lifecycle:delete";

  private LifecyclePermissionCode() {}
}
