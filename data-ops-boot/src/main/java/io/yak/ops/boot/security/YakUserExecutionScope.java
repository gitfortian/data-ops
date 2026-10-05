package io.yak.ops.boot.security;

import io.yak.framework.security.context.TrustedUserScope;
import io.yak.framework.security.context.YakSecurityContext;
import io.yak.framework.security.dao.UserDao;
import io.yak.framework.security.service.impl.AuthorizationSnapshotService;
import io.yak.ops.core.project.ProjectAccessGuard;
import io.yak.ops.core.project.ProjectContextScope;
import io.yak.ops.core.security.UserExecutionScope;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

/** Infrastructure adapter: live Security facts and Project membership precede business access. */
@Component
public class YakUserExecutionScope implements UserExecutionScope {
  private final TrustedUserScope users;
  private final ProjectAccessGuard projectAccess;
  private final ProjectContextScope projects;

  public YakUserExecutionScope(UserDao users, AuthorizationSnapshotService snapshots,
      ProjectAccessGuard projectAccess, ProjectContextScope projects) {
    this.users = new TrustedUserScope(users, snapshots);
    this.projectAccess = projectAccess;
    this.projects = projects;
  }

  @Override
  public <T> T call(long userId, long projectId, Supplier<T> action) {
    return users.call(userId, projectId, () -> projects.call(
        projectAccess.requireAccessible(projectId, YakSecurityContext.getCurrentUsername()), action));
  }
}
