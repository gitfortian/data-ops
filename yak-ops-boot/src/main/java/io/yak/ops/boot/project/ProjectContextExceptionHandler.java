package io.yak.ops.boot.project;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.framework.common.Result;
import io.yak.ops.core.project.ProjectContextError;
import io.yak.ops.core.project.ProjectContextException;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Converts Project Space failures into stable HTTP and business error semantics. */
@RestControllerAdvice(basePackages = "io.yak.ops")
public class ProjectContextExceptionHandler {

  private static final ObjectMapper RESPONSE_MAPPER = new ObjectMapper();

  @ExceptionHandler(ProjectContextException.class)
  public ResponseEntity<Result<Void>> handle(ProjectContextException exception) {
    return toResponseEntity(exception);
  }

  static ResponseEntity<Result<Void>> toResponseEntity(ProjectContextException exception) {
    ProjectContextError error = exception.getError();
    Result<Void> body = Result.fail(error.getCode(), error.name() + ": " + error.getMessage());
    return ResponseEntity.status(statusOf(error)).body(body);
  }

  /**
   * preHandle 阶段抛出的 ProjectContextException 会被各业务模块 advice 的 Exception.class
   * 兜底捕获成 999(advices 同优先级时排序不保证),因此拦截器直接落盘约定响应。
   */
  static void writeToResponse(ProjectContextException exception, HttpServletResponse response)
      throws IOException {
    ProjectContextError error = exception.getError();
    response.setStatus(statusOf(error).value());
    response.setContentType("application/json;charset=UTF-8");
    Result<Void> body = Result.fail(error.getCode(), error.name() + ": " + error.getMessage());
    response.getWriter().write(RESPONSE_MAPPER.writeValueAsString(body));
  }

  private static HttpStatus statusOf(ProjectContextError error) {
    return switch (error) {
      case PROJECT_REQUIRED -> HttpStatus.BAD_REQUEST;
      case PROJECT_NOT_FOUND -> HttpStatus.NOT_FOUND;
      case PROJECT_UNAVAILABLE -> HttpStatus.FORBIDDEN;
    };
  }
}
