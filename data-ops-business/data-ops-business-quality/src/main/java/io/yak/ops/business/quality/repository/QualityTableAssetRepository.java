package io.yak.ops.business.quality.repository;

import io.yak.framework.common.PageData;
import io.yak.ops.business.quality.domain.QualityDomain.TableAsset;
import io.yak.ops.business.quality.domain.QualityDomain.TableAssetSpec;
import io.yak.ops.business.quality.domain.QualityDomain.TableAssetTarget;
import io.yak.ops.business.quality.domain.QualityQuery;
import java.util.List;

/** Persistence port for registered table assets. */
public interface QualityTableAssetRepository {
  PageData<TableAsset> pageTableAssets(QualityQuery.TableAsset query);
  List<TableAssetTarget> listTableAssetTargets(long dataSourceId, String databaseName);
  boolean existsTableAssetTarget(long dataSourceId, String databaseName, String schemaName, String tableName);
  int registerTableAssets(List<TableAssetSpec> assets);
  int countMonitorsForTableAsset(long assetId);
  boolean deleteTableAsset(long assetId);
}
