package io.yak.ops.business.semantic.api;

import java.time.LocalDateTime;

/** 一个业务过程;编码为项目内稳定键。bizType FACT=事实/DIMENSION=维度。 */
public record BusinessProcess(
    Long id,
    String code,
    String name,
    Long domainId,
    String grain,
    String bizType,
    String owner,
    String description,
    int sortOrder,
    String createdBy,
    LocalDateTime createTime,
    LocalDateTime updateTime) {}
