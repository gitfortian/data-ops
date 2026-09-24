import {
  normalizeDevelopmentSqlDialect,
  parseDevelopmentSqlTaskConfig,
  type DevelopmentSqlDialect,
} from '@/services/data-development';
import { useSyncExternalStore } from 'react';

import {
  getEditorSession,
  updateEditorSessionConfig,
} from '../../session/editorSessionStore';
import type { DevelopmentId } from '../../../types';
import { mergeSqlTaskContextConfig } from './sqlTaskContextConfig';

export interface SqlMetadataContext {
  nodeId: DevelopmentId;
  dataSourceId?: string;
  dataSourceName?: string;
  dialect: DevelopmentSqlDialect;
  /** @deprecated Compatibility alias for existing SQL-assistance code; use dialect. */
  dbType?: DevelopmentSqlDialect;
  database?: string;
  schema?: string;
  updatedAt: number;
}

const STORAGE_KEY = 'yak-data-development.sql-metadata-contexts.v1';

interface PersistedSqlMetadataContexts {
  version: 1;
  contexts: unknown[];
}

const contexts = new Map<DevelopmentId, SqlMetadataContext>();
const listeners = new Set<() => void>();

let hydrated = false;
let version = 0;

const isBrowser = () => typeof window !== 'undefined';

const normalizePersistedContext = (
  value: unknown,
): SqlMetadataContext | undefined => {
  if (!value || typeof value !== 'object') return undefined;
  const context = value as Record<string, unknown>;
  if (
    typeof context.nodeId !== 'string' ||
    typeof context.updatedAt !== 'number'
  ) {
    return undefined;
  }

  const optionalString = (key: string) =>
    typeof context[key] === 'string' ? (context[key] as string) : undefined;
  const dialect = normalizeDevelopmentSqlDialect(
    optionalString('dialect') || optionalString('dbType'),
  );

  return {
    nodeId: context.nodeId,
    dataSourceId: optionalString('dataSourceId'),
    dataSourceName: optionalString('dataSourceName'),
    dialect,
    dbType: dialect,
    database: optionalString('database'),
    schema: optionalString('schema'),
    updatedAt: context.updatedAt,
  };
};

const ensureHydrated = () => {
  if (hydrated) return;
  hydrated = true;
  if (!isBrowser()) return;

  try {
    const raw = window.localStorage.getItem(STORAGE_KEY);
    if (!raw) return;
    const parsed = JSON.parse(raw) as Partial<PersistedSqlMetadataContexts>;
    if (parsed.version !== 1 || !Array.isArray(parsed.contexts)) return;
    parsed.contexts.forEach((value) => {
      const context = normalizePersistedContext(value);
      if (context) contexts.set(context.nodeId, context);
    });
  } catch {
    // Ignore malformed or unavailable local storage. SQL editing still works in memory.
  }
};

const persist = () => {
  if (!isBrowser()) return;
  const payload: PersistedSqlMetadataContexts = {
    version: 1,
    contexts: [...contexts.values()],
  };
  try {
    window.localStorage.setItem(STORAGE_KEY, JSON.stringify(payload));
  } catch {
    // Keep the in-memory context when local storage is unavailable or full.
  }
};

const emitChange = () => {
  version += 1;
  listeners.forEach((listener) => listener());
};

const subscribe = (listener: () => void) => {
  ensureHydrated();
  listeners.add(listener);
  return () => listeners.delete(listener);
};

const getVersion = () => {
  ensureHydrated();
  return version;
};

export const ensureSqlMetadataContext = (
  nodeId: DevelopmentId,
): SqlMetadataContext => {
  ensureHydrated();
  const current = contexts.get(nodeId);
  if (current) return current;

  const context: SqlMetadataContext = {
    nodeId,
    dialect: 'GENERIC',
    dbType: 'GENERIC',
    updatedAt: Date.now(),
  };
  contexts.set(nodeId, context);
  return context;
};

export const getSqlMetadataContext = (nodeId: DevelopmentId) => {
  ensureHydrated();
  return contexts.get(nodeId);
};

export const getSqlTaskConfigJson = (nodeId: DevelopmentId) => {
  const context = ensureSqlMetadataContext(nodeId);
  const currentConfigJson = getEditorSession(nodeId)?.configJson || '{}';
  return mergeSqlTaskContextConfig(currentConfigJson, context);
};

export const updateSqlMetadataContext = (
  nodeId: DevelopmentId,
  patch: Partial<Omit<SqlMetadataContext, 'nodeId' | 'updatedAt'>>,
) => {
  const current = ensureSqlMetadataContext(nodeId);
  const dialect = normalizeDevelopmentSqlDialect(
    patch.dialect ?? patch.dbType ?? current.dialect ?? current.dbType,
  );
  const next: SqlMetadataContext = {
    ...current,
    ...patch,
    dialect,
    dbType: dialect,
    nodeId,
    updatedAt: Date.now(),
  };
  contexts.set(nodeId, next);
  persist();
  emitChange();
  return next;
};

const syncEditorSessionContext = (
  nodeId: DevelopmentId,
  context: SqlMetadataContext,
) => {
  const currentConfigJson = getEditorSession(nodeId)?.configJson || '{}';
  updateEditorSessionConfig(
    nodeId,
    mergeSqlTaskContextConfig(currentConfigJson, context),
  );
};

export const hydrateSqlTaskConfig = (
  nodeId: DevelopmentId,
  configJson: string,
) => {
  const config = parseDevelopmentSqlTaskConfig(configJson);
  const current = ensureSqlMetadataContext(nodeId);
  const sameDataSource = current.dataSourceId === config.dataSourceId;
  const next: SqlMetadataContext = {
    ...current,
    nodeId,
    dataSourceId: config.dataSourceId,
    dataSourceName: sameDataSource ? current.dataSourceName : undefined,
    dialect: config.dialect,
    dbType: config.dialect,
    database: config.databaseName,
    schema: config.schemaName,
    updatedAt: Date.now(),
  };
  contexts.set(nodeId, next);
  persist();
  emitChange();
  return next;
};

/**
 * Presentation-only enrichment for a Draft-owned dataSourceId. This must never
 * clear database/schema or rewrite the editor Task config, otherwise merely
 * opening an existing Draft would create a fake unsaved change.
 */
export const enrichSqlDataSourceContext = (
  nodeId: DevelopmentId,
  dataSource: { id: string; name?: string },
) => {
  const current = ensureSqlMetadataContext(nodeId);
  if (current.dataSourceId !== dataSource.id || current.dataSourceName === dataSource.name) {
    return current;
  }
  return updateSqlMetadataContext(nodeId, { dataSourceName: dataSource.name });
};

export const selectSqlDataSourceContext = (
  nodeId: DevelopmentId,
  dataSource?: { id: string; name?: string; dbType?: string },
) => {
  const current = ensureSqlMetadataContext(nodeId);
  const dialect = dataSource?.dbType
    ? normalizeDevelopmentSqlDialect(dataSource.dbType)
    : current.dialect;
  const next = updateSqlMetadataContext(nodeId, {
    dataSourceId: dataSource?.id,
    dataSourceName: dataSource?.name,
    dialect,
    database: undefined,
    schema: undefined,
  });
  syncEditorSessionContext(nodeId, next);
  return next;
};

export const selectSqlDatabaseContext = (
  nodeId: DevelopmentId,
  database?: string,
) => {
  const next = updateSqlMetadataContext(nodeId, {
    database,
    schema: undefined,
  });
  syncEditorSessionContext(nodeId, next);
  return next;
};

export const selectSqlSchemaContext = (
  nodeId: DevelopmentId,
  schema?: string,
) => {
  const next = updateSqlMetadataContext(nodeId, { schema });
  syncEditorSessionContext(nodeId, next);
  return next;
};

export const useSqlMetadataContext = (nodeId: DevelopmentId) => {
  useSyncExternalStore(subscribe, getVersion, getVersion);
  return ensureSqlMetadataContext(nodeId);
};
