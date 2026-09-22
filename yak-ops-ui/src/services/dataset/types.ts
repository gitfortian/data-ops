export type DatasetStatus = 'ONLINE' | 'OFFLINE';

/** Persisted Dataset source types. Keep this union aligned with backend DatasetSourceType. */
export type DatasetSourceType = 'QUERY_REVISION' | 'SQL_QUERY' | 'TABLE' | 'VIEW';

/** Source types that currently have a Dataset Query Runtime adapter. */
export type QueryableDatasetSourceType = Extract<
  DatasetSourceType,
  'QUERY_REVISION' | 'SQL_QUERY'
>;

export type DatasetFieldDataType =
  | 'STRING'
  | 'NUMBER'
  | 'DATE'
  | 'DATETIME'
  | 'BOOLEAN'
  | 'UNKNOWN';

export type DatasetFieldRole = 'DIMENSION' | 'MEASURE';

/** HTTP contract for GET /api/v1/datasets. */
export interface DatasetSummaryWire {
  id: string;
  name: string;
  description?: string | null;
  status: DatasetStatus;
  currentVersionId?: string | null;
  createTime?: string;
  updateTime?: string;
}

/** HTTP contract for DatasetVersionVO. */
export interface DatasetVersionWire {
  id: string;
  datasetId: string;
  versionNo: number;
  sourceType: DatasetSourceType;
  sourceTaskAssetId: string;
  sourceTaskRevisionId: string;
  sourceTaskRevisionNo: number;
  dataSourceId?: string | null;
  sql?: string | null;
  schemaSnapshot?: string | null;
  createTime?: string;
}

/** HTTP contract for DatasetFieldVO. */
export interface DatasetFieldWire {
  fieldId: string;
  versionId: string;
  physicalName: string;
  displayName: string;
  dataType: DatasetFieldDataType;
  nullable: boolean;
  description?: string | null;
  defaultRole: DatasetFieldRole;
  sortOrder: number;
}

/** HTTP contract for GET /api/v1/datasets/catalog. */
export interface DatasetCatalogWire {
  dataset: DatasetSummaryWire;
  currentVersion?: DatasetVersionWire | null;
  fields: DatasetFieldWire[];
}

/** HTTP contract for DatasetDetailVO. */
export interface DatasetDetailWire {
  dataset: DatasetSummaryWire;
  currentVersion?: DatasetVersionWire | null;
  versions: DatasetVersionWire[];
  fields: DatasetFieldWire[];
}
