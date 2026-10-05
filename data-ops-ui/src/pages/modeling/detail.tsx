import { useSecurityProject } from '@/contexts/SecurityProjectContext';
import { useLatestOperation, useResourceScope } from '@/hooks/useLatestOperation';
import { useModelStructureDraft } from './editor/useModelStructureDraft';
import { type ColumnDraft, CODE_PATTERN, MODEL_PUBLISH_FLOW_CODE, MODEL_PUBLISH_BIZ_TYPE, PUBLISH_APPROVAL_POLL_MS, TYPE_DEFAULTS, typeSpecOf, resolveColumnType, PARTITION_TYPES_BY_DIALECT, AGGREGATE_FUNC_OPTIONS, AGGREGATE_LAYERS } from './editor/structureRules';
import { history, useParams } from '@umijs/max';
import { ApartmentOutlined, CloudDownloadOutlined, CopyOutlined, ExclamationCircleOutlined, FundOutlined, ReloadOutlined, ThunderboltOutlined } from '@ant-design/icons';
import {
  AutoComplete,
  Button,
  Card,
  Checkbox,
  Input,
  InputNumber,
  Modal,
  Radio,
  Select,
  Space,
  Switch,
  Table,
  type TableColumnsType,
  Tag,
  Tooltip,
  Typography,
  message,
} from 'antd';
import type { Key as ReactKey } from 'react';
import { createContext, useContext, useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { DndContext, DragOverlay, PointerSensor, useSensor, useSensors, type DragEndEvent, type DragStartEvent } from '@dnd-kit/core';
import { arrayMove, SortableContext, useSortable, verticalListSortingStrategy } from '@dnd-kit/sortable';
import { CSS } from '@dnd-kit/utilities';
import { YakButton, YakEmpty } from '@/components/ui';
import { ApprovalStatusTag } from '@/components/ApprovalStatusTag';
import { findByBiz, listFlows } from '@/services/approval/api';
import type { ApprovalInstance } from '@/services/approval/types';
import StandardAssistantDrawer from '@/pages/modeling/components/StandardAssistantDrawer';
import { MODELING_DIALECT_LABELS, MODELING_PUBLISHED_EVENT } from '@/pages/modeling/constants';
import {
  assignImportLineage,
  generateModelingDdl,
  getModelingMetricDraft,
  getModelingModel,
  getModelingStructure,
  getModelingTypeCatalog,
  getModelingVersion,
  pageModelingModels,
  previewModelingDerive,
  publishModelingModel,
  saveModelingStructure,
  submitModelingPublishApproval,
  validateModelingStructure,
} from '@/services/modeling/api';
import JsonDiffView from '@/components/version/JsonDiffView';
import { previewModelingImportColumns } from '@/services/modeling/import';
import type { ModelingModelRecord, ModelingStructureRecord, ModelingTypeOption, ModelingValidationIssue } from '@/services/modeling/types';
import { getStandardOptions, pageSemanticFields, pageSemanticProcesses } from '@/services/semantic/api';
import type { SemanticFieldRecord, SemanticProcessRecord } from '@/services/semantic/types';
import { listAllDataSources, listDataSourceTables } from '@/services/data-source/api';
import type { DataSourceRecord, DataSourceCatalogTable } from '@/services/data-source/types';
import { pageMetrics } from '@/services/metric/api';
import type { MetricRecord } from '@/services/metric/types';

const RowInteractionContext = createContext<{ highlight?: number; dragging: ReadonlySet<number> }>({ dragging: new Set() });

/** 可拖拽排序行。 */
const SortableRow = (
  props: React.HTMLAttributes<HTMLTableRowElement> & {
    'data-row-key'?: ReactKey;
  },
) => {
  const { className, style, ...restProps } = props;
  const interaction = useContext(RowInteractionContext);
  const rowKey = props['data-row-key'];
  const numericKey = Number(rowKey);
  const { attributes, listeners, setNodeRef, transform, transition, isDragging } = useSortable({
    id: numericKey,
  });
  const isHighlighted = rowKey !== undefined && numericKey === interaction.highlight;
  const isInMultiDrag = interaction.dragging.has(numericKey);
  const combinedStyle: React.CSSProperties = {
    ...style,
    transform: CSS.Translate.toString(transform),
    transition,
    ...(isDragging
      ? { opacity: 0 }
      : isInMultiDrag
        ? { opacity: 0.55, backgroundColor: '#e6f4ff' }
        : {}),
  };
  return (
    <tr
      ref={setNodeRef}
      className={[
        className,
        isDragging ? 'sortable-row-dragging' : '',
        isInMultiDrag ? 'sortable-row-multi-selected' : '',
        isHighlighted ? 'validation-highlight-row' : '',
      ].filter(Boolean).join(' ')}
      style={combinedStyle}
      {...attributes}
      {...listeners}
      {...restProps}
    />
  );
};

/** 模型详情：表结构在线编辑器（表基础信息 + 字段 + 主键/索引/分区/表属性全量保存）。 */
const ModelingModelDetail: React.FC = () => {
const draftSeq = useRef(0);
const nextDraftKey = () => {
  draftSeq.current += 1;
  return draftSeq.current;
};

const emptyColumnDraft = (): ColumnDraft => ({
  key: nextDraftKey(),
  columnName: '',
  dataType: '',
  // C6(2026-09-17):数仓字段通常不可空,默认不勾选
  nullable: false,
});


  const { captureDraft, resetDraft, tableName, setTableName, tableComment, setTableComment, rows, setRows, primaryKey, setPrimaryKey, indexes, setIndexes, partitionEnabled, setPartitionEnabled, partitionType, setPartitionType, partitionColumns, setPartitionColumns, partitionExpression, setPartitionExpression, properties, setProperties, dirty, setDirty } = useModelStructureDraft();
  const params = useParams<{ id?: string }>();
  const modelId = params.id;
  const { currentProject } = useSecurityProject();
  const captureEditorResource = useResourceScope(`${currentProject?.id ?? ""}:${modelId ?? ""}`);
  const beginStandardSearch = useLatestOperation(`${currentProject?.id ?? ""}:${modelId ?? ""}`);
  const beginImportTableLoad = useLatestOperation(`${currentProject?.id ?? ""}:${modelId ?? ""}`);
  const beginStructureLoad = useLatestOperation(`${currentProject?.id ?? ""}:${modelId ?? ""}`);
  const returnAssetIdValue = new URLSearchParams(window.location.search).get('returnAssetId');
  const returnAssetId = returnAssetIdValue && /^\d+$/.test(returnAssetIdValue)
    ? Number(returnAssetIdValue)
    : undefined;
  const [structure, setStructure] = useState<ModelingStructureRecord>();
  const [modelInfo, setModelInfo] = useState<ModelingModelRecord | null>(null);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [publishing, setPublishing] = useState(false);
  const [publishPreview, setPublishPreview] = useState<{
    before: unknown;
    after: unknown;
    publishedVersionNo: number | null;
  } | null>(null);
  // 发布审批(01):开关=MODEL_PUBLISH 流已配置且启用；在途单=审批中心反查(在途优先,否则最近一单)
  const [publishFlowEnabled, setPublishFlowEnabled] = useState(false);
  const [publishApproval, setPublishApproval] = useState<ApprovalInstance | null>(null);
  const [approvalSubmitting, setApprovalSubmitting] = useState(false);
  const [assistantRowKey, setAssistantRowKey] = useState<number | null>(null);
  const [typeCatalog, setTypeCatalog] = useState<ModelingTypeOption[]>([]);
  /** 同一份目录的引用版:供不便把 typeCatalog 加进依赖的回调（如 discoverStandards）读取。 */
  const typeCatalogRef = useRef<ModelingTypeOption[]>([]);
  const [validationIssues, setValidationIssues] = useState<ModelingValidationIssue[]>([]);
  const editorRootRef = useRef<HTMLDivElement>(null);
  const highlightTimerRef = useRef<ReturnType<typeof setTimeout>>();
  useEffect(() => () => { clearTimeout(highlightTimerRef.current); }, []);
  const [highlightRowKey, setHighlightRowKey] = useState<number>();
  const [draggingKeys, setDraggingKeys] = useState<ReadonlySet<number>>(new Set());
  const [ddlOpen, setDdlOpen] = useState(false);
  const [ddlLoading, setDdlLoading] = useState(false);
  const [ddlScript, setDdlScript] = useState('');
  const [ddlDialect, setDdlDialect] = useState('');
  // C3(2026-09-17)批量删除选择行
  const [selectedRowKeys, setSelectedRowKeys] = useState<ReactKey[]>([]);
  // 多选拖拽时 DragOverlay 中显示的行数据
  const [dragOverlayRows, setDragOverlayRows] = useState<ColumnDraft[]>([]);
  // C2(2026-09-17)"数据标准"列引用名称解析
  const [standardNameById, setStandardNameById] = useState<Record<number, string>>({});
  // C3(2026-09-17)批量添加
  const [bulkAddOpen, setBulkAddOpen] = useState(false);
  const [bulkAddCount, setBulkAddCount] = useState(5);
  // B9(2026-09-17)索引区默认折叠
  const [indexCollapsed, setIndexCollapsed] = useState(true);
  // 逆向导入配置(从 URL 读取后持久化,支持重新导入恢复源表字段)
  const [importConfig, setImportConfig] = useState<{ datasourceId: number; database: string; table: string } | null>(null);
  const [reimportLoading, setReimportLoading] = useState(false);
  const [discoverLoading, setDiscoverLoading] = useState(false);
  // 导入字段弹窗状态(用于空模型导入)
  const [importModalOpen, setImportModalOpen] = useState(false);
  const [importModalLoading, setImportModalLoading] = useState(false);
  const [datasources, setDatasources] = useState<DataSourceRecord[]>([]);
  const [importDatasourceId, setImportDatasourceId] = useState<number | undefined>();
  const [importTables, setImportTables] = useState<DataSourceCatalogTable[]>([]);
  const [importTablesLoading, setImportTablesLoading] = useState(false);
  const [importDatabase, setImportDatabase] = useState('');
  const [importTable, setImportTable] = useState<string | undefined>();
  // 从已有模型导入字段
  const [importFromModelOpen, setImportFromModelOpen] = useState(false);
  const [importFromModelLoading, setImportFromModelLoading] = useState(false);
  const [modelList, setModelList] = useState<ModelingModelRecord[]>([]);
  const [modelListLoading, setModelListLoading] = useState(false);
  const [selectedModelIds, setSelectedModelIds] = useState<number[]>([]);
  // 从业务过程导入字段
  const [importFromProcessOpen, setImportFromProcessOpen] = useState(false);
  const [importFromProcessLoading, setImportFromProcessLoading] = useState(false);
  const [processList, setProcessList] = useState<SemanticProcessRecord[]>([]);
  const [processListLoading, setProcessListLoading] = useState(false);
  const [selectedProcessId, setSelectedProcessId] = useState<number | undefined>();
  // DWS/ADS 按指标反推(60/61):选指标 → 反推草稿 → 并入表结构
  const [metricDraftOpen, setMetricDraftOpen] = useState(false);
  const [metricDraftLoading, setMetricDraftLoading] = useState(false);
  const [metricList, setMetricList] = useState<MetricRecord[]>([]);
  const [metricListLoading, setMetricListLoading] = useState(false);
  const [selectedMetricIds, setSelectedMetricIds] = useState<number[]>([]);
  // ADS 数据来源:DWS 汇总报表(默认) / DWD 明细实时报表
  const [adsSourceLayer, setAdsSourceLayer] = useState<'DWS' | 'DWD'>('DWS');
  // 字段名输入框的标准字段下拉候选(按关键字远端检索,仍允许自定义填写)
  const [standardFieldChoices, setStandardFieldChoices] = useState<SemanticFieldRecord[]>([]);
  const standardFieldByCodeRef = useRef(new Map<string, SemanticFieldRecord>());
  const standardFieldSearchTimerRef = useRef<ReturnType<typeof setTimeout>>();
  /** 该行标准引用来自哪个标准字段编码(仅下拉选中时记录),改名偏离后据此判废。 */
  const stdBoundCodeByRowRef = useRef(new Map<number, string>());

  const markDirty = () => setDirty(true);

  /** 标准发现核心逻辑:按字段名匹配标准字段集,返回匹配后的行和匹配数。 */
  const discoverStandards = useCallback(
    async (
      targetRows: ColumnDraft[],
    ): Promise<{ rows: ColumnDraft[]; matchCount: number }> => {
      const isCurrent = captureEditorResource();
      // 分页加载全部标准字段(后端限制 pageSize <= 200)
      const allFields: SemanticFieldRecord[] = [];
      let pageNo = 1;
      const pageSize = 200;
      let hasMore = true;
      while (hasMore) {
        const result = await pageSemanticFields({ pageNo, pageSize });
        if (!isCurrent()) return { rows: targetRows, matchCount: 0 };
        const batch = result?.bizData ?? [];
        allFields.push(...batch);
        hasMore = batch.length === pageSize;
        pageNo += 1;
      }
      if (!allFields.length) {
        return { rows: targetRows, matchCount: 0 };
      }
      // 按 code(小写)建立索引
      const fieldByCode = new Map<string, SemanticFieldRecord>();
      allFields.forEach((f) => {
        if (f.code) fieldByCode.set(f.code.toLowerCase(), f);
      });
      // 逐行匹配
      let matchCount = 0;
      const newRows = targetRows.map((row) => {
        const colName = row.columnName.trim().toLowerCase();
        const matched = colName ? fieldByCode.get(colName) : undefined;
        if (matched) {
          matchCount += 1;
          const derived = row.dataType
            ? null
            : resolveColumnType(matched.dataType, typeCatalogRef.current);
          return {
            ...row,
            stdFieldId: matched.id,
            stdTypeId: matched.stdTypeId ?? row.stdTypeId,
            stdUnitId: matched.stdUnitId ?? row.stdUnitId,
            stdCaliberId: matched.stdCaliberId ?? row.stdCaliberId,
            stdCodeSetCode: matched.stdCodeSetCode ?? row.stdCodeSetCode,
            stdSecurityId: matched.stdSecurityId ?? row.stdSecurityId,
            ...(derived ?? {}),
          };
        }
        return row;
      });
      return { rows: newRows, matchCount };
    },
    [captureEditorResource],
  );

  const loadStructure = useCallback(async (canApply: () => boolean = () => true) => {
    if (!modelId) return;
    const isCurrent = beginStructureLoad();
    setLoading(true);
    try {
      // 并行加载模型信息和结构信息
      const [modelData, data] = await Promise.all([
        getModelingModel(modelId).catch(() => null),
        getModelingStructure(modelId),
      ]);
      if (!isCurrent()) return;
      if (!canApply()) return;
      setStructure(data);
      setModelInfo(modelData);
      // B4(2026-09-17):物理表名默认为模型编码
      setTableName(data.tableName || data.modelCode || '');
      setTableComment(data.tableComment || '');
      setRows(
        (data.columns || []).map((column) => ({
          key: nextDraftKey(),
          columnName: column.columnName || '',
          dataType: column.dataType || '',
          length: column.length ?? null,
          scale: column.scale ?? null,
          // C6(2026-09-17):数仓字段默认不可空
          nullable: column.nullable ?? false,
          defaultValue: column.defaultValue || '',
          comment: column.comment || '',
          businessDescription: column.businessDescription || '',
          stdTypeId: column.stdTypeId ?? null,
          stdNamingId: column.stdNamingId ?? null,
          stdCodeSetCode: column.stdCodeSetCode ?? null,
          stdUnitId: column.stdUnitId ?? null,
          stdCaliberId: column.stdCaliberId ?? null,
          stdSecurityId: column.stdSecurityId ?? null,
          stdFieldId: column.stdFieldId ?? null,
          fieldRole: column.fieldRole ?? null,
          aggregateFunc: column.aggregateFunc ?? null,
          transformExpr: column.transformExpr ?? null,
        })),
      );
      setPrimaryKey(data.primaryKey || []);
      setIndexes(
        (data.indexes || []).map((index) => ({
          key: nextDraftKey(),
          indexName: index.indexName || '',
          uniqueIndex: index.uniqueIndex ?? false,
          indexType: index.indexType || '',
          columns: index.columns || [],
        })),
      );
      const hasPartition = Boolean(
        data.partition?.type || data.partition?.columns?.length || data.partition?.expression,
      );
      setPartitionEnabled(hasPartition);
      setPartitionType(data.partition?.type || undefined);
      setPartitionColumns(data.partition?.columns || []);
      setPartitionExpression(data.partition?.expression || '');
      setProperties(
        Object.entries(data.tableProperties || {}).map(([propKey, propValue]) => ({
          key: nextDraftKey(),
          propKey,
          propValue: propValue || '',
        })),
      );
      setDirty(false);
      setValidationIssues([]);
      // 2026-09-17:逆向导入初始化——从 URL query 读取源表信息并预览填充字段
      const search = new URLSearchParams(window.location.search);
      let autoDiscoverTriggered = false;
      if (search.get('initMode') === 'import') {
        const dsId = Number(search.get('datasourceId'));
        const db = search.get('database') || '';
        const tbl = search.get('table') || '';
        if (dsId && tbl) {
          setImportConfig({ datasourceId: dsId, database: db, table: tbl });
          try {
            const columns = await previewModelingImportColumns(dsId, db, tbl);
            if (!isCurrent()) return;
            if (columns?.length) {
              // 构建初始字段列表
              const initialRows: ColumnDraft[] = columns.map((column) => ({
                key: nextDraftKey(),
                columnName: column.name || '',
                dataType: column.typeName || '',
                length: column.size ?? null,
                scale: column.decimalDigits ?? null,
                nullable: column.nullable ?? false,
                defaultValue: column.defaultValue || '',
                comment: column.remarks || '',
                businessDescription: '',
                stdTypeId: null,
                stdNamingId: null,
                stdCodeSetCode: null,
                stdUnitId: null,
                stdCaliberId: null,
                stdSecurityId: null,
                stdFieldId: null,
              }));
              // 自动填充主键和表注释
              const pkCols = columns.filter((c) => c.primaryKey).map((c) => c.name.trim()).filter(Boolean);
              if (pkCols.length) setPrimaryKey(pkCols);
              const tableRemarks = columns[0]?.tableRemarks;
              if (tableRemarks) setTableComment(tableRemarks);
              // 自动触发标准发现
              try {
                const discovered = await discoverStandards(initialRows);
                if (!isCurrent()) return;
                setRows(discovered.rows);
                if (discovered.matchCount > 0) {
                  message.success(`标准发现:匹配到 ${discovered.matchCount} 个字段的标准绑定`);
                }
              } catch {
                // 标准发现失败不影响字段导入,降级为空
                if (!isCurrent()) return;
                setRows(initialRows);
              }
              setDirty(true);
              autoDiscoverTriggered = true;
            }
          } catch (err) {
            const errorMsg = err instanceof Error ? err.message : '未知错误';
            console.error('源表字段预览失败:', { dsId, db, tbl, error: errorMsg });
            message.error(`源表字段预览失败(${errorMsg})，请手动添加字段`);
          }
        }
        // 消费后清理 URL query，避免刷新重复填充
        window.history.replaceState({}, '', window.location.pathname);
      }
      // 如果模型无字段且 URL 没有导入参数，尝试从模型的来源信息读取
      if (!data.columns?.length && search.get('initMode') !== 'import' && modelData) {
        const { sourceDatasourceId, sourceDatabase, sourceTable } = modelData;
        if (sourceDatasourceId && sourceTable) {
          const config = { datasourceId: sourceDatasourceId, database: sourceDatabase || '', table: sourceTable };
          setImportConfig(config);
          try {
            const columns = await previewModelingImportColumns(config.datasourceId, config.database, config.table);
            if (!isCurrent()) return;
            if (columns?.length) {
              const initialRows: ColumnDraft[] = columns.map((column) => ({
                key: nextDraftKey(),
                columnName: column.name || '',
                dataType: column.typeName || '',
                length: column.size ?? null,
                scale: column.decimalDigits ?? null,
                nullable: column.nullable ?? false,
                defaultValue: column.defaultValue || '',
                comment: column.remarks || '',
                businessDescription: '',
                stdTypeId: null,
                stdNamingId: null,
                stdCodeSetCode: null,
                stdUnitId: null,
                stdCaliberId: null,
                stdSecurityId: null,
                stdFieldId: null,
              }));
              const pkCols = columns.filter((c) => c.primaryKey).map((c) => c.name.trim()).filter(Boolean);
              if (pkCols.length) setPrimaryKey(pkCols);
              const tableRemarks = columns[0]?.tableRemarks;
              if (tableRemarks) setTableComment(tableRemarks);
              try {
                const discovered = await discoverStandards(initialRows);
                if (!isCurrent()) return;
                setRows(discovered.rows);
                if (discovered.matchCount > 0) {
                  message.success(`自动导入:匹配到 ${discovered.matchCount} 个字段的标准绑定`);
                }
              } catch {
                if (!isCurrent()) return;
                setRows(initialRows);
              }
              setDirty(true);
              autoDiscoverTriggered = true;
            }
          } catch {
            // 预览失败不影响页面加载
          }
        }
      }
      // 如果已有字段但未走过标准发现流程,自动触发一次
      if (!autoDiscoverTriggered && data.columns?.length) {
        const hasStdBinding = data.columns.some((c) => c.stdFieldId != null);
        if (!hasStdBinding) {
          try {
            const currentRows = data.columns.map((column) => ({
              key: nextDraftKey(),
              columnName: column.columnName || '',
              dataType: column.dataType || '',
              length: column.length ?? null,
              scale: column.scale ?? null,
              nullable: column.nullable ?? false,
              defaultValue: column.defaultValue || '',
              comment: column.comment || '',
              businessDescription: column.businessDescription || '',
              stdTypeId: column.stdTypeId ?? null,
              stdNamingId: column.stdNamingId ?? null,
              stdCodeSetCode: column.stdCodeSetCode ?? null,
              stdUnitId: column.stdUnitId ?? null,
              stdCaliberId: column.stdCaliberId ?? null,
              stdSecurityId: column.stdSecurityId ?? null,
              stdFieldId: column.stdFieldId ?? null,
            }));
            const discovered = await discoverStandards(currentRows);
            if (discovered.matchCount > 0) {
              if (!isCurrent()) return;
              setRows(discovered.rows);
              setDirty(true);
              message.success(`标准发现:匹配到 ${discovered.matchCount} 个字段的标准绑定`);
            }
          } catch {
            // 标准发现失败不影响页面加载
          }
        }
      }
    } catch (error) {
      if (!isCurrent()) return;
      message.error(error instanceof Error ? error.message : '表结构加载失败');
    } finally {
      if (isCurrent()) setLoading(false);
    }
  }, [modelId, currentProject?.id, discoverStandards, beginStructureLoad]);

  /** 重新导入:用保存的 importConfig 重新拉取源表字段并覆盖当前字段列表。 */
  const handleReimport = useCallback(async () => {
    const isCurrent = captureEditorResource();
    if (!isCurrent()) return;
    if (!importConfig) return;
    const { datasourceId, database, table } = importConfig;
    setReimportLoading(true);
    try {
      const columns = await previewModelingImportColumns(datasourceId, database, table);
      if (!isCurrent()) return;
      if (columns?.length) {
        setRows(
          columns.map((column) => ({
            key: nextDraftKey(),
            columnName: column.name || '',
            dataType: column.typeName || '',
            length: column.size ?? null,
            scale: column.decimalDigits ?? null,
            nullable: column.nullable ?? false,
            defaultValue: column.defaultValue || '',
            comment: column.remarks || '',
            businessDescription: '',
            stdTypeId: null,
            stdNamingId: null,
            stdCodeSetCode: null,
            stdUnitId: null,
            stdCaliberId: null,
            stdSecurityId: null,
            stdFieldId: null,
          })),
        );
        const pkCols = columns.filter((c) => c.primaryKey).map((c) => c.name.trim()).filter(Boolean);
        if (pkCols.length) setPrimaryKey(pkCols);
        const tableRemarks = columns[0]?.tableRemarks;
        if (tableRemarks) setTableComment(tableRemarks);
        setDirty(true);
        message.success(`已从源表重新导入 ${columns.length} 个字段`);
      } else {
        message.warning('源表无字段信息');
      }
    } catch (err) {
      if (!isCurrent()) return;
      const errorMsg = err instanceof Error ? err.message : '未知错误';
      message.error(`重新导入失败(${errorMsg})`);
    } finally {
      if (!isCurrent()) return;
      setReimportLoading(false);
    }
  }, [importConfig, captureEditorResource]);

  /** 标准发现:按字段名匹配标准字段集,匹配到的自动回填标准绑定。 */
  const handleStandardDiscover = useCallback(async () => {
    const isCurrent = captureEditorResource();
    if (!isCurrent()) return;
    if (!rows.length) {
      message.warning('当前无字段可匹配');
      return;
    }
    const draftUnchanged = captureDraft();
    setDiscoverLoading(true);
    try {
      const result = await discoverStandards(rows);
      if (!isCurrent()) return;
      if (!draftUnchanged()) {
        message.info('草稿已修改，请重新执行标准发现');
        return;
      }
      if (!result) return;
      if (result.rows.length && !result.matchCount) {
        message.info('暂无标准字段集,请先在数据标准中维护');
        return;
      }
      setRows(result.rows);
      if (result.matchCount > 0) {
        markDirty();
        message.success(`标准发现:匹配到 ${result.matchCount} 个字段的标准绑定`);
      } else {
        message.info('未匹配到标准字段,请检查字段名是否与标准字段编码一致');
      }
    } catch (err) {
      if (!isCurrent()) return;
      const errorMsg = err instanceof Error ? err.message : '未知错误';
      message.error(`标准发现失败(${errorMsg})`);
    } finally {
      if (!isCurrent()) return;
      setDiscoverLoading(false);
    }
  }, [rows, discoverStandards, captureEditorResource, captureDraft]);

  /** 从 jdbcUrl 解析数据库名。 */
  const extractDatabaseFromUrl = useCallback((url?: string): string => {
    if (!url) return '';
    const match1 = url.match(/jdbc:\w+:\/\/[^/]+\/([^?;]+)/);
    if (match1) return match1[1];
    const match2 = url.match(/jdbc:oracle:thin:[^:]*@[^:]+:\d+:(\w+)/);
    if (match2) return match2[1];
    const match3 = url.match(/jdbc:oracle:thin:@\/\/[^:]+:\d+\/(\w+)/);
    if (match3) return match3[1];
    const match4 = url.match(/databaseName=([^;]+)/i);
    if (match4) return match4[1];
    return '';
  }, []);

  /** 打开导入字段弹窗。 */
  const openImportModal = useCallback(async () => {
    const isCurrent = captureEditorResource();
    if (!isCurrent()) return;
    setImportModalOpen(true);
    setImportDatasourceId(undefined);
    setImportTables([]);
    setImportTable(undefined);
    setImportDatabase('');
    try {
      const result = await listAllDataSources();
      if (!isCurrent()) return;
      setDatasources(result.bizData ?? []);
    } catch {
      if (!isCurrent()) return;
      setDatasources([]);
    }
  }, [captureEditorResource]);

  /** 加载数据源对应的表列表。 */
  const loadImportTables = useCallback(
    async (datasourceId: number) => {
      const resourceCurrent = captureEditorResource();
      const tableRequestCurrent = beginImportTableLoad();
      const isCurrent = () => resourceCurrent() && tableRequestCurrent();
      if (!isCurrent()) return;
      if (!datasourceId) {
        setImportTables([]);
        setImportDatabase('');
        return;
      }
      const ds = datasources.find((d) => d.id === datasourceId);
      const db = extractDatabaseFromUrl(ds?.jdbcUrl);
      setImportDatabase(db);
      setImportTablesLoading(true);
      try {
        const result = await listDataSourceTables(datasourceId, db);
        if (!isCurrent()) return;
        setImportTables(result ?? []);
      } catch {
        if (!isCurrent()) return;
        setImportTables([]);
      } finally {
        if (!isCurrent()) return;
        setImportTablesLoading(false);
      }
    },
    [datasources, extractDatabaseFromUrl, captureEditorResource, beginImportTableLoad],
  );

  /** 确认导入:从源表加载字段并自动标准发现。 */
  const handleImportConfirm = useCallback(async () => {
    const isCurrent = captureEditorResource();
    if (!isCurrent()) return;
    if (!importDatasourceId || !importTable) return;
    setImportModalLoading(true);
    try {
      const columns = await previewModelingImportColumns(importDatasourceId, importDatabase, importTable);
      if (!isCurrent()) return;
      if (columns?.length) {
        const initialRows: ColumnDraft[] = columns.map((column) => ({
          key: nextDraftKey(),
          columnName: column.name || '',
          dataType: column.typeName || '',
          length: column.size ?? null,
          scale: column.decimalDigits ?? null,
          nullable: column.nullable ?? false,
          defaultValue: column.defaultValue || '',
          comment: column.remarks || '',
          businessDescription: '',
          stdTypeId: null,
          stdNamingId: null,
          stdCodeSetCode: null,
          stdUnitId: null,
          stdCaliberId: null,
          stdSecurityId: null,
          stdFieldId: null,
        }));
        const pkCols = columns.filter((c) => c.primaryKey).map((c) => c.name.trim()).filter(Boolean);
        if (pkCols.length) setPrimaryKey(pkCols);
        const tableRemarks = columns[0]?.tableRemarks;
        if (tableRemarks) setTableComment(tableRemarks);
        // 自动标准发现
        try {
          const discovered = await discoverStandards(initialRows);
          if (!isCurrent()) return;
          setRows(discovered.rows);
          if (discovered.matchCount > 0) {
            message.success(`导入成功，标准发现匹配到 ${discovered.matchCount} 个字段`);
          } else {
            message.success(`导入成功，共 ${columns.length} 个字段`);
          }
        } catch {
          if (!isCurrent()) return;
          setRows(initialRows);
          message.success(`导入成功，共 ${columns.length} 个字段`);
        }
        // 保存导入配置以支持重新导入
        setImportConfig({ datasourceId: importDatasourceId, database: importDatabase, table: importTable });
        setDirty(true);
        setImportModalOpen(false);
        // 血缘追溯:记录导入方式
        if (modelId) {
          assignImportLineage(Number(modelId), 'SOURCE_TABLE').catch(() => undefined);
        }
      } else {
        message.warning('源表无字段信息');
      }
    } catch (err) {
      if (!isCurrent()) return;
      const errorMsg = err instanceof Error ? err.message : '未知错误';
      message.error(`导入失败(${errorMsg})`);
    } finally {
      if (!isCurrent()) return;
      setImportModalLoading(false);
    }
  }, [importDatasourceId, importTable, importDatabase, discoverStandards, captureEditorResource]);

  /** 打开从模型导入字段弹窗。 */
  const handleOpenImportFromModel = useCallback(async () => {
    const isCurrent = captureEditorResource();
    if (!isCurrent()) return;
    setImportFromModelOpen(true);
    setSelectedModelIds([]);
    setModelListLoading(true);
    try {
      const result = await pageModelingModels({ pageNo: 1, pageSize: 200 });
      if (!isCurrent()) return;
      const list = result.bizData ?? [];
      // 排除当前模型
      const currentId = modelId ? Number(modelId) : undefined;
      setModelList(list.filter((m) => m.id !== currentId));
    } catch {
      if (!isCurrent()) return;
      setModelList([]);
    } finally {
      if (!isCurrent()) return;
      setModelListLoading(false);
    }
  }, [modelId, captureEditorResource]);

  /** 确认从模型导入字段:合并选中模型的字段到当前模型。 */
  const handleImportFromModel = useCallback(async () => {
    const isCurrent = captureEditorResource();
    if (!isCurrent()) return;
    if (!selectedModelIds.length) {
      message.warning('请选择至少一个模型');
      return;
    }
    setImportFromModelLoading(true);
    try {
      const existingNames = new Set(rows.map((r) => r.columnName.trim().toLowerCase()));
      const mergedPk = new Set(primaryKey);
      let importedCount = 0;
      const newRows: ColumnDraft[] = [];
      for (const modelId of selectedModelIds) {
        const structure = await getModelingStructure(modelId);
        if (!isCurrent()) return;
        const cols = structure.columns ?? [];
        for (const col of cols) {
          const colName = col.columnName?.trim() ?? '';
          if (!colName) continue;
          const lowerName = colName.toLowerCase();
          // 同名字段跳过(以第一个导入的为准)
          if (existingNames.has(lowerName)) continue;
          existingNames.add(lowerName);
          newRows.push({
            key: nextDraftKey(),
            columnName: colName,
            dataType: col.dataType || '',
            length: col.length ?? null,
            scale: col.scale ?? null,
            nullable: col.nullable ?? false,
            defaultValue: col.defaultValue || '',
            comment: col.comment || '',
            businessDescription: col.businessDescription || '',
            stdTypeId: col.stdTypeId ?? null,
            stdNamingId: col.stdNamingId ?? null,
            stdCodeSetCode: col.stdCodeSetCode ?? null,
            stdUnitId: col.stdUnitId ?? null,
            stdCaliberId: col.stdCaliberId ?? null,
            stdSecurityId: col.stdSecurityId ?? null,
            stdFieldId: col.stdFieldId ?? null,
          });
          importedCount++;
          // 主键继承
          if (structure.primaryKey?.some((pk) => pk.trim().toLowerCase() === lowerName)) {
            mergedPk.add(colName);
          }
        }
      }
      if (!importedCount) {
        message.info('所选模型无可导入的新字段(已存在同名字段)');
        return;
      }
      // 自动标准发现
      let finalRows = newRows;
      try {
        const discovered = await discoverStandards(newRows);
        if (!isCurrent()) return;
        finalRows = discovered.rows;
        if (discovered.matchCount > 0) {
          message.success(`已从模型导入 ${importedCount} 个字段，标准发现匹配到 ${discovered.matchCount} 个`);
        } else {
          message.success(`已从 ${selectedModelIds.length} 个模型导入 ${importedCount} 个字段`);
        }
      } catch {
        if (!isCurrent()) return;
        message.success(`已从 ${selectedModelIds.length} 个模型导入 ${importedCount} 个字段`);
      }
      setRows((prev) => [...prev, ...finalRows]);
      setPrimaryKey(Array.from(mergedPk));
      setDirty(true);
      setImportFromModelOpen(false);
      // 血缘追溯:记录导入方式和来源模型
      if (modelId) {
        const firstSourceId = selectedModelIds.length === 1 ? Number(selectedModelIds[0]) : null;
        assignImportLineage(Number(modelId), 'MODEL', firstSourceId).catch(() => undefined);
      }
    } catch (err) {
      if (!isCurrent()) return;
      const errorMsg = err instanceof Error ? err.message : '未知错误';
      message.error(`从模型导入失败(${errorMsg})`);
    } finally {
      if (!isCurrent()) return;
      setImportFromModelLoading(false);
    }
  }, [selectedModelIds, rows, primaryKey, discoverStandards, captureEditorResource]);

  /** 打开从业务过程导入字段弹窗。 */
  const handleOpenImportFromProcess = useCallback(async () => {
    const isCurrent = captureEditorResource();
    if (!isCurrent()) return;
    setImportFromProcessOpen(true);
    setSelectedProcessId(undefined);
    setProcessListLoading(true);
    try {
      const result = await pageSemanticProcesses({ pageNo: 1, pageSize: 200 });
      if (!isCurrent()) return;
      setProcessList(result.bizData ?? []);
    } catch {
      if (!isCurrent()) return;
      setProcessList([]);
    } finally {
      if (!isCurrent()) return;
      setProcessListLoading(false);
    }
  }, [captureEditorResource]);

  /** 确认从业务过程导入字段。 */
  const handleImportFromProcess = useCallback(async () => {
    const isCurrent = captureEditorResource();
    if (!isCurrent()) return;
    if (!selectedProcessId) {
      message.warning('请选择业务过程');
      return;
    }
    const layerCode = modelInfo?.layerCode;
    if (!layerCode) {
      message.warning('当前模型未设置分层，无法从业务过程导入');
      return;
    }
    setImportFromProcessLoading(true);
    try {
      const preview = await previewModelingDerive(
        selectedProcessId,
        layerCode,
        structure?.dialect,
      );
      if (!isCurrent()) return;
      if (!preview.supported) {
        message.warning(preview.unsupportedReason || '该分层暂不支持从业务过程导入');
        return;
      }
      const fields = preview.fields ?? [];
      if (!fields.length) {
        message.info('该业务过程无可导入的字段');
        return;
      }
      const existingNames = new Set(rows.map((r) => r.columnName.trim().toLowerCase()));
      let importedCount = 0;
      const newRows: ColumnDraft[] = [];
      for (const field of fields) {
        const colName = field.landingField?.trim() ?? '';
        if (!colName) continue;
        const lowerName = colName.toLowerCase();
        if (existingNames.has(lowerName)) continue;
        existingNames.add(lowerName);
        newRows.push({
          key: nextDraftKey(),
          columnName: colName,
          dataType: field.dataType || '',
          length: field.length ?? null,
          scale: field.scale ?? null,
          nullable: field.nullable ?? false,
          defaultValue: '',
          comment: field.comment || '',
          businessDescription: '',
          stdTypeId: null,
          stdNamingId: null,
          stdCodeSetCode: null,
          stdUnitId: null,
          stdCaliberId: null,
          stdSecurityId: null,
          stdFieldId: field.stdFieldId ?? null,
        });
        importedCount++;
      }
      if (!importedCount) {
        message.info('无可导入的新字段(已存在同名字段)');
        return;
      }
      // 自动标准发现
      let finalProcessRows = newRows;
      try {
        const discovered = await discoverStandards(newRows);
        if (!isCurrent()) return;
        finalProcessRows = discovered.rows;
        if (discovered.matchCount > 0) {
          message.success(`已从业务过程导入 ${importedCount} 个字段，标准发现匹配到 ${discovered.matchCount} 个`);
        } else {
          message.success(`已从业务过程导入 ${importedCount} 个字段`);
        }
      } catch {
        if (!isCurrent()) return;
        message.success(`已从业务过程导入 ${importedCount} 个字段`);
      }
      setRows((prev) => [...prev, ...finalProcessRows]);
      setDirty(true);
      setImportFromProcessOpen(false);
      // 血缘追溯:记录导入方式
      if (modelId) {
        assignImportLineage(Number(modelId), 'BUSINESS_PROCESS').catch(() => undefined);
      }
    } catch (err) {
      if (!isCurrent()) return;
      const errorMsg = err instanceof Error ? err.message : '未知错误';
      message.error(`从业务过程导入失败(${errorMsg})`);
    } finally {
      if (!isCurrent()) return;
      setImportFromProcessLoading(false);
    }
  }, [selectedProcessId, modelInfo, structure?.dialect, rows, discoverStandards, captureEditorResource]);

  /** 打开"按指标反推"弹窗(仅 DWS/ADS):加载启用指标供选择。 */
  const handleOpenMetricDraft = useCallback(async () => {
    const isCurrent = captureEditorResource();
    if (!isCurrent()) return;
    const layerCode = modelInfo?.layerCode;
    if (!layerCode || !AGGREGATE_LAYERS.has(layerCode)) {
      message.warning('仅 DWS/ADS 分层支持按指标反推');
      return;
    }
    setMetricDraftOpen(true);
    setSelectedMetricIds([]);
    setAdsSourceLayer('DWS');
    setMetricListLoading(true);
    try {
      const result = await pageMetrics({ pageNo: 1, pageSize: 200, status: 'ENABLED' });
      if (!isCurrent()) return;
      setMetricList(result.records ?? []);
    } catch {
      if (!isCurrent()) return;
      setMetricList([]);
    } finally {
      if (!isCurrent()) return;
      setMetricListLoading(false);
    }
  }, [modelInfo, captureEditorResource]);

  /** 确认按指标反推:指标 → 反推草稿 → 派生预览 → 并入表结构(携带角色/聚合函数)。 */
  const handleApplyMetricDraft = useCallback(async () => {
    const isCurrent = captureEditorResource();
    if (!isCurrent()) return;
    if (!selectedMetricIds.length) {
      message.warning('请至少选择一个指标');
      return;
    }
    const layerCode = modelInfo?.layerCode;
    if (!layerCode || !AGGREGATE_LAYERS.has(layerCode)) {
      message.warning('当前模型未设置在 DWS/ADS 分层，无法按指标反推');
      return;
    }
    // ADS 数据来源覆盖(61);DWS 无此概念,不传。
    const upstreamLayer = layerCode === 'ADS' ? adsSourceLayer : undefined;
    setMetricDraftLoading(true);
    try {
      const draft = await getModelingMetricDraft(selectedMetricIds, structure?.dialect, upstreamLayer);
      if (!isCurrent()) return;
      const processId = draft.processIds?.[0];
      if (!processId) {
        message.warning('所选指标未关联业务过程，无法反推');
        return;
      }
      if (draft.processIds.length > 1) {
        message.info('所选指标跨多个业务过程,已按首个过程反推,可再次选择补充');
      }
      const preview = await previewModelingDerive(
        processId,
        layerCode,
        structure?.dialect,
        undefined,
        draft.upstreamModelIds?.length ? draft.upstreamModelIds : undefined,
        draft.statPeriod || undefined,
        undefined,
        undefined,
        upstreamLayer,
      );
      if (!isCurrent()) return;
      if (!preview.supported) {
        message.warning(preview.unsupportedReason || '该分层暂不支持按指标反推');
        return;
      }
      const fields = preview.fields ?? [];
      const existingNames = new Set(rows.map((r) => r.columnName.trim().toLowerCase()));
      let importedCount = 0;
      const newRows: ColumnDraft[] = [];
      for (const field of fields) {
        if (field.include === false) continue;
        const colName = field.landingField?.trim() ?? '';
        if (!colName) continue;
        const lowerName = colName.toLowerCase();
        if (existingNames.has(lowerName)) continue;
        existingNames.add(lowerName);
        newRows.push({
          key: nextDraftKey(),
          columnName: colName,
          dataType: field.dataType || '',
          length: field.length ?? null,
          scale: field.scale ?? null,
          nullable: field.nullable ?? false,
          defaultValue: '',
          comment: field.comment || '',
          businessDescription: '',
          stdTypeId: null,
          stdNamingId: null,
          stdCodeSetCode: null,
          stdUnitId: null,
          stdCaliberId: null,
          stdSecurityId: null,
          stdFieldId: field.stdFieldId ?? null,
          fieldRole: field.fieldRole ?? null,
          aggregateFunc: field.aggregateFunc ?? null,
          transformExpr: null,
        });
        importedCount++;
      }
      if (!importedCount) {
        message.info('无可并入的新字段(同名字段已存在或指标未产出字段)');
        return;
      }
      setRows((prev) => [...prev, ...newRows]);
      setDirty(true);
      setMetricDraftOpen(false);
      const warnings = [...(draft.warnings ?? []), ...(preview.warnings ?? [])];
      if (warnings.length) {
        message.warning(`已并入 ${importedCount} 个字段;提示：${warnings[0]}`);
      } else {
        message.success(`已按指标并入 ${importedCount} 个字段,保存后落库`);
      }
      if (modelId) {
        assignImportLineage(Number(modelId), 'BUSINESS_PROCESS').catch(() => undefined);
      }
    } catch (err) {
      if (!isCurrent()) return;
      const errorMsg = err instanceof Error ? err.message : '未知错误';
      message.error(`按指标反推失败(${errorMsg})`);
    } finally {
      if (!isCurrent()) return;
      setMetricDraftLoading(false);
    }
  }, [selectedMetricIds, adsSourceLayer, modelInfo, structure?.dialect, rows, modelId, captureEditorResource]);

  useEffect(() => {
    resetDraft();
    setModelInfo(null);
    setStructure(undefined);
    setImportConfig(null);
    setDatasources([]);
    setImportTables([]);
    setModelList([]);
    setProcessList([]);
    setMetricList([]);
    setImportDatasourceId(undefined);
    setImportTable(undefined);
    setImportDatabase('');
    setSelectedModelIds([]);
    setSelectedProcessId(undefined);
    setSelectedMetricIds([]);
    setSelectedRowKeys([]);
    setAssistantRowKey(null);
    setValidationIssues([]);
    setPublishPreview(null);
    setPublishApproval(null);
    setPublishFlowEnabled(false);
    setSaving(false);
    setPublishing(false);
    setApprovalSubmitting(false);
    setReimportLoading(false);
    setDiscoverLoading(false);
    setImportModalLoading(false);
    setImportTablesLoading(false);
    setImportFromModelLoading(false);
    setModelListLoading(false);
    setImportFromProcessLoading(false);
    setProcessListLoading(false);
    setMetricDraftLoading(false);
    setMetricListLoading(false);
    setImportModalOpen(false);
    setImportFromModelOpen(false);
    setImportFromProcessOpen(false);
    setMetricDraftOpen(false);
    setDdlOpen(false);
    setDdlLoading(false);
    setDdlScript("");
    setStandardFieldChoices([]);
    setStandardNameById({});
    standardFieldByCodeRef.current.clear();
    stdBoundCodeByRowRef.current.clear();
    clearTimeout(standardFieldSearchTimerRef.current);
    return () => clearTimeout(standardFieldSearchTimerRef.current);
  }, [currentProject?.id, modelId, resetDraft]);

  useEffect(() => {
    void loadStructure();
  }, [loadStructure]);

  // 发布审批开关(01):MODEL_PUBLISH 流未配置/未启用或无审批读权限时按关闭处理，维持现状直发
  useEffect(() => {
    const isCurrent = captureEditorResource();
    let alive = true;
    listFlows(MODEL_PUBLISH_FLOW_CODE)
      .then((result) => {
        if (!isCurrent()) return;
        if (alive) {
          setPublishFlowEnabled(
            (result.bizData ?? []).some(
              (flow) => flow.flowCode === MODEL_PUBLISH_FLOW_CODE && flow.enabled,
            ),
          );
        }
      })
      .catch(() => {
        if (!isCurrent()) return;
        /* 审批中心不可用/无 data-approval:read：保持开关关闭 */
      });
    return () => {
      alive = false;
    };
  }, [captureEditorResource]);

  const refreshPublishApproval = useCallback(async () => {
    const isCurrent = captureEditorResource();
    if (!isCurrent()) return;
    if (!modelId) return;
    try {
      const latest = (await findByBiz(MODEL_PUBLISH_FLOW_CODE, MODEL_PUBLISH_BIZ_TYPE, modelId)) ?? null;
      if (!isCurrent()) return;
      setPublishApproval(latest);
    } catch {
      if (!isCurrent()) return;
      /* 查询失败不阻塞业务页 */
    }
  }, [modelId, captureEditorResource]);

  useEffect(() => {
    void refreshPublishApproval();
  }, [refreshPublishApproval]);

  // 在途单低频轮询终态：批准回调与发布同事务，翻到 APPROVED 即刷新模型状态；驳回给出提示
  useEffect(() => {
    if (!modelId || publishApproval?.status !== 'PENDING') return undefined;
    const pendingId = publishApproval.id;
    const timer = setInterval(async () => {
      const isCurrent = captureEditorResource();
      if (!isCurrent()) return;
      let latest: ApprovalInstance | null = null;
      try {
        latest = (await findByBiz(MODEL_PUBLISH_FLOW_CODE, MODEL_PUBLISH_BIZ_TYPE, modelId)) ?? null;
        if (!isCurrent()) return;
      } catch {
        if (!isCurrent()) return;
        return; // 轮询失败等下一轮
      }
      if (!latest || latest.status === 'PENDING') return;
      setPublishApproval(latest);
      if (latest.id !== pendingId) return;
      if (latest.status === 'APPROVED') {
        message.info('发布审批已通过，已自动发布为新版本');
        const info = await getModelingModel(modelId).catch(() => null);
        if (!isCurrent()) return;
        if (info) setModelInfo(info);
        window.dispatchEvent(new CustomEvent(MODELING_PUBLISHED_EVENT, { detail: { modelId } }));
      } else if (latest.status === 'REJECTED') {
        message.warning('发布审批被驳回，版本未发布');
      }
    }, PUBLISH_APPROVAL_POLL_MS);
    return () => clearInterval(timer);
  }, [modelId, publishApproval?.id, publishApproval?.status, captureEditorResource]);

  // 类型下拉与后端方言目录同源（ticket 07）
  useEffect(() => {
    const isCurrent = captureEditorResource();
    if (!modelId || !structure?.dialect) {
      typeCatalogRef.current = [];
      setTypeCatalog([]);
      return;
    }
    getModelingTypeCatalog(modelId)
      .then((catalog) => {
        if (!isCurrent()) return;
        typeCatalogRef.current = catalog || [];
        setTypeCatalog(typeCatalogRef.current);
      })
      .catch(() => {
        if (!isCurrent()) return;
        typeCatalogRef.current = [];
        setTypeCatalog([]);
      });
  }, [modelId, structure?.dialect, captureEditorResource]);

  // C2(2026-09-17):"数据标准"列把类型标准 ID 解析为"名称（编码）";失败降级为空
  useEffect(() => {
    const isCurrent = captureEditorResource();
    if (!modelId) {
      return;
    }
    getStandardOptions(['TYPE', 'UNIT', 'CALIBER', 'SECURITY'])
      .then((standards) => {
        if (!isCurrent()) return;
        const nameById: Record<number, string> = {};
        Object.values(standards ?? {}).forEach((list) =>
          (list ?? []).forEach((item) => {
            nameById[item.id] = `${item.name}（${item.code}）`;
          }),
        );
        setStandardNameById(nameById);
      })
      .catch(() => undefined);
  }, [modelId, captureEditorResource]);

  const updateRow = (key: number, patch: Partial<ColumnDraft>) => {
    markDirty();
    setRows((previous) => previous.map((row) => (row.key === key ? { ...row, ...patch } : row)));
  };

  /** 字段名下拉检索:按关键字远端查标准字段集,空关键字不展示候选,自定义填写不受影响。 */
  const searchStandardFields = (keyword: string) => {
    const isCurrent = beginStandardSearch();
    const text = keyword.trim();
    clearTimeout(standardFieldSearchTimerRef.current);
    if (!text) {
      setStandardFieldChoices([]);
      return;
    }
    standardFieldSearchTimerRef.current = setTimeout(() => {
      if (!isCurrent()) return;
      void pageSemanticFields({ pageNo: 1, pageSize: 30, keyword: text })
        .then((result) => {
          if (!isCurrent()) return;
          const batch = result?.bizData ?? [];
          batch.forEach((field) => {
            if (field.code) standardFieldByCodeRef.current.set(field.code.toLowerCase(), field);
          });
          setStandardFieldChoices(batch);
        })
        .catch(() => { if (isCurrent()) setStandardFieldChoices([]); });
    }, 260);
  };

  /** 选中标准字段:回填字段名并带出该字段的标准引用,与「标准发现」保持一致。 */
  const bindStandardField = (row: ColumnDraft, code: string) => {
    clearTimeout(standardFieldSearchTimerRef.current);
    beginStandardSearch();
    const field = standardFieldByCodeRef.current.get(code.trim().toLowerCase());
    if (!field) {
      updateRow(row.key, { columnName: code });
      return;
    }
    stdBoundCodeByRowRef.current.set(row.key, field.code);
    // 类型只在空白时带出:源表导入/手填过的类型优先级更高,不覆盖
    const derived = row.dataType ? null : resolveColumnType(field.dataType, typeCatalog);
    updateRow(row.key, {
      columnName: field.code,
      stdFieldId: field.id,
      stdTypeId: field.stdTypeId ?? row.stdTypeId,
      stdUnitId: field.stdUnitId ?? row.stdUnitId,
      stdCaliberId: field.stdCaliberId ?? row.stdCaliberId,
      stdCodeSetCode: field.stdCodeSetCode ?? row.stdCodeSetCode,
      stdSecurityId: field.stdSecurityId ?? row.stdSecurityId,
      ...(derived ?? {}),
    });
    setStandardFieldChoices([]);
  };

  /** 手改字段名:名字已不等于下拉选中的标准编码时，那套标准引用一并作废(否则「数据标准」列会显示别人的类型)。 */
  const renameStandardField = (row: ColumnDraft, next: string) => {
    const bound = stdBoundCodeByRowRef.current.get(row.key);
    if (!bound || next.trim().toLowerCase() === bound.toLowerCase()) {
      updateRow(row.key, { columnName: next });
      return;
    }
    stdBoundCodeByRowRef.current.delete(row.key);
    updateRow(row.key, {
      columnName: next,
      stdFieldId: null,
      stdTypeId: null,
      stdUnitId: null,
      stdCaliberId: null,
      stdCodeSetCode: null,
      stdSecurityId: null,
    });
  };

  // 拖拽排序 sensors
  const sensors = useSensors(useSensor(PointerSensor, { activationConstraint: { distance: 5 } }));
  const rowsRef = useRef(rows);
  useEffect(() => { rowsRef.current = rows; }, [rows]);
  const selectedRowKeysRef = useRef(selectedRowKeys);
  useEffect(() => { selectedRowKeysRef.current = selectedRowKeys; }, [selectedRowKeys]);
  /** 拖拽结束:支持多选行同时拖拽——选中行作为整体块移动到目标位置。 */
  const handleDragEnd = useCallback((event: DragEndEvent) => {
    const { active, over } = event;
    // 清除多选拖拽标记与浮层
    setDraggingKeys(new Set());
    setDragOverlayRows([]);
    if (!over || active.id === over.id) return;
    markDirty();
    setRows((previous) => {
      const activeNum = Number(active.id);
      const overNum = Number(over.id);
      const selectedKeys = new Set<number>(
        selectedRowKeysRef.current.map((k) => Number(k)),
      );
      // 单行拖拽
      if (!selectedKeys.has(activeNum) || selectedKeys.size <= 1) {
        const oldIndex = previous.findIndex((r) => r.key === activeNum);
        const newIndex = previous.findIndex((r) => r.key === overNum);
        if (oldIndex === -1 || newIndex === -1) return previous;
        return arrayMove(previous, oldIndex, newIndex);
      }
      // 多行拖拽:如果 over 落在选中行上,找方向一致的最近未选中行作为有效目标
      let effectiveOverNum = overNum;
      if (selectedKeys.has(overNum)) {
        const overIndex = previous.findIndex((r) => r.key === overNum);
        const activeIndex = previous.findIndex((r) => r.key === activeNum);
        if (activeIndex < overIndex) {
          // 向下拖,找 over 之后的最近未选中行
          for (let i = overIndex + 1; i < previous.length; i++) {
            if (!selectedKeys.has(previous[i].key)) {
              effectiveOverNum = previous[i].key;
              break;
            }
          }
        } else {
          // 向上拖,找 over 之前的最近未选中行
          for (let i = overIndex - 1; i >= 0; i--) {
            if (!selectedKeys.has(previous[i].key)) {
              effectiveOverNum = previous[i].key;
              break;
            }
          }
        }
        if (effectiveOverNum === overNum) return previous;
      }
      // 将选中行作为整体块移动
      const selectedRows: ColumnDraft[] = [];
      const unselectedRows: ColumnDraft[] = [];
      previous.forEach((r) => {
        if (selectedKeys.has(r.key)) selectedRows.push(r);
        else unselectedRows.push(r);
      });
      const overUnselectedIdx = unselectedRows.findIndex((r) => r.key === effectiveOverNum);
      if (overUnselectedIdx === -1) return previous;
      const activeIndex = previous.findIndex((r) => r.key === activeNum);
      const effectiveOverIndex = previous.findIndex((r) => r.key === effectiveOverNum);
      const insertAt = activeIndex < effectiveOverIndex ? overUnselectedIdx + 1 : overUnselectedIdx;
      return [
        ...unselectedRows.slice(0, insertAt),
        ...selectedRows,
        ...unselectedRows.slice(insertAt),
      ];
    });
  }, []);
  /** 拖拽开始:检测是否多选拖拽,设置视觉标记与浮层数据。 */
  const handleDragStart = useCallback((event: DragStartEvent) => {
    const activeNum = Number(event.active.id);
    const selectedKeys = new Set<number>(
      selectedRowKeysRef.current.map((k) => Number(k)),
    );
    if (selectedKeys.has(activeNum) && selectedKeys.size > 1) {
      setDraggingKeys(selectedKeys);
      setDragOverlayRows(rowsRef.current.filter((r) => selectedKeys.has(r.key)));
    } else {
      const row = rowsRef.current.find((r) => r.key === activeNum);
      setDragOverlayRows(row ? [row] : []);
    }
  }, []);

  /** 删除字段时的联动：主键/索引/分区引用该字段则提示并同步清理（AC ticket 06）。 */
  const handleDeleteColumn = (draft: ColumnDraft) => {
    const name = draft.columnName.trim();
    const referencingIndexes = indexes.filter((index) => index.columns.includes(name));
    const referenced =
      (name && primaryKey.includes(name) ? 1 : 0) +
      referencingIndexes.length +
      (name && partitionColumns.includes(name) ? 1 : 0);

    const removeRow = () => {
      markDirty();
      setRows((previous) => previous.filter((row) => row.key !== draft.key));
      setSelectedRowKeys((previous) => previous.filter((key) => key !== draft.key));
      if (!name) return;
      setPrimaryKey((previous) => previous.filter((item) => item !== name));
      setIndexes((previous) =>
        previous.map((index) => ({
          ...index,
          columns: index.columns.filter((item) => item !== name),
        })),
      );
      setPartitionColumns((previous) => previous.filter((item) => item !== name));
    };

    if (referenced === 0) {
      removeRow();
      return;
    }
    Modal.confirm({
      centered: true,
      title: '删除被引用的字段',
      content: `字段「${name}」正被 ${
        primaryKey.includes(name) ? '主键、' : ''
      }${referencingIndexes.map((index) => `索引「${index.indexName}」`).join('、')}${
        partitionColumns.includes(name) ? '、分区' : ''
      } 引用，删除后将同步移除这些引用。`,
      okText: '删除并清理引用',
      cancelText: '取消',
      okButtonProps: { size: 'small', danger: true },
      cancelButtonProps: { size: 'small' },
      onOk: removeRow,
    });
  };

  /** C3(2026-09-17):批量删除选中行(单次确认,逐行走联动清理)。 */
  const handleBatchDelete = () => {
    const targets = rows.filter((row) => selectedRowKeys.includes(row.key));
    if (!targets.length) {
      message.info('请先勾选要删除的字段');
      return;
    }
    Modal.confirm({
      centered: true,
      title: '批量删除字段',
      content: `确定删除选中的 ${targets.length} 个字段？被主键/索引/分区引用的字段将同步清理引用。`,
      okText: '删除',
      cancelText: '取消',
      okButtonProps: { size: 'small', danger: true },
      cancelButtonProps: { size: 'small' },
      onOk: () => {
        targets.forEach((row) => handleDeleteColumn(row));
      },
    });
  };

  const handleSave = async () => {
    const isCurrent = captureEditorResource();
    if (!isCurrent()) return;
    if (!modelId) return;
    const seen = new Set<string>();
    for (const row of rows) {
      const name = row.columnName.trim();
      if (!name) {
        message.error('存在字段名为空的行，请补全后再保存');
        return;
      }
      if (!CODE_PATTERN.test(name)) {
        message.error(`字段名「${name}」仅允许字母、数字、下划线和 $`);
        return;
      }
      const lower = name.toLowerCase();
      if (seen.has(lower)) {
        message.error(`字段名重复：${name}`);
        return;
      }
      seen.add(lower);
      if (!row.dataType.trim()) {
        message.error(`字段「${name}」缺少数据类型`);
        return;
      }
    }

    const indexNames = new Set<string>();
    for (const index of indexes) {
      const name = index.indexName.trim();
      if (!name) {
        message.error('存在索引名为空的行，请补全后再保存');
        return;
      }
      if (name.toUpperCase() === 'PRIMARY') {
        message.error('索引名 PRIMARY 为保留名，请改用主键设置');
        return;
      }
      const lower = name.toLowerCase();
      if (indexNames.has(lower)) {
        message.error(`索引名重复：${name}`);
        return;
      }
      indexNames.add(lower);
      if (!index.columns.length) {
        message.error(`索引「${name}」至少需要一个字段`);
        return;
      }
      if (index.columns.some((column) => !seen.has(column.toLowerCase()))) {
        message.error(`索引「${name}」引用了不存在的字段`);
        return;
      }
    }

    if (primaryKey.some((column) => !seen.has(column.toLowerCase()))) {
      message.error('主键引用了不存在的字段');
      return;
    }
    if (partitionColumns.some((column) => !seen.has(column.toLowerCase()))) {
      message.error('分区字段引用了不存在的字段');
      return;
    }

    const propertyMap: Record<string, string> = {};
    for (const property of properties) {
      const key = property.propKey.trim();
      if (!key) continue;
      if (key in propertyMap) {
        message.error(`表属性键重复：${key}`);
        return;
      }
      propertyMap[key] = property.propValue.trim();
    }

    if (!tableName.trim()) {
      message.error('物理表名不能为空');
      return;
    }

    const payload = {
      tableName: tableName.trim(),
      tableComment: tableComment.trim() || undefined,
      columns: rows.map((row) => ({
        columnName: row.columnName.trim(),
        dataType: row.dataType.trim(),
        length: row.length ?? null,
        scale: row.scale ?? null,
        nullable: row.nullable,
        defaultValue: row.defaultValue?.trim() || undefined,
        comment: row.comment?.trim() || undefined,
        businessDescription: row.businessDescription?.trim() || undefined,
        stdTypeId: row.stdTypeId ?? undefined,
        stdNamingId: row.stdNamingId ?? undefined,
        stdCodeSetCode: row.stdCodeSetCode ?? undefined,
        stdUnitId: row.stdUnitId ?? undefined,
        stdCaliberId: row.stdCaliberId ?? undefined,
        stdSecurityId: row.stdSecurityId ?? undefined,
        stdFieldId: row.stdFieldId ?? undefined,
        fieldRole: row.fieldRole ?? undefined,
        aggregateFunc: row.aggregateFunc ?? undefined,
        transformExpr: row.transformExpr?.trim() || undefined,
      })),
      primaryKey: primaryKey.length ? primaryKey : undefined,
      indexes: indexes.length
        ? indexes.map((index) => ({
            indexName: index.indexName.trim(),
            uniqueIndex: index.uniqueIndex,
            indexType: index.indexType?.trim() || undefined,
            columns: index.columns,
          }))
        : undefined,
      partition: partitionEnabled
        ? {
            type: partitionType || undefined,
            columns: partitionColumns.length ? partitionColumns : undefined,
            expression: partitionExpression.trim() || undefined,
          }
        : undefined,
      tableProperties: Object.keys(propertyMap).length ? propertyMap : undefined,
    };

    const draftUnchanged = captureDraft();
    setSaving(true);
    try {
      const issues = await validateModelingStructure(modelId, payload);
      if (!isCurrent()) return;
      if (!draftUnchanged()) {
        message.info('草稿已修改，请重新保存');
        return;
      }
      const list = issues || [];
      setValidationIssues(list);
      if (list.some((issue) => issue.severity === 'ERROR')) {
        message.error('校验未通过：请处理列表中标红的问题后再保存');
        return;
      }
      await saveModelingStructure(modelId, payload);
      if (!isCurrent()) return;
      message.success('表结构已保存');
      if (!draftUnchanged()) {
        message.info('已保存提交时的草稿，后续编辑已保留');
        return;
      }
      await loadStructure(draftUnchanged);
      if (!isCurrent()) return;
    } catch (error) {
      if (!isCurrent()) return;
      message.error(error instanceof Error ? error.message : '表结构保存失败');
    } finally {
      if (!isCurrent()) return;
      setSaving(false);
    }
  };

  /** 发布/提审批共用前置：有未保存修改先保存。返回 true 表示已拦截。 */
  const guardUnsavedBeforePublish = () => {
    if (!dirty) return false;
    Modal.confirm({
      title: '有未保存的结构修改',
      centered: true,
      content: '发布只会固化最近一次保存的草稿。请先「保存表结构」，再发布/提交审批。',
      okText: '去保存',
      cancelText: '取消',
      onOk: () => void handleSave(),
    });
    return true;
  };

  /** 发布:先做「未保存守卫 + 草稿 vs 上次发布 diff」，确认后才固化快照。 */
  const handlePublish = async () => {
    const isCurrent = captureEditorResource();
    if (!isCurrent()) return;
    if (!modelId) return;
    if (guardUnsavedBeforePublish()) return;
    setPublishing(true);
    try {
      const publishedNo = modelInfo?.latestVersionNo;
      const published = publishedNo
        ? await getModelingVersion(modelId, publishedNo)
        : null;
      if (!isCurrent()) return;
      // 行主键 id 是保存时重建的易变字段，diff 与内容等值判断都应剔除（与后端语义投影口径一致）。
      const stripVolatile = (view: unknown) => {
        if (!view || typeof view !== 'object') return view;
        const v = view as Record<string, unknown>;
        return {
          ...v,
          columns: Array.isArray(v.columns)
            ? v.columns.map(({ id: _id, ...rest }: Record<string, unknown>) => rest)
            : v.columns,
          indexes: Array.isArray(v.indexes)
            ? v.indexes.map(({ id: _id, ...rest }: Record<string, unknown>) => rest)
            : v.indexes,
        };
      };
      setPublishPreview({
        before: stripVolatile(published?.structure ?? null),
        after: stripVolatile(structure ?? null),
        publishedVersionNo: publishedNo ?? null,
      });
    } catch (error) {
      if (!isCurrent()) return;
      message.error(error instanceof Error ? error.message : '加载发布对比失败');
    } finally {
      if (!isCurrent()) return;
      setPublishing(false);
    }
  };

  const doPublish = async () => {
    const isCurrent = captureEditorResource();
    if (!isCurrent()) return;
    if (!modelId) return;
    setPublishing(true);
    try {
      const version = await publishModelingModel(modelId);
      if (!isCurrent()) return;
      message.success(`已发布 V${version.versionNo}，${version.columnCount} 个字段`);
      // 刷新模型信息(状态变为 PUBLISHED)
      const info = await getModelingModel(modelId);
      if (!isCurrent()) return;
      setModelInfo(info);
      // 发布成功联动(01):统一视图头部/版本面板刷新
      window.dispatchEvent(new CustomEvent(MODELING_PUBLISHED_EVENT, { detail: { modelId } }));
    } catch (error) {
      if (!isCurrent()) return;
      message.error(error instanceof Error ? error.message : '发布失败');
    } finally {
      if (!isCurrent()) return;
      setPublishing(false);
    }
  };

  /** 发布审批(01):提交后由审批中心回调发布"批准时点的最新保存结构"，在途单唯一由后端 uk 保证。 */
  const handleSubmitPublishApproval = async () => {
    const isCurrent = captureEditorResource();
    if (!isCurrent()) return;
    if (!modelId) return;
    if (guardUnsavedBeforePublish()) return;
    setApprovalSubmitting(true);
    try {
      const instance = await submitModelingPublishApproval(modelId);
      if (!isCurrent()) return;
      setPublishApproval(instance);
      message.success(`已提交发布审批（单 #${instance.id}），批准后自动发布为新版本`);
    } catch (error) {
      if (!isCurrent()) return;
      message.error(error instanceof Error ? error.message : '提交发布审批失败');
    } finally {
      if (!isCurrent()) return;
      setApprovalSubmitting(false);
    }
  };

  const handleGenerateDdl = async () => {
    const isCurrent = captureEditorResource();
    if (!isCurrent()) return;
    if (!modelId) return;
    setDdlOpen(true);
    setDdlLoading(true);
    try {
      const data = await generateModelingDdl(modelId);
      if (!isCurrent()) return;
      setDdlScript(data?.script || '');
      setDdlDialect(data?.dialect || '');
    } catch (error) {
      if (!isCurrent()) return;
      message.error(error instanceof Error ? error.message : '建库脚本生成失败');
    } finally {
      if (!isCurrent()) return;
      setDdlLoading(false);
    }
  };

  const handleCopyDdl = async () => {
    const isCurrent = captureEditorResource();
    if (!isCurrent()) return;
    try {
      await navigator.clipboard.writeText(ddlScript);
      if (!isCurrent()) return;
      message.success('脚本已复制到剪贴板');
    } catch {
      if (!isCurrent()) return;
      message.error('复制失败，请手动选择脚本复制');
    }
  };

  const handleDownloadDdl = () => {
    const blob = new Blob([ddlScript], { type: 'text/plain;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement('a');
    anchor.href = url;
    anchor.download = `${tableName.trim() || structure?.modelCode || 'model'}.sql`;
    anchor.click();
    URL.revokeObjectURL(url);
  };

  /** 点击 COLUMN 类校验问题定位到具体字段行（AC ticket 07）。 */
  const locateIssue = (issue: ModelingValidationIssue) => {
    if (issue.scope !== 'COLUMN' || !issue.target) return;
    const row = rows.find((item) => item.columnName.trim().toLowerCase() === issue.target!.toLowerCase());
    if (!row) return;
    setHighlightRowKey(row.key);
    editorRootRef.current?.querySelector(`tr[data-row-key="${row.key}"]`)?.scrollIntoView({ behavior: 'smooth', block: 'center' });
    clearTimeout(highlightTimerRef.current);
    highlightTimerRef.current = setTimeout(() => {
      setHighlightRowKey(undefined);
    }, 2000);
  };

  const columnOptions = useMemo(
    () =>
      rows
        .map((row) => ({ value: row.columnName.trim(), label: row.columnName.trim() }))
        .filter((option) => option.value),
    [rows],
  );

  const partitionTypes = structure?.dialect ? PARTITION_TYPES_BY_DIALECT[structure.dialect] : undefined;

  const columns: TableColumnsType<ColumnDraft> = useMemo(
    () => [
      {
        title: '#',
        key: 'seq',
        width: 40,
        render: (_value, _record, index) => index + 1,
      },
      {
        title: '字段名',
        dataIndex: 'columnName',
        width: 160,
        render: (value: string, record) => (
          <AutoComplete
            size="small"
            variant="filled"
            className="!w-full"
            placeholder="检索标准字段或自定义"
            value={value}
            options={standardFieldChoices.map((field) => ({
              value: field.code,
              label: (
                <div className="flex items-baseline justify-between gap-3">
                  <span className="truncate">{field.code}</span>
                  <span className="shrink-0 text-[12px] text-[#667085]">{field.name}</span>
                </div>
              ),
            }))}
            filterOption={false}
            popupMatchSelectWidth={280}
            onFocus={() => searchStandardFields(value)}
            onSearch={searchStandardFields}
            onChange={(next) => renameStandardField(record, next)}
            onSelect={(code) => bindStandardField(record, String(code))}
          />
        ),
      },
      {
        title: '类型',
        dataIndex: 'dataType',
        width: 120,
        render: (value: string, record) => (
          <Select
            size="small"
            className="!w-full"
            placeholder="请选择"
            allowClear
            value={value || undefined}
            options={typeCatalog.map((item) => ({ value: item.name!, label: item.name! }))}
            onChange={(next) => {
              // C5(2026-09-17):按类型自动带出长度/精度默认值,用户可改
              const defaults = TYPE_DEFAULTS[next || ''] ?? {};
              const spec = typeSpecOf(typeCatalog, next);
              updateRow(record.key, {
                dataType: next ?? '',
                length: defaults.length ?? record.length,
                // 换成不支持小数位的类型后必须清掉残留精度,否则后端直接 ERROR 阻断保存
                scale:
                  spec?.scaleAllowed === false ? null : defaults.scale ?? record.scale,
              });
            }}
          />
        ),
      },
      {
        title: '长度',
        dataIndex: 'length',
        width: 70,
        render: (value: number | null | undefined, record) => {
          const spec = typeSpecOf(typeCatalog, record.dataType);
          const missingRequired = spec?.lengthRequired === true && value == null;
          return (
            <InputNumber
              size="small"
              variant="filled"
              min={0}
              className="!w-full"
              placeholder={missingRequired ? '必填' : undefined}
              status={missingRequired ? 'warning' : undefined}
              value={value ?? undefined}
              onChange={(next) => updateRow(record.key, { length: next ?? null })}
            />
          );
        },
      },
      {
        title: '小数位',
        dataIndex: 'scale',
        width: 70,
        render: (value: number | null | undefined, record) => {
          const spec = typeSpecOf(typeCatalog, record.dataType);
          const notAllowed = spec?.scaleAllowed === false && value != null;
          return (
            <InputNumber
              size="small"
              variant="filled"
              min={0}
              className="!w-full"
              status={notAllowed ? 'error' : undefined}
              value={value ?? undefined}
              onChange={(next) => updateRow(record.key, { scale: next ?? null })}
            />
          );
        },
      },
      {
        title: '可空',
        dataIndex: 'nullable',
        width: 50,
        align: 'center',
        render: (value: boolean, record) => (
          <Checkbox checked={value} onChange={(event) => updateRow(record.key, { nullable: event.target.checked })} />
        ),
      },
      {
        title: '主键',
        key: 'pk',
        width: 50,
        align: 'center',
        render: (_value, record) => (
          <Checkbox
            checked={primaryKey.includes(record.columnName.trim())}
            onChange={(event) => {
              const colName = record.columnName.trim();
              if (event.target.checked) {
                setPrimaryKey((prev) => [...prev, colName]);
              } else {
                setPrimaryKey((prev) => prev.filter((c) => c !== colName));
              }
              markDirty();
            }}
          />
        ),
      },
      {
        title: '分区',
        key: 'partition',
        width: 50,
        align: 'center',
        render: (_value, record) => (
          <Checkbox
            disabled={!partitionEnabled || Boolean(structure?.dialect && !partitionTypes)}
            checked={partitionColumns.includes(record.columnName.trim())}
            onChange={(event) => {
              const colName = record.columnName.trim();
              if (event.target.checked) {
                setPartitionColumns((prev) => [...prev, colName]);
              } else {
                setPartitionColumns((prev) => prev.filter((c) => c !== colName));
              }
              markDirty();
            }}
          />
        ),
      },
      {
        title: '注释',
        dataIndex: 'comment',
        minWidth: 140,
        render: (value: string, record) => (
          <Input
            size="small"
            variant="filled"
            className="!w-full"
            value={value}
            title={value || undefined}
            onChange={(event) => updateRow(record.key, { comment: event.target.value })}
          />
        ),
      },
      {
        title: '业务描述',
        dataIndex: 'businessDescription',
        minWidth: 120,
        render: (value: string, record) => (
          <Input
            size="small"
            variant="filled"
            className="!w-full"
            value={value}
            title={value || undefined}
            onChange={(event) => updateRow(record.key, { businessDescription: event.target.value })}
          />
        ),
      },
      {
        title: '数据标准',
        key: 'standards',
        width: 170,
        // C2(2026-09-17):只显示类型标准「名称（编码）」(不展示安全/命名等),无则显示空;
        // 非 ODS 层字段未绑定标准时显示警告提示。
        // 03 号单:绑定标准字段的行带出「影响」入口,按该标准字段反查各层落地。
        render: (_value, record) => {
          const typeName = record.stdTypeId ? standardNameById[record.stdTypeId] : undefined;
          const stdNode = typeName ? (
            <Tooltip title={typeName} placement="topLeft">
              <span className="block truncate text-[12px] text-[#344054]">{typeName}</span>
            </Tooltip>
          ) : modelInfo?.layerCode && modelInfo.layerCode !== 'ODS' ? (
            // 非 ODS 层未绑定标准字段显示治理警告
            <Tooltip title="该字段未绑定数据标准，建议治理" placement="topLeft">
              <Tag color="warning" className="!text-[12px]">
                <ExclamationCircleOutlined className="mr-1" />
                未治理
              </Tag>
            </Tooltip>
          ) : null;
          if (!record.stdFieldId) {
            return stdNode;
          }
          return (
            <span className="flex items-center gap-1">
              <span className="min-w-0 flex-1">{stdNode}</span>
              <Tooltip title="按绑定的标准字段反查各层落地，预览变更影响范围">
                <Typography.Link
                  className="!shrink-0 !text-[12px]"
                  onClick={() => history.push(`/modeling/impact?processFieldId=${record.stdFieldId}`)}
                >
                  影响
                </Typography.Link>
              </Tooltip>
            </span>
          );
        },
      },
      ...(AGGREGATE_LAYERS.has(modelInfo?.layerCode || '')
        ? ([
            {
              title: '角色',
              key: 'fieldRole',
              width: 100,
              render: (_value: unknown, record) => (
                <Select
                  size="small"
                  className="!w-full"
                  placeholder="未设置"
                  allowClear
                  value={record.fieldRole ?? undefined}
                  options={[
                    { value: 'DIMENSION', label: '维度' },
                    { value: 'MEASURE', label: '度量' },
                  ]}
                  onChange={(next) =>
                    updateRow(record.key, {
                      fieldRole: (next as 'DIMENSION' | 'MEASURE') ?? null,
                      // 非度量清除聚合函数,避免残留无效值
                      aggregateFunc: next === 'MEASURE' ? record.aggregateFunc : null,
                    })
                  }
                />
              ),
            },
            {
              title: '聚合函数',
              key: 'aggregateFunc',
              width: 120,
              render: (_value: unknown, record) => (
                <Select
                  size="small"
                  className="!w-full"
                  placeholder="请选择"
                  allowClear
                  disabled={record.fieldRole !== 'MEASURE'}
                  value={record.aggregateFunc ?? undefined}
                  options={AGGREGATE_FUNC_OPTIONS.map((f) => ({ value: f, label: f }))}
                  onChange={(next) => updateRow(record.key, { aggregateFunc: next ?? null })}
                />
              ),
            },
            {
              title: '口径',
              key: 'transformExpr',
              minWidth: 140,
              render: (_value: unknown, record) => (
                <Input
                  size="small"
                  variant="filled"
                  className="!w-full"
                  placeholder="度量口径/表达式"
                  value={record.transformExpr ?? ''}
                  title={record.transformExpr || undefined}
                  onChange={(event) => updateRow(record.key, { transformExpr: event.target.value })}
                />
              ),
            },
          ] as TableColumnsType<ColumnDraft>)
        : []),
      {
        title: '操作',
        key: 'actions',
        width: 70,
        fixed: 'right',
        render: (_value, record) => (
          <Button type="link" size="small" danger className="px-1" onClick={() => handleDeleteColumn(record)}>
            删除
          </Button>
        ),
      },
    ],
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [rows.length, primaryKey, indexes, partitionColumns, typeCatalog, standardNameById, modelInfo, standardFieldChoices],
  );

  const approvalPending = publishApproval?.status === 'PENDING';

  return (
    <div className="min-h-[calc(100dvh-64px)] bg-[#f7f8fa] p-6 text-[#242731]">
      <div className="mb-4 flex items-center justify-between">
        <div className="flex items-center gap-2">
          {modelInfo?.status ? (
            <Tag color={modelInfo.status === 'PUBLISHED' ? 'success' : 'default'}>
              {modelInfo.status === 'PUBLISHED' ? '已发布' : modelInfo.status === 'DRAFT' ? '草稿' : modelInfo.status}
            </Tag>
          ) : null}
          {modelInfo?.latestVersionNo ? (
            <span className="text-[12px] text-[#667085]">V{modelInfo.latestVersionNo}</span>
          ) : null}
          {publishApproval ? (
            <Tooltip
              title={
                approvalPending
                  ? '批准后按当时最新保存结构自动发布；在途期间结构编辑与发布已冻结'
                  : `最近一次发布审批：${publishApproval.title}`
              }
            >
              <span className="flex items-center gap-1">
                <ApprovalStatusTag status={publishApproval.status} />
                <Button
                  type="link"
                  size="small"
                  className="!px-0 !text-[12px]"
                  onClick={() => history.push(`/approval/instance/${publishApproval.id}`)}
                >
                  查看审批单
                </Button>
              </span>
            </Tooltip>
          ) : null}
        </div>
        <div className="flex items-center gap-3">
          {returnAssetId && Number.isSafeInteger(returnAssetId) && returnAssetId > 0 && (
            <YakButton
              className="!h-9 !rounded-lg !px-4"
              onClick={() => history.push(`/data-asset/detail/${returnAssetId}`)}
            >
              返回资产详情
            </YakButton>
          )}
          <YakButton className="!h-9 !rounded-lg !px-4" onClick={() => void handleGenerateDdl()}>
            建库脚本
          </YakButton>
          {publishFlowEnabled ? (
            <Tooltip title={approvalPending ? '已有在途发布审批单，禁止重复提交' : undefined}>
              <YakButton
                className="!h-9 !rounded-lg !px-4"
                disabled={loading || !structure || approvalPending}
                loading={approvalSubmitting}
                onClick={() => void handleSubmitPublishApproval()}
              >
                提交发布审批
              </YakButton>
            </Tooltip>
          ) : null}
          <Tooltip title={approvalPending ? '发布审批在途，需待终态后发布' : undefined}>
            <YakButton
              className="!h-9 !rounded-lg !px-4"
              disabled={loading || !structure || approvalPending}
              onClick={() => void handlePublish()}
              loading={publishing}
            >
              {publishFlowEnabled ? '直接发布' : '发布'}
            </YakButton>
          </Tooltip>
          <Tooltip title={approvalPending ? '发布审批在途，结构已冻结至终态' : undefined}>
            <YakButton
              type="primary"
              className="!h-9 !rounded-lg !px-5 !text-white"
              loading={saving}
              disabled={loading || dirty === false || approvalPending}
              onClick={() => void handleSave()}
            >
              保存表结构
            </YakButton>
          </Tooltip>
        </div>
      </div>

      {loading && !structure ? null : !structure ? (
        <Card bordered={false}>
          <YakEmpty compact title="模型不存在或已被删除" />
        </Card>
      ) : (
        <div className="space-y-4">
          <Card
            bordered={false}
            title="表基础信息"
            extra={
              structure.modelDescription ? (
                <span className="text-[13px] text-[#667085]">{structure.modelDescription}</span>
              ) : null
            }
          >
            <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
              <label className="block">
                <span className="mb-1 block text-[13px] text-[#475467]">
                  物理表名 <span className="text-[#f04438]">*</span>
                </span>
                <Input
                  variant="filled"
                  value={tableName}
                  onChange={(event) => {
                    setTableName(event.target.value);
                    markDirty();
                  }}
                />
              </label>
              <label className="block">
                <span className="mb-1 block text-[13px] text-[#475467]">表注释</span>
                <Input
                  variant="filled"
                  value={tableComment}
                  onChange={(event) => {
                    setTableComment(event.target.value);
                    markDirty();
                  }}
                />
              </label>
            </div>
          </Card>

          {validationIssues.length ? (
            <Card
              bordered={false}
              size="small"
              title={`校验结果（${validationIssues.filter((issue) => issue.severity === 'ERROR').length} 个错误 / ${validationIssues.length} 项提示）`}
            >
              <div className="max-h-[180px] space-y-1 overflow-y-auto">
                {validationIssues.map((issue, index) => (
                  <div
                    key={index}
                    className={[
                      'flex cursor-pointer items-center gap-2 rounded px-2 py-1 text-[13px]',
                      issue.severity === 'ERROR' ? 'bg-[#fef3f2]' : 'bg-[#fffaeb]',
                    ].join(' ')}
                    onClick={() => locateIssue(issue)}
                  >
                    <Tag color={issue.severity === 'ERROR' ? 'error' : 'warning'}>
                      {issue.severity === 'ERROR' ? '错误' : '提示'}
                    </Tag>
                    <span className="shrink-0 text-[#667085]">{issue.scope}</span>
                    <span className="shrink-0">{issue.target ? `「${issue.target}」` : ''}</span>
                    <span className="truncate">{issue.message}</span>
                  </div>
                ))}
              </div>
            </Card>
          ) : null}

          <Card
            bordered={false}
            title="字段列表"
            extra={
              modelInfo?.layerCode && modelInfo.layerCode !== 'ODS' ? (
                (() => {
                  const ungovernedCount = rows.filter((r) => !r.stdTypeId).length;
                  return ungovernedCount > 0 ? (
                    <Tooltip title={`${ungovernedCount} 个字段未绑定数据标准，建议点击「标准发现」自动匹配或手动治理`}>
                      <Tag color="warning" className="!text-[12px]">
                        <ExclamationCircleOutlined className="mr-1" />
                        {ungovernedCount} 个字段未治理
                      </Tag>
                    </Tooltip>
                  ) : (
                    <Tag color="success" className="!text-[12px]">全部已治理</Tag>
                  );
                })()
              ) : null
            }
          >
            {/* C3(2026-09-17):批量添加/批量删除/刷新工具栏 */}
            {rows.length ? (
              <div className="mb-2 flex items-center justify-between gap-2">
                <Space size={8}>
                  <Button size="small" onClick={() => setBulkAddOpen(true)}>
                    批量添加
                  </Button>
                  <Tooltip title="从源表导入字段">
                    <Button
                      size="small"
                      icon={<CloudDownloadOutlined />}
                      onClick={() => void openImportModal()}
                    >
                      从源表导入
                    </Button>
                  </Tooltip>
                  <Tooltip title="从其他已有模型导入字段">
                    <Button
                      size="small"
                      icon={<CopyOutlined />}
                      onClick={() => void handleOpenImportFromModel()}
                    >
                      从模型导入
                    </Button>
                  </Tooltip>
                  <Tooltip title="从业务过程继承字段">
                    <Button
                      size="small"
                      icon={<ApartmentOutlined />}
                      onClick={() => void handleOpenImportFromProcess()}
                    >
                      从业务过程导入
                    </Button>
                  </Tooltip>
                  {AGGREGATE_LAYERS.has(modelInfo?.layerCode || '') && (
                    <Tooltip title="选择指标,反推上游模型/统计周期/度量维度并并入表结构">
                      <Button
                        size="small"
                        icon={<FundOutlined />}
                        onClick={() => void handleOpenMetricDraft()}
                      >
                        按指标反推
                      </Button>
                    </Tooltip>
                  )}
                  {importConfig && (
                    <Tooltip title={`从源表 ${importConfig.table} 重新导入字段(会覆盖当前字段)`}>
                      <Button
                        size="small"
                        icon={<CloudDownloadOutlined />}
                        loading={reimportLoading}
                        onClick={() => void handleReimport()}
                      >
                        重新导入
                      </Button>
                    </Tooltip>
                  )}
                  <Tooltip title="按字段名匹配标准字段集,自动回填类型/单位/口径/安全等标准绑定">
                    <Button
                      size="small"
                      icon={<ThunderboltOutlined />}
                      loading={discoverLoading}
                      onClick={() => void handleStandardDiscover()}
                    >
                      标准发现
                    </Button>
                  </Tooltip>
                  <Button size="small" danger disabled={!selectedRowKeys.length} onClick={handleBatchDelete}>
                    批量删除{selectedRowKeys.length ? `（${selectedRowKeys.length}）` : ''}
                  </Button>
                </Space>
                <Button
                  size="small"
                  icon={<ReloadOutlined />}
                  loading={loading}
                  onClick={() => {
                    void loadStructure();
                  }}
                >
                  刷新
                </Button>
              </div>
            ) : null}
            {rows.length ? (
              <div ref={editorRootRef}>
              <RowInteractionContext.Provider value={{ highlight: highlightRowKey, dragging: draggingKeys }}>
              <DndContext sensors={sensors} onDragStart={handleDragStart} onDragEnd={handleDragEnd} onDragCancel={() => { setDraggingKeys(new Set()); setDragOverlayRows([]); }}>
                <SortableContext items={rows.map((r) => r.key)} strategy={verticalListSortingStrategy}>
                  <Table<ColumnDraft>
                    rowKey="key"
                    loading={loading}
                    size="small"
                    columns={columns}
                    dataSource={rows}
                    pagination={false}
                    scroll={{ x: 'max-content' }}
                    rowSelection={{ selectedRowKeys, onChange: setSelectedRowKeys }}
                    components={{ body: { row: SortableRow } }}
                  />
                </SortableContext>
                <DragOverlay dropAnimation={null}>
                  {dragOverlayRows.length > 0 ? (
                    <div className="overflow-hidden rounded-lg border border-[#1677ff] bg-white shadow-lg">
                      <table className="w-full border-collapse">
                        <tbody>
                          {dragOverlayRows.map((row, index) => (
                            <tr
                              key={row.key}
                              className={index % 2 === 0 ? 'bg-[#e6f4ff]' : 'bg-[#f0f7ff]'}
                            >
                              <td className="whitespace-nowrap px-3 py-1.5 text-[13px] text-[#242731]">
                                {index + 1}
                              </td>
                              <td className="whitespace-nowrap px-3 py-1.5 text-[13px] font-medium text-[#242731]">
                                {row.columnName || '(未命名)'}
                              </td>
                              <td className="whitespace-nowrap px-3 py-1.5 text-[13px] text-[#667085]">
                                {row.dataType || '-'}
                              </td>
                              <td className="whitespace-nowrap px-3 py-1.5 text-[13px] text-[#667085]">
                                {row.length ?? '-'}
                              </td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    </div>
                  ) : null}
                </DragOverlay>
              </DndContext>
              </RowInteractionContext.Provider>
              </div>
            ) : (
              /* B7(2026-09-17):空字段时轻量空态,不渲染表头 */
              <YakEmpty
                compact
                title="还没有字段"
                description="选择以下方式导入字段，或手动添加"
              >
                <Space>
                  <Button
                    type="primary"
                    icon={<CloudDownloadOutlined />}
                    onClick={() => void openImportModal()}
                  >
                    从源表导入
                  </Button>
                  <Button icon={<CopyOutlined />} onClick={() => void handleOpenImportFromModel()}>
                    从模型导入
                  </Button>
                  <Button icon={<ApartmentOutlined />} onClick={() => void handleOpenImportFromProcess()}>
                    从业务过程导入
                  </Button>
                  {AGGREGATE_LAYERS.has(modelInfo?.layerCode || '') && (
                    <Button icon={<FundOutlined />} onClick={() => void handleOpenMetricDraft()}>
                      按指标反推
                    </Button>
                  )}
                </Space>
              </YakEmpty>
            )}
            <Space className="mt-3">
              <Button
                type="dashed"
                onClick={() => {
                  markDirty();
                  setRows((previous) => [...previous, emptyColumnDraft()]);
                }}
              >
                添加字段
              </Button>
            </Space>
          </Card>

          {/* C3(2026-09-17):批量添加 N 行 */}
          <Modal
            open={bulkAddOpen}
            title="批量添加字段"
            okText="添加"
            cancelText="取消"
            onCancel={() => setBulkAddOpen(false)}
            onOk={() => {
              const count = Math.min(Math.max(bulkAddCount, 1), 100);
              markDirty();
              setRows((previous) => [...previous, ...Array.from({ length: count }, () => emptyColumnDraft())]);
              setBulkAddOpen(false);
            }}
          >
            <div className="flex items-center gap-2">
              <span className="text-[13px] text-[#475467]">新增行数</span>
              <InputNumber min={1} max={100} value={bulkAddCount} onChange={(value) => setBulkAddCount(value ?? 5)} />
            </div>
          </Modal>

          {/* 导入字段弹窗 */}
          <Modal
            open={importModalOpen}
            title="从源表导入字段"
            okText="导入"
            cancelText="取消"
            confirmLoading={importModalLoading}
            onCancel={() => setImportModalOpen(false)}
            onOk={() => void handleImportConfirm()}
            okButtonProps={{ disabled: !importDatasourceId || !importTable }}
            width={480}
          >
            <div className="space-y-4 py-2">
              <div>
                <div className="mb-1 text-[13px] text-[#344054]">数据源</div>
                <Select
                  className="w-full"
                  variant="filled"
                  showSearch
                  optionFilterProp="label"
                  placeholder="请选择"
                  value={importDatasourceId}
                  onChange={(value) => {
                    setImportDatasourceId(value);
                    setImportTable(undefined);
                    if (value) void loadImportTables(value);
                  }}
                  options={datasources.map((item) => ({
                    label: item.name ?? `数据源 #${item.id}`,
                    value: item.id as number,
                  }))}
                />
              </div>
              <div>
                <div className="mb-1 text-[13px] text-[#344054]">源表</div>
                <Select
                  className="w-full"
                  variant="filled"
                  showSearch
                  optionFilterProp="label"
                  placeholder="请选择"
                  value={importTable}
                  disabled={!importDatasourceId || importTablesLoading}
                  loading={importTablesLoading}
                  onChange={(value) => setImportTable(value)}
                  options={importTables.map((item) => ({
                    label: item.name ?? `表 #${item.name}`,
                    value: item.name as string,
                  }))}
                />
              </div>
            </div>
          </Modal>

          {/* 从已有模型导入字段弹窗 */}
          <Modal
            open={importFromModelOpen}
            title="从已有模型导入字段"
            okText="导入"
            cancelText="取消"
            confirmLoading={importFromModelLoading}
            onCancel={() => setImportFromModelOpen(false)}
            onOk={() => void handleImportFromModel()}
            okButtonProps={{ disabled: !selectedModelIds.length }}
            width={560}
          >
            <div className="py-2">
              <Select
                mode="multiple"
                className="w-full"
                variant="filled"
                showSearch
                optionFilterProp="label"
                placeholder="请选择要导入字段的模型（支持多选）"
                loading={modelListLoading}
                value={selectedModelIds}
                onChange={(value) => setSelectedModelIds(value as number[])}
                options={modelList.map((item) => ({
                  label: `${item.name ?? item.code ?? `模型 #${item.id}`}（${item.code ?? '-'}）`,
                  value: item.id as number,
                }))}
              />
              {modelList.length === 0 && !modelListLoading ? (
                <div className="mt-2 text-[12px] text-[#667085]">暂无其他可用模型</div>
              ) : null}
            </div>
          </Modal>

          {/* 从业务过程导入字段弹窗 */}
          <Modal
            open={importFromProcessOpen}
            title="从业务过程导入字段"
            okText="导入"
            cancelText="取消"
            confirmLoading={importFromProcessLoading}
            onCancel={() => setImportFromProcessOpen(false)}
            onOk={() => void handleImportFromProcess()}
            okButtonProps={{ disabled: !selectedProcessId }}
            width={560}
          >
            <div className="py-2">
              <Select
                className="w-full"
                variant="filled"
                showSearch
                optionFilterProp="label"
                placeholder="请选择业务过程"
                loading={processListLoading}
                value={selectedProcessId}
                onChange={(value) => setSelectedProcessId(value as number)}
                options={processList.map((item) => ({
                  label: `${item.name}（${item.code}）`,
                  value: item.id,
                }))}
              />
              {processList.length === 0 && !processListLoading ? (
                <div className="mt-2 text-[12px] text-[#667085]">暂无可用业务过程</div>
              ) : null}
            </div>
          </Modal>

          {/* DWS/ADS 按指标反推弹窗(60/61) */}
          <Modal
            open={metricDraftOpen}
            title="按指标反推字段"
            okText="反推并并入"
            cancelText="取消"
            confirmLoading={metricDraftLoading}
            onCancel={() => setMetricDraftOpen(false)}
            onOk={() => void handleApplyMetricDraft()}
            okButtonProps={{ disabled: !selectedMetricIds.length }}
            width={560}
          >
            <div className="space-y-4 py-2">
              <div>
                <div className="mb-1 text-[13px] text-[#475467]">选择指标</div>
                <Select
                  className="w-full"
                  variant="filled"
                  mode="multiple"
                  showSearch
                  optionFilterProp="label"
                  placeholder="可多选,反推其所属业务过程的上游模型与度量维度"
                  loading={metricListLoading}
                  value={selectedMetricIds}
                  onChange={(value) => setSelectedMetricIds(value as number[])}
                  options={metricList.map((item) => ({
                    label: `${item.metricName}（${item.metricCode}）`,
                    value: item.id,
                  }))}
                />
                {metricList.length === 0 && !metricListLoading ? (
                  <div className="mt-2 text-[12px] text-[#667085]">暂无启用的指标</div>
                ) : null}
              </div>
              {modelInfo?.layerCode === 'ADS' && (
                <div>
                  <div className="mb-1 text-[13px] text-[#475467]">数据来源</div>
                  <Radio.Group
                    value={adsSourceLayer}
                    onChange={(event) => setAdsSourceLayer(event.target.value as 'DWS' | 'DWD')}
                    options={[
                      { value: 'DWS', label: 'DWS 汇总报表' },
                      { value: 'DWD', label: 'DWD 明细实时报表' },
                    ]}
                    optionType="button"
                  />
                  <div className="mt-1 text-[12px] text-[#667085]">
                    DWS 取已聚合的汇总表;DWD 直接下探明细实时聚合。
                  </div>
                </div>
              )}
            </div>
          </Modal>

          <Card bordered={false} title="分区配置">
            <div className="grid grid-cols-1 gap-4">
              <label className="block">
                <span className="mb-1 block text-[13px] text-[#475467]">是否分区</span>
                <Switch
                  checked={partitionEnabled}
                  onChange={(checked) => {
                    setPartitionEnabled(checked);
                    markDirty();
                    if (checked && !partitionType && !partitionColumns.length && !partitionExpression.trim()) {
                      setPartitionType('RANGE');
                      setPartitionColumns(['event_time']);
                      setPartitionExpression('dt');
                    }
                  }}
                />
              </label>
              {partitionEnabled && (
                <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
                  <label className="block">
                    <span className="mb-1 block text-[13px] text-[#475467]">
                      分区类型
                      {structure.dialect && !partitionTypes
                        ? `（${MODELING_DIALECT_LABELS[structure.dialect] || structure.dialect} 暂不支持分区，选项已禁用）`
                        : ''}
                    </span>
                    <Select
                      allowClear
                      disabled={Boolean(structure.dialect && !partitionTypes)}
                      className="w-full"
                      placeholder="请选择"
                      value={partitionType}
                      options={(partitionTypes || []).map((item) => ({ value: item, label: item }))}
                      onChange={(value) => {
                        setPartitionType(value);
                        markDirty();
                      }}
                    />
                  </label>
                  <label className="block">
                    <span className="mb-1 block text-[13px] text-[#475467]">分区表达式（可选）</span>
                    <Input
                      variant="filled"
                      disabled={!partitionType || Boolean(structure.dialect && !partitionTypes)}
                      placeholder="如 YEAR(dt)"
                      value={partitionExpression}
                      onChange={(event) => {
                        setPartitionExpression(event.target.value);
                        markDirty();
                      }}
                    />
                  </label>
                </div>
              )}
            </div>
          </Card>

          <Card
            bordered={false}
            title="索引"
            extra={
              <Space size={4}>
                {/* B9(2026-09-17):索引区默认折叠 */}
                <Button size="small" type="link" onClick={() => setIndexCollapsed((previous) => !previous)}>
                  {indexCollapsed ? '展开' : '折叠'}
                </Button>
                <Button
                  size="small"
                  onClick={() => {
                    markDirty();
                    setIndexCollapsed(false);
                    setIndexes((previous) => [
                      ...previous,
                      {
                        key: nextDraftKey(),
                        indexName: '',
                        uniqueIndex: false,
                        indexType: '',
                        columns: [],
                      },
                    ]);
                  }}
                >
                  添加索引
                </Button>
              </Space>
            }
          >
            {indexCollapsed ? (
              <Typography.Text type="secondary" className="!text-[12px]">
                已折叠，点击右上角「展开」查看/编辑索引。
              </Typography.Text>
            ) : indexes.length === 0 ? (
              <YakEmpty compact title="暂无索引" description="点击右上角“添加索引”创建二级索引" />
            ) : (
              <div className="space-y-3">
                {indexes.map((index) => (
                  <div
                    key={index.key}
                    className="flex flex-wrap items-end gap-3 rounded-lg border border-[#eaecf0] p-3"
                  >
                    <label className="block w-[180px]">
                      <span className="mb-1 block text-[12px] text-[#667085]">索引名</span>
                      <Input
                        size="small"
                        variant="filled"
                        placeholder="如 idx_user_name"
                        value={index.indexName}
                        onChange={(event) => {
                          const value = event.target.value;
                          setIndexes((previous) =>
                            previous.map((item) => (item.key === index.key ? { ...item, indexName: value } : item)),
                          );
                          markDirty();
                        }}
                      />
                    </label>
                    <label className="block w-[140px]">
                      <span className="mb-1 block text-[12px] text-[#667085]">索引类型</span>
                      <Input
                        size="small"
                        variant="filled"
                        placeholder="BTREE / HASH"
                        value={index.indexType}
                        onChange={(event) => {
                          const value = event.target.value;
                          setIndexes((previous) =>
                            previous.map((item) => (item.key === index.key ? { ...item, indexType: value } : item)),
                          );
                          markDirty();
                        }}
                      />
                    </label>
                    <label className="block min-w-[220px] flex-1">
                      <span className="mb-1 block text-[12px] text-[#667085]">字段组合</span>
                      <Select
                        mode="multiple"
                        className="w-full"
                        placeholder="请选择"
                        value={index.columns}
                        options={columnOptions}
                        onChange={(values) => {
                          setIndexes((previous) =>
                            previous.map((item) => (item.key === index.key ? { ...item, columns: values } : item)),
                          );
                          markDirty();
                        }}
                      />
                    </label>
                    <label className="flex items-center gap-1 pb-1 text-[13px] text-[#475467]">
                      <Checkbox
                        checked={index.uniqueIndex}
                        onChange={(event) => {
                          const value = event.target.checked;
                          setIndexes((previous) =>
                            previous.map((item) => (item.key === index.key ? { ...item, uniqueIndex: value } : item)),
                          );
                          markDirty();
                        }}
                      >
                        唯一索引
                      </Checkbox>
                    </label>
                    <Button
                      type="link"
                      size="small"
                      danger
                      className="pb-1"
                      onClick={() => {
                        markDirty();
                        setIndexes((previous) => previous.filter((item) => item.key !== index.key));
                      }}
                    >
                      删除
                    </Button>
                  </div>
                ))}
              </div>
            )}
          </Card>

          <Card
            bordered={false}
            title="表属性"
            extra={
              <Button
                size="small"
                onClick={() => {
                  markDirty();
                  setProperties((previous) => [...previous, { key: nextDraftKey(), propKey: '', propValue: '' }]);
                }}
              >
                添加属性
              </Button>
            }
          >
            {properties.length === 0 ? (
              <YakEmpty compact title="暂无表属性" description="按需添加键值属性（如 ENGINE、CHARSET）" />
            ) : (
              <div className="space-y-2">
                {properties.map((property) => (
                  <div key={property.key} className="flex items-center gap-2">
                    <Input
                      size="small"
                      variant="filled"
                      className="!w-[220px]"
                      placeholder="属性名"
                      value={property.propKey}
                      onChange={(event) => {
                        const value = event.target.value;
                        setProperties((previous) =>
                          previous.map((item) => (item.key === property.key ? { ...item, propKey: value } : item)),
                        );
                        markDirty();
                      }}
                    />
                    <Input
                      size="small"
                      variant="filled"
                      className="flex-1"
                      placeholder="属性值"
                      value={property.propValue}
                      onChange={(event) => {
                        const value = event.target.value;
                        setProperties((previous) =>
                          previous.map((item) => (item.key === property.key ? { ...item, propValue: value } : item)),
                        );
                        markDirty();
                      }}
                    />
                    <Button
                      type="link"
                      size="small"
                      danger
                      onClick={() => {
                        markDirty();
                        setProperties((previous) => previous.filter((item) => item.key !== property.key));
                      }}
                    >
                      删除
                    </Button>
                  </div>
                ))}
              </div>
            )}
          </Card>
        </div>
      )}
      <Modal
        open={publishPreview !== null}
        width={820}
        centered
        title={
          publishPreview?.publishedVersionNo
            ? `发布确认：草稿 vs 线上 V${publishPreview.publishedVersionNo}`
            : '发布确认：首次发布'
        }
        okText="确认发布"
        cancelText="取消"
        confirmLoading={publishing}
        onOk={async () => {
          const isCurrent = captureEditorResource();
          if (!isCurrent()) return;
          await doPublish();
          if (!isCurrent()) return;
          setPublishPreview(null);
        }}
        onCancel={() => setPublishPreview(null)}
      >
        <div className="mb-2 text-[12px] text-[#667085]">
          发布将把当前已保存的结构固化为新版本；血缘/派生/建库脚本等消费方从此读取该快照。
        </div>
        <JsonDiffView
          before={publishPreview?.before ?? null}
          after={publishPreview?.after ?? null}
        />
      </Modal>
      <Modal
        open={ddlOpen}
        width={760}
        title={`建库脚本（${ddlDialect}，仅生成不执行）`}
        footer={[
          <Button key="copy" onClick={() => void handleCopyDdl()}>
            复制脚本
          </Button>,
          <Button key="download" type="primary" onClick={handleDownloadDdl}>
            下载 .sql
          </Button>,
        ]}
        onCancel={() => {
          if (!ddlLoading) setDdlOpen(false);
        }}
      >
        {ddlLoading ? (
          <div className="py-8 text-center text-[13px] text-[#667085]">正在生成脚本…</div>
        ) : (
          <pre className="max-h-[420px] overflow-auto rounded-lg bg-[#f7f8fa] p-4 text-[12px] leading-5 text-[#242731] whitespace-pre-wrap">
            {ddlScript || '-- 模型还没有字段，先保存表结构后再生成脚本'}
          </pre>
        )}
      </Modal>
      <StandardAssistantDrawer
        open={assistantRowKey != null && Boolean(modelId)}
        modelId={modelId ?? ''}
        columnName={rows.find((row) => row.key === assistantRowKey)?.columnName ?? ''}
        dataType={rows.find((row) => row.key === assistantRowKey)?.dataType ?? ''}
        applied={{
          stdTypeId: rows.find((row) => row.key === assistantRowKey)?.stdTypeId,
          stdNamingId: rows.find((row) => row.key === assistantRowKey)?.stdNamingId,
          stdCodeSetCode: rows.find((row) => row.key === assistantRowKey)?.stdCodeSetCode,
          stdUnitId: rows.find((row) => row.key === assistantRowKey)?.stdUnitId,
          stdCaliberId: rows.find((row) => row.key === assistantRowKey)?.stdCaliberId,
          stdSecurityId: rows.find((row) => row.key === assistantRowKey)?.stdSecurityId,
        }}
        onClose={() => setAssistantRowKey(null)}
        onApply={(patch) => {
          if (assistantRowKey != null) {
            updateRow(assistantRowKey, patch);
          }
        }}
      />
      <style>{`
        .validation-highlight-row > td {
          background: #fff7e6 !important;
          transition: background 0.3s ease;
        }
        .sortable-row-multi-selected > td {
          background: #e6f4ff !important;
        }
        .sortable-row-dragging > td {
          background: transparent !important;
        }
      `}</style>
    </div>
  );
};

export default ModelingModelDetail;
