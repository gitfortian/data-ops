package io.yak.ops.business.semantic.api;

import java.util.Optional;

/** 标准状态:ENABLED 可被引用,DISABLED 停用(软路径)。 */
public enum StandardStatus {
  ENABLED,
  DISABLED;

  public static Optional<StandardStatus> fromStored(String value) {
    if (value == null) {
      return Optional.empty();
    }
    try {
      return Optional.of(StandardStatus.valueOf(value));
    } catch (IllegalArgumentException ignored) {
      return Optional.empty();
    }
  }
}
