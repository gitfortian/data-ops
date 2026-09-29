package io.yak.ops.business.development.domain;

/** Validated command-side name for a data-development node. */
public record DevelopmentNodeName(String value) {

  public static final int MAX_LENGTH = 200;

  public DevelopmentNodeName {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("节点名称不能为空");
    }
    value = value.trim();
    if (value.length() > MAX_LENGTH) {
      throw new IllegalArgumentException("节点名称不能超过 " + MAX_LENGTH + " 个字符");
    }
    if (value.contains("/") || value.contains("\\")) {
      throw new IllegalArgumentException("节点名称不能包含路径分隔符");
    }
  }

  public static DevelopmentNodeName of(String value) {
    return new DevelopmentNodeName(value);
  }
}
