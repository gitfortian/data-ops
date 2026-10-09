package io.yak.framework.security.extend;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

class SecurityExtensionContractAbiTest {
  @Test
  void passwordPermissionAndAuditInterfacesRetainOriginalBinaryDescriptors() throws Exception {
    assertThat(PasswordEncoder.class.getName())
        .isEqualTo("io.yak.framework.security.extend.PasswordEncoder");
    Method encode = PasswordEncoder.class.getMethod("encode", CharSequence.class);
    assertThat(encode.getReturnType()).isEqualTo(String.class);
    Method matches = PasswordEncoder.class.getMethod("matches", CharSequence.class, String.class);
    assertThat(matches.getReturnType()).isEqualTo(boolean.class);
    assertThat(PermissionExtend.class.getMethod("hasPermission", String.class, String.class)
        .getReturnType()).isEqualTo(boolean.class);
    assertThat(OperationLogExtend.class.getMethod("record",
        String.class, String.class, String.class, String.class).getReturnType())
        .isEqualTo(void.class);
  }
}
