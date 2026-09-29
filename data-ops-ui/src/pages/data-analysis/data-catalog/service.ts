/**
 * @deprecated Data catalog code should import from `@/services/data-analysis`.
 * This file only keeps historical imports stable during the module migration.
 */
export {
  getCatalogWorkspace as fetchCatalogWorkspace,
  listCatalogDatasets as fetchCatalogDatasets,
  offlineCatalogDataset,
  onlineCatalogDataset,
} from '@/services/data-analysis';
export type {
  CatalogDataset,
  CatalogDatasetField,
  CatalogDatasetFieldRole,
  CatalogDatasetFieldType,
  CatalogDatasetSourceType,
  CatalogDatasetStatus,
  CatalogDatasetVersion,
  CatalogDirectory,
  CatalogWorkspace,
} from '@/services/data-analysis';
