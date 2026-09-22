package io.yak.ops.business.alert.exception;

import io.yak.framework.common.BusinessException;
import io.yak.framework.common.ErrorCode;

/** 告警服务业务异常。 */
public class AlertException extends BusinessException {

  private static final long serialVersionUID = 1L;

  private final ErrorCode actualErrorCode;
  private final String userMessage;

  public AlertException(ErrorCode errorCode) {
    super(errorCode);
    this.actualErrorCode = errorCode;
    this.userMessage = errorCode == null ? null : errorCode.getMessage();
  }

  public AlertException(ErrorCode errorCode, String detail) {
    this(errorCode, detail, null);
  }

  public AlertException(ErrorCode errorCode, String detail, Throwable cause) {
    super(buildMessage(errorCode, detail), cause);
    this.actualErrorCode = errorCode;
    this.userMessage = buildMessage(errorCode, detail);
  }

  @Override
  public ErrorCode getErrorCode() {
    return actualErrorCode;
  }

  public String getUserMessage() {
    return userMessage;
  }

  private static String buildMessage(ErrorCode errorCode, String detail) {
    String base = errorCode == null ? "告警操作失败" : errorCode.getMessage();
    return detail == null || detail.trim().isEmpty() ? base : base + "：" + detail.trim();
  }
}
