package io.yak.ops.business.dataservice.documentation;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

@Component
public class DocumentationFingerprint {
  public String sqlHash(String sql) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest((sql == null ? "" : sql).getBytes(StandardCharsets.UTF_8)));
    } catch (Exception exception) {
      throw new IllegalStateException("无法计算 SQL 文档指纹", exception);
    }
  }
}
