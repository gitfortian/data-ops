package io.yak.ops.business.development.domain;

import java.util.Locale;
import java.util.Optional;

/** Domain-level node types for data development. */
public enum DevelopmentNodeType {
  SQL(DevelopmentNodeCategory.PROCESSING),
  SHELL(DevelopmentNodeCategory.PROCESSING),
  HTTP(DevelopmentNodeCategory.PROCESSING),
  PYTHON(DevelopmentNodeCategory.PROCESSING),
  JAVA(DevelopmentNodeCategory.PROCESSING),
  DATASET(DevelopmentNodeCategory.OUTPUT),
  DATA_SERVICE(DevelopmentNodeCategory.OUTPUT);

  private final DevelopmentNodeCategory category;

  DevelopmentNodeType(DevelopmentNodeCategory category) {
    this.category = category;
  }

  public DevelopmentNodeCategory category() {
    return category;
  }

  public boolean isProcessing() {
    return category == DevelopmentNodeCategory.PROCESSING;
  }

  public boolean isOutput() {
    return category == DevelopmentNodeCategory.OUTPUT;
  }

  /**
   * Explicit authoring boundary for mutable draft -> immutable task revision -> Task Catalog.
   *
   * <p>Do not derive this from PROCESSING/OUTPUT at call sites. New node types must opt in here
   * before they can enter the executable task lifecycle.
   */
  public boolean supportsTaskLifecycle() {
    return switch (this) {
      case SQL, SHELL, HTTP, PYTHON, JAVA -> true;
      case DATASET, DATA_SERVICE -> false;
    };
  }

  /** Parses and validates a command-side node type using the domain vocabulary. */
  public static DevelopmentNodeType require(String value) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("节点类型不能为空");
    }
    String normalized = value.trim().toUpperCase(Locale.ROOT);
    return tryParse(normalized)
        .orElseThrow(() -> new IllegalArgumentException("不支持的数据开发节点类型：" + normalized));
  }

  public static Optional<DevelopmentNodeType> tryParse(String value) {
    if (value == null || value.isBlank()) return Optional.empty();
    try {
      return Optional.of(valueOf(value.trim().toUpperCase(Locale.ROOT)));
    } catch (IllegalArgumentException ignored) {
      return Optional.empty();
    }
  }
}
