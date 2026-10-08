package io.yak.ops.business.agent.domain;

/** Explicit saved target column and user-selected single source table, not an authorization. */
public record ModelMappingTarget(long modelId, String columnName, long datasourceId,
    String database, String table, String businessDescription, String keyword) {
  public ModelMappingTarget {
    if (modelId <= 0 || datasourceId <= 0 || columnName == null
        || !columnName.matches("[A-Za-z0-9_][A-Za-z0-9_$]{0,127}")
        || database == null || database.isBlank() || database.length() > 128
        || table == null || table.isBlank() || table.length() > 128
        || businessDescription == null || businessDescription.length() > 512
        || keyword == null || keyword.length() > 64) throw new IllegalArgumentException("模型来源映射目标无效");
  }
}
