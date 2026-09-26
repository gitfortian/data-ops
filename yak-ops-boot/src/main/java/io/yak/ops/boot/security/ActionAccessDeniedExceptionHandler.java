package io.yak.ops.boot.security;

import io.yak.framework.common.Result;
import io.yak.ops.core.security.ActionAccessDeniedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Converts action-level authorization denials into stable HTTP 403 semantics. */
@RestControllerAdvice(basePackages = "io.yak.ops")
public class ActionAccessDeniedExceptionHandler {

  private static final int ACTION_ACCESS_DENIED = 40301;

  @ExceptionHandler(ActionAccessDeniedException.class)
  public ResponseEntity<Result<Void>> handle(ActionAccessDeniedException exception) {
    Result<Void> body = Result.fail(ACTION_ACCESS_DENIED, exception.getMessage());
    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
  }
}
