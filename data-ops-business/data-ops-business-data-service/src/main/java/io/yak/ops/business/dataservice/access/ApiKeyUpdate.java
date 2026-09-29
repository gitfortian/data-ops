package io.yak.ops.business.dataservice.access;

import java.time.LocalDateTime;

/** expiresAtSet distinguishes leave-unchanged from clear-expiration. */
public record ApiKeyUpdate(
    String name, Integer rateLimitPerMinute, LocalDateTime expiresAt, boolean expiresAtSet) {}
