package io.yak.ops.business.alert.exception;

import io.yak.framework.common.ErrorCode;
import io.yak.framework.common.Result;
import io.yak.ops.business.alert.controller.v1.AlertController;
import io.yak.ops.common.enums.alert.AlertErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 告警管理接口异常转换。 */
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackageClasses = AlertController.class)
public class AlertExceptionHandler {

  @ExceptionHandler(AlertException.class)
  public Result<Void> handleAlertException(AlertException exception) {
    if (exception.getErrorCode() == null) {
      return Result.fail(exception.getUserMessage());
    }
    return Result.fail(exception.getErrorCode().getCode(), exception.getUserMessage());
  }

  @ExceptionHandler({
    MethodArgumentNotValidException.class,
    BindException.class,
    HttpMessageNotReadableException.class
  })
  public Result<Void> handleInvalidRequest(Exception exception) {
    return Result.buildParamIllegal(resolveValidationMessage(exception));
  }

  @ExceptionHandler(Exception.class)
  public Result<Void> handleUnexpectedException(Exception exception) {
    log.error("Unexpected alert management error", exception);
    return Result.fail(
        AlertErrorCode.SEND_FAILED.getCode(), "告警操作失败，请稍后重试");
  }

  private String resolveValidationMessage(Exception exception) {
    BindingResult bindingResult = null;
    if (exception instanceof MethodArgumentNotValidException) {
      MethodArgumentNotValidException validException =
          (MethodArgumentNotValidException) exception;
      bindingResult = validException.getBindingResult();
    } else if (exception instanceof BindException) {
      BindException bindException = (BindException) exception;
      bindingResult = bindException.getBindingResult();
    }
    if (bindingResult != null && bindingResult.getFieldError() != null) {
      return bindingResult.getFieldError().getDefaultMessage();
    }
    return "请求参数格式不正确";
  }
}
