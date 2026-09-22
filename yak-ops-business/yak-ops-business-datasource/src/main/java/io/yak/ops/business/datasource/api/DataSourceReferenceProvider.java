package io.yak.ops.business.datasource.api;

/**
 * 反向只读引用 SPI:下游模块实现,声明「谁在把这个数据源 ID 当配置用」。
 * 数据源删除前由 DataSourceManager 聚合所有实现做引用守卫(Ticket 01)。
 * 经 Spring 运行时注入,无实现时按零引用放行;各实现只查本模块的表,
 * 且只统计「活的配置引用」——执行历史/快照类记录不算引用。
 */
public interface DataSourceReferenceProvider {

  /** 引用方模块的展示名,将原样出现在删除拦截的错误信息里。 */
  String moduleName();

  /** 本模块中引用该数据源 ID 的活跃配置条数。 */
  long countReferences(Long dataSourceId);
}
