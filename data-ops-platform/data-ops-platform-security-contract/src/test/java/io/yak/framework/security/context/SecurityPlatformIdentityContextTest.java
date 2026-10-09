package io.yak.framework.security.context;

import io.yak.ops.platform.security.contract.SecurityPermissionCode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Executable ABI and security semantics after AuthorizationSnapshot/CurrentUser jar relocation. */
class SecurityPlatformIdentityContextTest {

  @AfterEach
  void clear() {
    YakSecurityContext.clear();
  }

  @Test
  void snapshotNormalizesFactsAndRemainsImmutable() {
    List<Long> roles = new ArrayList<>(Arrays.asList(null, -1L, 4L, 4L, 9L));
    List<String> permissions = new ArrayList<>(Arrays.asList(null, "", "dataset:read", "dataset:read"));
    AuthorizationSnapshot snapshot = new AuthorizationSnapshot(roles, permissions,
        List.of("system", "system", "dataset"), Arrays.asList(200L, null, -2L, 200L));
    roles.clear();
    permissions.clear();
    assertEquals(List.of(4L, 9L), snapshot.getRoleIds());
    assertEquals(Set.of("dataset:read"), snapshot.getPermissionCodes());
    assertEquals(List.of("system", "dataset"), snapshot.getMenuCodes());
    assertEquals(Set.of(200L), snapshot.getProjectIds());
    assertThrows(UnsupportedOperationException.class, () -> snapshot.getRoleIds().add(22L));
    assertThrows(UnsupportedOperationException.class, () -> snapshot.getProjectIds().add(22L));
    assertThrows(UnsupportedOperationException.class, () -> snapshot.getPermissionCodes().clear());
  }

  @Test
  void ordinaryUserCannotAcquireRootOrUnassignedProject() {
    AuthorizationSnapshot scope = new AuthorizationSnapshot(
        List.of(9L), List.of("dataset:read"), List.of("datasets"), List.of(101L));
    assertTrue(scope.hasPermission("dataset:read"));
    assertFalse(scope.hasPermission("dataset:write"));
    assertFalse(scope.hasPermission(null));
    assertFalse(scope.hasPermission(" "));
    assertTrue(scope.canAccessProject(101L));
    assertFalse(scope.canAccessProject(102L));
    assertFalse(scope.canAccessProject(null));
    assertFalse(AuthorizationSnapshot.empty().canAccessProject(101L));
  }

  @Test
  void rootPermissionsBypassProjectListButNotNullProject() {
    AuthorizationSnapshot root = new AuthorizationSnapshot(
        List.of(), List.of(SecurityPermissionCode.ROOT), List.of(), List.of());
    assertTrue(root.hasPermission("any:permission"));
    assertTrue(root.canAccessProject(100L));
    assertFalse(root.canAccessProject(null));
    assertFalse(root.hasPermission(""));
  }

  @Test
  void currentUserContextKeepsLegacyFqcnAndRestoresAnonymous() {
    assertEquals("io.yak.framework.security.context.YakSecurityContext", YakSecurityContext.class.getName());
    assertEquals("io.yak.framework.security.context.CurrentUser", CurrentUser.class.getName());
    assertEquals("io.yak.framework.security.context.DefaultCurrentUser", DefaultCurrentUser.class.getName());
    assertFalse(YakSecurityContext.isAuthenticated());
    assertNull(YakSecurityContext.getCurrentUserId());

    var facts = new AuthorizationSnapshot(List.of(3L), List.of("dataset:read"),
        List.of("dataset"), List.of(51L));
    YakSecurityContext.setCurrentUser(new YakSecurityContext.ImmutableCurrentUser(
        7L, "analyst", 51L, facts, true));
    assertTrue(YakSecurityContext.isAuthenticated());
    assertEquals(Long.valueOf(7L), new DefaultCurrentUser().getUserId());
    assertEquals("analyst", YakSecurityContext.getCurrentUsername());
    assertTrue(new DefaultCurrentUser().hasPermission("dataset:read"));
    assertFalse(new DefaultCurrentUser().canAccessProject(52L));
    YakSecurityContext.clear();
    assertFalse(new DefaultCurrentUser().isAuthenticated());
    assertTrue(new DefaultCurrentUser().getProjectIds().isEmpty());
  }

  @Test
  void contextNeverLeaksToAnotherThreadOrOutlivesClearing() throws Exception {
    YakSecurityContext.setCurrentUser(new YakSecurityContext.ImmutableCurrentUser(
        7L, "parent", 51L, AuthorizationSnapshot.forRoleIds(List.of(5L)), true));
    var executor = Executors.newSingleThreadExecutor();
    try {
      var result = executor.submit(() -> {
        assertFalse(YakSecurityContext.isAuthenticated());
        YakSecurityContext.setCurrentUser(new YakSecurityContext.ImmutableCurrentUser(
            8L, "child", 52L, List.of(9L), true));
        try {
          return YakSecurityContext.getCurrentUserId();
        } finally {
          YakSecurityContext.clear();
        }
      });
      assertEquals(Long.valueOf(8L), result.get(5, TimeUnit.SECONDS));
      assertEquals(Long.valueOf(7L), YakSecurityContext.getCurrentUserId());
      assertFalse(executor.submit(YakSecurityContext::isAuthenticated).get(5, TimeUnit.SECONDS));
    } finally {
      executor.shutdownNow();
    }
  }
}
