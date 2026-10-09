package io.yak.framework.security.extend.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;

class DefaultPermissionAndAuditExtensionsTest {
  @Test
  void additionalPermissionExtensionRemainsDenyByDefault() {
    DefaultPermissionExtend extension = new DefaultPermissionExtend();
    assertThat(extension.hasPermission("root", "security:root")).isFalse();
    assertThat(extension.hasPermission(null, null)).isFalse();
  }

  @Test
  void noopAuditImplementationRetainsNoPersistenceSideEffects() {
    NoOpOperationLogExtend extension = new NoOpOperationLogExtend();
    assertThatCode(() -> extension.record(null, "update", "sample", "detail"))
        .doesNotThrowAnyException();
  }
}
