package io.yak.ops.common.constant.metadata;

/** 元数据中心权限码(菜单注册见 yak-security V2034；V2033 已被审批中心占用)。 */
public final class MetadataPermissionCode {
  public static final String READ = "data-metadata:read";
  public static final String CREATE = "data-metadata:create";
  public static final String UPDATE = "data-metadata:update";
  public static final String DELETE = "data-metadata:delete";

  private MetadataPermissionCode() {}
}
