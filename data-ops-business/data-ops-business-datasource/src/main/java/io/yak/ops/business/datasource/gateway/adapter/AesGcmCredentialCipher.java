package io.yak.ops.business.datasource.gateway.adapter;

import io.yak.ops.business.datasource.config.DataSourceProperties;
import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import io.yak.ops.business.datasource.config.CredentialCipher;
import io.yak.ops.business.datasource.exception.DataSourceException;
import io.yak.ops.common.enums.datasource.DataSourceErrorCode;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * AES-256-GCM 凭证加解密(Ticket 05)。密文格式 {@code ENC:Base64(iv(12B) ‖ ciphertext ‖ tag)}。
 *
 * <p>GCM 自带认证标签，密钥不匹配或密文被改写都会解密失败，因此不存在"解出乱码继续用"的静默路径。
 */
@Component
@ConditionalOnDataSourceEnabled
public class AesGcmCredentialCipher implements CredentialCipher {

  private static final int KEY_BYTES = 32;
  private static final int IV_BYTES = 12;
  private static final int TAG_BITS = 128;
  private static final String TRANSFORMATION = "AES/GCM/NoPadding";

  private final SecretKeySpec secretKey;
  private final SecureRandom secureRandom = new SecureRandom();

  public AesGcmCredentialCipher(DataSourceProperties properties) {
    this.secretKey = resolveKey(properties.getCredential().getSecretKey());
  }

  @Override
  public boolean isEnabled() {
    return secretKey != null;
  }

  @Override
  public String encrypt(String value) {
    if (!StringUtils.hasText(value) || secretKey == null || value.startsWith(ENCRYPTED_PREFIX)) {
      return value;
    }
    try {
      byte[] iv = new byte[IV_BYTES];
      secureRandom.nextBytes(iv);
      byte[] cipherText = cipher(Cipher.ENCRYPT_MODE, iv).doFinal(value.getBytes(StandardCharsets.UTF_8));
      byte[] payload = new byte[iv.length + cipherText.length];
      System.arraycopy(iv, 0, payload, 0, iv.length);
      System.arraycopy(cipherText, 0, payload, iv.length, cipherText.length);
      return ENCRYPTED_PREFIX + Base64.getEncoder().encodeToString(payload);
    } catch (GeneralSecurityException failure) {
      throw new DataSourceException(DataSourceErrorCode.CREDENTIAL_CRYPTO_FAILED, "凭证加密失败", failure);
    }
  }

  @Override
  public String decrypt(String value) {
    if (value == null || !value.startsWith(ENCRYPTED_PREFIX)) {
      // 明文兼容：历史行/无密钥模式按原样放行，下次写入自然升级为密文。
      return value;
    }
    if (secretKey == null) {
      throw new DataSourceException(
          DataSourceErrorCode.CREDENTIAL_KEY_MISSING,
          "库中已存在加密凭证，但 yak.datasource.credential.secret-key 未配置");
    }
    byte[] payload = decode(value.substring(ENCRYPTED_PREFIX.length()));
    if (payload.length <= IV_BYTES) {
      throw cryptoFailed("密文长度不合法");
    }
    try {
      byte[] iv = Arrays.copyOfRange(payload, 0, IV_BYTES);
      byte[] cipherText = Arrays.copyOfRange(payload, IV_BYTES, payload.length);
      return new String(cipher(Cipher.DECRYPT_MODE, iv).doFinal(cipherText), StandardCharsets.UTF_8);
    } catch (GeneralSecurityException failure) {
      throw cryptoFailed(failure.getMessage());
    }
  }

  private Cipher cipher(int mode, byte[] iv) throws GeneralSecurityException {
    Cipher cipher = Cipher.getInstance(TRANSFORMATION);
    cipher.init(mode, secretKey, new GCMParameterSpec(TAG_BITS, iv));
    return cipher;
  }

  private static byte[] decode(String base64) {
    try {
      return Base64.getDecoder().decode(base64);
    } catch (IllegalArgumentException malformed) {
      throw new DataSourceException(
          DataSourceErrorCode.CREDENTIAL_CRYPTO_FAILED, "密文不是合法 Base64", malformed);
    }
  }

  private static DataSourceException cryptoFailed(String detail) {
    return new DataSourceException(DataSourceErrorCode.CREDENTIAL_CRYPTO_FAILED, detail);
  }

  private static SecretKeySpec resolveKey(String configuredKey) {
    if (!StringUtils.hasText(configuredKey)) return null;
    String value = configuredKey.trim();
    byte[] decoded = tryDecodeBase64(value);
    // 只有"Base64 且正好 32 字节"才按原始密钥使用（openssl rand -base64 32）；
    // 其余一律 SHA-256 派生，以兼容 compose 里任意长度的 YAK_OPS_DATASOURCE_MASTER_KEY。
    byte[] key =
        decoded != null && decoded.length == KEY_BYTES ? decoded : sha256(value);
    return new SecretKeySpec(key, "AES");
  }

  private static byte[] tryDecodeBase64(String value) {
    try {
      return Base64.getDecoder().decode(value);
    } catch (IllegalArgumentException notBase64) {
      return null;
    }
  }

  private static byte[] sha256(String value) {
    try {
      return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
    } catch (NoSuchAlgorithmException unavailable) {
      throw new IllegalStateException("当前 JVM 不支持 SHA-256，无法派生凭证密钥", unavailable);
    }
  }
}
