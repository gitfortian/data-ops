package io.yak.ops.business.asset.application;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Length-framed editable snapshot fingerprint; not an authorization token. */
public final class AssetSnapshotFingerprint {
  private AssetSnapshotFingerprint() {}

  public static String of(Long id, String name, String description, String uri) {
    try {
      var hash = MessageDigest.getInstance("SHA-256");
      for (Object value : new Object[] {id, name, description, uri}) {
        byte[] bytes = value == null ? null : value.toString().getBytes(StandardCharsets.UTF_8);
        hash.update(ByteBuffer.allocate(4).putInt(bytes == null ? -1 : bytes.length).array());
        if (bytes != null) hash.update(bytes);
      }
      return HexFormat.of().formatHex(hash.digest());
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException("SHA-256 unavailable", impossible);
    }
  }
}
