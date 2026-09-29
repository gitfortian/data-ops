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

const getProfileForModel = (model: monaco.editor.ITextModel) => {
  const nodeId = getSqlEditorNodeId(model);
  const context = nodeId ? getSqlMetadataContext(nodeId) : undefined;
  return resolveSqlEditorProfile(context?.dialect);
};

const createProvider = () =>
  monaco.languages.registerCompletionItemProvider('sql', {
    provideCompletionItems: (model, position, context) => {
      const textBeforePosition = getSqlTextBeforePosition(model, position);
      if (getSqlLexicalState(textBeforePosition) !== 'code') {
        return { suggestions: [] };
      }

      const word = model.getWordUntilPosition(position);
      const prefix = word.word.toLowerCase();
      const manualInvoke =
        context.triggerKind === monaco.languages.CompletionTriggerKind.Invoke;

      if (!manualInvoke && prefix.length < 2) {
        return { suggestions: [] };
      }

      if (word.startColumn > 1) {
        const beforeWord = model.getValueInRange(
          new monaco.Range(
            position.lineNumber,
            word.startColumn - 1,
            position.lineNumber,
            word.startColumn,
          ),
        );
        if (beforeWord === '.') return { suggestions: [] };
      }

      const range = new monaco.Range(
        position.lineNumber,
        word.startColumn,
        position.lineNumber,
        word.endColumn,
      );
      const profile = getProfileForModel(model);

      return {
        suggestions: profile.snippets.map((snippet, index) => ({
          label: snippet.label,
          kind: monaco.languages.CompletionItemKind.Snippet,
          insertText: snippet.body,
          insertTextRules:
            monaco.languages.CompletionItemInsertTextRule.InsertAsSnippet,
          filterText: `${snippet.prefix} ${snippet.label}`,
          detail: `${snippet.detail} · 输入 ${snippet.prefix}`,
          sortText: `0-${String(index).padStart(2, '0')}-${snippet.prefix}`,
          range,
        })),
      };
    },
  });

export const acquireSqlSnippetCompletionProvider = (): monaco.IDisposable => {
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
