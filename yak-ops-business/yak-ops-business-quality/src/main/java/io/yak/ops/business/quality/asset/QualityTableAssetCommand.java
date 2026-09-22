package io.yak.ops.business.quality.asset;

import java.util.List;

/** Typed command boundary for table registration. */
public final class QualityTableAssetCommand {
  private QualityTableAssetCommand() {}

  public record Register(
      Long dataSourceId,
      String dataSourceName,
      String databaseName,
      List<Item> tables) {
    public Register {
      tables = tables == null ? List.of() : List.copyOf(tables);
    }
  }

  public record Item(
      String databaseName,
      String schemaName,
      String tableName,
      String tableType,
      String remarks) {}
}
