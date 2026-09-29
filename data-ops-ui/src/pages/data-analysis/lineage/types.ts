/**
 * @deprecated Lineage contracts live in `@/services/data-analysis`.
 * Kept as a compatibility export for page-local helpers during migration.
 */
export {
  LINEAGE_ASSET_TYPES,
  assetTypeLabel,
  relationTypeLabel,
} from '@/services/data-analysis';
export type {
  LineageAsset,
  LineageAssetType,
  LineageDirection,
  LineageGraph,
  LineageRelation,
  LineageRelationType,
} from '@/services/data-analysis';
