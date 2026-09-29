package io.yak.ops.business.sync.realtime.engine;

import io.yak.ops.common.enums.datasource.DataSourceDbType;

/** Ephemeral, password-free connection coordinates used to compile a runtime submission. */
public record ResolvedCdcPipeline(Endpoint source, Endpoint sink) {

  public record Endpoint(
      long dataSourceId,
      String name,
      DataSourceDbType dbType,
      String host,
      int port,
      String jdbcUrl,
      String driver,
      String username,
      String database) {}
}
