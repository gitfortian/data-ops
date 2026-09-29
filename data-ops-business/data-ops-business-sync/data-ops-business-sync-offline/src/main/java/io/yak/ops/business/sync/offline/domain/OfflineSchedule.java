package io.yak.ops.business.sync.offline.domain;

import java.time.LocalDateTime;

/** 离线同步任务级调度投影；持久化仍落在任务定义表。 */
public record OfflineSchedule(
    Long jobDefinitionId,
    String cronExpression,
    boolean enabled,
    int retryMaxAttempts,
    int retryBackoffSeconds,
    LocalDateTime nextFireTime,
    LocalDateTime lastFireTime,
    String scheduleJson) {}
