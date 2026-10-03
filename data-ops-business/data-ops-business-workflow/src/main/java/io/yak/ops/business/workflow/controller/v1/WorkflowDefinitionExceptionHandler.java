package io.yak.ops.business.workflow.controller.v1;

import io.yak.framework.common.Result;
import io.yak.ops.business.workflow.definition.WorkflowDefinitionInputException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 将可修正的定义输入错误映射为请求错误，保留面向用户的提示。 */
@RestControllerAdvice(assignableTypes = WorkflowDefinitionController.class)
public class WorkflowDefinitionExceptionHandler {
  @ExceptionHandler(WorkflowDefinitionInputException.class)
  public ResponseEntity<Result<Void>> handleInvalidDefinition(WorkflowDefinitionInputException exception) {
    return ResponseEntity.badRequest().body(Result.fail(400, exception.getMessage()));
  }
}
