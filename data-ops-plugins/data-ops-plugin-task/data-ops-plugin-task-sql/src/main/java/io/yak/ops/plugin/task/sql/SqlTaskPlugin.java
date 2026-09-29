package io.yak.ops.plugin.task.sql;

import io.yak.ops.core.execution.sql.SqlExecutionRuntime;
import io.yak.ops.plugin.task.api.TaskExecutionContext;
import io.yak.ops.plugin.task.api.TaskExecutor;
import io.yak.ops.plugin.task.api.TaskPlugin;
import io.yak.ops.plugin.task.api.TaskPluginDescriptor;
import io.yak.ops.plugin.task.api.TaskValidationIssue;
import io.yak.ops.plugin.task.api.TaskValidationResult;
import io.yak.ops.spi.task.model.TaskDefinition;
import java.util.ArrayList;
import java.util.List;

/** SQL Task Plugin backed by the platform-level SQL execution lifecycle. */
public final class SqlTaskPlugin implements TaskPlugin {

  public static final String TYPE = "SQL";
  private static final int SCHEMA_VERSION = 1;

  private static final TaskPluginDescriptor DESCRIPTOR =
      new TaskPluginDescriptor(
          TYPE,
          "SQL",
          "Execute SQL through the Yak Ops shared SQL execution runtime",
          "1.2.0",
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
              "SQL plugin only accepts taskType=SQL"));
    }
    if (definition.schemaVersion() != SCHEMA_VERSION) {
      issues.add(
          new TaskValidationIssue(
              "UNSUPPORTED_SCHEMA_VERSION",
              "schemaVersion",
              "SQL plugin currently supports schemaVersion=1"));
    }
    if (definition.content() == null || definition.content().isBlank()) {
      issues.add(
          new TaskValidationIssue(
              "SQL_CONTENT_REQUIRED",
              "content",
              "SQL content must not be blank"));
    }

    try {
      SqlTaskConfig config = SqlTaskConfig.parse(definition.configJson());
      if (config.dataSourceId() == null || config.dataSourceId().isBlank()) {
        issues.add(
            new TaskValidationIssue(
                "SQL_DATASOURCE_REQUIRED",
                "config.dataSourceId",
                "SQL task requires a datasource"));
      }
    } catch (IllegalArgumentException exception) {
      issues.add(
          new TaskValidationIssue(
              "SQL_CONFIG_INVALID",
              "configJson",
              exception.getMessage() == null ? "SQL config is invalid" : exception.getMessage()));
    }

    return issues.isEmpty() ? TaskValidationResult.ok() : TaskValidationResult.of(issues);
  }

  @Override
  public TaskExecutor createExecutor(
      TaskDefinition definition,
      TaskExecutionContext context) {
    TaskValidationResult validation = validate(definition);
    if (!validation.valid()) {
      String summary =
          validation.issues().stream()
              .map(TaskValidationIssue::message)
              .limit(3)
              .reduce((left, right) -> left + "; " + right)
              .orElse("SQL task validation failed");
      throw new IllegalArgumentException(summary);
    }
    SqlTaskConfig config = SqlTaskConfig.parse(definition.configJson());
    SqlExecutionRuntime runtime = context.requireCapability(SqlExecutionRuntime.class);
    return new SqlTaskExecutor(definition, config, runtime);
  }
}
