package io.yak.ops.business.audit;

/** 跨模块共享的审计操作类型码与来源常量；模块专属码遵循 MODULE_ACTION 命名（语义补齐票逐模块登记）。 */
public final class AuditOperationTypes {

  /** Web 兜底拦截器产生的记录来源。 */
  public static final String SOURCE_WEB = "WEB";

  public static final String AUTH_LOGIN_SUCCESS = "AUTH_LOGIN_SUCCESS";
  public static final String AUTH_LOGIN_FAILED = "AUTH_LOGIN_FAILED";
  public static final String AUTH_LOGOUT = "AUTH_LOGOUT";

  private AuditOperationTypes() {}
}
