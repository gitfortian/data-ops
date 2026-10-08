package io.yak.ops.business.semantic.api;

import java.util.Map;

/**
 * 反向只读 SPI(2026-09-16):建模侧实现,semantic 查询分层被模型引用数
 * (列表展示 + 自定义分层删除阻断)。经 Spring 运行时注入(ObjectProvider),
 * 列表统计在 modeling 模块缺席时可显示为空；删除安全校验不可按 0 处理。
 */
public interface LayerUsageReader {

  /** 项目内各分层的被引用模型数(排除已删除模型),键为 layer_code。 */
  Map<String, Long> countModelsByLayer();

  /**
   * Deletion guard: count all persisted model references, including recoverable
   * recycle-bin models. Unlike the active-only dashboard count, this must not
   * ignore soft-deleted models that can be restored.
   */
  long countPersistedLayerReferences(String layerCode);
}
