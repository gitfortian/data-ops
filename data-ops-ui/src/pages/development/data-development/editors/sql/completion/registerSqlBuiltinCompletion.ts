import * as monaco from 'monaco-editor/esm/vs/editor/editor.api';

import {
  getSqlEditorNodeId,
  getSqlLexicalState,
  getSqlTextBeforePosition,
} from '../assistance/sqlTextContext';
import { getSqlMetadataContext } from '../metadata/sqlMetadataContextStore';
import { resolveSqlEditorProfile } from '../profiles/sqlEditorProfiles';

let providerDisposable: monaco.IDisposable | undefined;
let providerConsumers = 0;

const formatCandidateName = (name: string, prefix: string) => {
  const mixedCase = name !== name.toUpperCase() && name !== name.toLowerCase();
  if (mixedCase) return name;
  if (prefix && prefix === prefix.toLowerCase()) return name.toLowerCase();
  return name;
};

const getCompletionRange = (
  model: monaco.editor.ITextModel,
  position: monaco.Position,
) => {
  const word = model.getWordUntilPosition(position);
  return {
    prefix: word.word,
    range: new monaco.Range(
      position.lineNumber,
      word.startColumn,
      position.lineNumber,
      word.endColumn,
    ),
  };
};

const shouldProvideCompletion = (
  model: monaco.editor.ITextModel,
  position: monaco.Position,
  wordStartColumn: number,
) => {
  const textBeforePosition = getSqlTextBeforePosition(model, position);
  if (getSqlLexicalState(textBeforePosition) !== 'code') return false;

  if (wordStartColumn > 1) {
    const characterBeforeWord = model.getValueInRange(
      new monaco.Range(
        position.lineNumber,
        wordStartColumn - 1,
        position.lineNumber,
        wordStartColumn,
      ),
    );
    if (characterBeforeWord === '.') return false;
  }

  return true;
};

const getProfileForModel = (model: monaco.editor.ITextModel) => {
  const nodeId = getSqlEditorNodeId(model);
  const context = nodeId ? getSqlMetadataContext(nodeId) : undefined;
  return resolveSqlEditorProfile(context?.dialect);
};

const createProvider = () =>
  monaco.languages.registerCompletionItemProvider('sql', {
    provideCompletionItems: (model, position) => {
      const { prefix, range } = getCompletionRange(model, position);
      if (!shouldProvideCompletion(model, position, range.startColumn)) {
        return { suggestions: [] };
      }

      const profile = getProfileForModel(model);
      const keywordSuggestions: monaco.languages.CompletionItem[] =
        profile.keywords.map((keyword) => ({
          label: keyword,
          kind: monaco.languages.CompletionItemKind.Keyword,
          insertText: formatCandidateName(keyword, prefix),
          filterText: keyword,
          detail: 'SQL 关键字',
          sortText: `1-${keyword}`,
          range,
        }));

      const functionSuggestions: monaco.languages.CompletionItem[] =
        profile.functions.map((definition) => {
          const name = formatCandidateName(definition.name, prefix);
          return {
            label: definition.name,
            kind: monaco.languages.CompletionItemKind.Function,
            insertText: `${name}(${definition.argumentsSnippet})`,
            insertTextRules:
              monaco.languages.CompletionItemInsertTextRule.InsertAsSnippet,
            filterText: definition.name,
            detail: definition.signature,
            documentation: definition.description,
            sortText: `2-${definition.name}`,
            range,
          };
        });

      return {
        suggestions: [...keywordSuggestions, ...functionSuggestions],
      };
    },
  });

export const acquireSqlBuiltinCompletionProvider = (): monaco.IDisposable => {
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
