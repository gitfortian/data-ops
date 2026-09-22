package io.yak.ops.business.metadata.exception;

import io.yak.framework.common.ErrorCode;
import io.yak.framework.common.Result;
import io.yak.framework.security.common.enums.ResultCode;
import io.yak.framework.security.exception.YakSecurityException;
import io.yak.ops.common.enums.metadata.MetadataErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 元数据接口异常转换。 */
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackages = "io.yak.ops.business.metadata.controller")
public class MetadataExceptionHandler {

  @ExceptionHandler(YakSecurityException.class)
  public ResponseEntity<Result<Void>> handleSecurityException(YakSecurityException exception) {
    ErrorCode errorCode = exception.getErrorCode();
    Result<Void> body =
        errorCode == null
            ? Result.fail(exception)
            : Result.fail(errorCode.getCode(), errorCode.getMessage());
    HttpStatus status =
        errorCode == ResultCode.NO_PERMISSION ? HttpStatus.FORBIDDEN : HttpStatus.UNAUTHORIZED;
    return ResponseEntity.status(status).body(body);
  }

  @ExceptionHandler(MetadataException.class)
  public Result<Void> handleMetadataException(MetadataException exception) {
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

  @ExceptionHandler(DataIntegrityViolationException.class)
  public Result<Void> handleDataIntegrityViolation(DataIntegrityViolationException exception) {
    log.warn("Metadata persistence constraint violation", exception);
    return Result.fail(
        MetadataErrorCode.PERSISTENCE_CONFLICT.getCode(),
        MetadataErrorCode.PERSISTENCE_CONFLICT.getMessage());
  }

  @ExceptionHandler(Exception.class)
  public Result<Void> handleUnexpectedException(Exception exception) {
    log.error("Unexpected metadata error", exception);
    return Result.fail("元数据操作失败，请稍后重试");
  }

  private String resolveValidationMessage(Exception exception) {
    BindingResult bindingResult = null;
    if (exception instanceof MethodArgumentNotValidException validException) {
      bindingResult = validException.getBindingResult();
    } else if (exception instanceof BindException bindException) {
      bindingResult = bindException.getBindingResult();
    }
    if (bindingResult != null && bindingResult.getFieldError() != null) {
      return bindingResult.getFieldError().getDefaultMessage();
    }
    return "请求参数格式不正确";
  }
}
