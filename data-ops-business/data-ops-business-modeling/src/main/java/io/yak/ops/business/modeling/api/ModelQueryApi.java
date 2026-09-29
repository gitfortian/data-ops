package io.yak.ops.business.modeling.api;

import java.util.Collection;
import java.util.Map;

/**
 * 模型只读查询 SPI:供跨模块消费方(如 metric 的展示名解析与依赖版本快照)批量解析模型。
 * 消费方不得直读 modeling 的表或 dao。
 */
public interface ModelQueryApi {

  /** 按 id 批量解析模型概要(id → brief);不存在/已删除的 id 不返回。 */
  Map<Long, ModelBrief> resolve(Collection<Long> ids);

  /**
   * @param latestVersionNo 最新版本号(0=未发布),供依赖快照与影响分析比对。
   */
  record ModelBrief(Long id, String code, String name, String layerCode, Integer latestVersionNo) {}
}
