package io.yak.ops.plugin.task.java;

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

/**
 * Java JAR task plugin.
 *
 * <p>Executes JAR files uploaded to resource management.
 * Requires at least one resource reference in {@code configJson.resources} array
 * (or legacy {@code configJson.resourceId}).
 *
 * <p>When multiple JARs are referenced, {@code mainClass} is required since
 * the manifest {@code Main-Class} attribute cannot be determined automatically
 * from multiple JARs.
 *
 * <p>{@link ResourceResolver} is obtained via {@code context.requireCapability(ResourceResolver.class)}.
 */
public final class JavaTaskPlugin implements TaskPlugin {

  public static final String TYPE = "JAVA";
  private static final int SCHEMA_VERSION = 1;

  private static final TaskPluginDescriptor DESCRIPTOR =
      new TaskPluginDescriptor(
          TYPE,
          "Java",
          "Execute JAR files from resource management, supporting single or multi-JAR classpath",
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
              "Java plugin only accepts taskType=JAVA"));
    }
    if (definition.schemaVersion() != SCHEMA_VERSION) {
      issues.add(
          new TaskValidationIssue(
              "UNSUPPORTED_SCHEMA_VERSION",
              "schemaVersion",
              "Java plugin currently supports schemaVersion=1"));
    }

    // Java tasks must use resource reference mode; content should be empty
    if (definition.content() != null && !definition.content().isBlank()) {
      issues.add(
          new TaskValidationIssue(
              "JAVA_CONTENT_NOT_SUPPORTED",
              "content",
              "Java task does not support inline content; use resource references instead"));
    }

    try {
      JavaTaskConfig config = JavaTaskConfig.parse(definition.configJson());

      // Multiple JARs require explicit mainClass
      if (config.resources().size() > 1 && config.mainClass() == null) {
        issues.add(
            new TaskValidationIssue(
                "JAVA_MAIN_CLASS_REQUIRED",
                "configJson.mainClass",
                "mainClass is required when referencing multiple JAR files"));
      }
      if (config.mainClass() != null && config.mainClass().isBlank()) {
        issues.add(
            new TaskValidationIssue(
                "JAVA_MAIN_CLASS_INVALID",
                "configJson.mainClass",
                "mainClass must not be blank if provided"));
      }
    } catch (IllegalArgumentException exception) {
      issues.add(
          new TaskValidationIssue(
              "JAVA_CONFIG_INVALID",
              "configJson",
              exception.getMessage() == null ? "Java config is invalid" : exception.getMessage()));
    }

    return issues.isEmpty() ? TaskValidationResult.ok() : TaskValidationResult.of(issues);
  }

  @Override
  public TaskExecutor createExecutor(
      TaskDefinition definition,
      TaskExecutionContext context) {
    TaskValidationResult validation = validate(definition);
    if (!validation.valid()) {
      String summary = summarizeIssues(validation, "Java task validation failed");
      throw new IllegalArgumentException(summary);
    }
    Map<String, String> globalEnv = context.globalEnvVars();
    JavaTaskConfig config = JavaTaskConfig.parse(definition.configJson(), globalEnv);
    ResourceResolver resourceResolver = context.requireCapability(ResourceResolver.class);
    return new JavaTaskExecutor(definition, config, globalEnv, resourceResolver);
  }
}
