package io.yak.ops.business.dataservice.access;

import java.time.LocalDateTime;

public record IpAccessRuleInput(
    String ruleType,
    String networkCidr,
    String description,
    Boolean enabled,
    LocalDateTime expiresAt) {}
