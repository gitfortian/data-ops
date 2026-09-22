import { BRAND_CSS_VARIABLES } from '@/styles/brand';
import { history } from '@umijs/max';
import {
  Button,
  Input,
  InputNumber,
  Select,
  Spin,
  Switch,
  Table,
  Tag,
  Tooltip,
  message,
  type TableColumnsType,
} from 'antd';
import {
  ExternalLink,
  FileCode2,
  LoaderCircle,
  Play,
  Redo2,
  RefreshCw,
  Rocket,
  Save,
  Search,
  Sparkles,
  Undo2,
  Wand2,
  X,
} from 'lucide-react';
import type { PointerEvent as ReactPointerEvent, ReactNode } from 'react';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';

import {
  getDevelopmentDataServiceNode,
  previewDevelopmentDataServiceNode,
  publishDevelopmentDataServiceNode,
  saveDevelopmentDataServiceDraft,
  type DataServiceContractType,
  type DevelopmentDataServiceDefinition,
  type DevelopmentDataServiceNodeContext,
  type DevelopmentDataServiceParameter,
  type DevelopmentDataServiceResponseField,
} from '../../data-service-node-service';
import {
  bringDataServiceOnline,
  fetchDataServicePublicationState,
  type DataServicePublicationState,
} from '../../data-service-runtime-publication';
import {
  executeSqlEditorCommand,
  type SqlEditorCommand,
} from '../../editors/sql/commands/sqlEditorCommandBus';
import SqlMonacoEditor, {
  type SqlEditorPosition,
} from '../../editors/sql/components/SqlMonacoEditor';
import SqlMetadataContextToolbar from '../../editors/sql/metadata/SqlMetadataContextToolbar';
import {
  hydrateSqlTaskConfig,
  useSqlMetadataContext,
} from '../../editors/sql/metadata/sqlMetadataContextStore';
import type {
  DevelopmentId,
  DevelopmentResourceNode,
  DevelopmentTaskRunResult,
} from '../../types';
import SqlResultWorkspace from '../sql-result/SqlResultWorkspace';

interface DataServiceNodeEditorProps {
  node: DevelopmentResourceNode;
  onSaved?: () => void | Promise<void>;
  /** Legacy workbench callback retained for API compatibility. */
  onOpenSourceNode?: (nodeId: DevelopmentId) => void;
  onDirtyChange?: (dirty: boolean) => void;
}

type RightPanelKey = 'properties' | 'request' | 'response' | 'versions' | 'online';

const requestTypeOptions: { label: string; value: DataServiceContractType }[] = [
  { label: 'STRING', value: 'STRING' },
  { label: 'INTEGER', value: 'INTEGER' },
  { label: 'NUMBER', value: 'NUMBER' },
  { label: 'BOOLEAN', value: 'BOOLEAN' },
  { label: 'DATE', value: 'DATE' },
  { label: 'DATETIME', value: 'DATETIME' },
];

const responseTypeOptions: { label: string; value: DataServiceContractType }[] = [
  ...requestTypeOptions,
  { label: 'OBJECT', value: 'OBJECT' },
];

const panelItems: Array<{ key: RightPanelKey; label: string }> = [
  { key: 'properties', label: '属性' },
  { key: 'request', label: '请求参数' },
  { key: 'response', label: '返回参数' },
  { key: 'versions', label: '版本' },
  { key: 'online', label: '上线' },
];

const DEFAULT_PANEL_WIDTH = 380;
const MIN_PANEL_WIDTH = 280;
const MAX_PANEL_WIDTH = 640;
const PANEL_WIDTH_STORAGE_KEY = 'yak-data-development.data-service-right-panel-width';
const DEFAULT_RESULT_HEIGHT = 280;
const MIN_RESULT_HEIGHT = 160;
const MAX_RESULT_HEIGHT = 520;
const RESULT_HEIGHT_STORAGE_KEY = 'yak-data-development.bottom-panel-height';

const defaultPosition: SqlEditorPosition = {
  lineNumber: 1,
  column: 1,
  selectionLength: 0,
};

const iconButtonClassName =
  'flex h-7 w-7 shrink-0 items-center justify-center rounded-[3px] text-[#475467] outline-none transition-colors hover:bg-[#f5f5f6] hover:text-[#1f2937] focus-visible:ring-2 focus-visible:ring-[rgba(254,44,85,.16)] disabled:cursor-not-allowed disabled:opacity-45 disabled:hover:bg-transparent';

interface ToolbarButtonProps {
  title: string;
  onClick: () => void;
  disabled?: boolean;
  children: ReactNode;
}

const ToolbarButton = ({ title, onClick, disabled, children }: ToolbarButtonProps) => (
  <Tooltip title={title} mouseEnterDelay={0.35}>
    <button
      type="button"
      aria-label={title}
      disabled={disabled}
      onClick={onClick}
      className={iconButtonClassName}
    >
      {children}
    </button>
  </Tooltip>
);

const ToolbarDivider = () => <span className="mx-1 h-4 w-px shrink-0 bg-[#e5e7eb]" />;

const clampPanelWidth = (value: number) =>
  Math.min(MAX_PANEL_WIDTH, Math.max(MIN_PANEL_WIDTH, value));

const initialPanelWidth = () => {
  if (typeof window === 'undefined') return DEFAULT_PANEL_WIDTH;
  const stored = Number(window.localStorage.getItem(PANEL_WIDTH_STORAGE_KEY));
  return Number.isFinite(stored) && stored > 0
    ? clampPanelWidth(stored)
    : DEFAULT_PANEL_WIDTH;
};

const clampResultHeight = (value: number) =>
  Math.min(MAX_RESULT_HEIGHT, Math.max(MIN_RESULT_HEIGHT, value));

const initialResultHeight = () => {
  if (typeof window === 'undefined') return DEFAULT_RESULT_HEIGHT;
  const stored = Number(window.localStorage.getItem(RESULT_HEIGHT_STORAGE_KEY));
  return Number.isFinite(stored) && stored > 0
    ? clampResultHeight(stored)
    : DEFAULT_RESULT_HEIGHT;
};

const formatTime = (value?: string | null) => {
  if (!value) return '-';
  const date = new Date(value);
  return Number.isNaN(date.getTime())
    ? value.replace('T', ' ').slice(0, 19)
    : date.toLocaleString('zh-CN', { hour12: false });
};

const normalizeId = (value: unknown): DevelopmentId => {
  if (value === undefined || value === null || String(value).trim() === '') return '0';
  return String(value);
};

const safeArray = <T,>(value: T[] | null | undefined): T[] =>
  Array.isArray(value) ? value.filter(Boolean) : [];

const parseNamedParameters = (sql: string) => {
  const names: string[] = [];
  const seen = new Set<string>();
  const matcher = /(^|[^:]):([A-Za-z_][A-Za-z0-9_]*)/g;
  let match: RegExpExecArray | null;
  while ((match = matcher.exec(sql)) !== null) {
    const name = match[2];
    const key = name.toLowerCase();
    if (seen.has(key)) continue;
    seen.add(key);
    names.push(name);
  }
  return names;
};

const mergeParameters = (
  names: string[],
  current: DevelopmentDataServiceParameter[],
) => {
  const previous = new Map(
    current.filter((item) => item?.name).map((item) => [item.name.toLowerCase(), item]),
  );
  return names.map((name) => {
    const item = previous.get(name.toLowerCase());
    return item
      ? {
          ...item,
          name,
          type: item.type === 'OBJECT' ? 'STRING' as const : item.type,
          required: true,
        }
      : {
          name,
          type: 'STRING' as const,
          required: true,
        };
  });
};

const normalizeContext = (
  raw: DevelopmentDataServiceNodeContext,
  node: DevelopmentResourceNode,
): DevelopmentDataServiceNodeContext => {
  const rawDraft = raw?.draft;
  const rawDefinition = rawDraft?.definition;
  const nodeId = normalizeId(raw?.nodeId || node.id);
  const definition: DevelopmentDataServiceDefinition = {
    sourceTaskAssetId: rawDefinition?.sourceTaskAssetId,
    sourceTaskRevisionId: rawDefinition?.sourceTaskRevisionId,
    sourceTaskRevisionNo: Number(rawDefinition?.sourceTaskRevisionNo || 0),
    dataSourceId: normalizeId(rawDefinition?.dataSourceId),
    sql: rawDefinition?.sql || '',
    serviceName: rawDefinition?.serviceName || raw?.nodeName || node.name,
    path: rawDefinition?.path || `/query/${nodeId}`,
    method: 'GET',
    parameters: safeArray(rawDefinition?.parameters),
    responseFields: safeArray(rawDefinition?.responseFields),
    maxRows: Number(rawDefinition?.maxRows || 1000),
    timeoutSeconds: Number(rawDefinition?.timeoutSeconds || 30),
    description: rawDefinition?.description || undefined,
    paginationEnabled: Boolean(rawDefinition?.paginationEnabled),
    autoParseParameters: rawDefinition?.autoParseParameters !== false,
  };

  return {
    nodeId,
    nodeName: raw?.nodeName || node.name,
    configured: Boolean(raw?.configured),
    draft: {
      nodeId: normalizeId(rawDraft?.nodeId || nodeId),
      definition,
      draftRevision: Number(rawDraft?.draftRevision || 0),
      createTime: rawDraft?.createTime,
      updateTime: rawDraft?.updateTime,
    },
    latestPublishedRevision: raw?.latestPublishedRevision || null,
    revisions: safeArray(raw?.revisions),
  };
};

export default function DataServiceNodeEditor({
  node,
  onSaved,
  onDirtyChange,
}: DataServiceNodeEditorProps) {
  const metadataContext = useSqlMetadataContext(node.id);
  const dirtyChangeRef = useRef(onDirtyChange);
  const savedDataSourceIdRef = useRef<DevelopmentId>();

  useEffect(() => {
    dirtyChangeRef.current = onDirtyChange;
  }, [onDirtyChange]);

  const [context, setContext] = useState<DevelopmentDataServiceNodeContext>();
  const [sqlText, setSqlText] = useState('');
  const [serviceName, setServiceName] = useState(node.name);
  const [path, setPath] = useState(`/query/${node.id}`);
  const [maxRows, setMaxRows] = useState(1000);
  const [timeoutSeconds, setTimeoutSeconds] = useState(30);
  const [description, setDescription] = useState('');
  const [parameters, setParameters] = useState<DevelopmentDataServiceParameter[]>([]);
  const [responseFields, setResponseFields] = useState<DevelopmentDataServiceResponseField[]>([]);
  const [paginationEnabled, setPaginationEnabled] = useState(false);
  const [autoParseParameters, setAutoParseParameters] = useState(true);
  const [queryResult, setQueryResult] = useState<DevelopmentTaskRunResult>();
  const [dirty, setDirty] = useState(false);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string>();
  const [running, setRunning] = useState(false);
  const [saving, setSaving] = useState(false);
  const [publishing, setPublishing] = useState(false);
  const [publicationState, setPublicationState] = useState<DataServicePublicationState>();
  const [publicationError, setPublicationError] = useState<string>();
  const [publicationLoading, setPublicationLoading] = useState(false);
  const [goingOnline, setGoingOnline] = useState(false);
  const [activePanel, setActivePanel] = useState<RightPanelKey>();
  const [panelWidth, setPanelWidth] = useState(initialPanelWidth);
  const [resizing, setResizing] = useState(false);
  const [resultHeight, setResultHeight] = useState(initialResultHeight);
  const [resultResizing, setResultResizing] = useState(false);
  const [position, setPosition] = useState<SqlEditorPosition>(defaultPosition);

  const setDirtyState = useCallback((next: boolean) => {
    setDirty(next);
    dirtyChangeRef.current?.(next);
  }, []);

  const markDirty = useCallback(() => setDirtyState(true), [setDirtyState]);

  useEffect(() => () => dirtyChangeRef.current?.(false), []);

  const applyContext = useCallback((raw: DevelopmentDataServiceNodeContext) => {
    const next = normalizeContext(raw, node);
    const definition = next.draft.definition;
    const nextDataSourceId = definition.dataSourceId !== '0'
      ? definition.dataSourceId
      : undefined;

    savedDataSourceIdRef.current = nextDataSourceId;
    setContext(next);
    setSqlText(definition.sql || '');
    setServiceName(definition.serviceName || next.nodeName);
    setPath(definition.path || `/query/${next.nodeId}`);
    setMaxRows(definition.maxRows || 1000);
    setTimeoutSeconds(definition.timeoutSeconds || 30);
    setDescription(definition.description || '');
    setParameters(safeArray(definition.parameters));
    setResponseFields(safeArray(definition.responseFields));
    setPaginationEnabled(Boolean(definition.paginationEnabled));
    setAutoParseParameters(definition.autoParseParameters !== false);
    setQueryResult(undefined);
    hydrateSqlTaskConfig(
      node.id,
      JSON.stringify(nextDataSourceId ? { dataSourceId: nextDataSourceId } : {}),
    );
    setDirtyState(false);
    return next;
  }, [node, setDirtyState]);

  const loadPublicationState = useCallback(async (
    hasPublishedRevision: boolean,
    notifyOnError = false,
  ) => {
    if (!hasPublishedRevision) {
      setPublicationState(undefined);
      setPublicationError(undefined);
      return;
    }

    setPublicationLoading(true);
    setPublicationError(undefined);
    try {
      setPublicationState(await fetchDataServicePublicationState(node.id));
    } catch (error) {
      const text = error instanceof Error ? error.message : '查询服务状态失败';
      setPublicationState(undefined);
      setPublicationError(text);
      if (notifyOnError) message.error(text);
    } finally {
      setPublicationLoading(false);
    }
  }, [node.id]);

  const load = useCallback(async () => {
    setLoading(true);
    setLoadError(undefined);
    setPublicationState(undefined);
    setPublicationError(undefined);
    try {
      const next = applyContext(await getDevelopmentDataServiceNode(node.id));
      await loadPublicationState(Boolean(next.latestPublishedRevision));
    } catch (error) {
      const text = error instanceof Error ? error.message : '加载 Data Service Node 失败';
      setLoadError(text);
      setContext(undefined);
      message.error(text);
    } finally {
      setLoading(false);
    }
  }, [applyContext, loadPublicationState, node.id]);

  useEffect(() => {
    void load();
  }, [load]);

  useEffect(() => {
    if (!context || loading) return;
    if (metadataContext.dataSourceId === savedDataSourceIdRef.current) return;
    setResponseFields([]);
    setQueryResult(undefined);
    markDirty();
  }, [context, loading, markDirty, metadataContext.dataSourceId]);

  const metadataPath = useMemo(
    () => [
      metadataContext.dataSourceName || (
        metadataContext.dataSourceId ? `DS ${metadataContext.dataSourceId}` : undefined
      ),
      metadataContext.database,
      metadataContext.schema,
    ].filter(Boolean).join(' / '),
    [
      metadataContext.dataSourceId,
      metadataContext.dataSourceName,
      metadataContext.database,
      metadataContext.schema,
    ],
  );

  const execute = (command: SqlEditorCommand, fallback: string) => {
    if (!executeSqlEditorCommand(node.id, command)) message.info(fallback);
  };

  const changeSql = (value: string) => {
    setSqlText(value);
    setQueryResult(undefined);
    if (autoParseParameters) {
      setParameters((current) => mergeParameters(parseNamedParameters(value), current));
      setResponseFields([]);
    }
    markDirty();
  };

  const runQuery = async (forceContractSync = false) => {
    const dataSourceId = metadataContext.dataSourceId;
    if (!dataSourceId || !sqlText.trim() || running) return;

    setRunning(true);
    setQueryResult({
      status: 'RUNNING',
      message: '正在执行 Data Service 查询',
      durationMs: 0,
      output: {},
    });
    try {
      const parameterValues = Object.fromEntries(
        parameters
          .filter((item) => item?.name)
          .map((item) => [item.name, item.example?.trim() || null]),
      );
      const result = await previewDevelopmentDataServiceNode(
        node.id,
        dataSourceId,
        sqlText,
        maxRows,
        timeoutSeconds,
        parameterValues,
      );
      const nextParameters = safeArray(result?.parameters);
      const nextResponses = safeArray(result?.responseFields);

      if (autoParseParameters || forceContractSync) {
        const oldParameters = new Map(
          parameters.filter((item) => item?.name).map((item) => [item.name.toLowerCase(), item]),
        );
        const oldResponses = new Map(
          responseFields.filter((item) => item?.name).map((item) => [item.name.toLowerCase(), item]),
        );

        setParameters(nextParameters.map((item) => {
          const previous = oldParameters.get(item.name.toLowerCase());
          return previous
            ? {
                ...item,
                type: previous.type === 'OBJECT' ? 'STRING' : previous.type,
                required: true,
                description: previous.description,
                example: previous.example,
              }
            : { ...item, required: true };
        }));
        setResponseFields(nextResponses.map((item) => {
          const previous = oldResponses.get(item.name.toLowerCase());
          return previous
            ? {
                ...item,
                type: previous.type,
                description: previous.description,
                example: previous.example,
              }
            : item;
        }));
        markDirty();
      }

      setQueryResult({
        status: 'SUCCESS',
        message: '查询成功',
        durationMs: Number(result?.durationMs || 0),
        output: {
          kind: 'RESULT_SET',
          columns: safeArray(result?.result?.columns),
          rows: safeArray(result?.result?.rows),
          returnedRows: Number(result?.result?.returnedRows || 0),
          truncated: Boolean(result?.result?.truncated),
          dataSourceId: result?.dataSourceId,
        },
      });
      message.success(
        `查询完成 · ${Number(result?.result?.returnedRows || 0)} 行 / ${nextResponses.length} 个字段`,
      );
    } catch (error) {
      const text = error instanceof Error ? error.message : '运行 Data Service 查询失败';
      setQueryResult({
        status: 'FAILED',
        message: text,
        durationMs: 0,
        output: {},
      });
      message.error(text);
    } finally {
      setRunning(false);
    }
  };

  const save = async () => {
    const dataSourceId = metadataContext.dataSourceId;
    if (!dataSourceId || !sqlText.trim() || saving) return;

    setSaving(true);
    try {
      const next = await saveDevelopmentDataServiceDraft(node.id, {
        dataSourceId,
        sql: sqlText,
        serviceName: serviceName.trim(),
        path: path.trim(),
        method: 'GET',
        parameters: parameters.map((item) => ({
          ...item,
          required: true,
          description: item.description?.trim() || undefined,
          example: item.example?.trim() || undefined,
        })),
        responseFields: responseFields.map((item) => ({
          ...item,
          description: item.description?.trim() || undefined,
          example: item.example?.trim() || undefined,
        })),
        maxRows,
        timeoutSeconds,
        description: description.trim() || undefined,
        paginationEnabled,
        autoParseParameters,
        baseRevision: context?.draft?.draftRevision || 0,
      });
      applyContext(next);
      await onSaved?.();
      message.success(`草稿已保存 · Draft #${next?.draft?.draftRevision || '-'}`);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '保存 Data Service Node 草稿失败');
    } finally {
      setSaving(false);
    }
  };

  const publish = async () => {
    const draftRevision = context?.draft?.draftRevision || 0;
    if (!draftRevision || publishing) return;
    if (dirty) {
      message.warning('当前有未保存修改，请先保存草稿再发布版本');
      return;
    }

    setPublishing(true);
    try {
      const revision = await publishDevelopmentDataServiceNode(node.id, draftRevision);
      await load();
      await onSaved?.();
      setActivePanel('online');
      message.success(`已发布 DS R${revision.revisionNo} · 可继续上线 API`);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '发布 Data Service Node 失败');
    } finally {
      setPublishing(false);
    }
  };

  const latestPublished = context?.latestPublishedRevision;
  const onlineRevisionNo = publicationState?.detail?.sourceRevisionNo;
  const serviceStatus = !latestPublished
    ? '等待发布版本'
    : publicationLoading
      ? '正在查询服务状态'
      : publicationError
        ? '服务状态不可用'
        : !publicationState?.published
          ? '尚未上线'
          : publicationState.updateAvailable
            ? `线上 DS R${onlineRevisionNo || '-'} · 有新版本待上线`
            : publicationState.detail?.enabled
              ? `DS R${onlineRevisionNo || '-'} · 运行中`
              : `DS R${onlineRevisionNo || '-'} · 已停用`;

  const onlineActionLabel = !publicationState?.published
    ? '上线 API'
    : publicationState.updateAvailable
      ? '更新上线'
      : publicationState.detail?.enabled
        ? '已上线'
        : '重新上线';

  const onlineActionDisabled = !latestPublished
    || publicationLoading
    || Boolean(publicationError)
    || Boolean(
      publicationState?.published
      && !publicationState.updateAvailable
      && publicationState.detail?.enabled,
    );

  const goOnline = async () => {
    if (!latestPublished || goingOnline || publicationError) return;

    const updating = Boolean(publicationState?.published && publicationState.updateAvailable);
    setGoingOnline(true);
    try {
      await bringDataServiceOnline(node.id, publicationState);
      await loadPublicationState(true, true);
      message.success(
        updating
          ? `DS R${latestPublished.revisionNo} 已更新上线`
          : `API 已上线 · DS R${latestPublished.revisionNo}`,
      );
    } catch (error) {
      message.error(error instanceof Error ? error.message : 'Data Service API 上线失败');
    } finally {
      setGoingOnline(false);
    }
  };

  const updateParameter = (
    index: number,
    patch: Partial<DevelopmentDataServiceParameter>,
  ) => {
    setParameters((current) => current.map((item, itemIndex) =>
      itemIndex === index ? { ...item, ...patch, required: true } : item,
    ));
    markDirty();
  };

  const updateResponseField = (
    index: number,
    patch: Partial<DevelopmentDataServiceResponseField>,
  ) => {
    setResponseFields((current) => current.map((item, itemIndex) =>
      itemIndex === index ? { ...item, ...patch } : item,
    ));
    markDirty();
  };

  const parameterColumns = useMemo<TableColumnsType<DevelopmentDataServiceParameter>>(() => [
    {
      title: '参数名称',
      dataIndex: 'name',
      width: 130,
      render: (value: string) => <span className="font-mono text-[11px] text-[#344054]">{value}</span>,
    },
    {
      title: '类型',
      dataIndex: 'type',
      width: 112,
      render: (value: DataServiceContractType, _record, index) => (
        <Select
          size="small"
          value={value === 'OBJECT' ? 'STRING' : value}
          options={requestTypeOptions}
          className="w-[100px]"
          onChange={(next) => updateParameter(index, { type: next })}
        />
      ),
    },
    {
      title: '必填',
      dataIndex: 'required',
      width: 58,
      render: () => <Tag bordered={false}>是</Tag>,
    },
    {
      title: '描述',
      dataIndex: 'description',
      width: 170,
      render: (value: string | null | undefined, _record, index) => (
        <Input
          size="small"
          value={value || ''}
          placeholder="参数说明"
          onChange={(event) => updateParameter(index, { description: event.target.value })}
        />
      ),
    },
    {
      title: '示例',
      dataIndex: 'example',
      width: 140,
      render: (value: string | null | undefined, _record, index) => (
        <Input
          size="small"
          value={value || ''}
          placeholder="运行查询时作为参数值"
          onChange={(event) => updateParameter(index, { example: event.target.value })}
        />
      ),
    },
  ], []);

  const responseColumns = useMemo<TableColumnsType<DevelopmentDataServiceResponseField>>(() => [
    {
      title: '字段',
      dataIndex: 'name',
      width: 130,
      render: (value: string) => <span className="font-mono text-[11px] text-[#344054]">{value}</span>,
    },
    {
      title: '类型',
      dataIndex: 'type',
      width: 112,
      render: (value: DataServiceContractType, _record, index) => (
        <Select
          size="small"
          value={value}
          options={responseTypeOptions}
          className="w-[100px]"
          onChange={(next) => updateResponseField(index, { type: next })}
        />
      ),
    },
    {
      title: '可空',
      dataIndex: 'nullable',
      width: 58,
      render: (value: boolean) => <Tag bordered={false}>{value ? '是' : '否'}</Tag>,
    },
    {
      title: '描述',
      dataIndex: 'description',
      width: 170,
      render: (value: string | null | undefined, _record, index) => (
        <Input
          size="small"
          value={value || ''}
          placeholder="字段说明"
          onChange={(event) => updateResponseField(index, { description: event.target.value })}
        />
      ),
    },
    {
      title: '示例',
      dataIndex: 'example',
      width: 140,
      render: (value: string | null | undefined, _record, index) => (
        <Input
          size="small"
          value={value || ''}
          placeholder="示例值"
          onChange={(event) => updateResponseField(index, { example: event.target.value })}
        />
      ),
    },
  ], []);

  const propertiesPanel = (
    <div className="text-[12px] leading-5">
      <div className="grid grid-cols-[88px_minmax(0,1fr)] items-center gap-x-4 gap-y-4">
        <div className="text-[#667085]">API 名称：</div>
        <Input
          size="small"
          value={serviceName}
          maxLength={200}
          onChange={(event) => { setServiceName(event.target.value); markDirty(); }}
        />
        <div className="text-[#667085]">请求方式：</div>
        <div className="text-[#344054]">GET</div>
        <div className="text-[#667085]">API Path：</div>
        <Input
          size="small"
          value={path}
          maxLength={255}
          onChange={(event) => { setPath(event.target.value); markDirty(); }}
        />
        <div className="text-[#667085]">最大行数：</div>
        <InputNumber
          size="small"
          min={1}
          max={10000}
          value={maxRows}
          className="w-full"
          onChange={(value) => { setMaxRows(Number(value || 1000)); markDirty(); }}
        />
        <div className="text-[#667085]">超时（秒）：</div>
        <InputNumber
          size="small"
          min={1}
          max={3600}
          value={timeoutSeconds}
          className="w-full"
          onChange={(value) => { setTimeoutSeconds(Number(value || 30)); markDirty(); }}
        />
        <div className="self-start pt-1 text-[#667085]">描述：</div>
        <Input.TextArea
          autoSize={{ minRows: 3, maxRows: 6 }}
          maxLength={2000}
          value={description}
          placeholder="说明这个数据服务提供什么能力"
          onChange={(event) => { setDescription(event.target.value); markDirty(); }}
        />
      </div>

      <dl className="m-0 mt-5 grid grid-cols-[88px_minmax(0,1fr)] gap-x-4 gap-y-3 border-t border-[#eef0f2] pt-4">
        <dt className="text-[#667085]">Draft：</dt>
        <dd className="m-0 text-[#344054]">#{context?.draft?.draftRevision || 0}</dd>
        <dt className="text-[#667085]">发布版本：</dt>
        <dd className="m-0 text-[#344054]">{latestPublished ? `DS R${latestPublished.revisionNo}` : '尚未发布'}</dd>
        <dt className="text-[#667085]">数据源：</dt>
        <dd className="m-0 break-all text-[#344054]">{metadataPath || '未选择数据源'}</dd>
        <dt className="text-[#667085]">查询 SQL：</dt>
        <dd className="m-0 text-[#344054]">{sqlText.trim() ? '已配置' : '未填写'}</dd>
        <dt className="text-[#667085]">结果分页：</dt>
        <dd className="m-0 text-[#344054]">{paginationEnabled ? '开启' : '关闭'}</dd>
        <dt className="text-[#667085]">参数解析：</dt>
        <dd className="m-0 text-[#344054]">{autoParseParameters ? '自动' : '手动维护'}</dd>
      </dl>
    </div>
  );

  const requestPanel = (
    <div>
      <div className="mb-4 text-[11px] leading-5 text-[#98a2b3]">
        SQL 中的 <span className="font-mono">:name</span> 命名参数作为请求参数；示例值同时用于顶部“运行查询”。
      </div>
      {paginationEnabled ? (
        <div className="mb-4 rounded-[4px] border border-[#e6e9ef] bg-[#fafbfc] px-3 py-2 text-[11px] leading-5 text-[#667085]">
          <span className="font-medium text-[#475467]">分页系统参数</span>
          <span className="ml-2 font-mono">returnTotalNum</span>
          <span className="mx-1 text-[#c0c5ce]">·</span>
          <span className="font-mono">pageNum</span>
          <span className="mx-1 text-[#c0c5ce]">·</span>
          <span className="font-mono">pageSize</span>
        </div>
      ) : null}
      <Table<DevelopmentDataServiceParameter>
        rowKey="name"
        size="small"
        pagination={false}
        dataSource={parameters}
        columns={parameterColumns}
        scroll={{ x: 610 }}
        locale={{ emptyText: autoParseParameters ? '运行查询后自动发现请求参数' : '当前 SQL 没有请求参数' }}
      />
    </div>
  );

  const responsePanel = (
    <div>
      <div className="mb-4 text-[11px] leading-5 text-[#98a2b3]">
        运行当前 SQL 后从真实结果集发现字段类型，可补充描述与示例后固化到 DS Revision。
      </div>
      {paginationEnabled ? (
        <div className="mb-4 rounded-[4px] border border-[#e6e9ef] bg-[#fafbfc] px-3 py-2 text-[11px] leading-5 text-[#667085]">
          <span className="font-medium text-[#475467]">分页返回信息</span>
          <span className="ml-2 font-mono">totalNum</span>
          <span className="mx-1 text-[#c0c5ce]">·</span>
          <span className="font-mono">pageNum</span>
          <span className="mx-1 text-[#c0c5ce]">·</span>
          <span className="font-mono">pageSize</span>
        </div>
      ) : null}
      <Table<DevelopmentDataServiceResponseField>
        rowKey={(record) => record.name}
        size="small"
        pagination={false}
        dataSource={responseFields}
        columns={responseColumns}
        scroll={{ x: 610 }}
        locale={{ emptyText: '运行查询后自动发现返回字段' }}
      />
    </div>
  );

  const versionsPanel = (
    <div className="space-y-3 text-[12px]">
      {context?.revisions?.length ? (
        <div className="space-y-1.5">
          {context.revisions.map((revision, index) => (
            <div
              key={revision.id}
              className="flex w-full items-center gap-2 rounded-[3px] border border-[#eaecf0] px-2.5 py-2"
            >
              <FileCode2 size={14} className="shrink-0 text-[#667085]" strokeWidth={1.7} />
              <div className="min-w-0 flex-1">
                <div className="flex items-center gap-2">
                  <span className="font-medium text-[#344054]">DS R{revision.revisionNo}</span>
                  {index === 0 ? (
                    <span className="rounded bg-[#f2f4f7] px-1.5 py-0.5 text-[10px] text-[#667085]">最新</span>
                  ) : null}
                </div>
                <div className="mt-0.5 truncate text-[10px] text-[#98a2b3]">
                  {formatTime(revision.createTime)} · Draft #{revision.sourceDraftRevision}
                </div>
              </div>
            </div>
          ))}
        </div>
      ) : (
        <div className="py-8 text-center text-[11px] leading-5 text-[#98a2b3]">
          暂无已发布版本
          <div className="mt-1">保存草稿后点击顶部发布按钮生成 DS R1。</div>
        </div>
      )}
    </div>
  );

  const onlinePanel = (
    <div className="text-[12px] leading-5">
      <div className="mb-4 text-[11px] leading-5 text-[#98a2b3]">
        发布用于生成稳定版本；需要对外提供服务时点击“上线”。后续发布新版本后使用“更新上线”。
      </div>

      <dl className="m-0 grid grid-cols-[88px_minmax(0,1fr)] gap-x-4 gap-y-4">
        <dt className="text-[#667085]">最新版本：</dt>
        <dd className="m-0 text-[#344054]">{latestPublished ? `DS R${latestPublished.revisionNo}` : '尚未发布'}</dd>
        <dt className="text-[#667085]">线上版本：</dt>
        <dd className="m-0 text-[#344054]">
          {publicationState?.published ? `DS R${onlineRevisionNo || '-'}` : '-'}
        </dd>
        <dt className="text-[#667085]">服务状态：</dt>
        <dd className="m-0 text-[#344054]">{serviceStatus}</dd>
        <dt className="text-[#667085]">Endpoint：</dt>
        <dd className="m-0 break-all font-mono text-[11px] text-[#344054]">
          {publicationState?.detail?.runtimePath || '-'}
        </dd>
      </dl>

      <div className="mt-5 border-t border-[#eef0f2] pt-4">
        <Button
          block
          type="primary"
          icon={<Rocket size={14} />}
          disabled={onlineActionDisabled}
          loading={goingOnline}
          onClick={() => void goOnline()}
        >
          {onlineActionLabel}
        </Button>
        {publicationState?.published ? (
          <Button
            block
            className="mt-2"
            icon={<ExternalLink size={14} />}
            onClick={() => history.push('/data-service')}
          >
            打开 API 服务
          </Button>
        ) : null}
        {publicationError ? (
          <div className="mt-3 text-[11px] leading-5 text-[#b42318]">{publicationError}</div>
        ) : null}
      </div>
    </div>
  );

  const panelContent: Record<RightPanelKey, ReactNode> = {
    properties: propertiesPanel,
    request: requestPanel,
    response: responsePanel,
    versions: versionsPanel,
    online: onlinePanel,
  };

  const refreshActivePanel = () => {
    if (activePanel === 'online') {
      void loadPublicationState(Boolean(latestPublished), true);
      return;
    }
    void load();
  };

  const handleResizeStart = (event: ReactPointerEvent<HTMLDivElement>) => {
    if (!activePanel) return;
    event.preventDefault();

    const startX = event.clientX;
    const startWidth = panelWidth;
    const previousCursor = document.body.style.cursor;
    const previousUserSelect = document.body.style.userSelect;

    setResizing(true);
    document.body.style.cursor = 'col-resize';
    document.body.style.userSelect = 'none';

    const resize = (moveEvent: PointerEvent) => {
      setPanelWidth(clampPanelWidth(startWidth + startX - moveEvent.clientX));
    };

    const finish = (upEvent: PointerEvent) => {
      const nextWidth = clampPanelWidth(startWidth + startX - upEvent.clientX);
      setPanelWidth(nextWidth);
      setResizing(false);
      window.localStorage.setItem(PANEL_WIDTH_STORAGE_KEY, String(nextWidth));
      document.body.style.cursor = previousCursor;
      document.body.style.userSelect = previousUserSelect;
      window.removeEventListener('pointermove', resize);
      window.removeEventListener('pointerup', finish);
      window.removeEventListener('pointercancel', finish);
    };

    window.addEventListener('pointermove', resize);
    window.addEventListener('pointerup', finish);
    window.addEventListener('pointercancel', finish);
  };

  const handleResultResizeStart = (event: ReactPointerEvent<HTMLDivElement>) => {
    event.preventDefault();

    const startY = event.clientY;
    const startHeight = resultHeight;
    const previousCursor = document.body.style.cursor;
    const previousUserSelect = document.body.style.userSelect;

    setResultResizing(true);
    document.body.style.cursor = 'row-resize';
    document.body.style.userSelect = 'none';

    const resize = (moveEvent: PointerEvent) => {
      setResultHeight(clampResultHeight(startHeight + startY - moveEvent.clientY));
    };

    const finish = (upEvent: PointerEvent) => {
      const nextHeight = clampResultHeight(startHeight + startY - upEvent.clientY);
      setResultHeight(nextHeight);
      setResultResizing(false);
      window.localStorage.setItem(RESULT_HEIGHT_STORAGE_KEY, String(nextHeight));
      document.body.style.cursor = previousCursor;
      document.body.style.userSelect = previousUserSelect;
      window.removeEventListener('pointermove', resize);
      window.removeEventListener('pointerup', finish);
      window.removeEventListener('pointercancel', finish);
    };

    window.addEventListener('pointermove', resize);
    window.addEventListener('pointerup', finish);
    window.addEventListener('pointercancel', finish);
  };

  if (loading) {
    return (
      <div className="flex min-h-0 flex-1 items-center justify-center bg-white">
        <Spin size="small" />
      </div>
    );
  }

  if (loadError || !context) {
    return (
      <div className="flex min-h-0 flex-1 items-center justify-center bg-white px-6">
        <div className="max-w-[520px] text-center">
          <div className="text-[14px] font-semibold text-[#344054]">Data Service Node 加载失败</div>
          <div className="mt-2 text-[12px] leading-5 text-[#98a2b3]">{loadError || '未返回有效编辑上下文'}</div>
          <Button className="mt-4" size="small" icon={<RefreshCw size={13} />} onClick={() => void load()}>
            重新加载
          </Button>
        </div>
      </div>
    );
  }

  const canRun = Boolean(metadataContext.dataSourceId && sqlText.trim());
  const canSave = canRun && dirty;

  return (
    <div className="flex min-h-0 flex-1 flex-col overflow-hidden bg-white">
      <div className="flex h-9 shrink-0 items-center justify-between border-b border-[#e8e9ec] bg-white px-2">
        <div className="flex shrink-0 items-center gap-0.5">
          <ToolbarButton
            title={running ? '正在运行查询' : '运行查询'}
            disabled={!canRun || running}
            onClick={() => void runQuery()}
          >
            {running ? <LoaderCircle size={15} className="animate-spin" /> : <Play size={15} strokeWidth={1.8} />}
          </ToolbarButton>
          <ToolbarDivider />
          <ToolbarButton
            title="保存草稿"
            disabled={!canSave || saving || publishing}
            onClick={() => void save()}
          >
            {saving ? <LoaderCircle size={15} className="animate-spin" /> : <Save size={15} strokeWidth={1.8} />}
          </ToolbarButton>
          <ToolbarButton
            title="发布版本"
            disabled={!context.draft.draftRevision || dirty || saving || publishing}
            onClick={() => void publish()}
          >
            {publishing ? <LoaderCircle size={15} className="animate-spin" /> : <Rocket size={15} strokeWidth={1.8} />}
          </ToolbarButton>
          <ToolbarDivider />
          <ToolbarButton title="撤销" onClick={() => execute('undo', 'SQL 编辑器尚未就绪')}>
            <Undo2 size={15} strokeWidth={1.8} />
          </ToolbarButton>
          <ToolbarButton title="重做" onClick={() => execute('redo', 'SQL 编辑器尚未就绪')}>
            <Redo2 size={15} strokeWidth={1.8} />
          </ToolbarButton>
          <ToolbarButton title="查找" onClick={() => execute('find', 'SQL 编辑器尚未就绪')}>
            <Search size={15} strokeWidth={1.8} />
          </ToolbarButton>
          <ToolbarDivider />
          <ToolbarButton title="格式化 SQL" onClick={() => execute('format', 'SQL 编辑器尚未就绪')}>
            <Wand2 size={15} strokeWidth={1.8} />
          </ToolbarButton>
          <ToolbarButton title="触发智能提示" onClick={() => execute('suggest', 'SQL 编辑器尚未就绪')}>
            <Sparkles size={15} strokeWidth={1.8} />
          </ToolbarButton>
        </div>

        <div className="flex min-w-0 items-center justify-end gap-3">
          <div className="hidden shrink-0 items-center gap-4 border-r border-[#e5e7eb] pr-3 xl:flex">
            <Tooltip
              title="开启后，发布的数据服务使用分页请求参数 pageNum / pageSize / returnTotalNum，并返回分页信息。"
              mouseEnterDelay={0.3}
            >
              <label className="flex cursor-pointer items-center gap-2 text-[11px] text-[#475467]">
                <span>返回结果分页</span>
                <Switch
                  size="small"
                  checked={paginationEnabled}
                  onChange={(checked) => {
                    setPaginationEnabled(checked);
                    markDirty();
                  }}
                />
              </label>
            </Tooltip>
            <Tooltip
              title="开启后，运行查询会根据 SQL 命名参数和真实结果集同步请求参数、返回字段。"
              mouseEnterDelay={0.3}
            >
              <label className="flex cursor-pointer items-center gap-2 text-[11px] text-[#475467]">
                <span>自动解析参数</span>
                <Switch
                  size="small"
                  checked={autoParseParameters}
                  onChange={(checked) => {
                    setAutoParseParameters(checked);
                    if (checked) {
                      setParameters((current) => mergeParameters(parseNamedParameters(sqlText), current));
                      setResponseFields([]);
                    }
                    markDirty();
                    if (checked && metadataContext.dataSourceId && sqlText.trim()) {
                      window.setTimeout(() => void runQuery(true), 0);
                    }
                  }}
                />
              </label>
            </Tooltip>
          </div>
          <SqlMetadataContextToolbar nodeId={node.id} />
        </div>
      </div>

      <div className="flex min-h-0 flex-1 overflow-hidden">
        <section className="flex min-w-0 flex-1 flex-col overflow-hidden bg-white">
          <div className="min-h-[220px] min-w-0 flex-1">
            <SqlMonacoEditor
              id={String(node.id)}
              value={sqlText}
              onChange={changeSql}
              onPositionChange={setPosition}
            />
          </div>

          <div className="flex h-6 shrink-0 items-center justify-between border-t border-[#eef0f2] bg-[#fafafa] px-2.5 text-[10px] text-[#7b808a]">
            <div className="flex min-w-0 items-center gap-3">
              <span className="font-medium text-[#667085]">DATA SERVICE</span>
              <span className="truncate">{node.name}</span>
              {dirty ? (
                <span className="inline-flex shrink-0 items-center gap-1 text-[#667085]">
                  <span className="h-1.5 w-1.5 rounded-full bg-[#667085]" />
                  未保存
                </span>
              ) : null}
              <span
                className={[
                  'max-w-[260px] truncate',
                  metadataContext.dataSourceId ? 'text-[#667085]' : 'text-[#b0b7c3]',
                ].join(' ')}
                title={metadataPath || '未选择数据源'}
              >
                {metadataPath || '未选择数据源'}
              </span>
            </div>
            <div className="flex shrink-0 items-center gap-3">
              <span>{responseFields.length ? `${responseFields.length} 个响应字段` : '运行查询以发现字段'}</span>
              <span>
                Draft #{context.draft.draftRevision || 0}
                {latestPublished ? ` · DS R${latestPublished.revisionNo}` : ''}
              </span>
              {position.selectionLength > 0 ? <span>已选择 {position.selectionLength} 字符</span> : null}
              <span>Ln {position.lineNumber}, Col {position.column}</span>
            </div>
          </div>

          <div
            className={[
              'relative shrink-0 bg-white',
              resultResizing ? 'transition-none' : 'transition-[height] duration-200 ease-out',
            ].join(' ')}
            style={{ height: resultHeight }}
          >
            <div
              role="separator"
              aria-label="调整查询结果面板高度"
              aria-orientation="horizontal"
              onPointerDown={handleResultResizeStart}
              className="group absolute inset-x-0 top-0 z-40 h-3 -translate-y-1/2 cursor-row-resize touch-none"
            >
              <div className="pointer-events-none absolute inset-x-0 top-1/2 h-px -translate-y-1/2 bg-[#e5e7eb] transition-[height,background-color] duration-150 group-hover:h-[2px] group-hover:bg-[rgba(254,44,85,.55)] group-active:h-[2px] group-active:bg-[rgba(254,44,85,1)]" />
            </div>
            <div className="h-full overflow-hidden bg-white">
              <SqlResultWorkspace result={queryResult} />
            </div>
          </div>
        </section>

        <aside className="flex shrink-0 bg-white" style={BRAND_CSS_VARIABLES}>
          <div
            className={[
              'relative h-full shrink-0',
              resizing ? 'transition-none' : 'transition-[width] duration-200 ease-out',
            ].join(' ')}
            style={{ width: activePanel ? panelWidth : 0 }}
          >
            {activePanel ? (
              <div
                role="separator"
                aria-label="调整右侧面板宽度"
                aria-orientation="vertical"
                onPointerDown={handleResizeStart}
                className="group absolute inset-y-0 left-0 z-30 w-3 -translate-x-1/2 cursor-col-resize touch-none"
              >
                <div className="pointer-events-none absolute inset-y-0 left-1/2 w-px -translate-x-1/2 bg-[#e5e7eb] transition-[width,background-color] duration-150 group-hover:w-[2px] group-hover:bg-[rgba(254,44,85,.55)] group-active:w-[2px] group-active:bg-[rgba(254,44,85,1)]" />
              </div>
            ) : null}

            <div className="h-full overflow-hidden">
              <div className="flex h-full flex-col bg-white" style={{ width: panelWidth }}>
                <div className="flex h-11 shrink-0 items-center justify-between border-b border-[#e5e7eb] px-4">
                  <span className="text-[13px] font-semibold text-[#30323b]">
                    {panelItems.find((item) => item.key === activePanel)?.label}
                  </span>
                  <div className="flex items-center gap-1">
                    <button
                      type="button"
                      title="刷新"
                      onClick={refreshActivePanel}
                      className="flex h-7 items-center gap-1 rounded-[3px] px-2 text-[11px] text-[#475467] transition-colors hover:bg-[#f5f5f6]"
                    >
                      <RefreshCw size={13} strokeWidth={1.8} />
                      刷新
                    </button>
                    <button
                      type="button"
                      title="关闭"
                      aria-label="关闭右侧面板"
                      onClick={() => setActivePanel(undefined)}
                      className="flex h-7 w-7 items-center justify-center rounded-[3px] text-[#667085] transition-colors hover:bg-[#f5f5f6] hover:text-[#344054]"
                    >
                      <X size={14} strokeWidth={1.8} />
                    </button>
                  </div>
                </div>
                <div className="min-h-0 flex-1 overflow-y-auto px-4 py-5">
                  {activePanel ? panelContent[activePanel] : null}
                </div>
              </div>
            </div>
          </div>

          <div className="flex h-full w-9 shrink-0 flex-col border-l border-[#e5e7eb] bg-white">
            {panelItems.map((item, index) => {
              const active = activePanel === item.key;
              return (
                <button
                  key={item.key}
                  type="button"
                  title={item.label}
                  aria-label={`${active ? '收起' : '展开'}${item.label}`}
                  aria-expanded={active}
                  onClick={() => setActivePanel((current) => current === item.key ? undefined : item.key)}
                  className={[
                    'relative flex min-h-[72px] w-9 shrink-0 items-center justify-center border-b border-[#e5e7eb] py-3 text-[12px] leading-5 transition-[color,background-color,opacity]',
                    '[writing-mode:vertical-rl] [letter-spacing:3px]',
                    index === 0 ? 'border-t' : '',
                    active
                      ? 'text-[var(--yak-brand-color)] opacity-100 before:absolute before:inset-y-0 before:left-0 before:w-px before:bg-[var(--yak-brand-color)]'
                      : 'text-[#475467] opacity-70 hover:bg-[#f7f8fa] hover:text-[#344054] hover:opacity-100',
                  ].join(' ')}
                >
                  {item.label}
                </button>
              );
            })}
          </div>
        </aside>
      </div>
    </div>
  );
}
