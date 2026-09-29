package io.yak.ops.business.asset.api;

import io.yak.ops.common.enums.asset.AssetSourceType;
import java.util.Optional;

/**
 * 资产源域供给 SPI:由各源域(modeling/metric/dataset/…)实现并注册为 Spring Bean,
 * asset 模块只收集、只调用,绝不 import 任何源域内部包。
 *
 * <p>实现约束(design 8.1):只读本域既有 Service;游标批量 ≤500;
 * {@code assetKey} 必须复用本域血缘登记键生成器,与 {@code yak_metadata_asset} 同源。
 */
public interface AssetProvider {

  AssetSourceType sourceType();

  /** 增量游标遍历:按主键升序;updatedAfter 非空时仅返回该时间后有更新的资产。 */
  AssetPage cursorList(AssetCursorQuery query);

  /** 单资产刷新:详情聚合/对账复核用;源已删除或不存在时返回 empty。 */
  Optional<AssetDescriptor> refresh(String sourceId);
}
