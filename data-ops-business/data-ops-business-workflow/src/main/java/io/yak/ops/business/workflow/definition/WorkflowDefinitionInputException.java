package io.yak.ops.business.workflow.definition;

/** 工作流定义中可由用户修正的输入错误。 */
public class WorkflowDefinitionInputException extends IllegalArgumentException {
  public WorkflowDefinitionInputException(String message) {
    super(message);
  }
}
