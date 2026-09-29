package io.yak.ops.business.modeling.ddl;

import io.yak.ops.business.modeling.domain.ColumnDefinition;
import io.yak.ops.business.modeling.structure.StructureDialectCatalog;
import io.yak.ops.business.modeling.domain.ModelDialect;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 把模型保存的列类型落到目标方言可用的类型上（ticket 10）。
 *
 * <p>同名可用时完全按保存的结构渲染，保持 ticket 09「类型/长度直用保存的结构」的口径；
 * 一旦换了类型名，长度与小数位就按新类型在方言目录里的约束收敛——否则 MySQL 模型
 * 切到 PG 会生成 {@code BIGINT(20)} 这类语法上就不存在的写法。
 */
final class DdlTypeResolver {

  /** 最终类型名 + 该输出的长度/小数位 + 需要落到脚本里的提示（无提示为 null）。 */
  record Resolved(String dataType, Integer length, Integer scale, String note) {}

  /** 偏好链：取目标方言类型目录里第一个支持的类型，登记口径见 REQUIREMENTS.md「Ticket 10」。 */
  private static final Map<String, List<String>> PREFERENCE = Map.ofEntries(
      Map.entry("TINYINT", List.of("TINYINT", "INT8", "SMALLINT", "INT", "INTEGER")),
      Map.entry("SMALLINT", List.of("SMALLINT", "INT16", "INT", "INTEGER")),
      Map.entry("INT", List.of("INT", "INTEGER", "INT32", "NUMBER")),
      Map.entry("INTEGER", List.of("INTEGER", "INT", "INT32", "NUMBER")),
      Map.entry("BIGINT", List.of("BIGINT", "INT64", "LARGEINT", "NUMBER", "INT", "INTEGER")),
      Map.entry("LARGEINT", List.of("LARGEINT", "BIGINT", "INT64", "NUMBER")),
      Map.entry("DECIMAL", List.of("DECIMAL", "NUMERIC", "NUMBER")),
      Map.entry("NUMERIC", List.of("NUMERIC", "DECIMAL", "NUMBER")),
      Map.entry("FLOAT", List.of("FLOAT", "REAL", "FLOAT32", "BINARY_FLOAT")),
      Map.entry("DOUBLE", List.of("DOUBLE", "DOUBLE PRECISION", "FLOAT64", "BINARY_DOUBLE")),
      Map.entry("BOOLEAN", List.of("BOOLEAN", "TINYINT", "INT", "NUMBER")),
      Map.entry("CHAR", List.of("CHAR", "NCHAR", "FIXEDSTRING")),
      Map.entry("VARCHAR", List.of("VARCHAR", "VARCHAR2", "STRING", "TEXT")),
      Map.entry("STRING", List.of("STRING", "VARCHAR", "TEXT", "CLOB")),
      Map.entry("TEXT", List.of("TEXT", "STRING", "CLOB", "LONGTEXT")),
      Map.entry("JSON", List.of("JSON", "JSONB", "STRING", "CLOB")),
      Map.entry("JSONB", List.of("JSONB", "JSON", "STRING")),
      Map.entry("DATE", List.of("DATE", "DATETIME", "TIMESTAMP")),
      Map.entry("DATETIME", List.of("DATETIME", "TIMESTAMP", "DATE")),
      Map.entry("TIMESTAMP", List.of("TIMESTAMP", "DATETIME", "DATE")),
      Map.entry("TIME", List.of("TIME", "TIMESTAMP", "DATETIME")),
      Map.entry("UUID", List.of("UUID", "CHAR", "STRING", "VARCHAR")),
      Map.entry("BLOB", List.of("BLOB", "BYTEA", "BINARY", "RAW")),
      Map.entry("BYTEA", List.of("BYTEA", "BLOB", "BINARY")),
      Map.entry("VARBINARY", List.of("VARBINARY", "BLOB", "BYTEA", "RAW")));

  private DdlTypeResolver() {}

  /** 解析一列在目标方言下的类型写法；解析不到时原样输出并给提示，不抛异常。 */
  static Resolved resolve(ModelDialect dialect, ColumnDefinition column) {
    String raw = column.dataType() == null ? "" : column.dataType().trim();
    String stored = raw.toUpperCase(Locale.ROOT);
    StructureDialectCatalog.OptionalSpec direct =
        StructureDialectCatalog.lookupType(dialect, stored);
    if (direct != null) {
      return new Resolved(raw, column.length(), column.scale(), null);
    }
    for (String candidate : PREFERENCE.getOrDefault(stored, List.of())) {
      StructureDialectCatalog.OptionalSpec spec =
          StructureDialectCatalog.lookupType(dialect, candidate);
      if (spec != null) {
        return new Resolved(
            spec.name(),
            carryLength(spec, column.length()),
            spec.scaleAllowed() ? column.scale() : null,
            "类型 " + stored + " 在 " + dialect.name() + " 不支持，已按 " + spec.name() + " 输出");
      }
    }
    return new Resolved(
        stored,
        column.length(),
        column.scale(),
        "类型 " + stored + " 不在 " + dialect.name() + " 类型目录中，请人工确认");
  }

  /** 换过类型名后，长度只在该类型接受长度或精度时才跟过去。 */
  private static Integer carryLength(
      StructureDialectCatalog.OptionalSpec spec, Integer length) {
    if (length == null) {
      return null;
    }
    return spec.lengthRequired() || spec.scaleAllowed() ? length : null;
  }
}
