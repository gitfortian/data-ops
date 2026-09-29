package io.yak.ops.business.dataservice.access;

public record ConsumerInput(
    String name,
    String description,
    Boolean enabled,
    Integer defaultRateLimitPerMinute) {}
