import * as monaco from 'monaco-editor/esm/vs/editor/editor.api';

import type { DevelopmentId } from '../../../types';
import type { SqlMetadataContext } from '../metadata/sqlMetadataContextStore';
import { resolveSqlEditorProfile } from '../profiles/sqlEditorProfiles';

export type SqlLexicalState =
  | 'code'
  | 'single-quote'
  | 'double-quote'
  | 'backtick'
  | 'bracket-identifier'
  | 'line-comment'
  | 'block-comment';

export interface SqlTableReference {
  database?: string;
  schema?: string;
  table: string;
  alias?: string;
}

const STOP_ALIAS_WORDS = new Set([
  'WHERE',
  'JOIN',
  'LEFT',
  'RIGHT',
  'FULL',
  'INNER',
  'OUTER',
  'CROSS',
  'ON',
  'GROUP',
  'ORDER',
  'HAVING',
  'LIMIT',
  'OFFSET',
  'UNION',
  'EXCEPT',
  'INTERSECT',
  'SET',
  'VALUES',
  'RETURNING',
]);

const identifierPart =
  '(?:`[^`]+`|"[^"]+"|\\[[^\\]]+\\]|[A-Za-z_$][\\w$]*)';
const qualifiedIdentifier = `${identifierPart}(?:\\s*\\.\\s*${identifierPart}){0,2}`;
const tableReferencePattern = new RegExp(
  `\\b(?:FROM|JOIN)\\s+(${qualifiedIdentifier})(?:\\s+(?:AS\\s+)?([A-Za-z_$][\\w$]*))?`,
  'gi',
);

export const getSqlTextBeforePosition = (
  model: monaco.editor.ITextModel,
  position: monaco.Position,
) =>
  model.getValueInRange(
    new monaco.Range(1, 1, position.lineNumber, position.column),
  );

export const getCurrentSqlStatementText = (
  model: monaco.editor.ITextModel,
  position: monaco.Position,
) => {
  const sql = model.getValue();
  const offset = model.getOffsetAt(position);
  const start = sql.lastIndexOf(';', Math.max(0, offset - 1)) + 1;
  const nextSeparator = sql.indexOf(';', offset);
  const end = nextSeparator < 0 ? sql.length : nextSeparator;
  return sql.slice(start, end);
};

export const getSqlLexicalState = (text: string): SqlLexicalState => {
  let state: SqlLexicalState = 'code';

  for (let index = 0; index < text.length; index += 1) {
    const current = text[index];
    const next = text[index + 1];

    if (state === 'line-comment') {
      if (current === '\n') state = 'code';
      continue;
    }
    if (state === 'block-comment') {
      if (current === '*' && next === '/') {
        state = 'code';
        index += 1;
      }
      continue;
    }
    if (state === 'single-quote') {
      if (current === "'" && next === "'") {
        index += 1;
        continue;
      }
      if (current === "'") state = 'code';
      continue;
    }
    if (state === 'double-quote') {
      if (current === '"' && next === '"') {
        index += 1;
        continue;
      }
      if (current === '"') state = 'code';
      continue;
    }
    if (state === 'backtick') {
      if (current === '`' && next === '`') {
        index += 1;
        continue;
      }
      if (current === '`') state = 'code';
      continue;
    }
    if (state === 'bracket-identifier') {
      if (current === ']' && next === ']') {
        index += 1;
        continue;
      }
      if (current === ']') state = 'code';
      continue;
    }

    if (current === '-' && next === '-') {
      state = 'line-comment';
      index += 1;
    } else if (current === '/' && next === '*') {
      state = 'block-comment';
      index += 1;
    } else if (current === "'") {
      state = 'single-quote';
    } else if (current === '"') {
      state = 'double-quote';
    } else if (current === '`') {
      state = 'backtick';
    } else if (current === '[') {
      state = 'bracket-identifier';
    }
  }

  return state;
};

const stripQuotedValue = (value: string) => {
  const normalized = value.trim();
  if (
    (normalized.startsWith('`') && normalized.endsWith('`')) ||
    (normalized.startsWith('"') && normalized.endsWith('"')) ||
    (normalized.startsWith('[') && normalized.endsWith(']'))
  ) {
    return normalized.slice(1, -1);
  }
  return normalized;
};

const splitQualifiedIdentifier = (value: string) => {
  const parts: string[] = [];
  let current = '';
  let quote: '`' | '"' | '[' | undefined;

  for (let index = 0; index < value.length; index += 1) {
    const char = value[index];
    if (!quote && (char === '`' || char === '"' || char === '[')) {
      quote = char as '`' | '"' | '[';
      current += char;
      continue;
    }
    if (quote === '`' && char === '`') quote = undefined;
    else if (quote === '"' && char === '"') quote = undefined;
    else if (quote === '[' && char === ']') quote = undefined;

    if (!quote && char === '.') {
      if (current.trim()) parts.push(stripQuotedValue(current));
      current = '';
      continue;
    }
    current += char;
  }

  if (current.trim()) parts.push(stripQuotedValue(current));
  return parts;
};

const tableReferenceFromParts = (
  parts: string[],
  alias: string | undefined,
  context: SqlMetadataContext,
): SqlTableReference | undefined => {
  if (!parts.length) return undefined;
  const table = parts[parts.length - 1];
  if (!table) return undefined;

  if (parts.length >= 3) {
    return {
      database: parts[parts.length - 3],
      schema: parts[parts.length - 2],
      table,
      alias,
    };
  }

  if (parts.length === 2) {
    const first = parts[0];
    const profile = resolveSqlEditorProfile(context.dialect);
    return profile.twoPartNamespace === 'database'
      ? { database: first, table, alias }
      : { database: context.database, schema: first, table, alias };
  }

  return {
    database: context.database,
    schema: context.schema,
    table,
    alias,
  };
};

export const parseSqlTableReferences = (
  sql: string,
  context: SqlMetadataContext,
): SqlTableReference[] => {
  const references: SqlTableReference[] = [];
  tableReferencePattern.lastIndex = 0;
  let match = tableReferencePattern.exec(sql);

  while (match) {
    const rawAlias = match[2];
    const alias =
      rawAlias && !STOP_ALIAS_WORDS.has(rawAlias.toUpperCase())
        ? rawAlias
        : undefined;
    const reference = tableReferenceFromParts(
      splitQualifiedIdentifier(match[1]),
      alias,
      context,
    );
    if (reference) references.push(reference);
    match = tableReferencePattern.exec(sql);
  }

  return references;
};

export const findSqlTableReference = (
  qualifier: string,
  references: SqlTableReference[],
) => {
  const normalized = qualifier.toLowerCase();
  return references.find(
    (reference) =>
      reference.alias?.toLowerCase() === normalized ||
      reference.table.toLowerCase() === normalized,
  );
};

export const getSqlQualifierBeforeWord = (
  model: monaco.editor.ITextModel,
  position: monaco.Position,
  wordStartColumn: number,
) => {
  const line = model.getLineContent(position.lineNumber);
  const beforeWord = line.slice(0, Math.max(0, wordStartColumn - 1));
  return beforeWord.match(/([A-Za-z_$][\w$]*)\s*\.\s*$/)?.[1];
};

export const getSqlEditorNodeId = (
  model: monaco.editor.ITextModel,
): DevelopmentId | undefined => {
  const filename = model.uri.path.split('/').pop();
  if (!filename?.endsWith('.sql')) return undefined;
  const encoded = filename.slice(0, -4);
  if (!encoded) return undefined;

  try {
    return decodeURIComponent(encoded);
  } catch {
    return encoded;
  }
};
