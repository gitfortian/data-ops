package io.yak.ops.business.sync.offline.engine;

import io.yak.ops.business.datasource.domain.DataSourceDefinition;
import io.yak.ops.business.datasource.dao.model.DataSourcePO;

/** Keeps legacy fixtures separate from the production Datasource read-side contract. */
public final class DataSourceFixtures {
  private DataSourceFixtures() {}

  public static DataSourceDefinition definition(DataSourcePO value) {
    return DataSourceDefinition.restore(value.getId(), value.getProjectId(), value.getName(),
        value.getDbType(), value.getJdbcUrl(), value.getEnvironment(), value.getConnStatus(),
        value.getRemark(), value.getConnectionParams(), value.getOriginalJson(),
        value.getCreateTime(), value.getUpdateTime());
  }
}
