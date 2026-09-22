package io.yak.ops.business.mdm.exception;

import io.yak.framework.common.ErrorCode;
import io.yak.framework.common.Result;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 主数据接口异常转换:把 MdmException 的业务码原样放进响应体, 前端按 code 呈现真实原因。
 *
 * <p>刻意不加 Exception.class 兜底——那会把认证/权限等框架异常一并吞成通用文案, 保持缺席时由全局默认处理。</p>
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackages = "io.yak.ops.business.mdm.controller")
public class MdmExceptionHandler {

  @ExceptionHandler(MdmException.class)
  public Result<Void> handleMdmException(MdmException exception) {
    ErrorCode errorCode = exception.getErrorCode();
    if (errorCode == null) {
      return Result.fail(exception.getUserMessage());
    }
    return Result.fail(errorCode.getCode(), exception.getUserMessage());
  }
}
