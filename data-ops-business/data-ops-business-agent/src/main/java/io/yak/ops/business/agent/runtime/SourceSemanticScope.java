package io.yak.ops.business.agent.runtime;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * F-039 authorized, immutable selection of physical *schema* identities. No source rows,
 * credentials or arbitrary SQL may enter this value. The trusted caller owns project/ACL checks.
 */
public record SourceSemanticScope(long projectId, String dataSourceId, String database,
    String schema, String captureId, List<Table> tables) {

  public SourceSemanticScope {
    if (projectId <= 0) throw new IllegalArgumentException("projectId must be trusted and positive");
    dataSourceId = required(dataSourceId, "dataSourceId");
    database = required(database, "database");
    schema = Objects.requireNonNull(schema, "schema"); // some engines have no schema
    if (schema.length() > 128) throw new IllegalArgumentException("schema too long");
    captureId = required(captureId, "captureId");
    Objects.requireNonNull(tables, "tables");
    if (tables.isEmpty() || tables.size() > 20)
      throw new IllegalArgumentException("scope supports 1..20 explicitly selected tables");
    tables = tables.stream().map(Objects::requireNonNull)
        .sorted(java.util.Comparator.comparing(Table::assetKey)).toList();
    Set<String> seen = new HashSet<>();
    int columns = 0;
    for (Table table : tables) {
      if (!seen.add(table.assetKey())) throw new IllegalArgumentException("duplicate table");
      columns = Math.addExact(columns, table.columns().size());
    }
    if (columns > 500) throw new IllegalArgumentException("scope exceeds 500 columns");
  }

  public String fingerprint() {
    var parts = new ArrayList<String>();
    parts.add("f039-scope-v1");
    parts.add(Long.toString(projectId));
    parts.add(dataSourceId);
    parts.add(database);
    parts.add(schema);
    parts.add(captureId);
    for (Table table : tables) {
      parts.add("table");
      parts.add(table.assetKey());
      parts.add(table.fingerprint());
      parts.add(Integer.toString(table.columns().size()));
      parts.addAll(table.columns());
    }
    return digest(parts);
  }

  static String required(String value, String field) {
    if (value == null || value.isBlank() || value.length() > 128 || !value.equals(value.strip()))
      throw new IllegalArgumentException("invalid " + field);
    if (value.chars().anyMatch(c -> Character.isISOControl(c)))
      throw new IllegalArgumentException("control character in " + field);
    return value;
  }

  /** Length-prefixed canonical encoding avoids separator collision and order ambiguity. */
  static String digest(List<String> parts) {
    try {
      var bytes = new ByteArrayOutputStream();
      try (var out = new DataOutputStream(bytes)) {
        for (String part : parts) {
          byte[] utf8 = Objects.requireNonNull(part).getBytes(StandardCharsets.UTF_8);
          out.writeInt(utf8.length);
          out.write(utf8);
        }
      }
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
          .digest(bytes.toByteArray()));
    } catch (NoSuchAlgorithmException | IOException impossible) {
      throw new IllegalStateException("SHA-256 or in-memory serialization unavailable", impossible);
    }
  }

  public record Table(String assetKey, String fingerprint, List<String> columns) {
    public Table {
      assetKey = required(assetKey, "assetKey");
      fingerprint = required(fingerprint, "tableFingerprint");
      Objects.requireNonNull(columns, "columns");
      if (columns.isEmpty() || columns.size() > 500)
        throw new IllegalArgumentException("explicit columns required, at most 500");
      columns = columns.stream().map(c -> required(c, "columnId")).sorted().toList();
      if (new HashSet<>(columns).size() != columns.size())
        throw new IllegalArgumentException("duplicate column identity");
    }
  }
}
