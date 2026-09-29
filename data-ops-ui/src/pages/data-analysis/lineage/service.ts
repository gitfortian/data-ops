/**
 * @deprecated Lineage code should import from `@/services/data-analysis`.
 * This file only keeps historical imports stable during the module migration.
 */
export {
  getLineageAssetByKey as fetchLineageAssetByKey,
  getLineageGraph as fetchLineageGraph,
  searchLineageAssets,
} from '@/services/data-analysis';
export type { SearchLineageAssetsParams } from '@/services/data-analysis';
