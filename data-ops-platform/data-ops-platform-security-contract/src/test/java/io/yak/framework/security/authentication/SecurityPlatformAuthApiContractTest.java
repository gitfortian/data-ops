package io.yak.framework.security.authentication;

import io.yak.framework.security.context.AuthorizationSnapshot;
import io.yak.framework.security.service.PermissionCache;
import io.yak.framework.security.service.impl.MenuSelectionCodec;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** No Sa-Token/MyBatis runtime dependency is needed for pure Platform auth/cache/menu contracts. */
class SecurityPlatformAuthApiContractTest {
  @Test
  void legacyAuthenticationBoundaryKeepsDefaultLoginMetadataAndLogoutBehavior() {
    class MinimalAuthenticationManager implements AuthenticationManager {
      Long lastLogin;
      @Override public void login(Long userId) { lastLogin = userId; }
      @Override public void logout() {}
      @Override public boolean isLogin() { return lastLogin != null; }
      @Override public Long getLoginUserId() { return lastLogin; }
    }
    var manager = new MinimalAuthenticationManager();
    assertEquals("io.yak.framework.security.authentication.AuthenticationManager",
        AuthenticationManager.class.getName());
    manager.login(42L, "analyst");
    assertEquals(Long.valueOf(42L), manager.lastLogin);
    assertTrue(manager.isLogin());
    manager.logoutUser(42L); // no-op on old implementors without the override
    assertEquals(Long.valueOf(42L), manager.getLoginUserId());
    assertNull(manager.getLoginUsername());
  }

  @Test
  void customPermissionCacheDefaultsToUncachedSnapshotWithoutChangingOldImplementations() {
    var loaderCalls = new AtomicInteger();
    PermissionCache cache = new PermissionCache() {
      @Override public Set<String> get(Long id, Supplier<Set<String>> loader) { return loader.get(); }
      @Override public void invalidateUser(Long id) {}
      @Override public void invalidateRole(Long id) {}
      @Override public void invalidateAll() {}
    };
    assertEquals("io.yak.framework.security.service.PermissionCache", PermissionCache.class.getName());
    assertSame(AuthorizationSnapshot.empty(),
        cache.getAuthorizationSnapshot(null, () -> {
          loaderCalls.incrementAndGet();
          return AuthorizationSnapshot.forRoleIds(List.of(11L));
        }));
    assertEquals(0, loaderCalls.get());
    assertSame(AuthorizationSnapshot.empty(),
        cache.getAuthorizationSnapshot(42L, () -> {
          loaderCalls.incrementAndGet();
          return null;
        }));
    assertEquals(1, loaderCalls.get());
    AuthorizationSnapshot snapshot = cache.getAuthorizationSnapshot(42L, () -> {
      loaderCalls.incrementAndGet();
      return AuthorizationSnapshot.forRoleIds(List.of(5L));
    });
    assertEquals(List.of(5L), snapshot.getRoleIds());
    assertEquals(2, loaderCalls.get());
  }

  @Test
  void roleMenuSelectionPreservesNegativeIdEncodingAndDedupOrder() {
    assertEquals("io.yak.framework.security.service.impl.MenuSelectionCodec",
        MenuSelectionCodec.class.getName());
    assertEquals(-1L, MenuSelectionCodec.MENU_GROUP_NODE_ID);
    assertEquals(-20L, MenuSelectionCodec.encodeMenuId(19L));
    assertTrue(MenuSelectionCodec.isEncodedMenuId(-20L));
    assertFalse(MenuSelectionCodec.isEncodedMenuId(-1L));
    assertEquals(19L, MenuSelectionCodec.decodeMenuId(-20L));
    assertEquals(List.of(3L, 7L),
        MenuSelectionCodec.extractPermissionIds(Arrays.asList(null, 3L, -1L, -20L, 7L, 3L)));
    assertEquals(List.of(19L, 11L),
        MenuSelectionCodec.extractMenuIds(Arrays.asList(null, -20L, -1L, 7L, -12L, -20L)));
    assertThrows(IllegalArgumentException.class, () -> MenuSelectionCodec.encodeMenuId(null));
    assertThrows(IllegalArgumentException.class, () -> MenuSelectionCodec.decodeMenuId(-1L));
    assertTrue(MenuSelectionCodec.extractMenuIds(null).isEmpty());
  }
}
