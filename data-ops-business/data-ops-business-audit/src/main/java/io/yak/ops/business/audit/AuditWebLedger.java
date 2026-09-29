package io.yak.ops.business.audit;

/**
 * 请求级“业务审计已开账”登记：Web 兜底拦截器在 preHandle beginRequest，
 * 业务侧 BusinessAuditService.start 命中时打标，afterCompletion 据此跳过兜底、防止双记。
 * 仅在 beginRequest 之后的线程内生效；非 HTTP 线程（调度/执行器）调用 start 为无操作，不留脏值。
 */
public final class AuditWebLedger {

  private static final ThreadLocal<Boolean> SDK_OPERATION_OPENED = new ThreadLocal<>();

  private AuditWebLedger() {}

  /** 请求开始：进入“可登记”状态。仅由 Web 审计拦截器调用。 */
  public static void beginRequest() {
    SDK_OPERATION_OPENED.set(Boolean.FALSE);
  }

  /** 业务 SDK 在本请求内调用过 start() 时打标。 */
  public static void markSdkOperationOpened() {
    if (SDK_OPERATION_OPENED.get() != null) {
      SDK_OPERATION_OPENED.set(Boolean.TRUE);
    }
  }

  /** 本请求是否已有业务侧审计开账（未 beginRequest 的线程返回 false）。 */
  public static boolean hasSdkOperation() {
    return Boolean.TRUE.equals(SDK_OPERATION_OPENED.get());
  }

  /** 请求结束：无论结果如何必须调用，清理线程复用残留。 */
  public static void endRequest() {
    SDK_OPERATION_OPENED.remove();
  }
}
