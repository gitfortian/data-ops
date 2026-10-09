package io.yak.framework.security.context;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Platform-owned immutable authorization facts and thread-local identity semantics. */
class SecurityContextPlatformContractTest {
  @AfterEach void cleanup() { YakSecurityContext.clear(); }

  @Test
  void anonymousIdentityDeniesEveryResourceAndHasNoRoleData() {
    assertFalse(YakSecurityContext.isAuthenticated());
    assertNull(YakSecurityContext.getCurrentUserId());
    assertFalse(YakSecurityContext.hasPermission("security:project:read"));
    assertFalse(YakSecurityContext.canAccessProject(9L));
    assertTrue(YakSecurityContext.getCurrentProjectIds().isEmpty());
    assertTrue(YakSecurityContext.getCurrentPermissionCodes().isEmpty());
  }

  @Test
  void snapshotNormalizesIdsAndProtectsItsCollections() {
    var snapshot = new AuthorizationSnapshot(List.of(4L, 4L, -1L, 2L),
        List.of("security:project:read", "security:project:read", ""),
        List.of("system-users", "system-users"), List.of(9L, 9L, -5L));
    assertEquals(List.of(4L, 2L), snapshot.getRoleIds());
    assertEquals(Set.of("security:project:read"), snapshot.getPermissionCodes());
    assertEquals(List.of("system-users"), snapshot.getMenuCodes());
    assertEquals(Set.of(9L), snapshot.getProjectIds());
    assertThrows(UnsupportedOperationException.class, () -> snapshot.getRoleIds().add(3L));
    assertThrows(UnsupportedOperationException.class, () -> snapshot.getPermissionCodes().clear());
    assertTrue(snapshot.hasPermission("security:project:read"));
    assertFalse(snapshot.canAccessProject(8L));
    assertTrue(snapshot.canAccessProject(9L));
    assertFalse(snapshot.canAccessProject(null));
  }

  @Test
  void rootCodeBypassesPermissionAndProjectScopeButNotNullProject() {
    var root = new AuthorizationSnapshot(List.of(1L), List.of("security:root"), List.of(), List.of());
    assertTrue(root.hasPermission("security:anything"));
    assertTrue(root.canAccessProject(991L));
    assertFalse(root.canAccessProject(null));
  }

  @Test
  void currentContextRestoresAnonymousOnClearAndNeverLeaksAcrossThreads() throws Exception {
    var current = new YakSecurityContext.ImmutableCurrentUser(3L, "ops", 9L,
        new AuthorizationSnapshot(List.of(4L), List.of("security:project:read"),
            List.of("system-users"), List.of(9L)), true);
    YakSecurityContext.setCurrentUser(current);
    assertEquals(3L, YakSecurityContext.getCurrentUserId());
    assertTrue(YakSecurityContext.canAccessProject(9L));
    assertTrue(YakSecurityContext.hasPermission("security:project:read"));
    final boolean[] otherThread = {true};
    Thread thread = new Thread(() -> otherThread[0] = YakSecurityContext.isAuthenticated());
    thread.start();
    thread.join();
    assertFalse(otherThread[0], "request ThreadLocal must not propagate to unrelated worker");
    YakSecurityContext.clear();
    assertFalse(YakSecurityContext.isAuthenticated());
    assertFalse(YakSecurityContext.canAccessProject(9L));
  }

  @Test
  void originalPublicFqcnsAndLoginPortDefaultsAreReachable() throws Exception {
    assertSame(CurrentUser.class, Class.forName("io.yak.framework.security.context.CurrentUser"));
    assertSame(YakSecurityContext.class, Class.forName("io.yak.framework.security.context.YakSecurityContext"));
    assertSame(AuthorizationSnapshot.class, Class.forName("io.yak.framework.security.context.AuthorizationSnapshot"));
    assertSame(DefaultCurrentUser.class, Class.forName("io.yak.framework.security.context.DefaultCurrentUser"));
    assertEquals("io.yak.framework.security.authentication.AuthenticationManager",
        io.yak.framework.security.authentication.AuthenticationManager.class.getName());
    var manager = new io.yak.framework.security.authentication.AuthenticationManager() {
      Long logged;
      public void login(Long userId) { logged = userId; }
      public void logout() { logged = null; }
      public boolean isLogin() { return logged != null; }
      public Long getLoginUserId() { return logged; }
    };
    manager.login(42L, "ops");
    assertEquals(42L, manager.getLoginUserId());
    assertNull(manager.getLoginUsername());
    manager.logoutUser(42L);
    assertEquals(42L, manager.getLoginUserId(), "legacy default account-level logout is no-op");
  }
}
