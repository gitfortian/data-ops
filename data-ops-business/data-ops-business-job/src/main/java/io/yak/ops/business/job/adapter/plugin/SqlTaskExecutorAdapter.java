package io.yak.ops.business.job.adapter.plugin;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.job.runtime.AbstractTaskExecutorAdapter;
import io.yak.ops.business.job.runtime.TaskExecutionContextFactory;
import io.yak.ops.business.job.task.TaskVersionSnapshot;
import io.yak.ops.core.execution.sql.SqlExecutionRuntime;
import io.yak.ops.core.plugin.task.TaskPluginRegistry;
import io.yak.ops.plugin.task.api.DefaultTaskExecutionContext;
import io.yak.ops.plugin.task.api.TaskValidationIssue;
import io.yak.ops.plugin.task.api.TaskValidationResult;
import io.yak.ops.spi.datasource.execution.DataSourceExecutionProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** SQL TaskPlugin adapter; SQL contributes capabilities while shared runtime owns lifecycle. */
@Component
public class SqlTaskExecutorAdapter extends AbstractTaskExecutorAdapter {

  private final ObjectProvider<DataSourceExecutionProvider> dataSourceExecutionProvider;
  private final ObjectProvider<SqlExecutionRuntime> sqlExecutionRuntime;

  @Autowired
  public SqlTaskExecutorAdapter(
      TaskPluginRegistry pluginRegistry,
      ObjectProvider<DataSourceExecutionProvider> dataSourceExecutionProvider,
      ObjectProvider<SqlExecutionRuntime> sqlExecutionRuntime,
      ObjectMapper objectMapper,
      TaskExecutionContextFactory contextFactory) {
    super(pluginRegistry, null, objectMapper, contextFactory);
    this.dataSourceExecutionProvider = dataSourceExecutionProvider;
    this.sqlExecutionRuntime = sqlExecutionRuntime;
  }

  /** Backward-compatible constructor for focused adapter tests and embedders. */
  public SqlTaskExecutorAdapter(
      TaskPluginRegistry pluginRegistry,
      ObjectProvider<DataSourceExecutionProvider> dataSourceExecutionProvider,
      ObjectMapper objectMapper,
      TaskExecutionContextFactory contextFactory) {
    this(pluginRegistry, dataSourceExecutionProvider, null, objectMapper, contextFactory);
  }

  @Override
  public String taskType() {
    return "SQL";
  }

  @Override
  protected String executionIdPrefix() {
    return "sql";
  }

  @Override
  protected String displayName() {
    return "SQL";
  }

  @Override
  protected void configureContext(
      DefaultTaskExecutionContext.Builder builder,
      String definitionJson) {
    DataSourceExecutionProvider provider =
        dataSourceExecutionProvider == null ? null : dataSourceExecutionProvider.getIfAvailable();
    SqlExecutionRuntime runtime =
        sqlExecutionRuntime == null ? null : sqlExecutionRuntime.getIfAvailable();
    if (provider != null) builder.capability(DataSourceExecutionProvider.class, provider);
    if (runtime != null) builder.capability(SqlExecutionRuntime.class, runtime);
  }

  @Override
  protected String snapshotRequiredMessage() {
    return "任务版本快照不能为空";
  }

  @Override
  protected String snapshotTypeMismatchMessage(TaskVersionSnapshot snapshot) {
    return "SQL 执行器不能执行任务类型：" + snapshot.type();
  }

  @Override
  protected String missingDefinitionSnapshotMessage(TaskVersionSnapshot snapshot) {
    return "SQL 任务缺少不可变 definitionSnapshot，拒绝回退到当前草稿或最新版本："
        + snapshot.taskId();
  }

  @Override
  protected String executionNotFoundMessage(String executionId) {
    return "SQL 任务执行不存在：" + executionId;
  }

  @Override
  protected String pluginNotExecutableMessage() {
    return "SQL Task Plugin 暂不支持执行";
  }

  @Override
  protected String validationFailureMessage(TaskValidationResult validation) {
    if (validation.valid()) return null;
    return validation.issues().stream()
        .map(TaskValidationIssue::message)
        .limit(3)
        .reduce((left, right) -> left + "；" + right)
        .orElse("SQL 任务定义校验失败");
  }

  @Override
  protected String executionFailureMessage(Throwable throwable) {
    String message = throwable == null ? null : throwable.getMessage();
    if (message == null || message.isBlank()) {
      return throwable == null ? "SQL execution failed" : throwable.getClass().getSimpleName();
    }
    return message.length() > 500 ? message.substring(0, 500) : message;
  }
}
