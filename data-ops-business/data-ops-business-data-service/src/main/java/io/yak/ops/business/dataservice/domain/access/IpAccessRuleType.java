package io.yak.ops.business.dataservice.domain.access;

public enum IpAccessRuleType {
  ALLOWLIST,
  DENYLIST;

  public static IpAccessRuleType parse(String value) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("IP 访问规则类型不能为空");
    }
    return valueOf(value.trim().toUpperCase());
  }
}
