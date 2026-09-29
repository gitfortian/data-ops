import {
  normalizeDevelopmentSqlDialect,
  type DevelopmentSqlDialect,
} from '@/services/data-development';

import {
  SQL_BUILTIN_FUNCTIONS,
  SQL_KEYWORDS,
  type SqlBuiltinFunctionDefinition,
} from '../completion/sqlBuiltinCatalog';
import {
  SQL_SNIPPETS,
  type SqlSnippetDefinition,
} from '../completion/sqlSnippetCatalog';

export type SqlDialectFamily =
  | 'GENERIC'
  | 'MYSQL'
  | 'POSTGRESQL'
  | 'ORACLE'
  | 'SQL_SERVER'
  | 'DB2'
  | 'HANA'
  | 'DUCKDB'
  | 'CLICKHOUSE';

export type SqlIdentifierQuote = 'DOUBLE_QUOTE' | 'BACKTICK' | 'BRACKET';
export type SqlTwoPartNamespace = 'database' | 'schema';

export interface SqlEditorProfile {
  dialect: DevelopmentSqlDialect;
  family: SqlDialectFamily;
  identifierQuote: SqlIdentifierQuote;
  twoPartNamespace: SqlTwoPartNamespace;
  keywords: readonly string[];
  functions: readonly SqlBuiltinFunctionDefinition[];
  snippets: readonly SqlSnippetDefinition[];
  formatterClauses: readonly string[];
}

interface SqlEditorProfileOverlay {
  identifierQuote?: SqlIdentifierQuote;
  twoPartNamespace?: SqlTwoPartNamespace;
  keywords?: readonly string[];
  functions?: readonly SqlBuiltinFunctionDefinition[];
  snippets?: readonly SqlSnippetDefinition[];
  formatterClauses?: readonly string[];
}

const sqlFunction = (
  name: string,
  signature: string,
  argumentsSnippet: string,
  description: string,
): SqlBuiltinFunctionDefinition => ({
  name,
  signature,
  argumentsSnippet,
  description,
});

const sqlSnippet = (
  prefix: string,
  label: string,
  detail: string,
  body: string,
): SqlSnippetDefinition => ({ prefix, label, detail, body });

const DIALECT_FAMILIES: Record<DevelopmentSqlDialect, SqlDialectFamily> = {
  GENERIC: 'GENERIC',
  MYSQL: 'MYSQL',
  TIDB: 'MYSQL',
  GOLDENDB: 'MYSQL',
  GBASE8C: 'POSTGRESQL',
  GBASE8A: 'MYSQL',
  GBASE8S: 'GENERIC',
  HANA: 'HANA',
  ORACLE: 'ORACLE',
  POSTGRE_SQL: 'POSTGRESQL',
  DB2: 'DB2',
  OPEN_GAUSS: 'POSTGRESQL',
  SQL_SERVER: 'SQL_SERVER',
  OCEANBASE: 'GENERIC',
  YASHAN_DB: 'GENERIC',
  HIGHGO: 'POSTGRESQL',
  IRIS: 'GENERIC',
  XUGU: 'GENERIC',
  DUCKDB: 'DUCKDB',
  DORIS: 'MYSQL',
  STARROCKS: 'MYSQL',
  CLICKHOUSE: 'CLICKHOUSE',
  KINGBASE: 'POSTGRESQL',
  DAMENG: 'ORACLE',
};

const FAMILY_OVERLAYS: Record<SqlDialectFamily, SqlEditorProfileOverlay> = {
  GENERIC: {},
  MYSQL: {
    identifierQuote: 'BACKTICK',
    twoPartNamespace: 'database',
    keywords: [
      'AUTO_INCREMENT',
      'DESCRIBE',
      'SHOW',
      'USE',
      'REPLACE',
      'ON DUPLICATE KEY UPDATE',
    ],
    functions: [
      sqlFunction(
        'IFNULL',
        'IFNULL(value, fallback)',
        '${1:value}, ${2:fallback}',
        '返回第一个非 NULL 值，常用于 MySQL 兼容方言。',
      ),
      sqlFunction(
        'DATE_FORMAT',
        'DATE_FORMAT(date, format)',
        '${1:date}, ${2:format}',
        '按指定格式输出日期或时间。',
      ),
      sqlFunction(
        'GROUP_CONCAT',
        'GROUP_CONCAT(expression)',
        '${1:expression}',
        '将分组内的值连接为一个字符串。',
      ),
      sqlFunction(
        'JSON_EXTRACT',
        'JSON_EXTRACT(json_doc, path)',
        '${1:json_doc}, ${2:path}',
        '从 JSON 文档中提取指定路径的值。',
      ),
    ],
    snippets: [
      sqlSnippet(
        'insdup',
        'INSERT … ON DUPLICATE KEY UPDATE',
        'MySQL 兼容 Upsert 模板',
        'INSERT INTO ${1:table_name} (${2:column_name})\nVALUES (${3:value})\nON DUPLICATE KEY UPDATE ${2:column_name} = VALUES(${2:column_name});\n${0}',
      ),
    ],
    formatterClauses: ['ON DUPLICATE KEY UPDATE'],
  },
  POSTGRESQL: {
    identifierQuote: 'DOUBLE_QUOTE',
    twoPartNamespace: 'schema',
    keywords: [
      'RETURNING',
      'ILIKE',
      'LATERAL',
      'JSONB',
      'ON CONFLICT',
      'DO NOTHING',
      'FILTER',
    ],
    functions: [
      sqlFunction(
        'DATE_TRUNC',
        'DATE_TRUNC(field, source)',
        '${1:field}, ${2:source}',
        '按指定时间粒度截断日期或时间。',
      ),
      sqlFunction(
        'STRING_AGG',
        'STRING_AGG(expression, delimiter)',
        '${1:expression}, ${2:delimiter}',
        '将分组内的字符串按分隔符聚合。',
      ),
      sqlFunction(
        'JSONB_BUILD_OBJECT',
        'JSONB_BUILD_OBJECT(key, value, ...)',
        '${1:key}, ${2:value}',
        '构造 JSONB 对象。',
      ),
      sqlFunction(
        'TO_CHAR',
        'TO_CHAR(value, format)',
        '${1:value}, ${2:format}',
        '按指定格式将值转换为字符串。',
      ),
    ],
    snippets: [
      sqlSnippet(
        'insconflict',
        'INSERT … ON CONFLICT',
        'PostgreSQL 兼容 Upsert 模板',
        'INSERT INTO ${1:table_name} (${2:id}, ${3:column_name})\nVALUES (${4:id_value}, ${5:value})\nON CONFLICT (${2:id}) DO UPDATE\nSET ${3:column_name} = EXCLUDED.${3:column_name}\nRETURNING *;\n${0}',
      ),
    ],
    formatterClauses: ['ON CONFLICT', 'DO UPDATE', 'RETURNING'],
  },
  ORACLE: {
    identifierQuote: 'DOUBLE_QUOTE',
    twoPartNamespace: 'schema',
    keywords: ['MERGE', 'ROWNUM', 'DUAL', 'CONNECT BY', 'START WITH', 'RETURNING'],
    functions: [
      sqlFunction(
        'NVL',
        'NVL(value, fallback)',
        '${1:value}, ${2:fallback}',
        '值为 NULL 时返回备用值。',
      ),
      sqlFunction(
        'DECODE',
        'DECODE(expression, search, result, default)',
        '${1:expression}, ${2:search}, ${3:result}, ${4:default}',
        '按匹配值返回对应结果。',
      ),
      sqlFunction(
        'TO_DATE',
        'TO_DATE(value, format)',
        '${1:value}, ${2:format}',
        '按指定格式将字符串转换为日期。',
      ),
      sqlFunction(
        'TO_CHAR',
        'TO_CHAR(value, format)',
        '${1:value}, ${2:format}',
        '按指定格式将日期或数值转换为字符串。',
      ),
    ],
    snippets: [
      sqlSnippet(
        'merge',
        'MERGE INTO',
        'Oracle 兼容 MERGE 模板',
        'MERGE INTO ${1:target_table} t\nUSING ${2:source_table} s\nON (${3:t.id = s.id})\nWHEN MATCHED THEN\n  UPDATE SET ${4:t.column_name = s.column_name}\nWHEN NOT MATCHED THEN\n  INSERT (${5:column_name}) VALUES (${6:s.column_name});\n${0}',
      ),
    ],
    formatterClauses: ['USING', 'WHEN MATCHED THEN', 'WHEN NOT MATCHED THEN', 'CONNECT BY', 'START WITH', 'RETURNING'],
  },
  SQL_SERVER: {
    identifierQuote: 'BRACKET',
    twoPartNamespace: 'schema',
    keywords: ['TOP', 'GO', 'OUTPUT', 'IDENTITY', 'MERGE'],
    functions: [
      sqlFunction(
        'ISNULL',
        'ISNULL(value, fallback)',
        '${1:value}, ${2:fallback}',
        '值为 NULL 时返回备用值。',
      ),
      sqlFunction(
        'GETDATE',
        'GETDATE()',
        '',
        '返回当前数据库服务器日期和时间。',
      ),
      sqlFunction(
        'DATEADD',
        'DATEADD(datepart, number, date)',
        '${1:datepart}, ${2:number}, ${3:date}',
        '为日期增加指定的时间间隔。',
      ),
      sqlFunction(
        'DATEDIFF',
        'DATEDIFF(datepart, startdate, enddate)',
        '${1:datepart}, ${2:startdate}, ${3:enddate}',
        '返回两个日期之间指定粒度的差值。',
      ),
    ],
    snippets: [
      sqlSnippet(
        'seltop',
        'SELECT TOP',
        'SQL Server TOP 查询模板',
        'SELECT TOP (${1:100}) ${2:*}\nFROM ${3:table_name}\nWHERE ${4:1 = 1};\n${0}',
      ),
    ],
    formatterClauses: ['OUTPUT'],
  },
  DB2: {
    identifierQuote: 'DOUBLE_QUOTE',
    twoPartNamespace: 'schema',
    keywords: ['FETCH FIRST', 'WITH UR', 'GENERATED ALWAYS'],
    functions: [
      sqlFunction(
        'VARCHAR_FORMAT',
        'VARCHAR_FORMAT(value, format)',
        '${1:value}, ${2:format}',
        '按指定格式将日期或数值转换为字符串。',
      ),
      sqlFunction(
        'TIMESTAMP_FORMAT',
        'TIMESTAMP_FORMAT(value, format)',
        '${1:value}, ${2:format}',
        '按指定格式将字符串转换为时间戳。',
      ),
    ],
    formatterClauses: ['FETCH FIRST', 'WITH UR'],
  },
  HANA: {
    identifierQuote: 'DOUBLE_QUOTE',
    twoPartNamespace: 'schema',
    keywords: ['UPSERT', 'DUMMY', 'TOP'],
    functions: [
      sqlFunction(
        'IFNULL',
        'IFNULL(value, fallback)',
        '${1:value}, ${2:fallback}',
        '值为 NULL 时返回备用值。',
      ),
      sqlFunction(
        'TO_VARCHAR',
        'TO_VARCHAR(value, format)',
        '${1:value}, ${2:format}',
        '将值转换为字符串。',
      ),
      sqlFunction(
        'ADD_DAYS',
        'ADD_DAYS(date, days)',
        '${1:date}, ${2:days}',
        '为日期增加指定天数。',
      ),
    ],
    formatterClauses: [],
  },
  DUCKDB: {
    identifierQuote: 'DOUBLE_QUOTE',
    twoPartNamespace: 'schema',
    keywords: ['QUALIFY', 'PIVOT', 'UNPIVOT', 'SAMPLE'],
    functions: [
      sqlFunction(
        'DATE_TRUNC',
        'DATE_TRUNC(part, date)',
        '${1:part}, ${2:date}',
        '按指定时间粒度截断日期或时间。',
      ),
      sqlFunction(
        'STRUCT_PACK',
        'STRUCT_PACK(name := value, ...)',
        '${1:name} := ${2:value}',
        '构造 STRUCT 值。',
      ),
    ],
    snippets: [
      sqlSnippet(
        'qualify',
        'QUALIFY window result',
        'DuckDB QUALIFY 查询模板',
        'SELECT ${1:*}\nFROM ${2:table_name}\nQUALIFY ${3:ROW_NUMBER() OVER (PARTITION BY id ORDER BY updated_at DESC) = 1};\n${0}',
      ),
    ],
    formatterClauses: ['QUALIFY'],
  },
  CLICKHOUSE: {
    identifierQuote: 'BACKTICK',
    twoPartNamespace: 'database',
    keywords: ['PREWHERE', 'ARRAY JOIN', 'FINAL', 'SETTINGS', 'SAMPLE'],
    functions: [
      sqlFunction(
        'toDate',
        'toDate(value)',
        '${1:value}',
        '将值转换为 Date。',
      ),
      sqlFunction(
        'uniq',
        'uniq(expression)',
        '${1:expression}',
        '近似计算唯一值数量。',
      ),
      sqlFunction(
        'groupArray',
        'groupArray(expression)',
        '${1:expression}',
        '将分组内的值聚合为数组。',
      ),
    ],
    snippets: [
      sqlSnippet(
        'selpre',
        'SELECT … PREWHERE',
        'ClickHouse PREWHERE 查询模板',
        'SELECT ${1:*}\nFROM ${2:table_name}\nPREWHERE ${3:condition}\nWHERE ${4:1 = 1};\n${0}',
      ),
    ],
    formatterClauses: ['ARRAY JOIN', 'PREWHERE', 'SETTINGS'],
  },
};

const DIALECT_OVERLAYS: Partial<
  Record<DevelopmentSqlDialect, SqlEditorProfileOverlay>
> = {
  TIDB: {
    keywords: ['AUTO_RANDOM'],
  },
  DORIS: {
    keywords: [
      'DUPLICATE KEY',
      'UNIQUE KEY',
      'AGGREGATE KEY',
      'DISTRIBUTED BY',
      'PROPERTIES',
    ],
    formatterClauses: ['DISTRIBUTED BY', 'PROPERTIES'],
  },
  STARROCKS: {
    keywords: [
      'DUPLICATE KEY',
      'UNIQUE KEY',
      'PRIMARY KEY',
      'AGGREGATE KEY',
      'DISTRIBUTED BY',
      'PROPERTIES',
    ],
    formatterClauses: ['DISTRIBUTED BY', 'PROPERTIES'],
  },
};

const mergeUniqueStrings = (
  ...groups: ReadonlyArray<ReadonlyArray<string> | undefined>
) => {
  const values: string[] = [];
  const seen = new Set<string>();
  groups.forEach((group) => {
    group?.forEach((value) => {
      const key = value.toUpperCase();
      if (seen.has(key)) return;
      seen.add(key);
      values.push(value);
    });
  });
  return values;
};

const mergeFunctions = (
  ...groups: ReadonlyArray<
    ReadonlyArray<SqlBuiltinFunctionDefinition> | undefined
  >
) => {
  const values = new Map<string, SqlBuiltinFunctionDefinition>();
  groups.forEach((group) => {
    group?.forEach((definition) => {
      values.set(definition.name.toUpperCase(), definition);
    });
  });
  return [...values.values()];
};

const mergeSnippets = (
  ...groups: ReadonlyArray<ReadonlyArray<SqlSnippetDefinition> | undefined>
) => {
  const values = new Map<string, SqlSnippetDefinition>();
  groups.forEach((group) => {
    group?.forEach((definition) => {
      values.set(definition.prefix.toLowerCase(), definition);
    });
  });
  return [...values.values()];
};

const profileCache = new Map<DevelopmentSqlDialect, SqlEditorProfile>();

export const getSqlDialectFamily = (
  dialect?: DevelopmentSqlDialect | string,
): SqlDialectFamily =>
  DIALECT_FAMILIES[normalizeDevelopmentSqlDialect(dialect)];

export const resolveSqlEditorProfile = (
  dialect?: DevelopmentSqlDialect | string,
): SqlEditorProfile => {
  const normalized = normalizeDevelopmentSqlDialect(dialect);
  const cached = profileCache.get(normalized);
  if (cached) return cached;

  const family = DIALECT_FAMILIES[normalized];
  const familyOverlay = FAMILY_OVERLAYS[family];
  const dialectOverlay = DIALECT_OVERLAYS[normalized];
  const profile: SqlEditorProfile = {
    dialect: normalized,
    family,
    identifierQuote:
      dialectOverlay?.identifierQuote ??
      familyOverlay.identifierQuote ??
      'DOUBLE_QUOTE',
    twoPartNamespace:
      dialectOverlay?.twoPartNamespace ??
      familyOverlay.twoPartNamespace ??
      'schema',
    keywords: mergeUniqueStrings(
      SQL_KEYWORDS,
      familyOverlay.keywords,
      dialectOverlay?.keywords,
    ),
    functions: mergeFunctions(
      SQL_BUILTIN_FUNCTIONS,
      familyOverlay.functions,
      dialectOverlay?.functions,
    ),
    snippets: mergeSnippets(
      SQL_SNIPPETS,
      familyOverlay.snippets,
      dialectOverlay?.snippets,
    ),
    formatterClauses: mergeUniqueStrings(
      familyOverlay.formatterClauses,
      dialectOverlay?.formatterClauses,
    ),
  };

  profileCache.set(normalized, profile);
  return profile;
};

const SIMPLE_IDENTIFIER = /^[A-Za-z_][A-Za-z0-9_$]*$/;

export const quoteSqlIdentifier = (
  identifier: string,
  profile: SqlEditorProfile,
) => {
  if (profile.identifierQuote === 'BACKTICK') {
    return `\`${identifier.replace(/`/g, '``')}\``;
  }
  if (profile.identifierQuote === 'BRACKET') {
    return `[${identifier.replace(/]/g, ']]')}]`;
  }
  return `"${identifier.replace(/"/g, '""')}"`;
};

export const formatSqlIdentifierForCompletion = (
  identifier: string,
  profile: SqlEditorProfile,
) => {
  const normalized = identifier.trim();
  if (!normalized) return identifier;

  const reserved = profile.keywords.some(
    (keyword) => keyword.toUpperCase() === normalized.toUpperCase(),
  );
  if (SIMPLE_IDENTIFIER.test(normalized) && !reserved) return normalized;
  return quoteSqlIdentifier(normalized, profile);
};
