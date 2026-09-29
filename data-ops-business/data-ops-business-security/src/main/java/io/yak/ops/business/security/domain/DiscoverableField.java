package io.yak.ops.business.security.domain;

/** 敏感发现的可扫描字段(由数据源目录侧提供)。 */
public record DiscoverableField(
    Long datasourceId, String dbName, String tableName, String columnName, String comment) {}
