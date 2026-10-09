package io.yak.framework.security.util;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

class SensitiveDataSanitizerTest {
  @Test
  void shouldMaskBearerAndFormSecrets() {
    String source = "Authorization: Bearer abc123xyz password=p4ssw0rd cookie=abc987";
    String result = SensitiveDataSanitizer.sanitize(source);
    assertThat(result).contains("[REDACTED]");
    assertThat(result).doesNotContain("abc123xyz", "p4ssw0rd", "abc987");
  }

  @Test
  void shouldKeepNullAndNormalAuditText() {
    assertThat(SensitiveDataSanitizer.sanitize(null)).isNull();
    assertThat(SensitiveDataSanitizer.sanitize("successful request")).isEqualTo("successful request");
  }
}
