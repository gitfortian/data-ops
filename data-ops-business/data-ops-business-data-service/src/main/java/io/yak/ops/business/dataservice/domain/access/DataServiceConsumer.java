package io.yak.ops.business.dataservice.domain.access;

import java.time.LocalDateTime;

/** Project-scoped external caller. Credentials and API grants are managed independently. */
public record DataServiceConsumer(
    Long id,
    Long projectId,
    String name,
    String description,
    ConsumerAccessScope accessScope,
    boolean enabled,
    int defaultRateLimitPerMinute,
    LocalDateTime createTime,
    LocalDateTime updateTime) {}
