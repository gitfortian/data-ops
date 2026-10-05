package io.yak.framework.security.context;

import io.yak.framework.security.dao.UserDao;
import io.yak.framework.security.service.impl.AuthorizationSnapshotService;
import java.util.function.Supplier;

/**
 * Scoped identity restoration for application-owned background jobs. User IDs must come from
 * authenticated admission, never from tool parameters. Authorization is resolved on each call.
 */
public final class TrustedUserScope {
  private final UserDao users;
  private final AuthorizationSnapshotService snapshots;

  public TrustedUserScope(UserDao users, AuthorizationSnapshotService snapshots) {
    this.users = users;
    this.snapshots = snapshots;
  }

  public <T> T call(long userId, long projectId, Supplier<T> action) {
    if (userId <= 0 || projectId <= 0) throw new SecurityException("执行身份或项目无效");
    var user = users.selectByUserId(userId);
    if (user == null || Boolean.TRUE.equals(user.getIsDelete())
        || !Integer.valueOf(1).equals(user.getStatus())
        || user.getUserName() == null || user.getUserName().isBlank()) {
      throw new SecurityException("执行用户不可用");
    }
    CurrentUser previous = YakSecurityContext.currentUser();
    var current = new YakSecurityContext.ImmutableCurrentUser(
        userId, user.getUserName(), projectId, snapshots.get(userId), true);
    if (!current.canAccessProject(projectId)) throw new SecurityException("无权访问当前项目");
    YakSecurityContext.setCurrentUser(current);
    try {
      return action.get();
    } finally {
      if (previous.isAuthenticated()) YakSecurityContext.setCurrentUser(previous);
      else YakSecurityContext.clear();
    }
  }
}
