package io.yak.ops.business.sync.realtime.controller;

import io.yak.framework.common.Result;
import io.yak.ops.business.sync.realtime.controller.v1.ComputeEnvironmentController;
import io.yak.ops.business.sync.realtime.controller.v1.RealtimeJobController;
import io.yak.ops.business.sync.realtime.engine.RealtimeEngineException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(
    assignableTypes = {RealtimeJobController.class, ComputeEnvironmentController.class})
public class RealtimeExceptionHandler {

  @ExceptionHandler(IllegalArgumentException.class)
  ResponseEntity<Result<Void>> invalid(IllegalArgumentException exception) {
    return response(HttpStatus.BAD_REQUEST, exception.getMessage());
  }

  @ExceptionHandler(IllegalStateException.class)
  ResponseEntity<Result<Void>> conflict(IllegalStateException exception) {
    return response(HttpStatus.CONFLICT, exception.getMessage());
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<Result<Void>> validation(MethodArgumentNotValidException exception) {
    String message =
        exception.getBindingResult().getFieldErrors().stream()
            .findFirst()
            .map(error -> error.getField() + " " + error.getDefaultMessage())
            .orElse("请求参数无效");
    return response(HttpStatus.BAD_REQUEST, message);
  }

  @ExceptionHandler(RealtimeEngineException.class)
  ResponseEntity<Result<Void>> gateway(RealtimeEngineException exception) {
    HttpStatus status = exception.uncertain() ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.BAD_GATEWAY;
    return response(status, exception.getMessage());
  }

  private ResponseEntity<Result<Void>> response(HttpStatus status, String message) {
    return ResponseEntity.status(status).body(Result.fail(message));
  }
}
