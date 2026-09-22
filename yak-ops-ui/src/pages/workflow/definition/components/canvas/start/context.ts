import { WORKFLOW_EDITOR_META_KEY } from '../note/types';
import {
  WORKFLOW_START_META_KEY,
  type WorkflowStartConfig,
  type WorkflowStartInputField,
  type WorkflowStartValueType,
  type WorkflowStartVariable,
} from './types';

interface StartMeta {
  version?: 1 | 2;
  position?: { x?: number; y?: number };
  inputFields?: Array<Omit<WorkflowStartInputField, 'defaultValue'>>;
  variables?: Array<Omit<WorkflowStartVariable, 'value'>>;
  nextNodeIds?: string[];
}

const isRecord = (value: unknown): value is Record<string, unknown> =>
  Boolean(value) && !Array.isArray(value) && typeof value === 'object';

const inferType = (value: unknown): WorkflowStartValueType => {
  if (typeof value === 'number') return 'NUMBER';
  if (typeof value === 'boolean') return 'BOOLEAN';
  if (Array.isArray(value)) return 'ARRAY_STRING';
  return 'STRING';
};

const createId = (scope: string, name: string, index: number) =>
  `${scope}-${name || 'field'}-${index}`;

const normalizePosition = (value?: { x?: number; y?: number }) => ({
  x: Number.isFinite(value?.x) ? Number(value?.x) : 80,
  y: Number.isFinite(value?.y) ? Number(value?.y) : 160,
});

const readStartMeta = (
  editorMeta?: Record<string, unknown>,
  legacyInput?: Record<string, unknown>,
): StartMeta | undefined => {
  const current = editorMeta?.[WORKFLOW_START_META_KEY];
  if (isRecord(current)) return current as StartMeta;
  const legacy = legacyInput?.[WORKFLOW_START_META_KEY];
  return isRecord(legacy) ? legacy as StartMeta : undefined;
};

const readEditorMetaExtras = (
  editorMeta?: Record<string, unknown>,
): Record<string, unknown> => Object.fromEntries(
  Object.entries(editorMeta || {}).filter(
    ([key]) => key !== WORKFLOW_START_META_KEY && key !== WORKFLOW_EDITOR_META_KEY,
  ),
);

export const hydrateWorkflowStartConfig = (
  runtimeInput?: Record<string, unknown>,
  editorMeta?: Record<string, unknown>,
  legacyRootNodeIds: string[] = [],
): WorkflowStartConfig => {
  const input = runtimeInput || {};
  const meta = readStartMeta(editorMeta, input);
  const inputValues = isRecord(input.inputs) ? input.inputs : undefined;
  const variableValues = isRecord(input.vars) ? input.vars : {};

  let inputs: WorkflowStartInputField[];
  if (meta?.inputFields?.length) {
    inputs = meta.inputFields.map((field, index) => ({
      ...field,
      id: field.id || createId('input', field.name, index),
      label: field.label || field.name,
      defaultValue: inputValues?.[field.name],
    }));
  } else {
    const legacyValues = inputValues || Object.fromEntries(
      Object.entries(input).filter(([key]) =>
        key !== WORKFLOW_START_META_KEY
        && key !== WORKFLOW_EDITOR_META_KEY
        && key !== 'vars'
        && key !== 'sys'),
    );
    inputs = Object.entries(legacyValues).map(([name, value], index) => ({
      id: createId('input', name, index),
      name,
      label: name,
      type: inferType(value),
      required: false,
      defaultValue: value,
    }));
  }

  let variables: WorkflowStartVariable[];
  if (meta?.variables?.length) {
    variables = meta.variables.map((variable, index) => ({
      ...variable,
      id: variable.id || createId('var', variable.name, index),
      value: variableValues[variable.name],
    }));
  } else {
    variables = Object.entries(variableValues).map(([name, value], index) => ({
      id: createId('var', name, index),
      name,
      type: inferType(value) === 'FILE' ? 'STRING' : inferType(value) as WorkflowStartVariable['type'],
      value,
    }));
  }

  const hasExplicitNextNodes = Boolean(meta && Object.prototype.hasOwnProperty.call(meta, 'nextNodeIds'));
  const nextNodeIds = hasExplicitNextNodes && Array.isArray(meta?.nextNodeIds)
    ? [...new Set(meta.nextNodeIds.filter((nodeId): nodeId is string => typeof nodeId === 'string' && Boolean(nodeId)))]
    : [...new Set(legacyRootNodeIds.filter(Boolean))];

  return {
    position: normalizePosition(meta?.position),
    inputs,
    variables,
    nextNodeIds,
    editorMetaExtras: readEditorMetaExtras(editorMeta),
  };
};

const serializeValue = (type: WorkflowStartValueType, value: unknown) => {
  if (type === 'NUMBER') {
    if (value === '' || value === undefined || value === null) return 0;
    const parsed = Number(value);
    return Number.isFinite(parsed) ? parsed : 0;
  }
  if (type === 'BOOLEAN') return Boolean(value);
  if (type === 'ARRAY_STRING') {
    if (Array.isArray(value)) return value.map(String);
    return String(value || '')
      .split(',')
      .map((item) => item.trim())
      .filter(Boolean);
  }
  if (type === 'FILE') return value ?? null;
  return value === undefined || value === null ? '' : String(value);
};

/** 仅序列化运行时输入，不再夹带画布元数据。 */
export const serializeWorkflowStartContext = (
  config: WorkflowStartConfig,
  system: { definitionId: string; workflowName: string },
): Record<string, unknown> => ({
  sys: {
    definitionId: system.definitionId,
    workflowName: system.workflowName,
  },
  inputs: Object.fromEntries(
    config.inputs.map((field) => [field.name, serializeValue(field.type, field.defaultValue)]),
  ),
  vars: Object.fromEntries(
    config.variables.map((variable) => [variable.name, serializeValue(variable.type, variable.value)]),
  ),
});

/** Start 的位置、字段定义和显式连线仅属于编辑器元数据。 */
export const serializeWorkflowStartEditorMeta = (
  config: WorkflowStartConfig,
): Record<string, unknown> => ({
  ...(config.editorMetaExtras || {}),
  [WORKFLOW_START_META_KEY]: {
    version: 2,
    position: config.position,
    inputFields: config.inputs.map(({ defaultValue: _defaultValue, ...field }) => field),
    variables: config.variables.map(({ value: _value, ...variable }) => variable),
    nextNodeIds: [...new Set(config.nextNodeIds.filter(Boolean))],
  } satisfies StartMeta,
});
