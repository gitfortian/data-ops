package io.yak.ops.business.agent.runtime;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Non-secret fingerprints of runtime contracts; callers never log the input. */
final class RuntimeContractHash {
  private RuntimeContractHash() {}
  static String hash(String value) {
    try {
      return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
          .digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (java.security.NoSuchAlgorithmException impossible) {
      throw new IllegalStateException("SHA-256 unavailable", impossible);
    }
  }
}
