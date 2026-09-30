package io.yak.ops.business.semantic.catalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.semantic.api.Standard;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

/** Content token includes membership, labels, ordering, definition and status. */
public final class CodeSetRevision {
  private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();
  private CodeSetRevision() {}

  public static String of(List<Standard> rows) {
    try {
      String payload = MAPPER.writeValueAsString(rows.stream()
          .sorted(Comparator.comparing(Standard::id)).toList());
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
          .digest(payload.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception exception) {
      throw new IllegalStateException("码集版本计算失败", exception);
    }
  }
}
