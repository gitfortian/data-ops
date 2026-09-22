package io.yak.ops.boot.project;

import io.yak.ops.core.project.CurrentProject;
import io.yak.ops.core.project.ProjectContext;
import io.yak.ops.core.project.ProjectContextScope;
import java.util.Optional;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

/** Thread-local trusted Project Space runtime for HTTP requests and background scopes. */
@Component
public class ProjectContextRuntime implements CurrentProject, ProjectContextScope {

  private final ThreadLocal<ProjectContext> holder = new ThreadLocal<>();

  @Override
  public Optional<ProjectContext> current() {
    return Optional.ofNullable(holder.get());
  }

  @Override
  public <T> T call(ProjectContext context, Supplier<T> action) {
    if (context == null) throw new IllegalArgumentException("project context must not be null");
    if (action == null) throw new IllegalArgumentException("project action must not be null");

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
