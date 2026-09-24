import { normalizeDevelopmentSqlDialect } from '@/services/data-development';

import type { SqlMetadataContext } from './sqlMetadataContextStore';

const CONTROLLED_KEYS = [
  'dataSourceId',
  'databaseName',
  'database',
  'catalog',
  'schemaName',
  'schema',
  'dialect',
  'databaseType',
  'dbType',
] as const;

const parseConfigObject = (configJson?: string): Record<string, unknown> => {
  try {
    const parsed = JSON.parse(configJson || '{}');
    return parsed && typeof parsed === 'object' && !Array.isArray(parsed)
      ? { ...(parsed as Record<string, unknown>) }
      : {};
  } catch {
    return {};
  }
};

/**
 * Merge the SQL authoring context into the existing Task config without
 * replacing unrelated runtime options such as maxRows or timeoutSeconds.
 */
export const mergeSqlTaskContextConfig = (
  configJson: string | undefined,
  context: Partial<SqlMetadataContext>,
) => {
  const config = parseConfigObject(configJson);
  CONTROLLED_KEYS.forEach((key) => delete config[key]);

  const values: Record<string, unknown> = {
    dataSourceId: context.dataSourceId,
    databaseName: context.database,
    schemaName: context.schema,
    dialect: normalizeDevelopmentSqlDialect(context.dialect ?? context.dbType),
  };

  Object.entries(values).forEach(([key, value]) => {
    if (value !== undefined && value !== '') config[key] = value;
  });

  return JSON.stringify(config);
};

export const resolveSqlEffectiveDatabase = (
  explicitDatabase?: string,
  connectionDefault?: string,
) => explicitDatabase || connectionDefault;

export const resolveSqlEffectiveSchema = (
  explicitSchema?: string,
  connectionDefault?: string,
) => explicitSchema || connectionDefault;

export const uniqueSqlContextValues = (
  values: Array<string | undefined | null>,
) => [...new Set(values.map((value) => value?.trim()).filter(Boolean) as string[])];
