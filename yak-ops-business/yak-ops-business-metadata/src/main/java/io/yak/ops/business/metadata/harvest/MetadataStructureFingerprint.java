package io.yak.ops.business.metadata.harvest;

import io.yak.ops.business.metadata.metamodel.MetadataKeyCodec;
import io.yak.ops.spi.datasource.metadata.DataSourceColumn;
import io.yak.ops.spi.datasource.metadata.DataSourceTable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 物理结构指纹（plan §3.3）：{@code content_hash} 是<b>规范化关键字段串</b>的摘要，不是整行摘要。
 *
 * <p>它只回答"这张表的结构变没变"，因此表名/库名一律不进串——那是 {@code asset_key} 与 FQN 的活
 * （见 {@link MetadataKeyCodec}）。两张结构完全相同的表得到同一个 hash 是设计如此，不是碰撞。
 *
 * <p>参与字段清单见下面三个常量集合，由 {@code MetadataStructureFingerprintTest} 用反射锁住：
 * SPI 一旦加字段，测试会红，逼着当值的人显式决定它进不进指纹，而不是默默把易变项算进去。
 */
public final class MetadataStructureFingerprint {

  /** 表级参与指纹的字段；{@code database}/{@code schema}/{@code name} 属身份，不属内容。 */
  public static final Set<String> TABLE_FIELDS = Set.of("type", "remarks");

  /** 列级参与指纹的字段，与 {@link DataSourceColumn} 的字段全等。 */
  public static final Set<String> COLUMN_FIELDS =
      Set.of(
          "name",
          "typeName",
          "jdbcType",
          "size",
          "scale",
          "nullable",
          "ordinalPosition",
          "primaryKey",
          "remarks");

  /**
   * 永不进指纹的易变项（对齐 OM 的 {@code VOLATILE_*}，蒸馏 §3.3）。
   *
   * <p>认证有效期若进指纹，"认证过期"会每天伪装成一次"表结构变更"。
   */
  public static final Set<String> VOLATILE_FIELDS =
      Set.of("href", "deleted", "inherited", "appliedDate", "expiryDate");

  private static final String COLUMN_SEPARATOR = ":";
  private static final String TABLE_SEPARATOR = "|";
  private static final Pattern WHITESPACE = Pattern.compile("\\s+");
  private static final int UNKNOWN_NUMERIC = -1;

  private MetadataStructureFingerprint() {}

  /** 一轮采集交给仓库侧比对的表级结构指纹。 */
  public static String contentHash(DataSourceTable table, List<DataSourceColumn> columns) {
    return MetadataKeyCodec.digestHex(canonicalTableString(table, columns));
  }

  /** 列顺序无关（先排序再串联）；包内可见是为了让单测断言规范化串本身而不只是摘要。 */
  static String canonicalTableString(DataSourceTable table, List<DataSourceColumn> columns) {
    StringBuilder canonical = new StringBuilder();
    canonical
        .append(nullToEmpty(table == null ? null : table.getType()))
        .append(TABLE_SEPARATOR)
        .append(normalize(table == null ? null : table.getRemarks()));
    for (DataSourceColumn column : sortedColumns(columns)) {
      canonical.append(TABLE_SEPARATOR).append(columnFingerprint(column));
    }
    return canonical.toString();
  }

  /**
   * 按 {@code ordinalPosition} 升序；ordinal 缺失或全 0 时自然退化为列名字典序
   * （同一 comparator 覆盖两种情况，与 OM {@code _get_column_sort_key} 同策略）。
   */
  static List<DataSourceColumn> sortedColumns(List<DataSourceColumn> columns) {
    List<DataSourceColumn> sorted = new ArrayList<>(columns == null ? List.<DataSourceColumn>of() : columns);
    sorted.sort(
        Comparator.comparingInt(DataSourceColumn::getOrdinalPosition)
            .thenComparing(column -> lower(column.getName())));
    return sorted;
  }

  static String columnFingerprint(DataSourceColumn column) {
    return String.join(
        COLUMN_SEPARATOR,
        lower(column.getName()),
        lower(column.getTypeName()),
        String.valueOf(column.getJdbcType()),
        String.valueOf(orUnknown(column.getSize())),
        String.valueOf(orUnknown(column.getScale())),
        column.isNullable() ? "1" : "0",
        String.valueOf(column.getOrdinalPosition()),
        column.isPrimaryKey() ? "1" : "0",
        normalize(column.getRemarks()));
  }

  /** 折叠连续空白为单空格 + 去首尾；<b>不</b>转小写（中文注释转小写既无意义又丢信息）。 */
  static String normalize(String raw) {
    return raw == null ? "" : WHITESPACE.matcher(raw).replaceAll(" ").trim();
  }

  private static String lower(String raw) {
    return raw == null ? "" : raw.toLowerCase(Locale.ROOT);
  }

  private static int orUnknown(Integer value) {
    return value == null ? UNKNOWN_NUMERIC : value;
  }

  private static String nullToEmpty(String raw) {
    return raw == null ? "" : raw;
  }
}
