package io.yak.ops.business.development.domain;

/** Validated name segment for one data-development directory. */
public record DevelopmentDirectoryName(String value) {

  public static final int MAX_LENGTH = 128;

  public DevelopmentDirectoryName {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("目录名称不能为空");
    }
    value = value.trim();
    if (value.length() > MAX_LENGTH) {
      throw new IllegalArgumentException("目录名称不能超过 " + MAX_LENGTH + " 个字符");
    }
    if (".".equals(value)
        || "..".equals(value)
        || value.contains("/")
        || value.contains("\\")) {
      throw new IllegalArgumentException("目录名称不能包含 /、\\，也不能使用 . 或 ..");
    }
  }

  public static DevelopmentDirectoryName of(String value) {
    return new DevelopmentDirectoryName(value);
  }
}
