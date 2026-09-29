package io.yak.ops.business.dataservice.domain.access;

import java.util.Locale;

public enum AuthMode {
  NONE,
  API_KEY;

  public static AuthMode parse(String value) {
    if (value == null || value.isBlank()) return NONE;
    return valueOf(value.trim().toUpperCase(Locale.ROOT));
  }
}
