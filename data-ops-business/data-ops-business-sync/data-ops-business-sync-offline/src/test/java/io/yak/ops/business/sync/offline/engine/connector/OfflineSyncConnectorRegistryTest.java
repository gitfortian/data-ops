package io.yak.ops.business.sync.offline.engine.connector;

import static org.assertj.core.api.Assertions.assertThat;

import io.yak.ops.common.enums.datasource.DataSourceDbType;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class OfflineSyncConnectorRegistryTest {

  @Test
  void shouldExposeOneDefaultProfileForEveryCurrentDatasourceType() {
    Map<DataSourceDbType, OfflineSyncConnectorProfile> profiles =
        OfflineSyncConnectorRegistry.profiles().stream()
            .filter(OfflineSyncConnectorProfile::defaultProfile)
            .collect(Collectors.toMap(OfflineSyncConnectorProfile::dbType, value -> value));

    assertThat(profiles).containsOnlyKeys(DataSourceDbType.values());
    assertThat(profiles.get(DataSourceDbType.DORIS).sourceConnectorId()).isEqualTo("doris");
    assertThat(profiles.get(DataSourceDbType.STARROCKS).sourceConnectorId()).isEqualTo("starrocks");
    assertThat(profiles.get(DataSourceDbType.CLICKHOUSE).sourceConnectorId()).isEqualTo("clickhouse");
    assertThat(profiles.get(DataSourceDbType.ELASTICSEARCH7).sourceConnectorId())
        .isEqualTo("elasticsearch7");
    assertThat(profiles.get(DataSourceDbType.ELASTICSEARCH7).sinkConnectorId())
        .isEqualTo("elasticsearch7");
    assertThat(profiles.get(DataSourceDbType.ELASTICSEARCH8).sourceConnectorId())
        .isEqualTo("elasticsearch8");
    assertThat(profiles.get(DataSourceDbType.ELASTICSEARCH8).sinkConnectorId())
        .isEqualTo("elasticsearch8");

    for (DataSourceDbType dbType : new DataSourceDbType[] {
      DataSourceDbType.TIDB,
      DataSourceDbType.GOLDENDB,
      DataSourceDbType.GBASE8C,
      DataSourceDbType.GBASE8A,
      DataSourceDbType.GBASE8S,
      DataSourceDbType.HANA,
      DataSourceDbType.DB2,
      DataSourceDbType.OPEN_GAUSS,
      DataSourceDbType.SQL_SERVER,
      DataSourceDbType.OCEANBASE,
      DataSourceDbType.YASHAN_DB,
      DataSourceDbType.HIGHGO,
      DataSourceDbType.IRIS,
      DataSourceDbType.XUGU,
      DataSourceDbType.DUCKDB
    }) {
      assertThat(profiles.get(dbType).sourceConnectorId()).isEqualTo("jdbc");
      assertThat(profiles.get(dbType).sinkConnectorId()).isEqualTo("jdbc");
    }
  }

  @Test
  void shouldKeepDefinitionsWithoutDurableConnectorIdOnHistoricalJdbcPath() {
    assertThat(OfflineSyncConnectorRegistry.defaultConnectorId("DORIS")).contains("jdbc");
    assertThat(OfflineSyncConnectorRegistry.defaultConnectorId("STARROCKS")).contains("jdbc");
    assertThat(OfflineSyncConnectorRegistry.defaultConnectorId("CLICKHOUSE")).contains("jdbc");
    assertThat(OfflineSyncConnectorRegistry.defaultConnectorId("POSTGRESQL")).contains("jdbc");
    assertThat(OfflineSyncConnectorRegistry.defaultConnectorId("TIDB")).contains("jdbc");
    assertThat(OfflineSyncConnectorRegistry.defaultConnectorId("TI-DB")).contains("jdbc");
    assertThat(OfflineSyncConnectorRegistry.defaultConnectorId("GOLDENDB")).contains("jdbc");
    assertThat(OfflineSyncConnectorRegistry.defaultConnectorId("GOLDEN-DB")).contains("jdbc");
    assertThat(OfflineSyncConnectorRegistry.defaultConnectorId("ZTE-GOLDENDB")).contains("jdbc");
    assertThat(OfflineSyncConnectorRegistry.defaultConnectorId("GBASE8C")).contains("jdbc");
    assertThat(OfflineSyncConnectorRegistry.defaultConnectorId("GBASE-8C")).contains("jdbc");
    assertThat(OfflineSyncConnectorRegistry.defaultConnectorId("GBASE8A")).contains("jdbc");
    assertThat(OfflineSyncConnectorRegistry.defaultConnectorId("GBASE-8A")).contains("jdbc");
    assertThat(OfflineSyncConnectorRegistry.defaultConnectorId("GBASE8S")).contains("jdbc");
    assertThat(OfflineSyncConnectorRegistry.defaultConnectorId("GBASE-8S")).contains("jdbc");
    assertThat(OfflineSyncConnectorRegistry.defaultConnectorId("HANA")).contains("jdbc");
    assertThat(OfflineSyncConnectorRegistry.defaultConnectorId("SAP-HANA")).contains("jdbc");
    assertThat(OfflineSyncConnectorRegistry.defaultConnectorId("SAPHANA")).contains("jdbc");
    assertThat(OfflineSyncConnectorRegistry.defaultConnectorId("YASHANDB")).contains("jdbc");
    assertThat(OfflineSyncConnectorRegistry.defaultConnectorId("HGDB")).contains("jdbc");
    assertThat(OfflineSyncConnectorRegistry.defaultConnectorId("XUGUDB")).contains("jdbc");
    assertThat(OfflineSyncConnectorRegistry.defaultConnectorId("DUCK_DB")).contains("jdbc");
    assertThat(OfflineSyncConnectorRegistry.defaultConnectorId("HTTP")).isEmpty();
  }

  @Test
  void shouldResolveVersionedElasticsearchAliasesWithoutCollapsingThem() {
    assertThat(OfflineSyncConnectorRegistry.defaultProfile("ES7"))
        .get()
        .extracting(OfflineSyncConnectorProfile::sourceConnectorId)
        .isEqualTo("elasticsearch7");
    assertThat(OfflineSyncConnectorRegistry.defaultProfile("ELASTICSEARCH_8"))
        .get()
        .extracting(OfflineSyncConnectorProfile::sourceConnectorId)
        .isEqualTo("elasticsearch8");
  }
}
