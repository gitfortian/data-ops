package io.yak.ops.business.sync.offline;

import io.yak.ops.core.project.CurrentProject;
import io.yak.ops.core.project.ProjectContext;
import io.yak.ops.core.project.ProjectContextScope;
import java.util.Optional;
import java.util.function.Supplier;

/** Shared Project fixture for focused Offline Sync unit tests. */
public final class OfflineProjectTestContext {

  public static final long PROJECT_ID = 7L;

  private OfflineProjectTestContext() {}

  public static CurrentProject currentProject() {
    ProjectContext context = new ProjectContext(PROJECT_ID, "offline-test");
    return () -> Optional.of(context);
  }

  public static ProjectContextScope directScope() {
    return new ProjectContextScope() {
      @Override
      public <T> T call(ProjectContext context, Supplier<T> action) {
        return action.get();
      }
    };
  }
}
