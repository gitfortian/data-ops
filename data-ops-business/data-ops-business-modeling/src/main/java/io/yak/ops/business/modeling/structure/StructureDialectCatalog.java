package io.yak.ops.business.modeling.structure;

import io.yak.ops.business.modeling.domain.ModelDialect;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Per-dialect data-type catalog and reserved words (single source for the
 * editor's type dropdown and server-side validation, ticket 07).
 */
public final class StructureDialectCatalog {

  /** One data-type entry of a dialect: whether length is required and scale is allowed. */
  public record TypeSpec(String name, boolean lengthRequired, boolean scaleAllowed) {}

  private record DialectProfile(List<TypeSpec> types, Set<String> reservedWords) {}

  private static final Map<String, DialectProfile> PROFILES =
      Map.ofEntries(
          Map.entry(
              "MYSQL",
              new DialectProfile(
                  List.of(
                      new TypeSpec("BIT", false, false),
                      new TypeSpec("TINYINT", false, false),
                      new TypeSpec("SMALLINT", false, false),
                      new TypeSpec("INT", false, false),
                      new TypeSpec("BIGINT", false, false),
                      new TypeSpec("DECIMAL", false, true),
                      new TypeSpec("FLOAT", false, false),
                      new TypeSpec("DOUBLE", false, false),
                      new TypeSpec("BOOLEAN", false, false),
                      new TypeSpec("CHAR", true, false),
                      new TypeSpec("VARCHAR", true, false),
                      new TypeSpec("BINARY", true, false),
                      new TypeSpec("VARBINARY", true, false),
                      new TypeSpec("TINYTEXT", false, false),
                      new TypeSpec("TEXT", false, false),
                      new TypeSpec("MEDIUMTEXT", false, false),
                      new TypeSpec("LONGTEXT", false, false),
                      new TypeSpec("TINYBLOB", false, false),
                      new TypeSpec("BLOB", false, false),
                      new TypeSpec("MEDIUMBLOB", false, false),
                      new TypeSpec("LONGBLOB", false, false),
                      new TypeSpec("DATE", false, false),
                      new TypeSpec("DATETIME", false, false),
                      new TypeSpec("TIMESTAMP", false, false),
                      new TypeSpec("TIME", false, false),
                      new TypeSpec("YEAR", false, false),
                      new TypeSpec("JSON", false, false)),
                  Set.of(
                      "SELECT", "INSERT", "UPDATE", "DELETE", "TABLE", "INDEX", "PRIMARY", "KEY",
                      "ORDER", "GROUP", "WHERE", "JOIN", "LEFT", "RIGHT", "INNER", "OUTER", "UNION",
                      "CREATE", "DROP", "ALTER", "COLUMN", "DATABASE", "SCHEMA", "VALUES",
                      "PARTITION", "UNIQUE", "DEFAULT", "CONSTRAINT", "FOREIGN", "REFERENCES",
                      "CHECK", "INTERVAL", "RANGE", "LIST", "HASH", "CONDITION", "TRIGGER"))),
          Map.entry(
              "POSTGRESQL",
              new DialectProfile(
                  List.of(
                      new TypeSpec("SMALLINT", false, false),
                      new TypeSpec("INTEGER", false, false),
                      new TypeSpec("BIGINT", false, false),
                      new TypeSpec("NUMERIC", false, true),
                      new TypeSpec("REAL", false, false),
                      new TypeSpec("DOUBLE PRECISION", false, false),
                      new TypeSpec("BOOLEAN", false, false),
                      new TypeSpec("CHAR", true, false),
                      new TypeSpec("VARCHAR", true, false),
                      new TypeSpec("TEXT", false, false),
                      new TypeSpec("DATE", false, false),
                      new TypeSpec("TIMESTAMP", false, false),
                      new TypeSpec("TIMESTAMPTZ", false, false),
                      new TypeSpec("TIME", false, false),
                      new TypeSpec("JSONB", false, false),
                      new TypeSpec("UUID", false, false),
                      new TypeSpec("BYTEA", false, false)),
                  Set.of(
                      "SELECT", "INSERT", "UPDATE", "DELETE", "TABLE", "INDEX", "PRIMARY", "KEY",
                      "ORDER", "GROUP", "WHERE", "JOIN", "UNION", "CREATE", "DROP", "ALTER",
                      "COLUMN", "DATABASE", "SCHEMA", "VALUES", "PARTITION", "UNIQUE", "DEFAULT",
                      "CONSTRAINT", "FOREIGN", "REFERENCES", "CHECK", "INTERVAL", "RANGE", "LIST",
                      "HASH", "USER", "CURRENT_DATE", "CURRENT_TIMESTAMP", "CASE", "WHEN",
                      "THEN", "ELSE", "END", "NULL", "NOT", "AND", "OR"))),
          Map.entry(
              "ORACLE",
              new DialectProfile(
                  List.of(
                      new TypeSpec("NUMBER", false, true),
                      new TypeSpec("BINARY_FLOAT", false, false),
                      new TypeSpec("BINARY_DOUBLE", false, false),
                      new TypeSpec("CHAR", true, false),
                      new TypeSpec("VARCHAR2", true, false),
                      new TypeSpec("CLOB", false, false),
                      new TypeSpec("NCLOB", false, false),
                      new TypeSpec("DATE", false, false),
                      new TypeSpec("TIMESTAMP", false, false),
                      new TypeSpec("RAW", true, false),
                      new TypeSpec("BLOB", false, false)),
                  Set.of(
                      "SELECT", "INSERT", "UPDATE", "DELETE", "TABLE", "INDEX", "PRIMARY", "KEY",
                      "ORDER", "GROUP", "WHERE", "JOIN", "UNION", "CREATE", "DROP", "ALTER",
                      "COLUMN", "VALUES", "PARTITION", "UNIQUE", "DEFAULT", "CONSTRAINT",
                      "FOREIGN", "REFERENCES", "CHECK", "INTERVAL", "RANGE", "LIST", "HASH",
                      "USER", "LEVEL", "SIZE", "TYPE", "ROW", "ROWS", "COMMENT", "DATE"))),
          Map.entry(
              "DORIS",
              new DialectProfile(
                  List.of(
                      new TypeSpec("TINYINT", false, false),
                      new TypeSpec("SMALLINT", false, false),
                      new TypeSpec("INT", false, false),
                      new TypeSpec("BIGINT", false, false),
                      new TypeSpec("LARGEINT", false, false),
                      new TypeSpec("DECIMAL", false, true),
                      new TypeSpec("FLOAT", false, false),
                      new TypeSpec("DOUBLE", false, false),
                      new TypeSpec("BOOLEAN", false, false),
                      new TypeSpec("CHAR", true, false),
                      new TypeSpec("VARCHAR", true, false),
                      new TypeSpec("STRING", false, false),
                      new TypeSpec("DATE", false, false),
                      new TypeSpec("DATETIME", false, false),
                      new TypeSpec("JSONB", false, false)),
                  Set.of(
                      "SELECT", "INSERT", "UPDATE", "DELETE", "TABLE", "INDEX", "PRIMARY", "KEY",
                      "ORDER", "GROUP", "WHERE", "JOIN", "UNION", "CREATE", "DROP", "ALTER",
                      "COLUMN", "DATABASE", "VALUES", "PARTITION", "UNIQUE", "DEFAULT",
                      "CONSTRAINT", "FOREIGN", "REFERENCES", "CHECK", "RANGE", "LIST", "HASH",
                      "DUPLICATE", "AGGREGATE", "DISTRIBUTED", "BUCKETS"))),
          Map.entry(
              "CLICKHOUSE",
              new DialectProfile(
                  List.of(
                      new TypeSpec("INT8", false, false),
                      new TypeSpec("INT16", false, false),
                      new TypeSpec("INT32", false, false),
                      new TypeSpec("INT64", false, false),
                      new TypeSpec("FLOAT32", false, false),
                      new TypeSpec("FLOAT64", false, false),
                      new TypeSpec("DECIMAL", false, true),
                      new TypeSpec("BOOLEAN", false, false),
                      new TypeSpec("STRING", false, false),
                      new TypeSpec("FIXEDSTRING", true, false),
                      new TypeSpec("DATE", false, false),
                      new TypeSpec("DATETIME", false, false),
                      new TypeSpec("UUID", false, false)),
                  Set.of(
                      "SELECT", "INSERT", "UPDATE", "DELETE", "TABLE", "INDEX", "PRIMARY", "KEY",
                      "ORDER", "GROUP", "WHERE", "JOIN", "UNION", "CREATE", "DROP", "ALTER",
                      "COLUMN", "DATABASE", "VALUES", "PARTITION", "UNIQUE", "DEFAULT",
                      "CHECK", "RANGE", "HASH"))),
          Map.entry(
              "STARROCKS",
              new DialectProfile(
                  List.of(
                      new TypeSpec("TINYINT", false, false),
                      new TypeSpec("SMALLINT", false, false),
                      new TypeSpec("INT", false, false),
                      new TypeSpec("BIGINT", false, false),
                      new TypeSpec("LARGEINT", false, false),
                      new TypeSpec("DECIMAL", false, true),
                      new TypeSpec("FLOAT", false, false),
                      new TypeSpec("DOUBLE", false, false),
                      new TypeSpec("BOOLEAN", false, false),
                      new TypeSpec("CHAR", true, false),
                      new TypeSpec("VARCHAR", true, false),
                      new TypeSpec("STRING", false, false),
                      new TypeSpec("DATE", false, false),
                      new TypeSpec("DATETIME", false, false),
                      new TypeSpec("JSON", false, false)),
                  Set.of(
                      "SELECT", "INSERT", "UPDATE", "DELETE", "TABLE", "INDEX", "PRIMARY", "KEY",
                      "ORDER", "GROUP", "WHERE", "JOIN", "UNION", "CREATE", "DROP", "ALTER",
                      "COLUMN", "DATABASE", "VALUES", "PARTITION", "UNIQUE", "DEFAULT",
                      "CONSTRAINT", "FOREIGN", "REFERENCES", "CHECK", "RANGE", "LIST", "HASH",
                      "DUPLICATE", "AGGREGATE", "DISTRIBUTED", "BUCKETS", "INTERVAL"))));

  private StructureDialectCatalog() {}

  private static DialectProfile profile(ModelDialect dialect) {
    return dialect == null ? null : PROFILES.get(dialect.name());
  }

  /** Type catalog of a dialect; empty when the dialect is unknown. */
  public static List<TypeSpec> types(ModelDialect dialect) {
    DialectProfile profile = profile(dialect);
    return profile == null ? List.of() : profile.types();
  }

  /** Reserved words of a dialect (case-insensitive match); empty when unknown. */
  public static boolean isReservedWord(ModelDialect dialect, String identifier) {
    DialectProfile profile = profile(dialect);
    if (profile == null || identifier == null) {
      return false;
    }
    return profile.reservedWords().contains(identifier.toUpperCase(Locale.ROOT));
  }

  /** Case-insensitive lookup of a type in the dialect catalog. */
  public static OptionalSpec lookupType(ModelDialect dialect, String dataType) {
    DialectProfile profile = profile(dialect);
    if (profile == null || dataType == null || dataType.isBlank()) {
      return null;
    }
    String normalized = dataType.trim().toUpperCase(Locale.ROOT);
    return profile.types().stream()
        .filter(spec -> spec.name().toUpperCase(Locale.ROOT).equals(normalized))
        .findFirst()
        .map(spec -> new OptionalSpec(spec.name(), spec.lengthRequired(), spec.scaleAllowed()))
        .orElse(null);
  }

  /** Result wrapper so callers can distinguish "not in catalog" from "dialect unknown". */
  public record OptionalSpec(String name, boolean lengthRequired, boolean scaleAllowed) {}
}
