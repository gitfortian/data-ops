package io.yak.ops.business.sync.offline.domain;

import java.time.LocalDateTime;

/** 任务定义业务查询条件，不依赖 HTTP DTO。 */
public record OfflineDefinitionQuery(
    int current,
    int pageSize,
    Long id,
    String jobName,
    String status,
    String sourceType,
    String sinkType,
    String sourceTable,
    String sinkTable,
    LocalDateTime createTimeStart,
    LocalDateTime createTimeEnd) {}
