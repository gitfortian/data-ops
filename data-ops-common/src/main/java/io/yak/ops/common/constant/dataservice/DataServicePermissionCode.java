package io.yak.ops.common.constant.dataservice;

/** Permission codes for the Data Service management plane. */
public final class DataServicePermissionCode {

  public static final String READ = "data-service:read";
  public static final String PUBLISH = "data-service:publish";
  public static final String MANAGE = "data-service:manage";
  public static final String DELETE = "data-service:delete";
  public static final String ACCESS = "data-service:access";
  public static final String RUNTIME = "data-service:runtime";
  public static final String OBSERVE = "data-service:observe";

  private DataServicePermissionCode() {}
}
