package io.yak.ops.business.asset.exception;

import io.yak.framework.common.BusinessException;
import io.yak.framework.common.ErrorCode;

/** 数据资产模块业务异常。 */
public class AssetException extends BusinessException {

  private static final long serialVersionUID = 1L;

  private final ErrorCode actualErrorCode;
  private final String userMessage;

  public AssetException(ErrorCode errorCode) {
    super(errorCode);
    this.actualErrorCode = errorCode;
    this.userMessage = errorCode == null ? null : errorCode.getMessage();
  }

  public AssetException(ErrorCode errorCode, String detail) {
    this(errorCode, detail, null);
  }

  public AssetException(ErrorCode errorCode, String detail, Throwable cause) {
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
    String base = errorCode == null ? "资产操作失败" : errorCode.getMessage();
    return detail == null || detail.trim().isEmpty() ? base : base + "：" + detail.trim();
  }
}
