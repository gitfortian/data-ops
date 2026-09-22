package io.yak.ops.business.semantic.api;

import java.util.Map;

/**
 * 反向只读 SPI(2026-09-16):建模侧实现,semantic 查询分层被模型引用数
 * (列表展示 + 自定义分层删除阻断)。经 Spring 运行时注入(ObjectProvider),
 * modeling 模块缺席时 semantic 侧按 0 处理,无编译反向依赖。
 */
public interface LayerUsageReader {

  /** 项目内各分层的被引用模型数(排除已删除模型),键为 layer_code。 */
  Map<String, Long> countModelsByLayer();
}
