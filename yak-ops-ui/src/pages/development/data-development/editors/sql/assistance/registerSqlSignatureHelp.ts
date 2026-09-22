import * as monaco from 'monaco-editor/esm/vs/editor/editor.api';

import { getSqlMetadataContext } from '../metadata/sqlMetadataContextStore';
import { resolveSqlEditorProfile } from '../profiles/sqlEditorProfiles';
import { getSqlEditorNodeId } from './sqlTextContext';

interface FunctionFrame {
  name?: string;
  commas: number;
}

type ScanState =
  | 'code'
  | 'single-quote'
  | 'double-quote'
  | 'backtick'
  | 'line-comment'
  | 'block-comment';

let providerDisposable: monaco.IDisposable | undefined;
let providerConsumers = 0;

const previousIdentifier = (text: string, offset: number) =>
  text.slice(0, offset).match(/([A-Za-z_][\w$]*)\s*$/)?.[1];

const findActiveFunction = (
  text: string,
  functionNames: ReadonlySet<string>,
): FunctionFrame | undefined => {
  const stack: FunctionFrame[] = [];
  let state: ScanState = 'code';

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

    if (current === '-' && next === '-') {
      state = 'line-comment';
      index += 1;
      continue;
    }
    if (current === '/' && next === '*') {
      state = 'block-comment';
      index += 1;
      continue;
    }
    if (current === "'") {
      state = 'single-quote';
      continue;
    }
    if (current === '"') {
      state = 'double-quote';
      continue;
    }
    if (current === '`') {
      state = 'backtick';
      continue;
    }

    if (current === '(') {
      const name = previousIdentifier(text, index);
      stack.push({ name, commas: 0 });
      continue;
    }
    if (current === ')') {
      stack.pop();
      continue;
    }
    if (current === ',' && stack.length) {
      stack[stack.length - 1].commas += 1;
    }
  }

  for (let index = stack.length - 1; index >= 0; index -= 1) {
    const frame = stack[index];
    if (frame.name && functionNames.has(frame.name.toUpperCase())) return frame;
  }
  return undefined;
};

const signatureParameters = (signature: string) => {
  const start = signature.indexOf('(');
  const end = signature.lastIndexOf(')');
  if (start < 0 || end <= start + 1) return [];

  const content = signature.slice(start + 1, end).trim();
  if (!content) return [];
  return content.split(/\s*,\s*/).map((label) => ({ label }));
};

const createProvider = () =>
  monaco.languages.registerSignatureHelpProvider('sql', {
    signatureHelpTriggerCharacters: ['(', ','],
    signatureHelpRetriggerCharacters: [','],
    provideSignatureHelp: (model, position) => {
      const text = model.getValueInRange(
        new monaco.Range(1, 1, position.lineNumber, position.column),
      );
      const nodeId = getSqlEditorNodeId(model);
      const context = nodeId ? getSqlMetadataContext(nodeId) : undefined;
      const profile = resolveSqlEditorProfile(context?.dialect);
      const functionMap = new Map(
        profile.functions.map((definition) => [
          definition.name.toUpperCase(),
          definition,
        ]),
      );
      const frame = findActiveFunction(text, new Set(functionMap.keys()));
      if (!frame?.name) return undefined;

      const definition = functionMap.get(frame.name.toUpperCase());
      if (!definition) return undefined;

      const parameters = signatureParameters(definition.signature);
      const activeParameter = parameters.length
        ? Math.min(frame.commas, parameters.length - 1)
        : 0;

      return {
        value: {
          signatures: [
            {
              label: definition.signature,
              documentation: definition.description,
              parameters,
            },
          ],
          activeSignature: 0,
          activeParameter,
        },
        dispose: () => undefined,
      };
    },
  });

export const acquireSqlSignatureHelpProvider = (): monaco.IDisposable => {
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
