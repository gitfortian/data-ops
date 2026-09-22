package io.yak.ops.business.development.controller.v1;

import io.yak.ops.business.development.service.DevelopmentDraftConflictException;
import io.yak.ops.business.development.task.DevelopmentTaskValidationException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Focused HTTP error mapping for data-development task, execution and release endpoints. */
@RestControllerAdvice(
    assignableTypes = {
      DevelopmentTaskController.class,
      DevelopmentTaskExecutionController.class,
      DevelopmentReleaseController.class
    })
public class DevelopmentTaskExceptionHandler {

  @ExceptionHandler(DevelopmentDraftConflictException.class)
  public ResponseEntity<Map<String, Object>> handleConflict(
      DevelopmentDraftConflictException exception) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(errorBody(exception.getMessage()));
  }

  @ExceptionHandler(DevelopmentTaskValidationException.class)
  public ResponseEntity<Map<String, Object>> handleValidation(
      DevelopmentTaskValidationException exception) {
    Map<String, Object> body = errorBody(exception.getMessage());
    body.put("issues", exception.issues());
    return ResponseEntity.badRequest().body(body);
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<Map<String, Object>> handleIllegalArgument(
      IllegalArgumentException exception) {
    return ResponseEntity.badRequest().body(errorBody(exception.getMessage()));
  }

  private Map<String, Object> errorBody(String message) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("message", message == null || message.isBlank() ? "数据开发任务请求失败" : message);
    return body;
  }
}
