package io.yak.ops.business.dataservice.domain.access;

import java.time.LocalDateTime;

/** IP/CIDR rule attached to a caller instead of one API. */
public record DataServiceConsumerIpAccessRule(
    Long id,
    Long consumerId,
    IpAccessRuleType ruleType,
    String networkCidr,
    String description,
    boolean enabled,
    LocalDateTime expiresAt,
    LocalDateTime createTime,
    LocalDateTime updateTime) {

  public boolean active(LocalDateTime now) {
    return enabled && (expiresAt == null || expiresAt.isAfter(now));
  }
}
