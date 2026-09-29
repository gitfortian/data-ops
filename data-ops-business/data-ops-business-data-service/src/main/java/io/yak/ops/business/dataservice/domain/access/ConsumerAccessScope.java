package io.yak.ops.business.dataservice.domain.access;

import java.util.Locale;

/** Which Data Service APIs one caller is allowed to invoke. */
public enum ConsumerAccessScope {
  ALL,
  SELECTED;

  public static ConsumerAccessScope parse(String value) {
    if (value == null || value.isBlank()) return SELECTED;
    return valueOf(value.trim().toUpperCase(Locale.ROOT));
  }
}
