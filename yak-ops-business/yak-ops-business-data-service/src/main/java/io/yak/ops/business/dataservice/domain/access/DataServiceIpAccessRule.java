package io.yak.ops.business.dataservice.domain.access;

import java.time.LocalDateTime;

/** Persisted IP/CIDR access rule owned by one Data Service. */
public record DataServiceIpAccessRule(
    Long id,
    Long apiId,
    IpAccessRuleType ruleType,
    String networkCidr,
    String description,
    boolean enabled,
    LocalDateTime expiresAt,
    LocalDateTime createTime,
    LocalDateTime updateTime) {

  public boolean active(LocalDateTime now) {
    return enabled && (expiresAt == null || now == null || expiresAt.isAfter(now));
  }
}
