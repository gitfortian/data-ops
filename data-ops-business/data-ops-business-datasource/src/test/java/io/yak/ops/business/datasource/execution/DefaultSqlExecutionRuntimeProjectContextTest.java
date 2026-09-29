package io.yak.ops.business.datasource.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.yak.ops.business.datasource.gateway.adapter.SpiSqlExecutionGateway;
import io.yak.ops.core.execution.sql.SqlExecutionCaller;
import io.yak.ops.core.execution.sql.SqlExecutionContext;
import io.yak.ops.core.execution.sql.SqlExecutionPlan;
import io.yak.ops.core.execution.sql.SqlExecutionSnapshot;
import io.yak.ops.core.execution.sql.SqlExecutionStatus;
import io.yak.ops.core.execution.sql.SqlStatementRequest;
import io.yak.ops.core.project.CurrentProject;
import io.yak.ops.core.project.ProjectContext;
import io.yak.ops.core.project.ProjectContextScope;
import io.yak.ops.spi.datasource.execution.DataSourceExecutionProvider;
import io.yak.ops.spi.datasource.execution.DataSourceSqlResult;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class DefaultSqlExecutionRuntimeProjectContextTest {

  @Test
  void restoresCapturedProjectInsideAsyncExecutionThread() {
    TestProjectRuntime projectRuntime = new TestProjectRuntime();
    projectRuntime.bind(new ProjectContext(7L, "Project A"));

    DataSourceExecutionProvider provider =
        dataSourceId -> {
          assertEquals(7L, projectRuntime.requireProjectId());
          return request -> DataSourceSqlResult.resultSet(List.of(), List.of(), false);
        };

    DefaultSqlExecutionRuntime runtime =
        new DefaultSqlExecutionRuntime(
            new SpiSqlExecutionGateway(provider),
            new DefaultSqlExecutionPolicy(),
            projectRuntime,
            projectRuntime);

    SqlExecutionSnapshot started =
        runtime.start(
            new SqlExecutionPlan(
                "42",
                List.of(new SqlStatementRequest("select 1", 10, 30)),
                SqlExecutionContext.of(SqlExecutionCaller.SQL_TASK, "task-project")));

    projectRuntime.clear();
    SqlExecutionSnapshot completed = runtime.await(started.executionId());
    runtime.shutdown();

    assertEquals(SqlExecutionStatus.SUCCEEDED, completed.status());
  }

  private static final class TestProjectRuntime implements CurrentProject, ProjectContextScope {
    private final ThreadLocal<ProjectContext> holder = new ThreadLocal<>();

    @Override
    public Optional<ProjectContext> current() {
      return Optional.ofNullable(holder.get());
    }

    @Override
    public <T> T call(ProjectContext context, Supplier<T> action) {
      ProjectContext previous = holder.get();
      holder.set(context);
      try {
        return action.get();
      } finally {
        if (previous == null) holder.remove();
        else holder.set(previous);
      }
    }

    void bind(ProjectContext context) {
      holder.set(context);
    }

    void clear() {
      holder.remove();
    }
  }
}
