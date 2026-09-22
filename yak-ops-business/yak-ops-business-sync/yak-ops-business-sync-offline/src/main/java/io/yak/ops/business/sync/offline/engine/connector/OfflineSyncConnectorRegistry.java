package io.yak.ops.business.sync.offline.engine.connector;

import io.yak.ops.common.enums.datasource.DataSourceDbType;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 离线同步 Connector Profile 注册表。
 *
 * <p>Profile 描述新建任务的默认执行身份；{@link #defaultConnectorId(String)} 则只服务于
 * 没有固化 connectorId 的历史定义兼容解析。两者刻意分开，避免 Profile 切换到 native
 * 后静默改变旧任务的执行语义。</p>
 */
public final class OfflineSyncConnectorRegistry {

  private static final String JDBC = "jdbc";

  private static final List<OfflineSyncConnectorProfile> PROFILES = List.of(
      jdbcProfile("mysql-jdbc", DataSourceDbType.MYSQL, "JDBC-MYSQL"),
      jdbcProfile("tidb-jdbc", DataSourceDbType.TIDB, "JDBC-TIDB"),
      jdbcProfile("goldendb-jdbc", DataSourceDbType.GOLDENDB, "JDBC-GOLDENDB"),
      jdbcProfile("gbase8c-jdbc", DataSourceDbType.GBASE8C, "JDBC-GBASE8C"),
      jdbcProfile("gbase8a-jdbc", DataSourceDbType.GBASE8A, "JDBC-GBASE8A"),
      jdbcProfile("gbase8s-jdbc", DataSourceDbType.GBASE8S, "JDBC-GBASE8S"),
      jdbcProfile("hana-jdbc", DataSourceDbType.HANA, "JDBC-HANA"),
      jdbcProfile("oracle-jdbc", DataSourceDbType.ORACLE, "JDBC-ORACLE"),
      jdbcProfile("postgresql-jdbc", DataSourceDbType.POSTGRE_SQL, "JDBC-POSTGRESQL"),
      jdbcProfile("db2-jdbc", DataSourceDbType.DB2, "JDBC-DB2"),
      jdbcProfile("opengauss-jdbc", DataSourceDbType.OPEN_GAUSS, "JDBC-OPENGAUSS"),
      jdbcProfile("sqlserver-jdbc", DataSourceDbType.SQL_SERVER, "JDBC-SQLSERVER"),
      jdbcProfile("oceanbase-jdbc", DataSourceDbType.OCEANBASE, "JDBC-OCEANBASE"),
      jdbcProfile("yashandb-jdbc", DataSourceDbType.YASHAN_DB, "JDBC-YASHANDB"),
      jdbcProfile("highgo-jdbc", DataSourceDbType.HIGHGO, "JDBC-HIGHGO"),
      jdbcProfile("iris-jdbc", DataSourceDbType.IRIS, "JDBC-IRIS"),
      jdbcProfile("xugu-jdbc", DataSourceDbType.XUGU, "JDBC-XUGU"),
      jdbcProfile("duckdb-jdbc", DataSourceDbType.DUCKDB, "JDBC-DUCKDB"),
      nativeProfile("doris-native", DataSourceDbType.DORIS, "doris", "DORIS"),
      nativeProfile("starrocks-native", DataSourceDbType.STARROCKS, "starrocks", "STARROCKS"),
      nativeProfile("clickhouse-native", DataSourceDbType.CLICKHOUSE, "clickhouse", "CLICKHOUSE"),
      nativeProfile(
          "elasticsearch7-native",
          DataSourceDbType.ELASTICSEARCH7,
          "elasticsearch7",
          "ELASTICSEARCH7"),
      nativeProfile(
          "elasticsearch8-native",
          DataSourceDbType.ELASTICSEARCH8,
          "elasticsearch8",
          "ELASTICSEARCH8"),
      nativeProfile("mongodb-native", DataSourceDbType.MONGODB, "mongodb", "MONGODB"),
      jdbcProfile("kingbase-jdbc", DataSourceDbType.KINGBASE, "JDBC-KINGBASE"),
      jdbcProfile("dameng-jdbc", DataSourceDbType.DAMENG, "JDBC-DAMENG"));

  /**
   * Historical labels accepted before connectorId became durable execution identity.
   * Native-capable OLAP labels intentionally stay JDBC here so old definitions are not upgraded.
   */
  private static final Set<String> LEGACY_JDBC_LABELS = Set.of(
      "JDBC",
      "MARIADB",
      "DORIS",
      "STARROCKS",
      "CLICKHOUSE",
      "HIVE",
      "DM");

  private static final Map<String, DataSourceDbType> DATA_SOURCE_ALIASES = Map.ofEntries(
      Map.entry("POSTGRESQL", DataSourceDbType.POSTGRE_SQL),
      Map.entry("POSTGRES", DataSourceDbType.POSTGRE_SQL),
      Map.entry("TI_DB", DataSourceDbType.TIDB),
      Map.entry("GOLDEN_DB", DataSourceDbType.GOLDENDB),
      Map.entry("ZTE_GOLDENDB", DataSourceDbType.GOLDENDB),
      Map.entry("GBASE_8C", DataSourceDbType.GBASE8C),
      Map.entry("GBASE_8A", DataSourceDbType.GBASE8A),
      Map.entry("GBASE_8S", DataSourceDbType.GBASE8S),
      Map.entry("SAP_HANA", DataSourceDbType.HANA),
      Map.entry("SAPHANA", DataSourceDbType.HANA),
      Map.entry("OPENGAUSS", DataSourceDbType.OPEN_GAUSS),
      Map.entry("SQLSERVER", DataSourceDbType.SQL_SERVER),
      Map.entry("MSSQL", DataSourceDbType.SQL_SERVER),
      Map.entry("YASHANDB", DataSourceDbType.YASHAN_DB),
      Map.entry("YASDB", DataSourceDbType.YASHAN_DB),
      Map.entry("HIGH_GO", DataSourceDbType.HIGHGO),
      Map.entry("HGDB", DataSourceDbType.HIGHGO),
      Map.entry("INTERSYSTEMS_IRIS", DataSourceDbType.IRIS),
      Map.entry("XUGUDB", DataSourceDbType.XUGU),
      Map.entry("DUCK_DB", DataSourceDbType.DUCKDB),
      Map.entry("ELASTICSEARCH_7", DataSourceDbType.ELASTICSEARCH7),
      Map.entry("ES7", DataSourceDbType.ELASTICSEARCH7),
      Map.entry("ELASTICSEARCH_8", DataSourceDbType.ELASTICSEARCH8),
      Map.entry("ES8", DataSourceDbType.ELASTICSEARCH8),
      Map.entry("MONGO", DataSourceDbType.MONGODB),
      Map.entry("MONGO_DB", DataSourceDbType.MONGODB));

  private static final Map<DataSourceDbType, OfflineSyncConnectorProfile> DEFAULTS =
      buildDefaults();

  private OfflineSyncConnectorRegistry() {}

  public static List<OfflineSyncConnectorProfile> profiles() {
    return PROFILES;
  }

  public static Optional<OfflineSyncConnectorProfile> defaultProfile(String value) {
    DataSourceDbType dbType = parseDbType(value);
    return dbType == null ? Optional.empty() : Optional.ofNullable(DEFAULTS.get(dbType));
  }

  /**
   * Compatibility resolver for definitions that predate durable connectorId.
   * New definitions must use {@link #defaultProfile(String)} and persist its connector IDs.
   */
  public static Optional<String> defaultConnectorId(String value) {
    String normalized = normalize(value);
    if (normalized == null) {
      return Optional.empty();
    }
    if (LEGACY_JDBC_LABELS.contains(normalized)) {
      return Optional.of(JDBC);
    }

    Optional<OfflineSyncConnectorProfile> profile = defaultProfile(normalized);
    if (profile.isPresent()) {
      OfflineSyncConnectorProfile selected = profile.get();
      if (!selected.sourceConnectorId().equals(selected.sinkConnectorId())) {
        throw new IllegalStateException(
            "当前兼容解析需要 Source/Sink 默认 Connector 一致：" + selected.profileId());
      }
      return Optional.of(selected.sourceConnectorId());
    }
    return Optional.empty();
  }

  public static boolean isLegacyDatasourceLabel(String value) {
    return defaultConnectorId(value).isPresent();
  }

  private static OfflineSyncConnectorProfile jdbcProfile(
      String profileId,
      DataSourceDbType dbType,
      String pluginName) {
    return profile(profileId, dbType, JDBC, JDBC, pluginName);
  }

  private static OfflineSyncConnectorProfile nativeProfile(
      String profileId,
      DataSourceDbType dbType,
      String connectorId,
      String pluginName) {
    return profile(profileId, dbType, connectorId, connectorId, pluginName);
  }

  private static OfflineSyncConnectorProfile profile(
      String profileId,
      DataSourceDbType dbType,
      String sourceConnectorId,
      String sinkConnectorId,
      String pluginName) {
    return new OfflineSyncConnectorProfile(
        profileId,
        dbType,
        sourceConnectorId,
        sinkConnectorId,
        pluginName,
        true);
  }

  private static Map<DataSourceDbType, OfflineSyncConnectorProfile> buildDefaults() {
    Map<DataSourceDbType, OfflineSyncConnectorProfile> result =
        new EnumMap<>(DataSourceDbType.class);
    Map<String, OfflineSyncConnectorProfile> profileIds = new LinkedHashMap<>();

    for (OfflineSyncConnectorProfile profile : PROFILES) {
      OfflineSyncConnectorProfile duplicateId = profileIds.put(profile.profileId(), profile);
      if (duplicateId != null) {
        throw new IllegalStateException("重复的离线同步 profileId：" + profile.profileId());
      }
      if (!profile.defaultProfile()) {
        continue;
      }
      OfflineSyncConnectorProfile duplicateDefault = result.put(profile.dbType(), profile);
      if (duplicateDefault != null) {
        throw new IllegalStateException(
            "数据源存在多个默认离线同步 Profile：" + profile.dbType());
      }
    }

    return Map.copyOf(result);
  }

  private static DataSourceDbType parseDbType(String value) {
    String normalized = normalize(value);
    if (normalized == null) {
      return null;
    }
    DataSourceDbType alias = DATA_SOURCE_ALIASES.get(normalized);
    if (alias != null) {
      return alias;
    }
    try {
      return DataSourceDbType.valueOf(normalized);
    } catch (IllegalArgumentException ignored) {
      return null;
    }
  }

  private static String normalize(String value) {
    if (value == null || value.trim().isEmpty()) {
      return null;
    }
    return value.trim().toUpperCase(Locale.ROOT).replace('-', '_');
  }
}
