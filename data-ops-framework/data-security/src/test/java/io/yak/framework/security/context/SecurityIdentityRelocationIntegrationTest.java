package io.yak.framework.security.context;

import io.yak.framework.security.authentication.AuthenticationManager;
import io.yak.framework.security.authentication.SaTokenAuthenticationManager;
import io.yak.framework.security.common.entity.user.User;
import io.yak.framework.security.service.PermissionCache;
import io.yak.framework.security.service.impl.CaffeinePermissionCache;
import io.yak.framework.security.service.impl.MenuSelectionCodec;
import io.yak.framework.security.service.impl.MenuAwareRolePermissionService;
import java.net.URL;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Starter-to-Platform runtime linkage and exactly-one binary owner for relocated FQCNs. */
class SecurityIdentityRelocationIntegrationTest {
  private static final String PREFIX = "io/yak/framework/security/";
  private static final List<String> OWNERS = List.of(
      "authentication/AuthenticationManager",
      "context/AuthorizationSnapshot",
      "context/CurrentUser",
      "context/DefaultCurrentUser",
      "context/YakSecurityContext",
      "context/YakSecurityContext$ImmutableCurrentUser",
      "service/PermissionCache",
      "service/impl/MenuSelectionCodec",
      "common/entity/user/User",
      "common/entity/user/UserBrief",
      "common/entity/dept/Dept",
      "common/entity/dept/DeptBrief");

  @Test
  void legacyRuntimeTypesLoadFromExactlyOneClasspathArtifact() throws Exception {
    ClassLoader loader = getClass().getClassLoader();
    for (String cls : OWNERS) {
      String resourceName = PREFIX + cls + ".class";
      List<URL> classResources = Collections.list(loader.getResources(resourceName));
      assertEquals(1, classResources.size(), resourceName + " has duplicate/missing owner: " + classResources);
      assertNotNull(Class.forName((PREFIX + cls).replace('/', '.')));
    }
  }

  @Test
  void originalSpringAndSaTokenAdaptersStillUsePlatformContracts() throws Exception {
    assertTrue(AuthenticationManager.class.isAssignableFrom(SaTokenAuthenticationManager.class));
    assertTrue(PermissionCache.class.isAssignableFrom(CaffeinePermissionCache.class));
    assertNotNull(MenuAwareRolePermissionService.class);
    assertNotNull(MenuSelectionCodec.class);
    assertNotNull(User.class.getDeclaredConstructor().newInstance());
  }

  @Test
  void legacySecurityContextFilterCanStillAccessPackagePrivatePlatformContext() {
    assertEquals("io.yak.framework.security.context.YakSecurityContextFilter",
        YakSecurityContextFilter.class.getName());
    assertEquals("io.yak.framework.security.context.TrustedUserScope",
        TrustedUserScope.class.getName());
    // Compile/linkage of this package-private API across the original Starter and new Platform jars.
    YakSecurityContext.setCurrentUser(new YakSecurityContext.ImmutableCurrentUser(
        9L, "worker", 20L, AuthorizationSnapshot.forRoleIds(List.of(11L)), true));
    try {
      assertEquals(Long.valueOf(9), YakSecurityContext.getCurrentUserId());
      assertTrue(YakSecurityContext.isAuthenticated());
    } finally {
      YakSecurityContext.clear();
    }
    assertFalse(YakSecurityContext.isAuthenticated());
  }
}
