package io.yak.ops.business.sync.offline.engine.connector;

import static org.assertj.core.api.Assertions.assertThat;

import io.yak.ops.common.enums.datasource.DataSourceDbType;
import org.junit.jupiter.api.Test;

class MongoOfflineSyncConnectorRegistryTest {

  @Test
  void mongodbUsesNativeConnectorProfileForNewDefinitions() {
    OfflineSyncConnectorProfile profile =
        OfflineSyncConnectorRegistry.defaultProfile("mongodb").orElseThrow();

    assertThat(profile.profileId()).isEqualTo("mongodb-native");
    assertThat(profile.dbType()).isEqualTo(DataSourceDbType.MONGODB);
    assertThat(profile.sourceConnectorId()).isEqualTo("mongodb");
    assertThat(profile.sinkConnectorId()).isEqualTo("mongodb");
    assertThat(profile.defaultProfile()).isTrue();
    assertThat(OfflineSyncConnectorRegistry.defaultProfile("mongo_db")).contains(profile);
  }
}
