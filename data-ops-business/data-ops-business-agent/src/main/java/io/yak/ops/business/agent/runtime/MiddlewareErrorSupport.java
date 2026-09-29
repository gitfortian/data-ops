package io.yak.ops.business.agent.runtime;

import java.util.concurrent.TimeoutException;

/** 中间件共享的错误分类支持：状态码提取、根因剥离、错误码归类与文本预览。 */
final class MiddlewareErrorSupport {

  /** 分类错误码（与 yak_agent_step 表注释保持一致）。 */
  static final String CODE_TIMEOUT = "TIMEOUT";
  static final String CODE_USER_ERROR = "USER_ERROR";
  static final String CODE_PROVIDER_ERROR = "PROVIDER_ERROR";

  private MiddlewareErrorSupport() {}

  static boolean isUserSideStatus(Integer status) {
    return status != null && (status == 401 || status == 403 || status == 429);
  }

  static boolean isRetryableStatus(Integer status) {
    if (status == null) {
      return true;
    }
    return !isUserSideStatus(status) && (status >= 500 || status == 408);
  }

  static Integer statusCodeOf(Throwable error) {
    Throwable current = error;
    for (int depth = 0; current != null && depth < 8; depth++, current = current.getCause()) {
      if (current instanceof io.agentscope.core.model.ModelHttpException http
          && http.getStatusCode() != null) {
        return http.getStatusCode();
      }
    }
    return null;
  }

  static Throwable rootCause(Throwable error) {
    // 剥离 reactor 重试包装，取业务根因
    Throwable current = error;
    for (int depth = 0; current.getCause() != null && depth < 8; depth++) {
      current = current.getCause();
    }
    return current;
  }

  static boolean isTimeout(Throwable error) {
    return rootCause(error) instanceof TimeoutException;
  }

  static String classify(Throwable error) {
    if (isTimeout(error)) {
      return CODE_TIMEOUT;
    }
    if (isUserSideStatus(statusCodeOf(error))) {
      return CODE_USER_ERROR;
    }
    return CODE_PROVIDER_ERROR;
  }

  static long elapsed(long startNanos) {
    return (System.nanoTime() - startNanos) / 1_000_000L;
  }

  static String preview(Throwable error) {
    String message = rootCause(error).getMessage();
    if (message == null || message.isBlank()) {
      return rootCause(error).getClass().getSimpleName();
    }
    return message.length() <= 300 ? message : message.substring(0, 300);
  }
}
