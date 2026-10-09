package io.yak.framework.security.extend;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URL;
import java.util.Collections;
import java.util.Enumeration;
import org.junit.jupiter.api.Test;

/**
 * The Starter remains the compatibility entry point but must no longer package
 * old copies of contracts and runtime classes now owned by Security Platform.
 */
class SecurityExtensionClasspathOwnershipTest {

  @Test
  void allMovedExtensionAndUtilityTypesHaveExactlyOneCompiledOwner() throws Exception {
    String[] originalNames = {
        "io.yak.framework.security.extend.PasswordEncoder",
        "io.yak.framework.security.extend.PermissionExtend",
        "io.yak.framework.security.extend.OperationLogExtend",
        "io.yak.framework.security.extend.CurrentUserProvider",
        "io.yak.framework.security.extend.impl.DefaultCurrentUserProvider",
        "io.yak.framework.security.extend.impl.DefaultPasswordEncoder",
        "io.yak.framework.security.extend.impl.DefaultPermissionExtend",
        "io.yak.framework.security.extend.impl.LoginAttemptGuard",
        "io.yak.framework.security.extend.impl.NoOpOperationLogExtend",
        "io.yak.framework.security.util.DatabaseNumberUtils",
        "io.yak.framework.security.util.JsonUtils",
        "io.yak.framework.security.util.MathUtil"
    };
    ClassLoader loader = Thread.currentThread().getContextClassLoader();
    for (String name : originalNames) {
      Class<?> type = Class.forName(name, false, loader);
      assertThat(type.getName()).isEqualTo(name);
      Enumeration<URL> resources = loader.getResources(name.replace('.', '/') + ".class");
      assertThat(Collections.list(resources))
          .as("Compiled classpath owners for " + name)
          .hasSize(1);
    }
    assertThat(PasswordEncoder.class.isAssignableFrom(
        Class.forName("io.yak.framework.security.extend.impl.DefaultPasswordEncoder")))
        .isTrue();
  }
}
