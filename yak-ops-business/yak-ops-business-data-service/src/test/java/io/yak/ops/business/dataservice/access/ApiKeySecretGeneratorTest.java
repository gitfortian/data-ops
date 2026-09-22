package io.yak.ops.business.dataservice.access;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ApiKeySecretGeneratorTest {

  @Test
  void rawSecretIsDifferentFromPersistedHashAndKeepsSafePrefix() {
    ApiKeySecretGenerator generator = new ApiKeySecretGenerator();
    ApiKeySecretGenerator.SecretMaterial material = generator.create();

    assertThat(material.rawKey()).startsWith("yak_ds_");
    assertThat(material.prefix()).isEqualTo(material.rawKey().substring(0, 16));
    assertThat(material.hash()).isNotEqualTo(material.rawKey());
    assertThat(generator.hash(material.rawKey())).isEqualTo(material.hash());
  }
}
