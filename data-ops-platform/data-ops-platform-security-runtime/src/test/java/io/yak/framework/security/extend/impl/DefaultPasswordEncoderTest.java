package io.yak.framework.security.extend.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class DefaultPasswordEncoderTest {
  @Test
  void preservesBcryptStorageAndPasswordMatchingCompatibility() {
    DefaultPasswordEncoder encoder = new DefaultPasswordEncoder(4);
    String hash = encoder.encode("safe-passphrase");
    assertThat(hash).startsWith("$2");
    assertThat(encoder.matches("safe-passphrase", hash)).isTrue();
    assertThat(encoder.matches("incorrect", hash)).isFalse();
    assertThat(encoder.matches("safe-passphrase", "invalid-hash")).isFalse();
  }

  @Test
  void rejectsBlankPasswordAndIgnoresNullCredentialOnVerification() {
    DefaultPasswordEncoder encoder = new DefaultPasswordEncoder(4);
    assertThatThrownBy(() -> encoder.encode("   "))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(encoder.matches(null, "not-a-bcrypt-hash")).isFalse();
    assertThat(encoder.matches("password", null)).isFalse();
  }
}
