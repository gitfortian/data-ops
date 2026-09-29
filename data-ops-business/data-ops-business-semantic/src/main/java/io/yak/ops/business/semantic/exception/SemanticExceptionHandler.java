package io.yak.ops.business.semantic.exception;

import io.yak.framework.common.ErrorCode;
import io.yak.framework.common.Result;
import io.yak.framework.security.common.enums.ResultCode;
import io.yak.framework.security.exception.YakSecurityException;
import io.yak.ops.business.approval.exception.ApprovalException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 业务语义接口异常转换。
 *
 * <p>语义模块此前没有异常处理器,业务异常(SemanticException,如"沉淀字段缺 std_type_id"、
 * "码集不存在")会直接逃逸成裸 HTTP 500,前端只能看到笼统的"失败"。按其他业务模块
 * (modeling/datasource/quality)的统一模式补齐:@RestControllerAdvice 限定本模块 controller 包,
 * 把业务异常与参数校验转成 Result 业务码;不改变任何成功路径。
 */
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackages = "io.yak.ops.business.semantic.controller")
public class SemanticExceptionHandler {

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

  @ExceptionHandler(SemanticException.class)
  public Result<Void> handleSemanticException(SemanticException exception) {
    if (exception.getErrorCode() == null) {
      return Result.fail(exception.getUserMessage());
    }
    return Result.fail(exception.getErrorCode().getCode(), exception.getUserMessage());
  }

  /** 审批中心错误(49001~49011)经 semantic 接口透出时保持原码。 */
  @ExceptionHandler(ApprovalException.class)
  public Result<Void> handleApprovalException(ApprovalException exception) {
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
  public Result<Void> handleValidation(BindException exception) {
    BindingResult binding = exception.getBindingResult();
    String message =
        binding.getFieldErrors().stream()
            .findFirst()
            .map(
                error ->
                    (error.getField() == null ? "" : error.getField() + ":")
                        + error.getDefaultMessage())
            .orElse("参数校验失败");
    return Result.fail(ResultCode.PARAM_NOT_VALID.getCode(), message);
  }

  @ExceptionHandler(Exception.class)
  public Result<Void> handleUnexpected(Exception exception) {
    log.error("unhandled exception in semantic controller", exception);
    return Result.fail("系统异常，请稍后重试");
  }
}
