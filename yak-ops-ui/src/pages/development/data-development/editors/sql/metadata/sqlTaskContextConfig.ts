import {
  normalizeDevelopmentSqlDialect,
  parseDevelopmentSqlTaskConfig,
  type DevelopmentSqlDialect,
} from '@/services/data-development';

interface SqlTaskContextValues {
  dataSourceId?: string;
  database?: string;
  schema?: string;
  dialect?: DevelopmentSqlDialect;
  dbType?: DevelopmentSqlDialect;
}

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

const normalizeOptional = (value?: string) => value?.trim() || undefined;

const hasSameSqlContext = (
  configJson: string | undefined,
  context: SqlTaskContextValues,
) => {
  const current = parseDevelopmentSqlTaskConfig(configJson);
  return (
    current.dataSourceId === normalizeOptional(context.dataSourceId) &&
    current.databaseName === normalizeOptional(context.database) &&
    current.schemaName === normalizeOptional(context.schema) &&
    current.dialect ===
      normalizeDevelopmentSqlDialect(context.dialect ?? context.dbType)
  );
};

/**
 * Merge the SQL authoring context into the existing Task config without
 * replacing unrelated runtime options such as maxRows or timeoutSeconds.
 *
 * If the context is already semantically identical, return the original JSON
 * byte-for-byte so merely opening or running an old Draft does not create a
 * fake unsaved change through alias normalization or JSON re-serialization.
 */
export const mergeSqlTaskContextConfig = (
  configJson: string | undefined,
  context: SqlTaskContextValues,
) => {
  if (hasSameSqlContext(configJson, context)) return configJson || '{}';

  const config = parseConfigObject(configJson);
  CONTROLLED_KEYS.forEach((key) => delete config[key]);

  const values: Record<string, unknown> = {
    dataSourceId: normalizeOptional(context.dataSourceId),
    databaseName: normalizeOptional(context.database),
    schemaName: normalizeOptional(context.schema),
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
