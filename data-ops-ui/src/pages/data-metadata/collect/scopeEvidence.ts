/**
 * A Run's saved scopeSnapshot is evidence about that historical run, not
 * current job configuration nor the list of all saved tables.
 */
export interface FrozenHarvestScope {
  dataSourceId: number;
  databaseName?: string;
  schemaName?: string;
  tablePattern?: string;
  collectColumns?: boolean;
}

export const readFrozenHarvestScope = (snapshot?: string | null): FrozenHarvestScope | null => {
  if (!snapshot) return null;
  try {
    const obj: unknown = JSON.parse(snapshot);
    if (!obj || typeof obj !== 'object' || Array.isArray(obj)) return null;
    const record = obj as Record<string, unknown>;
    const id = record.dataSourceId;
    if (typeof id !== 'number' || !Number.isSafeInteger(id) || id <= 0) return null;
    const optionalText = (value: unknown): string | undefined =>
      typeof value === 'string' && value.length <= 255 ? value : undefined;
    return {
      dataSourceId: id,
      databaseName: optionalText(record.databaseName),
      schemaName: optionalText(record.schemaName),
      tablePattern: optionalText(record.tablePattern),
      collectColumns: typeof record.collectColumns === 'boolean' ? record.collectColumns : undefined,
    };
  } catch {
    return null;
  }
};

/**
 * Catalogue is current Metadata truth. The historical SQL LIKE scope cannot
 * be substituted with a keyword search and is deliberately NOT auto-applied.
 */
export const currentSourceCatalogPath = (scope: FrozenHarvestScope): string => {
  const params = new URLSearchParams({ view: 'entity', datasourceId: String(scope.dataSourceId) });
  if (scope.databaseName) params.set('databaseName', scope.databaseName);
  return `/data-asset/catalog?${params.toString()}`;
};
