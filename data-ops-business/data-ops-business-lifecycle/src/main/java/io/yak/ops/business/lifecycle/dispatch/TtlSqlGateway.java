package io.yak.ops.business.lifecycle.dispatch;

import java.util.List;

/** TTL 语句/查询下传通道(D3:执行只经统一 SQL SPI,失败以异常表达)。 */
public interface TtlSqlGateway {

  /** 数据源执行能力是否可用(未装配插件时预览/下发降级为仅生成)。 */
  boolean available();

  /** 执行 DDL/SET 语句;失败抛异常携带存储端原始报错。 */
  void execute(Long datasourceId, String sql, int timeoutSeconds);

  /** 执行查询并返回行(SHOW PARTITIONS / SHOW DATA 等);失败抛异常。 */
  List<List<Object>> query(Long datasourceId, String sql, int maxRows);
}
