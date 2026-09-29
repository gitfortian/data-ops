package io.yak.ops.business.semantic.api;

import java.time.LocalDateTime;

/** 一条数仓分层配置;编码为项目内稳定键。 */
public record WarehouseLayer(
    Long id,
    String code,
    String name,
    String databaseName,
    Long datasourceId,
    Long stdNamingId,
    String defaultPartition,
    String storageFormat,
    Integer lifecycleDays,
    String description,
    int sortOrder,
    String status,
    boolean stdMandatory,
    boolean preset,
    String createdBy,
    LocalDateTime createTime,
    LocalDateTime updateTime) {}
