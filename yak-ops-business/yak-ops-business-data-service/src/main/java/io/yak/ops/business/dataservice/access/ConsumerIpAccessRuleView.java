package io.yak.ops.business.dataservice.access;

import io.yak.ops.business.dataservice.domain.access.IpAccessRuleType;
import java.time.LocalDateTime;

public record ConsumerIpAccessRuleView(
    Long id,
    Long consumerId,
    IpAccessRuleType ruleType,
    String networkCidr,
    String description,
    boolean enabled,
    LocalDateTime expiresAt,
    LocalDateTime createTime,
    LocalDateTime updateTime) {}
