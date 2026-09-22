package io.yak.ops.business.datasource.gateway.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.yak.ops.business.datasource.config.DataSourceProperties;
import io.yak.ops.business.datasource.exception.DataSourceException;
import io.yak.ops.common.enums.datasource.DataSourceErrorCode;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;

/** 凭证静态加密的行为边界(Ticket 05)：密文自描述、明文兼容、失败一律响。 */
class AesGcmCredentialCipherTest {

  private static final String PLAIN_JSON = "{\"password\":\"Root@123456\",\"host\":\"127.0.0.1\"}";
  private static final String BASE64_KEY =
      Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8));

  @Test
  void roundTripsThroughSelfDescribingCiphertext() {
    AesGcmCredentialCipher cipher = cipher(BASE64_KEY);

    String encrypted = cipher.encrypt(PLAIN_JSON);

    assertThat(cipher.isEnabled()).isTrue();
    assertThat(encrypted).startsWith("ENC:").isNotEqualTo(PLAIN_JSON);
    assertThat(encrypted).doesNotContain("Root@123456");
    assertThat(cipher.decrypt(encrypted)).isEqualTo(PLAIN_JSON);
  }

  @Test
  void usesFreshIvSoIdenticalPlaintextsEncryptDifferently() {
    AesGcmCredentialCipher cipher = cipher(BASE64_KEY);

    String first = cipher.encrypt(PLAIN_JSON);
    String second = cipher.encrypt(PLAIN_JSON);

    assertThat(first).isNotEqualTo(second);
    assertThat(cipher.decrypt(first)).isEqualTo(PLAIN_JSON);
    assertThat(cipher.decrypt(second)).isEqualTo(PLAIN_JSON);
  }

  @Test
  void acceptsAlreadyEncryptedValueWithoutDoubleEncrypting() {
    AesGcmCredentialCipher cipher = cipher(BASE64_KEY);
    String encrypted = cipher.encrypt(PLAIN_JSON);

    assertThat(cipher.encrypt(encrypted)).isEqualTo(encrypted);
    assertThat(cipher.decrypt(encrypted)).isEqualTo(PLAIN_JSON);
  }

  @Test
  void passesThroughLegacyPlaintextRows() {
    AesGcmCredentialCipher cipher = cipher(BASE64_KEY);

    assertThat(cipher.decrypt(PLAIN_JSON)).isEqualTo(PLAIN_JSON);
    assertThat(cipher.decrypt(null)).isNull();
    assertThat(cipher.encrypt(null)).isNull();
    assertThat(cipher.encrypt("   ")).isEqualTo("   ");
  }

  @Test
  void staysTransparentWhenNoKeyIsConfigured() {
    AesGcmCredentialCipher cipher = cipher("");

    assertThat(cipher.isEnabled()).isFalse();
    assertThat(cipher.encrypt(PLAIN_JSON)).isEqualTo(PLAIN_JSON);
  }

  @Test
  void rejectsCiphertextLoudlyWhenKeyIsMissing() {
    String encrypted = cipher(BASE64_KEY).encrypt(PLAIN_JSON);

    assertThatThrownBy(() -> cipher("").decrypt(encrypted))
        .isInstanceOfSatisfying(
            DataSourceException.class,
            failure ->
                assertThat(failure.getErrorCode())
                    .isEqualTo(DataSourceErrorCode.CREDENTIAL_KEY_MISSING));
  }

  @Test
  void rejectsWrongKeyInsteadOfReturningGarbage() {
    String encrypted = cipher(BASE64_KEY).encrypt(PLAIN_JSON);
    String otherKey =
        Base64.getEncoder()
            .encodeToString("fedcba9876543210fedcba9876543210".getBytes(StandardCharsets.UTF_8));

    assertThatThrownBy(() -> cipher(otherKey).decrypt(encrypted))
        .isInstanceOfSatisfying(
            DataSourceException.class,
            failure ->
                assertThat(failure.getErrorCode())
                    .isEqualTo(DataSourceErrorCode.CREDENTIAL_CRYPTO_FAILED));
  }

  @Test
  void rejectsTamperedCiphertextPayload() {
    AesGcmCredentialCipher cipher = cipher(BASE64_KEY);
    String encrypted = cipher.encrypt(PLAIN_JSON);
    String tampered = encrypted.substring(0, encrypted.length() - 4) + "AAAA";

    assertThatThrownBy(() -> cipher.decrypt(tampered)).isInstanceOf(DataSourceException.class);
    assertThatThrownBy(() -> cipher.decrypt("ENC:not base64 at all!"))
        .isInstanceOfSatisfying(
            DataSourceException.class,
            failure ->
                assertThat(failure.getErrorCode())
                    .isEqualTo(DataSourceErrorCode.CREDENTIAL_CRYPTO_FAILED));
    assertThatThrownBy(() -> cipher.decrypt("ENC:" + Base64.getEncoder().encodeToString(new byte[4])))
        .isInstanceOf(DataSourceException.class);
  }

  @Test
  void derivesKeyFromArbitraryMasterKeyString() {
    // compose 里 YAK_OPS_DATASOURCE_MASTER_KEY 是任意随机串，不是 Base64；必须同样可用。
    AesGcmCredentialCipher cipher = cipher("replace_with_a_random_32_byte_secret_key");

    assertThat(cipher.isEnabled()).isTrue();
    assertThat(cipher.decrypt(cipher.encrypt(PLAIN_JSON))).isEqualTo(PLAIN_JSON);
  }

  private static AesGcmCredentialCipher cipher(String secretKey) {
    DataSourceProperties properties = new DataSourceProperties();
    properties.getCredential().setSecretKey(secretKey);
    return new AesGcmCredentialCipher(properties);
  }
}
