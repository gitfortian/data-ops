package io.yak.ops.business.datasource.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SensitiveTextMaskerTest {

  private final SensitiveTextMasker masker = new SensitiveTextMasker();

  @Test
  void masksJdbcCredentialsAndQuerySecrets() {
    assertThat(
            masker.mask(
                "jdbc:mysql://user:TEST_ONLY_PASSWORD@db/demo?token=TEST_ONLY_TOKEN"))
        .isEqualTo("jdbc:mysql://user:******@db/demo?token=******");
  }
}
