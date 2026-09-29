import * as monaco from 'monaco-editor/esm/vs/editor/editor.api';

import { loadSqlColumns, loadSqlTables } from '../metadata/sqlMetadataCache';
import { getSqlMetadataContext } from '../metadata/sqlMetadataContextStore';
import type {
  SqlCatalogColumn,
  SqlCatalogTable,
} from '../metadata/sqlMetadataService';
import { resolveSqlEditorProfile } from '../profiles/sqlEditorProfiles';
import {
  findSqlTableReference,
  getCurrentSqlStatementText,
  getSqlEditorNodeId,
  getSqlLexicalState,
  getSqlQualifierBeforeWord,
  getSqlTextBeforePosition,
  parseSqlTableReferences,
  type SqlTableReference,
} from './sqlTextContext';

let providerDisposable: monaco.IDisposable | undefined;
let providerConsumers = 0;

const escapeMarkdown = (value: string) =>
  value.replace(/([\\`*_{}\[\]()#+\-.!|>])/g, '\\$1');

const inlineCode = (value: string) => `\`${value.replace(/`/g, '\\`')}\``;

const wordRange = (
  position: monaco.Position,
  word: monaco.editor.IWordAtPosition,
) =>
  new monaco.Range(
    position.lineNumber,
    word.startColumn,
    position.lineNumber,
    word.endColumn,
  );

const isFunctionCall = (
  model: monaco.editor.ITextModel,
  position: monaco.Position,
  word: monaco.editor.IWordAtPosition,
) => {
  const line = model.getLineContent(position.lineNumber);
  return /^\s*\(/.test(line.slice(word.endColumn - 1));
};

const tablePath = (value: SqlCatalogTable | SqlTableReference) => {
  const tableName = 'table' in value ? value.table : value.name;
  return [value.database, value.schema, tableName].filter(Boolean).join('.');
};

const columnHover = (
  range: monaco.Range,
  column: SqlCatalogColumn,
  reference: SqlTableReference,
): monaco.languages.Hover => {
  const flags = [
    column.primaryKey ? 'PRIMARY KEY' : undefined,
    column.nullable === false ? 'NOT NULL' : undefined,
  ].filter(Boolean);
  const type = column.typeName || '未知类型';
  const owner = reference.alias
    ? `${reference.alias} → ${tablePath(reference)}`
    : tablePath(reference);

  const contents: monaco.IMarkdownString[] = [
    {
      value: `**字段** ${inlineCode(column.name)} · ${inlineCode(type)}`,
    },
    {
      value: `来源：${inlineCode(owner)}${flags.length ? ` · ${flags.join(' · ')}` : ''}`,
    },
  ];

  if (column.size != null) {
    contents.push({
      value: `长度：${column.size}${column.scale != null ? `，精度：${column.scale}` : ''}`,
    });
  }
  if (column.remarks?.trim()) {
    contents.push({ value: escapeMarkdown(column.remarks.trim()) });
  }

  return { range, contents };
};

const tableHover = (
  range: monaco.Range,
  reference: SqlTableReference,
  table?: SqlCatalogTable,
): monaco.languages.Hover => {
  const alias = reference.alias ? ` · 别名 ${inlineCode(reference.alias)}` : '';
  const type = table?.type || 'TABLE';
  const contents: monaco.IMarkdownString[] = [
    {
      value: `**${escapeMarkdown(type)}** ${inlineCode(tablePath(reference))}${alias}`,
    },
  ];

  if (table?.remarks?.trim()) {
    contents.push({ value: escapeMarkdown(table.remarks.trim()) });
  }

  return { range, contents };
};

const findCatalogTable = async (
  reference: SqlTableReference,
  context: NonNullable<ReturnType<typeof getSqlMetadataContext>>,
) => {
  const tables = await loadSqlTables(context, {
    database: reference.database,
    schema: reference.schema,
  });
  return tables.find(
    (table) => table.name.toLowerCase() === reference.table.toLowerCase(),
  );
};

const provideHover = async (
  model: monaco.editor.ITextModel,
  position: monaco.Position,
  token: monaco.CancellationToken,
): Promise<monaco.languages.Hover | undefined> => {
  const word = model.getWordAtPosition(position);
  if (!word) return undefined;

  const textBeforePosition = getSqlTextBeforePosition(model, position);
  if (getSqlLexicalState(textBeforePosition) !== 'code') return undefined;

  const range = wordRange(position, word);
  const nodeId = getSqlEditorNodeId(model);
  const context = nodeId ? getSqlMetadataContext(nodeId) : undefined;
  const profile = resolveSqlEditorProfile(context?.dialect);
  const builtin = profile.functions.find(
    (definition) =>
      definition.name.toUpperCase() === word.word.toUpperCase(),
  );
  if (builtin && isFunctionCall(model, position, word)) {
    return {
      range,
      contents: [
        { value: `**SQL 函数** ${inlineCode(builtin.signature)}` },
        { value: escapeMarkdown(builtin.description) },
      ],
    };
  }

  if (!context?.dataSourceId) return undefined;

  const references = parseSqlTableReferences(
    getCurrentSqlStatementText(model, position),
    context,
  );
  if (!references.length) return undefined;

  try {
    const qualifier = getSqlQualifierBeforeWord(
      model,
      position,
      word.startColumn,
    );

    if (qualifier) {
      const reference = findSqlTableReference(qualifier, references);
      if (!reference) return undefined;

      const columns = await loadSqlColumns(context, reference.table, {
        database: reference.database,
        schema: reference.schema,
      });
      if (token.isCancellationRequested) return undefined;
      const column = columns.find(
        (item) => item.name.toLowerCase() === word.word.toLowerCase(),
      );
      return column ? columnHover(range, column, reference) : undefined;
    }

    const tableReference = references.find(
      (reference) =>
        reference.table.toLowerCase() === word.word.toLowerCase() ||
        reference.alias?.toLowerCase() === word.word.toLowerCase(),
    );
    if (tableReference) {
      const table = await findCatalogTable(tableReference, context);
      if (token.isCancellationRequested) return undefined;
      return tableHover(range, tableReference, table);
    }

    if (references.length === 1) {
      const reference = references[0];
      const columns = await loadSqlColumns(context, reference.table, {
        database: reference.database,
        schema: reference.schema,
      });
      if (token.isCancellationRequested) return undefined;
      const column = columns.find(
        (item) => item.name.toLowerCase() === word.word.toLowerCase(),
      );
      return column ? columnHover(range, column, reference) : undefined;
    }
  } catch {
    // Hover is best-effort and must never block editing when Catalog is unavailable.
  }

  return undefined;
};

const createProvider = () =>
  monaco.languages.registerHoverProvider('sql', {
    provideHover,
  });

export const acquireSqlHoverProvider = (): monaco.IDisposable => {
  providerConsumers += 1;
  if (!providerDisposable) providerDisposable = createProvider();

  let released = false;
  return {
    dispose: () => {
      if (released) return;
      released = true;
      providerConsumers = Math.max(0, providerConsumers - 1);
      if (providerConsumers > 0) return;

      providerDisposable?.dispose();
      providerDisposable = undefined;
    },
  };
};
