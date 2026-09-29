package io.yak.ops.plugin.task.python;

import static io.yak.ops.plugin.task.api.ScriptTaskSupport.parseResourceId;
import static io.yak.ops.plugin.task.api.ScriptTaskSupport.summarizeIssues;

import io.yak.ops.plugin.task.api.TaskExecutionContext;
import io.yak.ops.plugin.task.api.TaskExecutor;
import io.yak.ops.plugin.task.api.TaskPlugin;
import io.yak.ops.plugin.task.api.TaskPluginDescriptor;
import io.yak.ops.plugin.task.api.TaskValidationIssue;
import io.yak.ops.plugin.task.api.TaskValidationResult;
import io.yak.ops.spi.resource.ResourceResolver;
import io.yak.ops.spi.task.model.TaskDefinition;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Python script task plugin. */
public final class PythonTaskPlugin implements TaskPlugin {

  public static final String TYPE = "PYTHON";
  private static final int SCHEMA_VERSION = 1;

  private static final TaskPluginDescriptor DESCRIPTOR =
      new TaskPluginDescriptor(
          TYPE,
          "Python",
          "Execute Python scripts via PYTHON_HOME or configurable interpreter, with arguments and environment",
          "1.1.0",
          SCHEMA_VERSION,
          true,
          true);

  @Override
  public TaskPluginDescriptor descriptor() {
    return DESCRIPTOR;
  }

  @Override
  public TaskValidationResult validate(TaskDefinition definition) {
    if (definition == null) {
      return TaskValidationResult.invalid(
          new TaskValidationIssue(
              "TASK_DEFINITION_REQUIRED",
              null,
              "Task definition is required"));
    }

    List<TaskValidationIssue> issues = new ArrayList<>();
    if (!TYPE.equalsIgnoreCase(definition.taskType())) {
      issues.add(
          new TaskValidationIssue(
              "TASK_TYPE_MISMATCH",
              "taskType",
              "Python plugin only accepts taskType=PYTHON"));
    }
    if (definition.schemaVersion() != SCHEMA_VERSION) {
      issues.add(
          new TaskValidationIssue(
              "UNSUPPORTED_SCHEMA_VERSION",
              "schemaVersion",
              "Python plugin currently supports schemaVersion=1"));
    }
    if (definition.content() == null || definition.content().isBlank()) {
      // Stage 1: allow empty content when resourceId is present in configJson
      // (resource reference mode — execution path is Stage 2)
      boolean hasResource = parseResourceId(definition.configJson()) != null;
      if (!hasResource) {
        issues.add(
            new TaskValidationIssue(
                "PYTHON_CONTENT_OR_RESOURCE_REQUIRED",
                "content",
                "Python task must have either inline content or a referenced resource (resourceId in configJson)"));
      }
    }
    if (definition.content() != null && !definition.content().isBlank()
        && parseResourceId(definition.configJson()) != null) {
      issues.add(
          new TaskValidationIssue(
              "PYTHON_CONTENT_RESOURCE_CONFLICT",
              "content",
              "Python task cannot have both inline content and a referenced resource"));
    }

    try {
      PythonTaskConfig.parse(definition.configJson());
    } catch (IllegalArgumentException exception) {
      issues.add(
          new TaskValidationIssue(
              "PYTHON_CONFIG_INVALID",
              "configJson",
              exception.getMessage() == null ? "Python config is invalid" : exception.getMessage()));
    }

    return issues.isEmpty() ? TaskValidationResult.ok() : TaskValidationResult.of(issues);
  }

  @Override
  public TaskExecutor createExecutor(
      TaskDefinition definition,
      TaskExecutionContext context) {
    TaskValidationResult validation = validate(definition);
    if (!validation.valid()) {
      String summary = summarizeIssues(validation, "Python task validation failed");
      throw new IllegalArgumentException(summary);
    }
    Map<String, String> globalEnv = context.globalEnvVars();
    PythonTaskConfig config = PythonTaskConfig.parse(definition.configJson(), globalEnv);
    ResourceResolver resourceResolver = null;
    boolean hasContent = definition.content() != null && !definition.content().isBlank();
    if (!hasContent && config.resourceId() > 0) {
      resourceResolver = context.requireCapability(ResourceResolver.class);
    }
    return new PythonTaskExecutor(definition, config, globalEnv, resourceResolver);
  }
}
