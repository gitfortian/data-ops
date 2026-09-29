package io.yak.ops.business.sync.offline.domain;

/** 执行实例业务查询条件，不依赖 HTTP DTO。 */
public record OfflineExecutionQuery(
    int current,
    int pageSize,
    Long jobDefinitionId,
    String status) {}
