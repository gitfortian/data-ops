package io.yak.ops.business.metadata.harvest;

import java.util.Collection;

/**
 * 把确认已消失的实体从<b>血缘图</b>上撤销（plan §3.4 末：血缘图上挂着一张不存在的表比挂着一张软删的表更糟）。
 *
 * <p><b>为什么是一端口而不是直接调用</b>：撤销逻辑归 lineage，元数据既不该直插它的表（§0.4），
 * 也不该复制一份撤销代码（第二份真相）。而 ticket 113 至今只把 {@code registerAsset}
 * 那四个<b>写入</b>方法抽进了 {@code lineage.api}，<b>没有</b>撤销入口——所以本端口先按形状存在，
 * 实现由 113 一侧提供；在那之前 {@link #revoke} 没有装配者，缺席时采集侧只记日志、不假装撤销成功。
 *
 * <p>调用时机只有一处：{@link MetadataPresenceService} 确认 GONE 之后。{@code SUSPECT} 一律不调
 * ——一轮可疑的缺席不构成"从图上摘节点"的理由，摘错了要人工重挂。
 */
public interface CatalogGraphRevocation {

  /**
   * @param assetKeys 已软删的目录键；与 {@code yak_metadata_asset.asset_key} 逐字相同，
   *     所以 lineage 侧能按同一把键定位节点（ticket 134 的共键前提）
   */
  void revoke(Collection<String> assetKeys);
}
