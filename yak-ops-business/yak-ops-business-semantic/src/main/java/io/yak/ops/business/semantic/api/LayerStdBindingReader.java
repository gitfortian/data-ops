package io.yak.ops.business.semantic.api;

import java.util.Map;

/**
 * 反向只读 SPI(M2-5 定标观察期):建模侧实现,semantic 查询分层内模型字段的
 * 落标计数,供「数仓分层」列表展示各层绑定率。经 ObjectProvider 松耦合注入,
 * modeling 缺席时按无统计处理,无编译反向依赖。
 */
public interface LayerStdBindingReader {

  /** @param columnTotal 该层全部模型字段数;@param stdBoundColumns 已绑定标准字段数 */
  record StdBindingStats(long columnTotal, long stdBoundColumns) {}

  /** 项目内各分层字段落标统计(排除已删除模型),键为 layer_code。 */
  Map<String, StdBindingStats> bindingStatsByLayer();
}
