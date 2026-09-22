import * as monaco from 'monaco-editor/esm/vs/editor/editor.api';

import type { DevelopmentId } from '../../../types';
import {
  findSqlTableReference,
  getCurrentSqlStatementText,
  getSqlLexicalState,
  getSqlTextBeforePosition,
  parseSqlTableReferences,
} from '../assistance/sqlTextContext';
import { loadSqlColumns, loadSqlTables } from '../metadata/sqlMetadataCache';
import { getSqlMetadataContext } from '../metadata/sqlMetadataContextStore';
import type {
  SqlCatalogColumn,
  SqlCatalogTable,
} from '../metadata/sqlMetadataService';
import {
  formatSqlIdentifierForCompletion,
  resolveSqlEditorProfile,
  type SqlEditorProfile,
} from '../profiles/sqlEditorProfiles';

let providerDisposable: monaco.IDisposable | undefined;
let providerConsumers = 0;
const modelNodeIds = new Map<string, DevelopmentId>();

const getCompletionRange = (
  model: monaco.editor.ITextModel,
  position: monaco.Position,
) => {
  const word = model.getWordUntilPosition(position);
  return new monaco.Range(
    position.lineNumber,
    word.startColumn,
    position.lineNumber,
    word.endColumn,
  );
};

const getQualifier = (
  model: monaco.editor.ITextModel,
  position: monaco.Position,
) => {
  const line = model
    .getLineContent(position.lineNumber)
    .slice(0, position.column - 1);
  const match = line.match(/([A-Za-z_$][\w$]*)\s*\.\s*[A-Za-z_$\d]*$/);
  return match?.[1];
};

const isTablePosition = (textBeforePosition: string) =>
  /\b(?:FROM|JOIN|UPDATE|INTO)\s+(?:[A-Za-z_$][\w$]*\s*\.\s*){0,2}[A-Za-z_$\d]*$/i.test(
    textBeforePosition,
  );

const columnSuggestion = (
  column: SqlCatalogColumn,
  range: monaco.Range,
  owner: string,
  profile: SqlEditorProfile,
): monaco.languages.CompletionItem => ({
  label: column.name,
  kind: monaco.languages.CompletionItemKind.Field,
  insertText: formatSqlIdentifierForCompletion(column.name, profile),
  detail: [column.typeName, column.primaryKey ? 'PK' : undefined, owner]
    .filter(Boolean)
    .join(' · '),
  documentation: column.remarks || undefined,
  sortText: `1-${String(column.ordinalPosition ?? 9999).padStart(5, '0')}-${column.name}`,
  range,
});

const tableSuggestion = (
  table: SqlCatalogTable,
  range: monaco.Range,
  profile: SqlEditorProfile,
): monaco.languages.CompletionItem => ({
  label: table.name,
  kind:
    table.type?.toUpperCase() === 'VIEW'
      ? monaco.languages.CompletionItemKind.Interface
      : monaco.languages.CompletionItemKind.Struct,
  insertText: formatSqlIdentifierForCompletion(table.name, profile),
  detail: [table.type || 'TABLE', table.database, table.schema]
    .filter(Boolean)
    .join(' · '),
  documentation: table.remarks || undefined,
  sortText: `1-${table.name}`,
  range,
});

const provideMetadataSuggestions = async (
  model: monaco.editor.ITextModel,
  position: monaco.Position,
  token: monaco.CancellationToken,
): Promise<monaco.languages.CompletionList> => {
  const nodeId = modelNodeIds.get(model.uri.toString());
  const context = nodeId ? getSqlMetadataContext(nodeId) : undefined;
  if (!context?.dataSourceId) return { suggestions: [] };

  const textBeforePosition = getSqlTextBeforePosition(model, position);
  if (getSqlLexicalState(textBeforePosition) !== 'code') {
    return { suggestions: [] };
  }

  const profile = resolveSqlEditorProfile(context.dialect);
  const range = getCompletionRange(model, position);
  const qualifier = getQualifier(model, position);
  const references = parseSqlTableReferences(
    getCurrentSqlStatementText(model, position),
    context,
  );

  try {
    if (qualifier) {
      const reference = findSqlTableReference(qualifier, references);
      if (reference) {
        const columns = await loadSqlColumns(context, reference.table, {
          database: reference.database,
          schema: reference.schema,
        });
        if (token.isCancellationRequested) return { suggestions: [] };
        return {
          suggestions: columns.map((column) =>
            columnSuggestion(
              column,
              range,
              reference.alias || reference.table,
              profile,
            ),
          ),
        };
      }

      if (
        qualifier.toLowerCase() === context.schema?.toLowerCase() ||
        qualifier.toLowerCase() === context.database?.toLowerCase()
      ) {
        const tables = await loadSqlTables(context);
        if (token.isCancellationRequested) return { suggestions: [] };
        return {
          suggestions: tables.map((table) =>
            tableSuggestion(table, range, profile),
          ),
        };
      }

      return { suggestions: [] };
    }

    if (isTablePosition(textBeforePosition)) {
      const tables = await loadSqlTables(context);
      if (token.isCancellationRequested) return { suggestions: [] };
      return {
        suggestions: tables.map((table) => tableSuggestion(table, range, profile)),
      };
    }

    if (references.length === 1) {
      const reference = references[0];
      const columns = await loadSqlColumns(context, reference.table, {
        database: reference.database,
        schema: reference.schema,
      });
      if (token.isCancellationRequested) return { suggestions: [] };
      return {
        suggestions: columns.map((column) =>
          columnSuggestion(column, range, reference.table, profile),
        ),
      };
    }
  } catch {
    // Metadata completion is best-effort. Keep SQL editing/builtin completion available.
  }

  return { suggestions: [] };
};

const createProvider = () =>
  monaco.languages.registerCompletionItemProvider('sql', {
    triggerCharacters: ['.'],
    provideCompletionItems: provideMetadataSuggestions,
  });

export const bindSqlMetadataModel = (
  modelUri: string,
  nodeId: DevelopmentId,
): monaco.IDisposable => {
  modelNodeIds.set(modelUri, nodeId);
  return {
    dispose: () => {
      if (modelNodeIds.get(modelUri) === nodeId) modelNodeIds.delete(modelUri);
    },
  };
};

export const acquireSqlMetadataCompletionProvider = (): monaco.IDisposable => {
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
