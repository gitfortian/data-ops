package io.yak.ops.business.dataservice.access;

import java.time.LocalDateTime;

public record ApiKeyView(
    Long id, Long apiId, String name, String keyPrefix, Boolean enabled, Integer rateLimitPerMinute,
    LocalDateTime expiresAt, LocalDateTime lastUsedAt, LocalDateTime createTime, LocalDateTime updateTime) {}
