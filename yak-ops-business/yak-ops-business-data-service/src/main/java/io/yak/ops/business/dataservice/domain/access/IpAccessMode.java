package io.yak.ops.business.dataservice.domain.access;

public enum IpAccessMode {
  NONE,
  ALLOWLIST,
  DENYLIST;

  public static IpAccessMode parse(String value) {
    if (value == null || value.isBlank()) return NONE;
    return valueOf(value.trim().toUpperCase());
  }
}
