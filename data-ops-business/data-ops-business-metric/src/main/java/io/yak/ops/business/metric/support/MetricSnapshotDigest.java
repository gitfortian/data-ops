package io.yak.ops.business.metric.support;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Shared SHA-256 identity for immutable MetricVersion snapshots. */
public final class MetricSnapshotDigest {

  private MetricSnapshotDigest() {}

  public static String sha256(String snapshot) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(
          digest.digest((snapshot == null ? "" : snapshot).getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is unavailable", exception);
    }
  }
}
