package io.yak.ops.common.enums.asset;

/** 资产上架状态机(台账 status 列)。 */
public enum AssetStatus {
  /** 待上架(对账新发现或忽略后回归)。 */
  PENDING,
  /** 已上架(目录可见)。 */
  PUBLISHED,
  /** 已下架(人工,带原因)。 */
  OFFLINE,
  /** 忽略抑制(盘点批量忽略;对账不复活,仅刷 reconciled_at)。 */
  IGNORED,
  /** 源已消失(对账窗口内未再出现)。 */
  SOURCE_GONE
}
