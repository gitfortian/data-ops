package io.yak.ops.business.dataservice.access;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

@Component
public class ApiKeySecretGenerator {
  private static final String KEY_PREFIX = "yak_ds_";
  private final SecureRandom secureRandom = new SecureRandom();

  public SecretMaterial create() {
    byte[] bytes = new byte[32];
    secureRandom.nextBytes(bytes);
    String raw = KEY_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    return new SecretMaterial(raw, raw.substring(0, Math.min(16, raw.length())), hash(raw));
  }

  public String hash(String value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException("SHA-256 is unavailable", impossible);
    }
  }

  public record SecretMaterial(String rawKey, String prefix, String hash) {}
}
