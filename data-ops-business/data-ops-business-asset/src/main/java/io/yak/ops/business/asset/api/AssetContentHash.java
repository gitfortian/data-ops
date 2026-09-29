package io.yak.ops.business.asset.api;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 元数据指纹:对参与对账比对的字段按序做 SHA-256;null 用哨兵区分,字段间以 NUL 分隔防拼接歧义。
 * 源域 provider 与 asset 侧变更判定共用,保证两侧口径一致。
 */
public final class AssetContentHash {

  private static final String NULL_SENTINEL = "\u0001";

  private AssetContentHash() {}

  public static String of(String... parts) {
    StringBuilder joined = new StringBuilder();
    for (String part : parts) {
      if (!joined.isEmpty()) {
        joined.append('\u0000');
      }
      joined.append(part == null ? NULL_SENTINEL : part);
    }
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(joined.toString().getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 unavailable", e);
    }
  }
}
