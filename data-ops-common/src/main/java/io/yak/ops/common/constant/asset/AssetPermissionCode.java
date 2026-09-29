package io.yak.ops.common.constant.asset;

/** 数据资产权限码(菜单注册见 yak-security V2032)。 */
public final class AssetPermissionCode {
  public static final String READ = "data-asset:read";
  public static final String CREATE = "data-asset:create";
  public static final String UPDATE = "data-asset:update";
  public static final String DELETE = "data-asset:delete";

  private AssetPermissionCode() {}
}
