package io.yak.ops.business.lifecycle.generate;

import io.yak.ops.common.bean.po.lifecycle.LifecyclePolicyPO;
import io.yak.ops.common.enums.lifecycle.LifecycleEnums.Granularity;
import io.yak.ops.common.enums.lifecycle.LifecycleEnums.StorageType;
import org.springframework.util.StringUtils;

/**
 * TTL 语句生成器(纯函数,ticket 83)。D2:按目标真实方言族生成,策略不手选存储类型。
 * D3:生成与执行分离——writable=false 的语句只供复制,网关侧同样拒绝下发。
 */
public final class TtlStatementGenerator {

  /** Doris 动态分区默认提前创建 3 个未来分区、分区名前缀 p(requirement D8)。 */
  private static final int DEFAULT_END = 3;
  private static final String DEFAULT_PREFIX = "p";

  /** 目标表定位:来自模型 + 分层配置解析。 */
  public record TtlTarget(String dialect, String databaseName, String tableName) {}

  public static StorageType storageTypeOf(String dialect) {
    if (!StringUtils.hasText(dialect)) {
      return StorageType.UNSUPPORTED;
    }
    String upper = dialect.trim().toUpperCase();
    if (upper.contains("DORIS") || upper.contains("STARROCKS")) {
      return StorageType.DORIS;
    }
    if (upper.contains("PAIMON")) {
      return StorageType.PAIMON;
    }
    return StorageType.UNSUPPORTED;
  }

  public static TtlStatement generate(LifecyclePolicyPO policy, TtlTarget target) {
    Granularity granularity = Granularity.valueOf(policy.getPartitionGranularity());
    return generate(granularity, policy.getHotDays(), policy.getColdDays(),
        policy.getDestroyDays(), target);
  }

  public static TtlStatement generate(Granularity granularity, Integer hotDays,
      Integer coldDays, Integer destroyDays, TtlTarget target) {
    StorageType type = storageTypeOf(target.dialect());
    String table = qualify(target);
    return switch (type) {
      case DORIS -> new TtlStatement(type, table,
          dorisStatement(table, granularity, hotDays, destroyDays), true,
          destroyDays == null ? "永久保留:关闭动态分区自动回收,历史分区不再被删除"
              : "由 Doris 动态分区自治清理,平台不执行删除");
      case PAIMON -> paimon(granularity, destroyDays, table);
      case UNSUPPORTED -> new TtlStatement(type, table,
          dorisStatement(table, granularity, hotDays, destroyDays), false,
          "目标存储方言 " + (StringUtils.hasText(target.dialect()) ? target.dialect() : "未知")
              + " 暂不支持平台下发,已按 Doris 语法生成,仅可复制后手工执行");
    };
  }

  private static TtlStatement paimon(Granularity granularity, Integer destroyDays, String table) {
    if (destroyDays == null) {
      return new TtlStatement(StorageType.PAIMON, table,
          "-- 永久保留:Paimon 不设置 partition.expiration-time 即为永久保留,无需执行任何语句",
          false, "永久保留:目标表保持现状,无需下发");
    }
    String statement = "ALTER TABLE " + table + "\nSET (\n"
        + "    'partition.expiration-time' = '" + destroyDays + " d',\n"
        + "    'partition.expiration-check-interval' = '1 d',\n"
        + "    'partition.timestamp-formatter' = '" + paimonFormatter(granularity) + "'\n"
        + ");";
    return new TtlStatement(StorageType.PAIMON, table, statement, true,
        "由 Paimon partition expiration 自治清理,平台不执行删除");
  }

  static String dorisStatement(String table, Granularity granularity,
      Integer hotDays, Integer destroyDays) {
    if (destroyDays == null) {
      return "ALTER TABLE " + table + "\nSET (\n"
          + "    \"dynamic_partition.enable\" = \"false\"\n"
          + ");";
    }
    int units = toUnits(destroyDays, granularity);
    StringBuilder sb = new StringBuilder();
    sb.append("ALTER TABLE ").append(table).append("\nSET (\n")
        .append("    \"dynamic_partition.enable\" = \"true\",\n")
        .append("    \"dynamic_partition.time_unit\" = \"").append(granularity.name()).append("\",\n")
        .append("    \"dynamic_partition.start\" = \"-").append(units).append("\",\n")
        .append("    \"dynamic_partition.end\" = \"").append(DEFAULT_END).append("\",\n")
        .append("    \"dynamic_partition.prefix\" = \"").append(DEFAULT_PREFIX).append("\"");
    if (hotDays != null) {
      sb.append(",\n    \"dynamic_partition.hot_partition_num\" = \"")
          .append(toUnits(hotDays, granularity)).append("\"");
    }
    return sb.append("\n);").toString();
  }

  /** 天数按粒度换算为分区个数(向上取整,宁多留不提前删)。 */
  static int toUnits(int days, Granularity granularity) {
    return switch (granularity) {
      case DAY -> days;
      case MONTH -> (int) Math.ceil(days / 30.0);
      case YEAR -> (int) Math.ceil(days / 365.0);
    };
  }

  static String paimonFormatter(Granularity granularity) {
    return switch (granularity) {
      case DAY -> "yyyyMMdd";
      case MONTH -> "yyyyMM";
      case YEAR -> "yyyy";
    };
  }

  private static String qualify(TtlTarget target) {
    String table = safeIdentifier(target.tableName());
    if (!StringUtils.hasText(target.databaseName())) {
      return table;
    }
    return safeIdentifier(target.databaseName().trim()) + "." + table;
  }

  /** 反引号包裹并禁止内嵌反引号,杜绝标识符注入。 */
  private static String safeIdentifier(String raw) {
    String cleaned = raw == null ? "" : raw.replace("`", "").trim();
    return "`" + cleaned + "`";
  }

  private TtlStatementGenerator() {}
}
