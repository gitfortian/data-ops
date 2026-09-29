import YakTab from '@/components/YakTab';
import { getWorkflowInstances } from '@/services/workflow';
import { useIntl } from '@umijs/max';
import { Input, Modal, Select, Switch, message } from 'antd';
import dayjs from 'dayjs';
import { GitBranch, Plus, RefreshCw, Trash2, Variable, X } from 'lucide-react';
import { useCallback, useEffect, useMemo, useState } from 'react';
import WorkflowNextStep from '../WorkflowNextStep';
import type { WorkflowCanvasTaskOption } from '../types';
import useWorkflowInspectorBehavior from '../useWorkflowInspectorBehavior';
import type {
  WorkflowStartConfig,
  WorkflowStartInputField,
  WorkflowStartValueType,
  WorkflowStartVariable,
} from './types';

interface WorkflowStartNextNode {
  id: string;
  label: string;
  taskType: string;
}

interface WorkflowStartInspectorProps {
  definitionId: string;
  workflowName: string;
  config: WorkflowStartConfig;
  locked: boolean;
  nextNodes: WorkflowStartNextNode[];
  appendOptions: WorkflowCanvasTaskOption[];
  onChange: (config: WorkflowStartConfig) => void;
  onAppend: (taskId: string) => void;
  onClose: () => void;
}

type StartInspectorTab = 'settings' | 'lastRun';
type VariableScope = 'inputs' | 'variables';

interface EditorDraft {
  name: string;
  label: string;
  type: WorkflowStartValueType;
  required: boolean;
  description: string;
  defaultText: string;
}

interface StartTypeOption<T extends WorkflowStartValueType = WorkflowStartValueType> {
  value: T;
  label: string;
}

const NAME_PATTERN = /^[A-Za-z_][A-Za-z0-9_]*$/;

const isInputField = (
  value: WorkflowStartInputField | WorkflowStartVariable,
): value is WorkflowStartInputField => 'required' in value;

const INPUT_TYPE_OPTIONS: StartTypeOption[] = [
  { value: 'STRING', label: 'String' },
  { value: 'NUMBER', label: 'Number' },
  { value: 'BOOLEAN', label: 'Boolean' },
  { value: 'FILE', label: 'File' },
  { value: 'ARRAY_STRING', label: 'Array[String]' },
];

const VARIABLE_TYPE_OPTIONS: Array<StartTypeOption<WorkflowStartVariable['type']>> = [
  { value: 'STRING', label: 'String' },
  { value: 'NUMBER', label: 'Number' },
  { value: 'BOOLEAN', label: 'Boolean' },
  { value: 'ARRAY_STRING', label: 'Array[String]' },
];

const valueToDraftText = (value: unknown) => {
  if (value === undefined || value === null) return '';
  if (Array.isArray(value)) return value.map(String).join(', ');
  if (typeof value === 'string') return value;
  return JSON.stringify(value);
};

const createDraft = (
  value?: WorkflowStartInputField | WorkflowStartVariable,
): EditorDraft => {
  const inputField = value && isInputField(value) ? value : undefined;
  const variable = value && !isInputField(value) ? value : undefined;
  const currentValue = inputField ? inputField.defaultValue : variable?.value;

  return {
    name: value?.name || '',
    label: inputField?.label || value?.name || '',
    type: value?.type || 'STRING',
    required: Boolean(inputField?.required),
    description: inputField?.description || '',
    defaultText: valueToDraftText(currentValue),
  };
};

const defaultValueText = (value: unknown) => {
  if (value === undefined || value === null || value === '') return '--';
  if (Array.isArray(value)) return value.map(String).join(', ');
  if (typeof value === 'string') return value;
  return JSON.stringify(value);
};

const formatTime = (value?: string) =>
  value ? dayjs(value).format('YYYY-MM-DD HH:mm:ss') : '--';

const referenceScope = (scope: VariableScope) =>
  scope === 'variables' ? 'vars' : 'inputs';

const WorkflowStartInspector = ({
  definitionId,
  workflowName,
  config,
  locked,
  nextNodes,
  appendOptions,
  onChange,
  onAppend,
  onClose,
}: WorkflowStartInspectorProps) => {
  const intl = useIntl();
  const [activeTab, setActiveTab] = useState<StartInspectorTab>('settings');
  const [modalScope, setModalScope] = useState<VariableScope>();
  const [modalIndex, setModalIndex] = useState<number>();
  const [draft, setDraft] = useState<EditorDraft>(createDraft());
  const [lastRunLoading, setLastRunLoading] = useState(false);
  const [lastRunInput, setLastRunInput] = useState<Record<string, unknown>>();
  const [lastRunTime, setLastRunTime] = useState<string>();
  const { panelWidth, resizing, handleResizePointerDown } = useWorkflowInspectorBehavior({
    storageKey: 'yak-workflow-start-inspector-width',
    defaultWidth: 380,
    minWidth: 340,
    maxWidth: 560,
  });

  const usedNames = useMemo(
    () => new Set([...config.inputs, ...config.variables].map((item) => item.name)),
    [config.inputs, config.variables],
  );

  const systemVariables = useMemo(
    () => [
      {
        name: 'definitionId',
        value: definitionId || '--',
        type: 'STRING' as const,
      },
      {
        name: 'workflowName',
        value: workflowName || '--',
        type: 'STRING' as const,
      },
    ],
    [definitionId, workflowName],
  );

  const openEditor = (
    scope: VariableScope,
    index?: number,
    value?: WorkflowStartInputField | WorkflowStartVariable,
  ) => {
    setModalScope(scope);
    setModalIndex(index);
    setDraft(createDraft(value));
  };

  const closeEditor = () => {
    setModalScope(undefined);
    setModalIndex(undefined);
    setDraft(createDraft());
  };

  const parseDefaultValue = (
    type: WorkflowStartValueType,
    text: string,
  ): unknown => {
    const normalized = text.trim();
    if (!normalized) return undefined;
    if (type === 'STRING') return normalized;
    if (type === 'NUMBER') {
      const value = Number(normalized);
      if (!Number.isFinite(value)) throw new Error('Invalid number');
      return value;
    }
    if (type === 'BOOLEAN') {
      if (normalized === 'true') return true;
      if (normalized === 'false') return false;
      throw new Error('Boolean must be true or false');
    }
    if (type === 'FILE') return undefined;
    return normalized
      .split(',')
      .map((item) => item.trim())
      .filter(Boolean);
  };

  const saveDraft = () => {
    if (!modalScope) return;
    const name = draft.name.trim();
    if (!name) {
      message.error(intl.formatMessage({ id: 'pages.workflow.editor.startInspector.variableName' }));
      return;
    }
    if (!NAME_PATTERN.test(name)) {
      message.error(intl.formatMessage({ id: 'pages.workflow.editor.startInspector.namePattern' }));
      return;
    }
    const currentName =
      modalIndex !== undefined ? config[modalScope][modalIndex]?.name : undefined;
    if (usedNames.has(name) && currentName !== name) {
      message.error(
        intl.formatMessage(
          { id: 'pages.workflow.editor.startInspector.duplicate' },
          { name },
        ),
      );
      return;
    }

    let defaultValue: unknown;
    try {
      defaultValue = parseDefaultValue(draft.type, draft.defaultText);
    } catch (error) {
      message.error(error instanceof Error ? error.message : 'Invalid default value');
      return;
    }

    if (modalScope === 'inputs') {
      const current =
        modalIndex !== undefined ? config.inputs[modalIndex] : undefined;
      const next: WorkflowStartInputField = {
        id: current?.id || `input-${Date.now()}`,
        name,
        label: draft.label.trim() || name,
        type: draft.type,
        required: draft.required,
        description: draft.description.trim() || undefined,
        defaultValue,
      };
      const inputs = [...config.inputs];
      if (modalIndex === undefined) inputs.push(next);
      else inputs.splice(modalIndex, 1, next);
      onChange({ ...config, inputs });
    } else {
      const current =
        modalIndex !== undefined ? config.variables[modalIndex] : undefined;
      const variableType: WorkflowStartVariable['type'] =
        draft.type === 'FILE' ? 'STRING' : draft.type;
      const next: WorkflowStartVariable = {
        id: current?.id || `var-${Date.now()}`,
        name,
        type: variableType,
        value: defaultValue,
      };
      const variables = [...config.variables];
      if (modalIndex === undefined) variables.push(next);
      else variables.splice(modalIndex, 1, next);
      onChange({ ...config, variables });
    }
    closeEditor();
  };

  const removeItem = (scope: VariableScope, index: number) => {
    const next = [...config[scope]];
    next.splice(index, 1);
    onChange({ ...config, [scope]: next });
  };

  const loadLastRun = useCallback(async () => {
    if (!definitionId) return;
    setLastRunLoading(true);
    try {
      const instances = await getWorkflowInstances();
      const latest = instances
        .filter((item) => item.definitionId === definitionId)
        .sort((left, right) =>
          String(right.startedAt || '').localeCompare(String(left.startedAt || '')),
        )[0];
      setLastRunInput(latest?.input);
      setLastRunTime(latest?.startedAt);
    } finally {
      setLastRunLoading(false);
    }
  }, [definitionId]);

  useEffect(() => {
    if (activeTab === 'lastRun') void loadLastRun();
  }, [activeTab, loadLastRun]);

  const renderVariableRow = (
    scope: VariableScope,
    item: WorkflowStartInputField | WorkflowStartVariable,
    index: number,
  ) => {
    const inputField = isInputField(item) ? item : undefined;
    const currentValue = inputField
      ? inputField.defaultValue
      : (item as WorkflowStartVariable).value;
    const displayLabel = inputField?.label || item.name;

    return (
      <div
        key={`${scope}-${item.name}`}
        className="group flex min-h-[50px] items-center gap-2 border-b border-[#f0f1f3] px-1 py-2 last:border-b-0"
      >
        <div className="flex h-7 w-7 shrink-0 items-center justify-center rounded-lg bg-[#f5f6f7] text-[#667085]">
          <Variable size={13} />
        </div>
        <button
          type="button"
          disabled={locked}
          className="min-w-0 flex-1 border-0 bg-transparent p-0 text-left disabled:cursor-default"
          onClick={() => openEditor(scope, index, item)}
        >
          <div className="flex min-w-0 items-center gap-1.5">
            <span className="truncate text-[11px] font-medium text-[#344054]">
              {displayLabel}
            </span>
            {inputField?.required ? (
              <span className="shrink-0 rounded bg-[#fff1f3] px-1 py-0.5 text-[8px] font-medium text-[#d92d50]">
                {intl.formatMessage({ id: 'pages.workflow.editor.startInspector.required' })}
              </span>
            ) : null}
          </div>
          <div className="mt-0.5 truncate font-mono text-[9px] text-[#98a2b3]">
            {referenceScope(scope)}.{item.name} · {item.type}
            {currentValue !== undefined ? ` · ${defaultValueText(currentValue)}` : ''}
          </div>
        </button>
        {!locked ? (
          <button
            type="button"
            aria-label={intl.formatMessage({
              id:
                scope === 'inputs'
                  ? 'pages.workflow.editor.startInspector.deleteInput'
                  : 'pages.workflow.editor.startInspector.deleteVariable',
            })}
            className="flex h-7 w-7 shrink-0 items-center justify-center rounded-md border-0 bg-transparent text-[#b0b7c3] opacity-0 hover:bg-[#fff1f3] hover:text-[#d92d50] group-hover:opacity-100"
            onClick={() => removeItem(scope, index)}
          >
            <Trash2 size={13} />
          </button>
        ) : null}
      </div>
    );
  };

  const startLabel = intl.formatMessage({ id: 'pages.workflow.editor.start' });
  const typeOptions = modalScope === 'variables' ? VARIABLE_TYPE_OPTIONS : INPUT_TYPE_OPTIONS;

  return (
    <>
      <aside
        className="absolute bottom-0 right-0 top-0 z-20 flex flex-col overflow-hidden border-l border-[#e8eaee] bg-white"
        style={{ width: panelWidth }}
      >
        <div
          role="separator"
          aria-label={intl.formatMessage({ id: 'pages.workflow.editor.startInspector.resize' })}
          aria-orientation="vertical"
          className="group/resize absolute left-0 top-0 z-30 flex h-full w-2 cursor-col-resize touch-none items-center justify-start"
          onPointerDown={handleResizePointerDown}
        >
          <span
            className={[
              'h-full w-0.5 transition-colors duration-150',
              resizing ? 'bg-[#6172f3]' : 'bg-transparent group-hover/resize:bg-[#6172f3]',
            ].join(' ')}
          />
        </div>

        <header className="shrink-0 bg-white">
          <div className="flex h-12 items-center gap-2 border-b border-[#eef0f2] px-4">
            <div className="flex h-7 w-7 shrink-0 items-center justify-center rounded-lg border-[0.5px] border-white/20 bg-[#155eef] text-white shadow-[0_1px_2px_rgba(16,24,40,.08)]">
              <DifyStartIcon />
            </div>
            <div className="min-w-0 flex-1 truncate text-[13px] font-semibold text-[#161823]">
              {startLabel}
            </div>
            <button
              type="button"
              aria-label={intl.formatMessage({ id: 'pages.workflow.editor.common.close' })}
              className="flex h-7 w-7 items-center justify-center rounded-md border-0 bg-transparent text-[#667085] hover:bg-[#f2f4f7]"
              onClick={onClose}
            >
              <X size={15} />
            </button>
          </div>
          <div className="px-4">
            <YakTab
              activeKey={activeTab}
              items={[
                {
                  key: 'settings',
                  label: intl.formatMessage({ id: 'pages.workflow.editor.startInspector.settings' }),
                },
                {
                  key: 'lastRun',
                  label: intl.formatMessage({ id: 'pages.workflow.editor.startInspector.lastRun' }),
                },
              ]}
              onChange={(key) => setActiveTab(key as StartInspectorTab)}
            />
          </div>
        </header>

        <div className="min-h-0 flex-1 overflow-y-auto">
          {activeTab === 'settings' ? (
            <div className="pb-6">
              <StartSection
                title={intl.formatMessage({ id: 'pages.workflow.editor.startInspector.inputs' })}
                action={
                  !locked ? (
                    <button
                      type="button"
                      className="flex h-7 items-center gap-1 rounded-md border-0 bg-[#f5f6f7] px-2 text-[10px] font-medium text-[#475467] hover:bg-[#eceef2]"
                      onClick={() => openEditor('inputs')}
                    >
                      <Plus size={12} />
                      {intl.formatMessage({ id: 'pages.workflow.editor.startInspector.addInput' })}
                    </button>
                  ) : undefined
                }
              >
                {config.inputs.length ? (
                  config.inputs.map((item, index) => renderVariableRow('inputs', item, index))
                ) : (
                  <EmptySection text={intl.formatMessage({ id: 'pages.workflow.editor.startInspector.noInputs' })} />
                )}
              </StartSection>

              <StartSection
                title={intl.formatMessage({ id: 'pages.workflow.editor.startInspector.variables' })}
                action={
                  !locked ? (
                    <button
                      type="button"
                      className="flex h-7 items-center gap-1 rounded-md border-0 bg-[#f5f6f7] px-2 text-[10px] font-medium text-[#475467] hover:bg-[#eceef2]"
                      onClick={() => openEditor('variables')}
                    >
                      <Plus size={12} />
                      {intl.formatMessage({ id: 'pages.workflow.editor.startInspector.addVariable' })}
                    </button>
                  ) : undefined
                }
              >
                {config.variables.length ? (
                  config.variables.map((item, index) => renderVariableRow('variables', item, index))
                ) : (
                  <EmptySection text="--" />
                )}
              </StartSection>

              <StartSection
                title={intl.formatMessage({ id: 'pages.workflow.editor.startInspector.systemVariables' })}
              >
                {systemVariables.map((item) => (
                  <div key={item.name} className="flex min-h-[44px] items-center gap-2 border-b border-[#f0f1f3] px-1 py-2 last:border-b-0">
                    <GitBranch size={13} className="shrink-0 text-[#98a2b3]" />
                    <div className="min-w-0 flex-1">
                      <div className="truncate text-[11px] font-medium text-[#475467]">sys.{item.name}</div>
                      <div className="mt-0.5 truncate font-mono text-[9px] text-[#98a2b3]">{item.value} · {item.type}</div>
                    </div>
                    <span className="rounded bg-[#f5f6f7] px-1.5 py-0.5 text-[8px] text-[#98a2b3]">
                      {intl.formatMessage({ id: 'pages.workflow.editor.startInspector.readonly' })}
                    </span>
                  </div>
                ))}
              </StartSection>

              <StartSection title={intl.formatMessage({ id: 'pages.workflow.editor.startInspector.nextNodes' })}>
                <WorkflowNextStep
                  currentIcon={<StartDot />}
                  nextNodes={nextNodes}
                  appendOptions={appendOptions}
                  locked={locked}
                  onAppend={onAppend}
                />
              </StartSection>
            </div>
          ) : (
            <div className="px-4 py-4">
              <div className="mb-3 flex items-center justify-between">
                <div className="text-[12px] font-semibold text-[#344054]">
                  {intl.formatMessage({ id: 'pages.workflow.editor.startInspector.workflowInput' })}
                </div>
                <button
                  type="button"
                  aria-label={intl.formatMessage({ id: 'pages.workflow.editor.common.refresh' })}
                  className="flex h-7 w-7 items-center justify-center rounded-md border-0 bg-transparent text-[#98a2b3] hover:bg-[#f2f4f7] hover:text-[#475467]"
                  onClick={() => void loadLastRun()}
                >
                  <RefreshCw size={14} className={lastRunLoading ? 'animate-spin' : undefined} />
                </button>
              </div>
              {lastRunLoading ? (
                <div className="py-12 text-center text-[11px] text-[#98a2b3]">
                  {intl.formatMessage({ id: 'pages.workflow.editor.startInspector.lastRunLoading' })}
                </div>
              ) : lastRunInput ? (
                <>
                  <div className="mb-2 text-[9px] text-[#98a2b3]">{formatTime(lastRunTime)}</div>
                  <pre className="m-0 max-h-[420px] overflow-auto whitespace-pre-wrap break-words rounded-xl bg-[#f5f6f7] p-3 font-mono text-[11px] leading-5 text-[#344054]">
                    {JSON.stringify(lastRunInput, null, 2)}
                  </pre>
                </>
              ) : (
                <div className="py-12 text-center text-[11px] text-[#98a2b3]">
                  {intl.formatMessage({ id: 'pages.workflow.editor.startInspector.lastRunEmpty' })}
                </div>
              )}
            </div>
          )}
        </div>
      </aside>

      <Modal
        open={Boolean(modalScope)}
        title={intl.formatMessage({
          id:
            modalScope === 'inputs'
              ? modalIndex === undefined
                ? 'pages.workflow.editor.startInspector.addInputTitle'
                : 'pages.workflow.editor.startInspector.editInputTitle'
              : modalIndex === undefined
                ? 'pages.workflow.editor.startInspector.addVariableTitle'
                : 'pages.workflow.editor.startInspector.editVariableTitle',
        })}
        okText={intl.formatMessage({ id: 'pages.workflow.editor.common.confirm' })}
        cancelText={intl.formatMessage({ id: 'pages.workflow.editor.common.cancel' })}
        onCancel={closeEditor}
        onOk={saveDraft}
      >
        <div className="space-y-4 pt-2">
          <EditorField label={intl.formatMessage({ id: 'pages.workflow.editor.startInspector.variableName' })} required>
            <Input
              autoFocus
              value={draft.name}
              placeholder={intl.formatMessage({ id: 'pages.workflow.editor.startInspector.variableNamePlaceholder' })}
              onChange={(event) => setDraft((current) => ({ ...current, name: event.target.value }))}
            />
            {draft.name.trim() && modalScope ? (
              <div className="mt-1 text-[9px] text-[#98a2b3]">
                {intl.formatMessage(
                  { id: 'pages.workflow.editor.startInspector.reference' },
                  { scope: referenceScope(modalScope), name: draft.name.trim() },
                )}
              </div>
            ) : null}
          </EditorField>
          {modalScope === 'inputs' ? (
            <EditorField label={intl.formatMessage({ id: 'pages.workflow.editor.startInspector.displayName' })}>
              <Input
                value={draft.label}
                placeholder={intl.formatMessage({ id: 'pages.workflow.editor.startInspector.displayNamePlaceholder' })}
                onChange={(event) => setDraft((current) => ({ ...current, label: event.target.value }))}
              />
            </EditorField>
          ) : null}
          <EditorField label={intl.formatMessage({ id: 'pages.workflow.editor.startInspector.type' })} required>
            <Select
              className="w-full"
              value={draft.type}
              options={typeOptions}
              onChange={(value: WorkflowStartValueType) =>
                setDraft((current) => ({
                  ...current,
                  type: value,
                  defaultText: value === 'FILE' ? '' : current.defaultText,
                }))
              }
            />
          </EditorField>
          {modalScope === 'inputs' ? (
            <EditorField label={intl.formatMessage({ id: 'pages.workflow.editor.startInspector.required' })}>
              <Switch
                size="small"
                checked={draft.required}
                onChange={(checked) => setDraft((current) => ({ ...current, required: checked }))}
              />
            </EditorField>
          ) : null}
          {modalScope === 'inputs' ? (
            <EditorField label={intl.formatMessage({ id: 'pages.workflow.editor.startInspector.description' })}>
              <Input.TextArea
                rows={2}
                value={draft.description}
                onChange={(event) => setDraft((current) => ({ ...current, description: event.target.value }))}
              />
            </EditorField>
          ) : null}
          <EditorField label={intl.formatMessage({ id: 'pages.workflow.editor.startInspector.defaultValue' })}>
            {draft.type === 'FILE' ? (
              <div className="rounded-lg bg-[#f7f8fa] px-3 py-2 text-[10px] leading-5 text-[#667085]">
                {intl.formatMessage({ id: 'pages.workflow.editor.startInspector.fileDefaultHint' })}
              </div>
            ) : draft.type === 'BOOLEAN' ? (
              <Select
                allowClear
                className="w-full"
                value={draft.defaultText || undefined}
                options={[
                  { value: 'true', label: 'true' },
                  { value: 'false', label: 'false' },
                ]}
                onChange={(value) => setDraft((current) => ({ ...current, defaultText: value || '' }))}
              />
            ) : (
              <Input
                value={draft.defaultText}
                placeholder={
                  draft.type === 'ARRAY_STRING'
                    ? intl.formatMessage({ id: 'pages.workflow.editor.startInspector.arrayPlaceholder' })
                    : undefined
                }
                onChange={(event) => setDraft((current) => ({ ...current, defaultText: event.target.value }))}
              />
            )}
          </EditorField>
        </div>
      </Modal>
    </>
  );
};

const DifyStartIcon = () => (
  <svg
    viewBox="0 0 20 20"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    aria-hidden="true"
    className="h-4 w-4"
  >
    <circle
      cx="7.1"
      cy="5.45"
      r="2.35"
      stroke="currentColor"
      strokeWidth="1.75"
    />
    <path
      d="M2.85 14.35c.45-2.65 2.05-4.05 4.35-4.05 1.26 0 2.3.32 3.08.95"
      stroke="currentColor"
      strokeWidth="1.75"
      strokeLinecap="round"
    />
    <path
      d="m11.55 14.55 3.25-3.25a1.75 1.75 0 1 1 2.48 2.48l-3.25 3.25a1.75 1.75 0 0 1-2.48 0"
      stroke="currentColor"
      strokeWidth="1.75"
      strokeLinecap="round"
      strokeLinejoin="round"
    />
    <path
      d="m14.2 13.2-3.25 3.25a1.75 1.75 0 0 1-2.48-2.48l2.35-2.35"
      stroke="currentColor"
      strokeWidth="1.75"
      strokeLinecap="round"
      strokeLinejoin="round"
    />
  </svg>
);

const StartDot = () => (
  <span className="flex h-6 w-6 items-center justify-center rounded-lg bg-[#155eef] text-white shadow-[0_1px_2px_rgba(16,24,40,.08)]">
    <DifyStartIcon />
  </span>
);

const StartSection = ({
  title,
  action,
  children,
}: {
  title: string;
  action?: React.ReactNode;
  children: React.ReactNode;
}) => (
  <section className="border-t border-[#f0f1f3] px-4 py-4 first:border-t-0">
    <div className="mb-2 flex items-start justify-between gap-3">
      <div className="text-[12px] font-semibold text-[#344054]">{title}</div>
      {action}
    </div>
    {children}
  </section>
);

const EmptySection = ({ text }: { text: string }) => (
  <div className="whitespace-pre-line rounded-lg border border-dashed border-[#e4e7ec] px-3 py-4 text-center text-[10px] leading-5 text-[#98a2b3]">
    {text}
  </div>
);

const EditorField = ({
  label,
  required,
  children,
}: {
  label: string;
  required?: boolean;
  children: React.ReactNode;
}) => (
  <div>
    <div className="mb-1.5 text-[11px] font-medium text-[#475467]">
      {label}
      {required ? <span className="ml-1 text-[#d92d50]">*</span> : null}
    </div>
    {children}
  </div>
);

export default WorkflowStartInspector;