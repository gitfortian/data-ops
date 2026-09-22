package io.yak.ops.plugin.task.shell;

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

/** Shell script task plugin. */
public final class ShellTaskPlugin implements TaskPlugin {

  public static final String TYPE = "SHELL";
  private static final int SCHEMA_VERSION = 1;

  private static final TaskPluginDescriptor DESCRIPTOR =
      new TaskPluginDescriptor(
          TYPE,
          "Shell",
          "Execute Shell scripts via SHELL_HOME or configurable bash, with arguments and environment",
          "1.0.0",
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
              "Shell plugin only accepts taskType=SHELL"));
    }
    if (definition.schemaVersion() != SCHEMA_VERSION) {
      issues.add(
          new TaskValidationIssue(
              "UNSUPPORTED_SCHEMA_VERSION",
              "schemaVersion",
              "Shell plugin currently supports schemaVersion=1"));
    }
    if (definition.content() == null || definition.content().isBlank()) {
      boolean hasResource = parseResourceId(definition.configJson()) != null;
      if (!hasResource) {
        issues.add(
            new TaskValidationIssue(
                "SHELL_CONTENT_OR_RESOURCE_REQUIRED",
                "content",
                "Shell task must have either inline content or a referenced resource (resourceId in configJson)"));
      }
    }
    if (definition.content() != null && !definition.content().isBlank()
        && parseResourceId(definition.configJson()) != null) {
      issues.add(
          new TaskValidationIssue(
              "SHELL_CONTENT_RESOURCE_CONFLICT",
              "content",
              "Shell task cannot have both inline content and a referenced resource"));
    }

    try {
      ShellTaskConfig.parse(definition.configJson());
    } catch (IllegalArgumentException exception) {
      issues.add(
          new TaskValidationIssue(
              "SHELL_CONFIG_INVALID",
              "configJson",
              exception.getMessage() == null ? "Shell config is invalid" : exception.getMessage()));
    }

    return issues.isEmpty() ? TaskValidationResult.ok() : TaskValidationResult.of(issues);
  }

  @Override
  public TaskExecutor createExecutor(
      TaskDefinition definition,
      TaskExecutionContext context) {
    TaskValidationResult validation = validate(definition);
    if (!validation.valid()) {
      String summary = summarizeIssues(validation, "Shell task validation failed");
      throw new IllegalArgumentException(summary);
    }
    Map<String, String> globalEnv = context.globalEnvVars();
    ShellTaskConfig config = ShellTaskConfig.parse(definition.configJson(), globalEnv);
    ResourceResolver resourceResolver = null;
    boolean hasContent = definition.content() != null && !definition.content().isBlank();
    if (!hasContent && config.resourceId() > 0) {
      resourceResolver = context.requireCapability(ResourceResolver.class);
    }
    return new ShellTaskExecutor(definition, config, globalEnv, resourceResolver);
  }
}
