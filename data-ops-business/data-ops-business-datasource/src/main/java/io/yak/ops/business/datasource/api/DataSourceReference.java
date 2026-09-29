package io.yak.ops.business.datasource.api;

/** 单个下游模块对某数据源的引用统计结果。 */
public record DataSourceReference(String moduleName, long count) {

  public DataSourceReference {
    if (moduleName == null || moduleName.isBlank()) {
      throw new IllegalArgumentException("moduleName must not be blank");
    }
    if (count < 0L) {
      throw new IllegalArgumentException("count must not be negative");
    }
  }
}
