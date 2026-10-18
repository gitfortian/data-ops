package io.yak.ops.business.semantic.catalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.semantic.api.Standard;
import io.yak.ops.business.semantic.api.StandardKind;
import io.yak.ops.business.semantic.api.StandardStatus;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

/**
 * Content token includes membership, labels, ordering, definition, version and status.
 * Audit metadata (creator and timestamps) is deliberately excluded.
 */
public final class CodeSetRevision {
  private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();
  private CodeSetRevision() {}

  public static String of(List<Standard> rows) {
    try {
      String payload = MAPPER.writeValueAsString(rows.stream()
          .sorted(Comparator.comparing(Standard::id))
          .map(CodeSetRevision::contentOf)
          .toList());
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
          .digest(payload.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception exception) {
      throw new IllegalStateException("码集版本计算失败", exception);
    }
  }

  private static CodeSetMember contentOf(Standard row) {
    return new CodeSetMember(row.id(), row.kind(), row.code(), row.name(), row.status(),
        row.version(), row.sortOrder(), row.preset(), row.description(), row.fields());
  }

  private record CodeSetMember(
      Long id,
      StandardKind kind,
      String code,
      String name,
      StandardStatus status,
      int version,
      int sortOrder,
      boolean preset,
      String description,
      Standard.KindFields fields) {}
}
