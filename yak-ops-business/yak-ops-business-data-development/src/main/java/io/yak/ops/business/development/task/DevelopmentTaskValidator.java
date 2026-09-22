package io.yak.ops.business.development.task;

import io.yak.ops.core.plugin.task.TaskPluginRegistry;
import io.yak.ops.plugin.task.api.TaskPlugin;
import io.yak.ops.plugin.task.api.TaskValidationIssue;
import io.yak.ops.plugin.task.api.TaskValidationResult;
import io.yak.ops.spi.task.model.TaskDefinition;
import java.util.List;
import org.springframework.stereotype.Component;

/** Delegates publish capability validation to the installed Task Plugin. */
@Component
public class DevelopmentTaskValidator {

  private final TaskPluginRegistry pluginRegistry;

  public DevelopmentTaskValidator(TaskPluginRegistry pluginRegistry) {
    this.pluginRegistry = pluginRegistry;
  }

  public DevelopmentTaskValidation validateForPublish(TaskDefinition definition) {
    TaskPlugin plugin = pluginRegistry.find(definition.taskType()).orElse(null);
    if (plugin == null) {
      return DevelopmentTaskValidation.invalid(
          "当前未安装 " + definition.taskType() + " Task Plugin，无法发布",
          List.of(new TaskValidationIssue(
              "TASK_PLUGIN_NOT_INSTALLED",
              "taskType",
              "Task plugin is not installed: " + definition.taskType())));
    }

    TaskValidationResult validation = plugin.validate(definition);
    if (validation.valid()) {
      return DevelopmentTaskValidation.ok();
    }
    String summary = validation.issues().stream()
        .map(TaskValidationIssue::message)
        .limit(3)
        .reduce((left, right) -> left + "；" + right)
        .orElse("任务定义校验失败");
    return DevelopmentTaskValidation.invalid(summary, validation.issues());
  }
}
