package io.yak.ops.business.dataservice.access;

import java.time.LocalDateTime;

public record ApiKeyInput(String name, Integer rateLimitPerMinute, LocalDateTime expiresAt) {}
