package io.yak.ops.business.asset.api;

import io.yak.ops.common.enums.asset.AssetEnums.AssetType;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 源域供给的资产描述符(只读快照,非台账本体)。
 *
 * @param assetKey       血缘登记键原文,与源域生成器逐字符一致(D6);台账 upsert 的唯一键
 * @param sourceId       源域主键的字符串形态
 * @param contentHash    参与比对的元数据指纹({@link AssetContentHash}),对账判定 META_CHANGED
 * @param suggestedOwner 建议负责人,新资产入台账时作为 owner 初始值
 * @param extra          源域私有属性(只读展示,不参与指纹)
 */
public record AssetDescriptor(
    String assetKey,
    String sourceId,
    String name,
    String description,
    AssetType assetType,
    String layerCode,
    String domainCode,
    String suggestedOwner,
    LocalDateTime updatedAt,
    String contentHash,
    Map<String, String> extra) {

  public AssetDescriptor {
    extra = extra == null ? Map.of() : Map.copyOf(extra);
  }
}
