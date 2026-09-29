package io.yak.ops.business.dataservice.access;

import io.yak.ops.business.dataservice.domain.access.ConsumerAccessScope;
import io.yak.ops.business.dataservice.domain.access.IpAccessMode;
import java.time.LocalDateTime;
import java.util.List;

public record ConsumerView(
    Long id,
    String name,
    String description,
    boolean enabled,
    ConsumerAccessScope accessScope,
    List<Long> apiIds,
    int apiCount,
    int keyCount,
    int activeKeyCount,
    IpAccessMode ipAccessMode,
    int ipRuleCount,
    int defaultRateLimitPerMinute,
    LocalDateTime createTime,
    LocalDateTime updateTime) {}
