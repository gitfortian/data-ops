import {
  listSqlColumns,
  listSqlTables,
  type SqlCatalogColumn,
  type SqlCatalogTable,
} from './sqlMetadataService';
import type { SqlMetadataContext } from './sqlMetadataContextStore';

const CACHE_TTL = 60_000;

interface CacheEntry<T> {
  expiresAt: number;
  value: T;
}

const tableCache = new Map<string, CacheEntry<SqlCatalogTable[]>>();
const columnCache = new Map<string, CacheEntry<SqlCatalogColumn[]>>();
const pendingTables = new Map<string, Promise<SqlCatalogTable[]>>();
const pendingColumns = new Map<string, Promise<SqlCatalogColumn[]>>();

const cacheKey = (...parts: Array<string | undefined>) =>
  parts.map((part) => part || '').join('::');

const readCache = <T,>(cache: Map<string, CacheEntry<T>>, key: string) => {
  const entry = cache.get(key);
  if (!entry) return undefined;
  if (entry.expiresAt <= Date.now()) {
    cache.delete(key);
    return undefined;
  }
  return entry.value;
};

export const loadSqlTables = async (
  context: SqlMetadataContext,
  override: { database?: string; schema?: string } = {},
) => {
  if (!context.dataSourceId) return [];
  const database = override.database ?? context.database;
  const schema = override.schema ?? context.schema;
  const key = cacheKey(context.dataSourceId, database, schema);
  const cached = readCache(tableCache, key);
  if (cached) return cached;

  const pending = pendingTables.get(key);
  if (pending) return pending;

  const request = listSqlTables(context.dataSourceId, { database, schema })
    .then((tables) => {
      tableCache.set(key, { expiresAt: Date.now() + CACHE_TTL, value: tables });
      return tables;
    })
    .finally(() => pendingTables.delete(key));
  pendingTables.set(key, request);
  return request;
};

export const loadSqlColumns = async (
  context: SqlMetadataContext,
  table: string,
  override: { database?: string; schema?: string } = {},
) => {
  if (!context.dataSourceId || !table) return [];
  const database = override.database ?? context.database;
  const schema = override.schema ?? context.schema;
  const key = cacheKey(context.dataSourceId, database, schema, table.toLowerCase());
  const cached = readCache(columnCache, key);
  if (cached) return cached;

  const pending = pendingColumns.get(key);
  if (pending) return pending;

  const request = listSqlColumns(context.dataSourceId, {
    database,
    schema,
    table,
  })
    .then((columns) => {
      columnCache.set(key, { expiresAt: Date.now() + CACHE_TTL, value: columns });
      return columns;
    })
    .finally(() => pendingColumns.delete(key));
  pendingColumns.set(key, request);
  return request;
};

export const clearSqlMetadataCache = () => {
  tableCache.clear();
  columnCache.clear();
  pendingTables.clear();
  pendingColumns.clear();
};
