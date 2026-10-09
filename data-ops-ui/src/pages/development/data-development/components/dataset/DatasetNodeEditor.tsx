import { toFieldDrafts } from './datasetDraftModel';
import { useLatestOperation } from '@/hooks/useLatestOperation';
import { useSecurityProject } from '@/contexts/SecurityProjectContext';
import { BRAND_CSS_VARIABLES } from '@/styles/brand';
import {
  Input,
  Select,
  Spin,
  Table,
  Tooltip,
  message,
  type TableColumnsType,
} from 'antd';
import { YakButton } from '@/components/ui';
import {
  FileStack,
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
  getDevelopmentDatasetMetricRefs,
  getDevelopmentDatasetNode,
  publishDevelopmentDatasetNode,
  runDevelopmentDatasetNode,
  saveDevelopmentDatasetNode,
  syncDevelopmentDatasetMetricRefs,
  type DevelopmentDatasetFieldDraft,
  type DevelopmentDatasetFieldRole,
  type DevelopmentDatasetNodeContext,
} from '@/services/data-development';
import { listPublishedMetrics } from '@/services/metric/api';
import type { MetricVersionRef } from '@/services/metric/types';
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
import {
  datasetDeliveryState,
  datasetPublishOutcome,
} from './datasetDeliveryExperience';
import { datasetVersionNoFromSearch } from '../../assetGovernance';

interface DatasetNodeEditorProps {
  node: DevelopmentResourceNode;
  onSaved?: () => void | Promise<void>;
  onDirtyChange?: (dirty: boolean) => void;
}

type RightPanelKey = 'properties' | 'fields' | 'versions';

const panelItems: Array<{ key: RightPanelKey; label: string }> = [
  { key: 'properties', label: '属性' },
  { key: 'fields', label: '字段' },
  { key: 'versions', label: '版本' },
];

const roleOptions = [
  { label: '维度', value: 'DIMENSION' },
  { label: '指标', value: 'MEASURE' },
];

const DEFAULT_PANEL_WIDTH = 420;
const MIN_PANEL_WIDTH = 320;
const MAX_PANEL_WIDTH = 720;
const PANEL_WIDTH_STORAGE_KEY = 'yak-data-development.dataset-right-panel-width';
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

const ToolbarButton = ({
  title,
  onClick,
  disabled,
  children,
}: {
  title: string;
  onClick: () => void;
  disabled?: boolean;
  children: ReactNode;
}) => (
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

export default function DatasetNodeEditor({
  node,
  onSaved,
  onDirtyChange,
}: DatasetNodeEditorProps) {
  const { currentProject } = useSecurityProject();
  const beginLoad = useLatestOperation(`${currentProject?.id ?? ""}:${node.id}`);
  const beginQuery = useLatestOperation(`${currentProject?.id ?? ""}:${node.id}`);
  const beginSave = useLatestOperation(`${currentProject?.id ?? ""}:${node.id}`);
  const beginPublish = useLatestOperation(`${currentProject?.id ?? ""}:${node.id}`);
  const beginOnline = useLatestOperation(`${currentProject?.id ?? ""}:${node.id}`);
  const metadataContext = useSqlMetadataContext(node.id);
  const requestedVersionNo = typeof window === 'undefined'
    ? undefined
    : datasetVersionNoFromSearch(window.location.search);
  const dirtyChangeRef = useRef(onDirtyChange);
  const savedDataSourceIdRef = useRef<DevelopmentId>();

  const [context, setContext] = useState<DevelopmentDatasetNodeContext>();
  const [sqlText, setSqlText] = useState('');
  const [description, setDescription] = useState('');
  const [fields, setFields] = useState<DevelopmentDatasetFieldDraft[]>([]);
  const [queryResult, setQueryResult] = useState<DevelopmentTaskRunResult>();
  const [dirty, setDirty] = useState(false);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string>();
  const [running, setRunning] = useState(false);
  const [saving, setSaving] = useState(false);
  const [publishing, setPublishing] = useState(false);
  const [activePanel, setActivePanel] = useState<RightPanelKey | undefined>(
    () => requestedVersionNo ? 'versions' : undefined,
  );
  const [panelWidth, setPanelWidth] = useState(initialPanelWidth);
  const [resizing, setResizing] = useState(false);
  const [resultHeight, setResultHeight] = useState(initialResultHeight);
  const [resultResizing, setResultResizing] = useState(false);
  const [position, setPosition] = useState<SqlEditorPosition>(defaultPosition);
  const [metricRefs, setMetricRefs] = useState<MetricVersionRef[]>([]);
  const [metricOptions, setMetricOptions] = useState<{ label: string; value: string }[]>([]);
  const [metricRefsState, setMetricRefsState] = useState<'LOADING' | 'READY' | 'UNAVAILABLE' | 'FORBIDDEN'>('LOADING');
  const [publishedMetricsState, setPublishedMetricsState] = useState<'LOADING' | 'READY' | 'UNAVAILABLE' | 'FORBIDDEN'>('LOADING');

  useEffect(() => {
    dirtyChangeRef.current = onDirtyChange;
  }, [onDirtyChange]);

  const setDirtyState = useCallback((next: boolean) => {
    setDirty(next);
    dirtyChangeRef.current?.(next);
  }, []);
  const markDirty = useCallback(() => setDirtyState(true), [setDirtyState]);

  useEffect(() => () => dirtyChangeRef.current?.(false), []);

  const applyContext = useCallback((next: DevelopmentDatasetNodeContext) => {
    const ds = next.dataset;
    const currentVersion = ds?.currentVersion;
    // Draft takes priority over version: the editor should reflect the last saved work,
    // even if no immutable version has been published yet.
    const dataSourceId =
      ds?.draftDataSourceId || currentVersion?.dataSourceId || undefined;
    savedDataSourceIdRef.current = dataSourceId;
    setContext(next);
    setSqlText(ds?.draftSql || currentVersion?.sql || '');
    setDescription(ds?.description || '');
    setFields(
      ds?.draftFields?.length
        ? ds.draftFields
        : toFieldDrafts(next),
    );
    setQueryResult(undefined);
    hydrateSqlTaskConfig(node.id, JSON.stringify(dataSourceId ? { dataSourceId } : {}));
    setDirtyState(false);
  }, [node.id, setDirtyState]);

  const load = useCallback(async () => {
    const isCurrent = beginLoad();
    setLoading(true);
    setRunning(false);
    setSaving(false);
    setPublishing(false);
    setLoadError(undefined);
    setMetricRefsState('LOADING');
    try {
      const raw = await getDevelopmentDatasetNode(node.id);
      if (!isCurrent()) return;
      applyContext(raw);
      getDevelopmentDatasetMetricRefs(node.id)
        .then((references) => {
          if (!isCurrent()) return;
          setMetricRefs(references ?? []);
          setMetricRefsState('READY');
        })
        .catch((error) => {
          if (!isCurrent()) return;
          const response = error as { status?: number; response?: { status?: number } } | null;
          const status = response?.status ?? response?.response?.status;
          setMetricRefsState(status === 401 || status === 403 ? 'FORBIDDEN' : 'UNAVAILABLE');
        });
    } catch (error) {
      if (!isCurrent()) return;
      const text = error instanceof Error ? error.message : '加载 Dataset Node 失败';
      setLoadError(text);
      setContext(undefined);
      message.error(text);
    } finally {
      if (isCurrent()) setLoading(false);
    }
  }, [applyContext, node.id, currentProject?.id, beginLoad]);

  useEffect(() => {
    void load();
  }, [load]);

  useEffect(() => {
    let active = true;
    setMetricOptions([]);
    setPublishedMetricsState('LOADING');
    listPublishedMetrics()
      .then((publications) => {
        if (!active) return;
        setMetricOptions(publications.map((published) => {
          let snapshot: { metricName?: string; metricCode?: string } = {};
          try {
            snapshot = JSON.parse(published.snapshot || '{}');
          } catch {
            // The stable Metric id and version still provide a usable option label.
          }
          const metricName = snapshot.metricName || `指标 #${published.metricId}`;
          const metricCode = snapshot.metricCode || String(published.metricId);
          return {
            label: `${metricName}（${metricCode}） · 已发布 v${published.metricVersion}`,
            value: `${published.metricId}:${published.metricVersion}`,
          };
        }));
        setPublishedMetricsState('READY');
      })
      .catch((error) => {
        if (!active) return;
        const response = error as { status?: number; response?: { status?: number } } | null;
        const status = response?.status ?? response?.response?.status;
        setPublishedMetricsState(status === 401 || status === 403 ? 'FORBIDDEN' : 'UNAVAILABLE');
      });
    return () => { active = false; };
  }, [currentProject?.id]);

  useEffect(() => {
    if (!context || loading || metadataContext.dataSourceId === savedDataSourceIdRef.current) return;
    setFields([]);
    setQueryResult(undefined);
    markDirty();
  }, [context, loading, markDirty, metadataContext.dataSourceId]);

  const deliveryState = datasetDeliveryState(context, dirty);
  const versions = useMemo(
    () => [...(context?.dataset?.versions || [])].sort((a, b) => b.versionNo - a.versionNo),
    [context?.dataset?.versions],
  );
  const activeMetricRefValues = useMemo(
    () => new Set(metricOptions.map((option) => option.value)),
    [metricOptions],
  );
  const historicalMetricRefOptions = metricRefs
    .filter((reference) => reference.versionNo != null
      && !activeMetricRefValues.has(`${reference.metricId}:${reference.versionNo}`))
    .map((reference) => ({
      label: `指标 #${reference.metricId} · 已引用 v${reference.versionNo}（当前未发布）`,
      value: `${reference.metricId}:${reference.versionNo}`,
    }));
  const metadataPath = useMemo(
    () => [
      metadataContext.dataSourceName
        || (metadataContext.dataSourceId ? `DS ${metadataContext.dataSourceId}` : undefined),
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
    setFields([]);
    setQueryResult(undefined);
    markDirty();
  };

  const updateField = useCallback((
    index: number,
    patch: Partial<DevelopmentDatasetFieldDraft>,
  ) => {
    setFields((current) => current.map((field, fieldIndex) =>
      fieldIndex === index ? { ...field, ...patch } : field,
    ));
    markDirty();
  }, [markDirty]);

  const columns = useMemo<TableColumnsType<DevelopmentDatasetFieldDraft>>(() => [
    {
      title: '字段',
      dataIndex: 'physicalName',
      width: 150,
      render: (value: string) => (
        <span className="font-mono text-[12px] text-[#344054]">{value}</span>
      ),
    },
    {
      title: '显示名称',
      dataIndex: 'displayName',
      width: 150,
      render: (value: string, _record, index) => (
        <Input
          size="small"
          value={value}
          maxLength={200}
          onChange={(event) => updateField(index, { displayName: event.target.value })}
        />
      ),
    },
    {
      title: '类型',
      dataIndex: 'dataType',
      width: 92,
      render: (value: string) => (
        <span className="font-mono text-[12px] text-[#667085]">{value}</span>
      ),
    },
    {
      title: '角色',
      dataIndex: 'defaultRole',
      width: 105,
      render: (value: DevelopmentDatasetFieldRole, _record, index) => (
        <Select
          size="small"
          value={value}
          options={roleOptions}
          className="w-[94px]"
          onChange={(next) => updateField(index, {
            defaultRole: next as DevelopmentDatasetFieldRole,
          })}
        />
      ),
    },
    {
      title: '可空',
      dataIndex: 'nullable',
      width: 54,
      render: (value: boolean) => (
        <span className="text-[12px] text-[#667085]">{value ? '是' : '否'}</span>
      ),
    },
    {
      title: '描述',
      dataIndex: 'description',
      width: 180,
      render: (value: string | null | undefined, _record, index) => (
        <Input
          size="small"
          value={value || ''}
          maxLength={1000}
          placeholder="字段说明"
          onChange={(event) => updateField(index, { description: event.target.value })}
        />
      ),
    },
  ], [updateField]);

  const runQuery = async () => {
    const dataSourceId = metadataContext.dataSourceId;
    if (!dataSourceId || !sqlText.trim() || running) return;

    const isCurrent = beginQuery();
    setRunning(true);
    setQueryResult({
      status: 'RUNNING',
      message: '正在执行 Dataset 查询',
      durationMs: 0,
      output: {},
    });

    try {
      const existing = new Map(
        fields.map((field) => [field.physicalName.toLowerCase(), field]),
      );
      const result = await runDevelopmentDatasetNode(node.id, dataSourceId, sqlText);
      if (!isCurrent()) return;
      const discovered = result?.fields || [];

      setFields(discovered.map((field) => {
        const previous = existing.get(field.physicalName.toLowerCase());
        return previous
          ? {
              ...field,
              fieldId: previous.fieldId || field.fieldId,
              displayName: previous.displayName || field.displayName,
              description: previous.description,
              defaultRole: previous.defaultRole,
            }
          : field;
      }));
      markDirty();

      setQueryResult({
        status: 'SUCCESS',
        message: '查询成功',
        durationMs: Number(result?.durationMs || 0),
        output: {
          kind: 'RESULT_SET',
          columns: result?.columns || [],
          rows: result?.rows || [],
          returnedRows: Number(result?.returnedRows || 0),
          truncated: Boolean(result?.truncated),
          dataSourceId,
        },
      });
      message.success(
        `查询完成 · ${Number(result?.returnedRows || 0)} 行 / ${discovered.length} 个字段`,
      );
    } catch (error) {
      if (!isCurrent()) return;
      const text = error instanceof Error ? error.message : '运行 Dataset 查询失败';
      setQueryResult({
        status: 'FAILED',
        message: text,
        durationMs: 0,
        output: {},
      });
      message.error(text);
    } finally {
      if (isCurrent()) setRunning(false);
    }
  };

  const save = async () => {
    const dataSourceId = metadataContext.dataSourceId;
    if (!dataSourceId || !sqlText.trim() || !fields.length || saving) return;
    const isCurrent = beginSave();
    setSaving(true);
    try {
      const next = await saveDevelopmentDatasetNode(node.id, {
        dataSourceId,
        sql: sqlText,
        description: description.trim() || undefined,
        fields: fields.map((field) => ({
          ...field,
          displayName: field.displayName.trim() || field.physicalName,
          description: field.description?.trim() || undefined,
        })),
      });
      if (!isCurrent()) return;
      applyContext(next);
      await onSaved?.();
      if (!isCurrent()) return;
      // Never replace references from an incomplete or unavailable read.
      if (metricRefsState !== 'READY' || publishedMetricsState !== 'READY') {
        message.warning('指标引用或发布状态暂不可读；Dataset Draft 已保存，原有引用保持不变');
      } else if (metricRefs.every((reference) => reference.versionNo != null
        && activeMetricRefValues.has(`${reference.metricId}:${reference.versionNo}`))) {
        try {
          await syncDevelopmentDatasetMetricRefs(node.id, node.name, metricRefs);
      if (!isCurrent()) return;
        } catch (error) {
      if (!isCurrent()) return;
          message.error(error instanceof Error ? error.message : '指标引用同步失败；Dataset Draft 已保存');
        }
      } else {
        message.warning('仍有历史版本未知或当前未发布的指标引用；请移除或重新选择已发布版本后再同步');
      }
      message.success(
        `Dataset Draft 已保存 · Dataset #${next.dataset?.datasetId || '-'}`,
      );
    } catch (error) {
      if (!isCurrent()) return;
      message.error(error instanceof Error ? error.message : '保存 Dataset Draft 失败');
    } finally {
      if (isCurrent()) setSaving(false);
    }
  };

  const publish = async () => {
    if (!context?.dataset || publishing) return;
    if (dirty) {
      message.warning('当前有未保存修改，请先保存 Draft 再发布 DatasetVersion');
      return;
    }
    const beforeVersionNo = context.dataset.currentVersion?.versionNo;
    const isCurrent = beginPublish();
    setPublishing(true);
    try {
      const next = await publishDevelopmentDatasetNode(node.id);
      if (!isCurrent()) return;
      const outcome = datasetPublishOutcome(
        beforeVersionNo,
        next.dataset?.currentVersion?.versionNo,
      );
      applyContext(next);
      await onSaved?.();
      if (!isCurrent()) return;
      setActivePanel('versions');
      if (outcome.kind === 'PUBLISHED') {
        message.success(`已发布 immutable DatasetVersion · DV${outcome.versionNo}`);
      } else if (outcome.kind === 'UNCHANGED') {
        message.info(`Draft 与当前发布版本无 material change · 保持 DV${outcome.versionNo}`);
      } else {
        message.warning('发布请求已完成，但未返回可识别的 DatasetVersion');
      }
    } catch (error) {
      if (!isCurrent()) return;
      message.error(error instanceof Error ? error.message : '发布 DatasetVersion 失败');
    } finally {
      if (isCurrent()) setPublishing(false);
    }
  };

  const draftStateText =
    deliveryState.draftState === 'LOCAL_UNSAVED'
      ? '本地有未保存修改'
      : deliveryState.draftState === 'SAVED'
        ? 'Draft 已保存'
        : '尚未创建 Draft';

  const propertiesPanel = (
    <div className="text-[12px] leading-5">
      <div className="grid grid-cols-[88px_minmax(0,1fr)] items-center gap-x-4 gap-y-4">
        <div className="text-[#667085]">数据集名称：</div>
        <Input size="small" value={node.name} disabled />
        <div className="text-[#667085]">数据源：</div>
        <div className="break-all text-[#344054]">{metadataPath || '未选择数据源'}</div>
        <div className="self-start pt-1 text-[#667085]">描述：</div>
        <Input.TextArea
          autoSize={{ minRows: 3, maxRows: 6 }}
          maxLength={2000}
          value={description}
          placeholder="说明这个数据集提供什么数据"
          onChange={(event) => {
            setDescription(event.target.value);
            markDirty();
          }}
        />
        <div className="self-start pt-1 text-[#667085]">引用指标：</div>
        <Select
          mode="multiple"
          allowClear
          showSearch
          size="small"
          maxTagCount="responsive"
          placeholder="从指标中心选择该数据集服务的指标"
          disabled={metricRefsState !== 'READY' || publishedMetricsState !== 'READY'}
          value={metricRefs.map((reference) => reference.versionNo == null
            ? `legacy:${reference.metricId}`
            : `${reference.metricId}:${reference.versionNo}`)}
          options={[
            ...metricRefs.filter((reference) => reference.versionNo == null).map((reference) => ({
              label: `指标 #${reference.metricId}（历史引用版本未知）`,
              value: `legacy:${reference.metricId}`,
            })),
            ...historicalMetricRefOptions,
            ...metricOptions,
          ]}
          optionFilterProp="label"
          onChange={(values: string[]) => {
            const next = (values ?? []).map((value) => {
              const [id, version] = value.split(':');
              return { metricId: Number(value.startsWith('legacy:') ? version : id),
                versionNo: value.startsWith('legacy:') ? null : Number(version) };
            });
            setMetricRefs(next);
            markDirty();
          }}
        />
        {(metricRefsState === 'UNAVAILABLE' || publishedMetricsState === 'UNAVAILABLE') ? (
          <div className="col-span-2 -mt-2 text-[12px] text-[#b54708]">指标引用或当前发布状态暂不可读；保存 Dataset Draft 时会保留原有引用。</div>
        ) : (metricRefsState === 'FORBIDDEN' || publishedMetricsState === 'FORBIDDEN') ? (
          <div className="col-span-2 -mt-2 text-[12px] text-[#b54708]">无权读取指标引用或发布状态；保存 Dataset Draft 时会保留原有引用。</div>
        ) : null}
      </div>
      <dl className="m-0 mt-5 grid grid-cols-[88px_minmax(0,1fr)] gap-x-4 gap-y-3 border-t border-[#eef0f2] pt-4">
        <dt className="text-[#667085]">Dataset ID：</dt>
        <dd className="m-0 font-mono text-[#344054]">
          {deliveryState.datasetId ? `#${deliveryState.datasetId}` : '尚未创建'}
        </dd>
        <dt className="text-[#667085]">Draft：</dt>
        <dd className="m-0 text-[#344054]">{draftStateText}</dd>
        <dt className="text-[#667085]">已发布版本：</dt>
        <dd className="m-0 text-[#344054]">
          {deliveryState.publishedVersionNo
            ? `DV${deliveryState.publishedVersionNo}`
            : '尚未发布'}
        </dd>
        <dt className="text-[#667085]">交付状态：</dt>
        <dd className="m-0 text-[#344054]">{deliveryState.deliveryStatus || '-'}</dd>
        <dt className="text-[#667085]">查询 SQL：</dt>
        <dd className="m-0 text-[#344054]">{sqlText.trim() ? '已配置' : '未填写'}</dd>
        <dt className="text-[#667085]">字段：</dt>
        <dd className="m-0 text-[#344054]">{fields.length} 个</dd>
      </dl>
    </div>
  );

  const fieldsPanel = (
    <div>
      <div className="mb-3 text-[12px] leading-5 text-[#667085]">
        字段来自当前 SQL 输出结构。运行查询后会同步字段，再调整显示名称、角色和描述。
      </div>
      <Table<DevelopmentDatasetFieldDraft>
        size="small"
        pagination={false}
        rowKey={(record) => record.fieldId || record.physicalName}
        dataSource={fields}
        columns={columns}
        scroll={{ x: 730 }}
        locale={{ emptyText: '运行查询后生成字段结构' }}
      />
    </div>
  );

  const versionsPanel = (
    <div className="space-y-2 text-[12px]">
      {versions.length ? versions.map((version, index) => (
        <div
          key={version.versionId}
          aria-current={version.versionNo === requestedVersionNo ? 'true' : undefined}
          className={[
            'flex items-center gap-2 rounded-[3px] border px-2.5 py-2',
            version.versionNo === requestedVersionNo
              ? 'border-[#fe2c55] bg-[#fff7f8]'
              : 'border-[#eaecf0]',
          ].join(' ')}
        >
          <FileStack size={14} className="shrink-0 text-[#667085]" strokeWidth={1.7} />
          <div className="min-w-0 flex-1">
            <div className="flex items-center gap-2">
              <span className="font-medium text-[#344054]">DV{version.versionNo}</span>
              {index === 0 ? (
                <span className="rounded bg-[#f2f4f7] px-1.5 py-0.5 text-[12px] text-[#667085]">
                  当前发布
                </span>
              ) : null}
            </div>
            <div className="mt-0.5 truncate text-[12px] text-[#667085]">
              {version.sourceType === 'SQL_QUERY' ? '独立 SQL' : version.sourceType}
              {' · '}
              {formatTime(version.createTime)}
            </div>
          </div>
        </div>
      )) : (
        <div className="py-8 text-center text-[12px] text-[#667085]">暂无已发布 DatasetVersion</div>
      )}
    </div>
  );

  const panelContent: Record<RightPanelKey, ReactNode> = {
    properties: propertiesPanel,
    fields: fieldsPanel,
    versions: versionsPanel,
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
          <div className="text-[14px] font-semibold text-[#344054]">Dataset Node 加载失败</div>
          <div className="mt-2 text-[12px] leading-5 text-[#667085]">
            {loadError || '未返回有效编辑上下文'}
          </div>
          <YakButton
            className="mt-4"
            size="small"
            icon={<RefreshCw size={13} />}
            onClick={() => void load()}
          >
            重新加载
          </YakButton>
        </div>
      </div>
    );
  }

  const canRun = Boolean(metadataContext.dataSourceId && sqlText.trim());
  const canSave = canRun && fields.length > 0 && dirty;
  const canPublish = Boolean(context?.dataset && !dirty && !saving && !publishing);

  return (
    <div className="flex min-h-0 flex-1 flex-col overflow-hidden bg-white">
      <div className="flex h-9 shrink-0 items-center justify-between border-b border-[#e8e9ec] bg-white px-2">
        <div className="flex shrink-0 items-center gap-0.5">
          <ToolbarButton
            title={running ? '正在运行查询' : '运行查询'}
            disabled={!canRun || running}
            onClick={() => void runQuery()}
          >
            {running ? (
              <LoaderCircle size={15} className="animate-spin" />
            ) : (
              <Play size={15} strokeWidth={1.8} />
            )}
          </ToolbarButton>
          <ToolbarDivider />
          <ToolbarButton
            title="保存 Dataset Draft"
            disabled={!canSave || saving || publishing}
            onClick={() => void save()}
          >
            {saving ? (
              <LoaderCircle size={15} className="animate-spin" />
            ) : (
              <Save size={15} strokeWidth={1.8} />
            )}
          </ToolbarButton>
          <ToolbarButton
            title="发布 immutable DatasetVersion"
            disabled={!canPublish}
            onClick={() => void publish()}
          >
            {publishing ? (
              <LoaderCircle size={15} className="animate-spin" />
            ) : (
              <Rocket size={15} strokeWidth={1.8} />
            )}
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
        <SqlMetadataContextToolbar nodeId={node.id} />
      </div>

      <div className="flex min-h-0 flex-1 overflow-hidden">
        <section className="flex min-w-0 flex-1 flex-col overflow-hidden bg-white">
          <div className="min-h-[220px] flex-1">
            <SqlMonacoEditor
              id={String(node.id)}
              value={sqlText}
              onChange={changeSql}
              onPositionChange={setPosition}
            />
          </div>

          <div className="flex h-6 shrink-0 items-center justify-between border-t border-[#eef0f2] bg-[#fafafa] px-2.5 text-[12px] text-[#7b808a]">
            <div className="flex min-w-0 items-center gap-3">
              <span className="font-medium text-[#667085]">DATASET</span>
              <span className="truncate">{node.name}</span>
              {deliveryState.datasetId ? (
                <span className="font-mono text-[#667085]">Dataset #{deliveryState.datasetId}</span>
              ) : null}
              {dirty ? (
                <span className="inline-flex shrink-0 items-center gap-1 text-[#667085]">
                  <span className="h-1.5 w-1.5 rounded-full bg-[#667085]" />
                  Draft 未保存
                </span>
              ) : context.dataset ? (
                <span className="text-[#667085]">Draft 已保存</span>
              ) : null}
              <span
                className="max-w-[280px] truncate text-[#667085]"
                title={metadataPath || '未选择数据源'}
              >
                {metadataPath || '未选择数据源'}
              </span>
            </div>
            <div className="flex shrink-0 items-center gap-3">
              <span>{fields.length ? `${fields.length} 个字段` : '运行查询以发现字段'}</span>
              <span>
                {deliveryState.publishedVersionNo
                  ? `Published DV${deliveryState.publishedVersionNo}`
                  : '尚未发布 DatasetVersion'}
              </span>
              {position.selectionLength > 0 ? (
                <span>已选择 {position.selectionLength} 字符</span>
              ) : null}
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
                      onClick={() => {
                        if (dirty) {
                          message.warning('当前有未保存修改，请先保存');
                          return;
                        }
                        void load();
                      }}
                      className="flex h-7 items-center gap-1 rounded-[3px] px-2 text-[12px] text-[#475467] transition-colors hover:bg-[#f5f5f6]"
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
                  onClick={() => setActivePanel((current) =>
                    current === item.key ? undefined : item.key,
                  )}
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
