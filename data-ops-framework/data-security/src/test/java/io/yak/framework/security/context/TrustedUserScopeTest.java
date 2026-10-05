package io.yak.framework.security.context;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.yak.framework.security.common.entity.user.User;
import io.yak.framework.security.dao.UserDao;
import io.yak.framework.security.service.impl.AuthorizationSnapshotService;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class TrustedUserScopeTest {
  private final UserDao users = mock(UserDao.class);
  private final AuthorizationSnapshotService snapshots = mock(AuthorizationSnapshotService.class);
  private final TrustedUserScope scope = new TrustedUserScope(users, snapshots);

  @AfterEach void clear() { YakSecurityContext.clear(); }

  @Test void bindsOnWorkerAndRestoresEvenAfterFailure() throws Exception {
    User user = new User(); user.setUserName("analyst"); user.setId(7L);
    when(users.selectByUserId(7L)).thenReturn(user);
    when(snapshots.get(7L)).thenReturn(new AuthorizationSnapshot(List.of(9L), Set.of("agent:chat:run"), List.of(), Set.of(42L)));
    var worker = java.util.concurrent.Executors.newSingleThreadExecutor();
    try {
      worker.submit(() -> {
        assertFalse(YakSecurityContext.isAuthenticated());
        assertThrows(IllegalStateException.class, () -> scope.call(7, 42, () -> {
          assertEquals("analyst", YakSecurityContext.getCurrentUsername());
          assertEquals(42L, YakSecurityContext.getCurrentProjectId());
          throw new IllegalStateException("test failure");
        }));
        assertFalse(YakSecurityContext.isAuthenticated());
      }).get();
    } finally { worker.shutdownNow(); }
  }

  @Test void disabledDeletedAndRevokedProjectNeverRunAction() {
    User user = new User(); user.setUserName("analyst"); user.setId(7L); user.setStatus(2);
    when(users.selectByUserId(7L)).thenReturn(user);
    assertThrows(SecurityException.class, () -> scope.call(7, 42, () -> fail("disabled user executed")));
    user.setStatus(1); user.setIsDelete(true);
    assertThrows(SecurityException.class, () -> scope.call(7, 42, () -> fail("deleted user executed")));
    user.setIsDelete(false);
    when(snapshots.get(7L)).thenReturn(new AuthorizationSnapshot(List.of(), Set.of(), List.of(), Set.of(42L)))
        .thenReturn(AuthorizationSnapshot.empty());
    assertEquals("allowed", scope.call(7, 42, () -> "allowed"));
    assertThrows(SecurityException.class, () -> scope.call(7, 42, () -> fail("revoked project executed")));
    assertFalse(YakSecurityContext.isAuthenticated());
  }

  @Test void nestedScopeRestoresAuthenticatedCaller() {
    User user = new User(); user.setUserName("worker");
    when(users.selectByUserId(7L)).thenReturn(user);
    when(snapshots.get(7L)).thenReturn(new AuthorizationSnapshot(List.of(), Set.of(), List.of(), Set.of(42L)));
    YakSecurityContext.setCurrentUser(new YakSecurityContext.ImmutableCurrentUser(1L, "caller", 10L, List.of(), true));
    scope.call(7, 42, () -> { assertEquals("worker", YakSecurityContext.getCurrentUsername()); return null; });
    assertEquals("caller", YakSecurityContext.getCurrentUsername());
    assertEquals(10L, YakSecurityContext.getCurrentProjectId());
  }
}
