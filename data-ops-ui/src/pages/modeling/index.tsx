import { DownOutlined } from '@ant-design/icons';
import { history, useSearchParams } from '@umijs/max';
import {
  Button,
  Checkbox,
  Dropdown,
  Input,
  Modal,
  message,
  Select,
  Space,
  Table,
  type TableColumnsType,
  Tag,
  Tooltip,
} from 'antd';
import type { PointerEvent as ReactPointerEvent } from 'react';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { YakButton, YakEmpty } from '@/components/ui';
import usePermissionAccess from '@/hooks/usePermissionAccess';
import { useSecurityProject } from '@/contexts/SecurityProjectContext';
import { ModelTagAssignModal } from '@/pages/modeling/components/ModelAssignModals';
import ModelCreateWizardDrawer from '@/pages/modeling/components/ModelCreateWizardDrawer';
import ModelingStartGuide from '@/pages/modeling/components/ModelingStartGuide';
import ModelEditModal from '@/pages/modeling/components/ModelEditModal';
import OdsSourceJourney from '@/pages/modeling/components/OdsSourceJourney';
import ModelTreePane from '@/pages/modeling/components/ModelTreePane';
import ModelingStudioHeader from '@/pages/modeling/components/ModelingStudioHeader';
import RecycleBinDrawer from '@/pages/modeling/components/RecycleBinDrawer';
import TagManageModal from '@/pages/modeling/components/TagManageModal';
import {
  formatModelingTimeMinute,
  MODELING_DIALECT_LABELS,
  MODELING_STATUS_HINTS,
  MODELING_STATUS_LABELS,
} from '@/pages/modeling/constants';
import {
  createModelingModel,
  deleteModelingModel,
  listModelingTags,
  pageModelingModels,
} from '@/services/modeling/api';
import type { ModelingModelRecord, ModelingTagRecord } from '@/services/modeling/types';
import { getSemanticDomainTree, getSemanticProcess, listSemanticLayers, pageSemanticProcesses } from '@/services/semantic/api';
import type { SemanticDomainNode, SemanticLayerRecord, SemanticProcessRecord } from '@/services/semantic/types';
import {
  parseJourneyProcessId, processToLogical, processToSemantic,
} from '@/utils/journeyNavigation';

const PAGE_SIZE_OPTIONS = [10, 20, 50];
const TREE_WIDTH_STORAGE_KEY = 'yak.modeling.tree.width';
const HIDDEN_COLUMNS_KEY = 'yak.modeling.hiddenColumns';

const TREE_MIN_WIDTH = 180;
const TREE_MAX_WIDTH = 480;

const clampTreeWidth = (width: number) => Math.min(TREE_MAX_WIDTH, Math.max(TREE_MIN_WIDTH, Math.round(width)));

const ModelingWorkspace: React.FC = () => {
  const { can } = usePermissionAccess();
  const { currentProject } = useSecurityProject();
  const currentProjectId = currentProject?.id;
  const [searchParams, setSearchParams] = useSearchParams();
  const processParam = searchParams.get('processId');
  const requestedProcessId = parseJourneyProcessId(processParam);
  const [journeyProcess, setJourneyProcess] = useState<SemanticProcessRecord>();
  const [journeyReady, setJourneyReady] = useState(processParam === null);
  const listEpoch = useRef(0);
  const dictionaryEpoch = useRef(0);
  const [journeyProblem, setJourneyProblem] = useState<string>();
  // 列表与过滤状态
  const [records, setRecords] = useState<ModelingModelRecord[]>([]);
  const [total, setTotal] = useState(0);
  const [pageNo, setPageNo] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [keyword, setKeyword] = useState('');
  const [selectedTagIds, setSelectedTagIds] = useState<number[]>([]);
  // 2026-09-17 筛选扩展:分层/业务过程/状态/目标库
  const [layerFilter, setLayerFilter] = useState<string | undefined>(undefined);
  const [processFilter, setProcessFilter] = useState<number | undefined>(requestedProcessId);
  const [statusFilter, setStatusFilter] = useState<string | undefined>(undefined);
  const [dbFilter, setDbFilter] = useState<string | undefined>(undefined);
  const [layers, setLayers] = useState<SemanticLayerRecord[]>([]);
  const [processes, setProcesses] = useState<SemanticProcessRecord[]>([]);
  const [domainTree, setDomainTree] = useState<SemanticDomainNode[]>([]);
  const [domainFilter, setDomainFilter] = useState<number | undefined>(undefined);
  const [optionsLoading, setOptionsLoading] = useState(false);
  // 2026-09-17 列设置:低频列(目标/所属目录/标签)可隐藏,localStorage 持久化
  const [hiddenColumns, setHiddenColumns] = useState<string[]>(() => {
    try {
      const stored = window.localStorage.getItem(HIDDEN_COLUMNS_KEY);
      return stored ? (JSON.parse(stored) as string[]) : [];
    } catch {
      return [];
    }
  });
  const [loading, setLoading] = useState(false);

  // 目录树面板状态（复刻数据开发工作台）
  const [treeWidth, setTreeWidth] = useState(240);
  const [treeCollapsed, setTreeCollapsed] = useState(false);

  // 组织数据
  const [tags, setTags] = useState<ModelingTagRecord[]>([]);

  // 操作状态
  const [creating, setCreating] = useState(false);
  const [wizardOpen, setWizardOpen] = useState(false);
  const [actionId, setActionId] = useState<ModelingModelRecord['id']>();
  const [tagsManageOpen, setTagsManageOpen] = useState(false);
  const [recycleOpen, setRecycleOpen] = useState(false);
  const [tagTarget, setTagTarget] = useState<ModelingModelRecord>();
  const [editTarget, setEditTarget] = useState<ModelingModelRecord>();

  useEffect(() => {
    const stored = window.localStorage.getItem(TREE_WIDTH_STORAGE_KEY);
    if (stored) setTreeWidth(clampTreeWidth(Number(stored)));
  }, []);

  const handleTreeResizeStart = useCallback(
    (event: ReactPointerEvent) => {
      if (treeCollapsed) return;
      event.preventDefault();
      const startX = event.clientX;
      const startWidth = treeWidth;
      const previousCursor = document.body.style.cursor;
      const previousUserSelect = document.body.style.userSelect;
      document.body.style.cursor = 'col-resize';
      document.body.style.userSelect = 'none';

      const handlePointerMove = (moveEvent: PointerEvent) => {
        setTreeWidth(clampTreeWidth(startWidth + moveEvent.clientX - startX));
      };
      const finish = (upEvent: PointerEvent) => {
        const width = clampTreeWidth(startWidth + upEvent.clientX - startX);
        setTreeWidth(width);
        window.localStorage.setItem(TREE_WIDTH_STORAGE_KEY, String(width));
        document.body.style.cursor = previousCursor;
        document.body.style.userSelect = previousUserSelect;
        window.removeEventListener('pointermove', handlePointerMove);
        window.removeEventListener('pointerup', finish);
        window.removeEventListener('pointercancel', finish);
      };

      window.addEventListener('pointermove', handlePointerMove);
      window.addEventListener('pointerup', finish);
      window.addEventListener('pointercancel', finish);
    },
    [treeCollapsed, treeWidth],
  );

  const loadRecords = useCallback(
    async (
      page: number,
      size: number,
      search: string,
      tagIds: number[],
      layerCode?: string,
      processId?: number,
      status?: string,
      domainId?: number,
    ) => {
      const epoch = ++listEpoch.current;
      setLoading(true);
      try {
        const data = await pageModelingModels({
          pageNo: page,
          pageSize: size,
          keyword: search.trim() || undefined,
          tagIds: tagIds.length ? tagIds : undefined,
          layerCode,
          processId,
          domainId,
          status,
        });
        if (epoch !== listEpoch.current) return;
        setRecords(data?.bizData || []);
        setTotal(data?.pagination?.total || 0);
      } catch (error) {
        if (epoch !== listEpoch.current) return;
        setRecords([]);
        setTotal(0);
        message.error(error instanceof Error ? error.message : '模型列表加载失败');
      } finally {
        if (epoch === listEpoch.current) setLoading(false);
      }
    },
    [currentProjectId],
  );

  /** 筛选选项字典(分层/业务过程/业务域,2026-09-17):列表页加载一次,规模小。 */
  const loadFilterOptions = useCallback(async () => {
    const epoch = ++dictionaryEpoch.current;
    setOptionsLoading(true);
    try {
      const [layerList, processPage, domains] = await Promise.all([
        listSemanticLayers(),
        pageSemanticProcesses({ pageNo: 1, pageSize: 200 }),
        getSemanticDomainTree(),
      ]);
      if (epoch !== dictionaryEpoch.current) return;
      setLayers(layerList ?? []);
      setProcesses(processPage.bizData ?? []);
      setDomainTree(domains ?? []);
    } catch {
      if (epoch !== dictionaryEpoch.current) return;
      setLayers([]);
      setProcesses([]);
      setDomainTree([]);
    } finally {
      if (epoch === dictionaryEpoch.current) setOptionsLoading(false);
    }
  }, [currentProjectId]);

  const loadOrganization = useCallback(async () => {
    try {
      const tagList = await listModelingTags();
      setTags(tagList || []);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '标签加载失败');
    }
  }, [currentProjectId]);

  /** 业务域扁平列表(右侧筛选 + 目录树按业务域视图,2026-09-17)。 */
  const flatDomains = useMemo(() => {
    const out: { id: number; name: string }[] = [];
    const walk = (nodes: SemanticDomainNode[]) => {
      nodes.forEach((node) => {
        out.push({ id: node.id, name: node.name });
        walk(node.children ?? []);
      });
    };
    walk(domainTree);
    return out;
  }, [domainTree]);

  // The URL processId is just a routing hint. Semantic must confirm
  // current-project access before opening this scoped physical catalog.
  useEffect(() => {
    let active = true;
    // A previous process filter's pending response must not repaint this route.
    listEpoch.current += 1;
    setRecords([]);
    setTotal(0);
    setJourneyProcess(undefined);
    setJourneyProblem(undefined);
    if (processParam === null) {
      setJourneyReady(true);
      return () => { active = false; };
    }
    setJourneyReady(false);
    if (!requestedProcessId) {
      setJourneyProblem('无效的业务过程参数，请从业务过程列表重新选择');
      return () => { active = false; };
    }
    void getSemanticProcess(requestedProcessId).then((process) => {
      if (!active) return;
      if (!process || Number(process.id) !== requestedProcessId) {
        throw new Error('业务过程不属于当前授权项目');
      }
      setJourneyProcess(process);
      setProcesses((previous) => previous.some((item) => item.id === process.id)
        ? previous : [...previous, process]);
      setProcessFilter(requestedProcessId);
      setDomainFilter(undefined);
      setPageNo(1);
      setJourneyReady(true);
    }).catch(() => {
      if (!active) return;
      setRecords([]);
      setTotal(0);
      setJourneyProblem('指定业务过程不存在、已失效或当前项目无权访问');
    });
    return () => { active = false; };
  }, [processParam, requestedProcessId, currentProjectId]);

  useEffect(() => {
    if (!journeyReady || journeyProblem) return;
    void loadRecords(
      pageNo,
      pageSize,
      keyword,
      selectedTagIds,
      layerFilter,
      processFilter,
      statusFilter,
      domainFilter,
    );
  }, [
    journeyReady,
    journeyProblem,
    loadRecords,
    pageNo,
    pageSize,
    keyword,
    selectedTagIds,
    layerFilter,
    processFilter,
    statusFilter,
    domainFilter,
  ]);

  useEffect(() => {
    // Never carry the creation drawer's current Project selections into
    // another Project; dictionaries are fetched under the active Owner scope.
    setWizardOpen(false);
    setDomainTree([]);
    setProcesses([]);
    setLayers([]);
    void loadOrganization();
    void loadFilterOptions();
  }, [loadOrganization, loadFilterOptions]);

  const domainNameById = useMemo(() => {
    const map = new Map<number, string>();
    flatDomains.forEach((domain) => map.set(domain.id, domain.name));
    return map;
  }, [flatDomains]);

  const tagNameById = useMemo(() => {
    const map = new Map<number, string>();
    tags.forEach((tag) => map.set(tag.id!, tag.name!));
    return map;
  }, [tags]);

  /** 列设置(2026-09-17):低频列显隐,localStorage 持久化。 */
  const toggleHiddenColumn = (key: string, visible: boolean) => {
    setHiddenColumns((prev) => {
      const next = visible ? prev.filter((item) => item !== key) : [...prev, key];
      window.localStorage.setItem(HIDDEN_COLUMNS_KEY, JSON.stringify(next));
      return next;
    });
  };

  const goSemantic = () => {
    history.push('/semantic/domains');
  };

  const clearJourney = () => {
    const next = new URLSearchParams(searchParams);
    next.delete('processId');
    setSearchParams(next, { replace: true });
    setJourneyProcess(undefined);
    setJourneyProblem(undefined);
    setProcessFilter(undefined);
    setPageNo(1);
  };

  const handleSearch = (value: string) => {
    setKeyword(value);
    setPageNo(1);
  };

  const handleWizardSubmit = async (payload: import('@/pages/modeling/components/ModelCreateWizardDrawer').WizardSubmitPayload) => {
    setCreating(true);
    try {
      const created = await createModelingModel(payload.base);
      message.success('物理模型草稿已创建；下一步请保存表结构并核对逻辑依据和来源映射');
      setWizardOpen(false);
      setKeyword('');
      setPageNo(1);
      await loadRecords(
        1,
        pageSize,
        '',
        selectedTagIds,
        layerFilter,
        processFilter,
        statusFilter,
        domainFilter,
      );
      if (!created.id) return;
      history.push(`/modeling/models/${created.id}?tab=structure`);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '模型创建失败');
    } finally {
      setCreating(false);
    }
  };

  const handleDelete = (record: ModelingModelRecord) => {
    Modal.confirm({
      centered: true,
      title: '删除物理模型',
      content: `确定删除模型「${record.name || record.code}」吗？删除后可在回收站恢复。`,
      okText: '删除',
      cancelText: '取消',
      okType: 'primary',
      okButtonProps: { size: 'small', danger: true },
      cancelButtonProps: { size: 'small' },
      maskClosable: true,
      async onOk() {
        if (actionId) return;
        setActionId(record.id);
        try {
          await deleteModelingModel(record.id!);
          message.success('模型已移入回收站');
          await loadRecords(
            pageNo,
            pageSize,
            keyword,
            selectedTagIds,
            layerFilter,
            processFilter,
            statusFilter,
            domainFilter,
          );
        } catch (error) {
          message.error(error instanceof Error ? error.message : '模型删除失败');
        } finally {
          setActionId(undefined);
        }
      },
    });
  };



  const columns: TableColumnsType<ModelingModelRecord> = useMemo(
    () => [
      {
        title: '模型名称',
        dataIndex: 'name',
        width: 200,
        render: (value: string, record) => <a onClick={() => history.push(`/modeling/models/${record.id}`)}>{value}</a>,
      },
      { title: '模型编码', dataIndex: 'code', width: 180 },
      {
        title: '分层',
        dataIndex: 'layerCode',
        width: 80,
        render: (value?: string) => (value ? <Tag color="blue">{value}</Tag> : '-'),
      },
      {
        title: '业务过程',
        dataIndex: 'processName',
        width: 100,
        ellipsis: true,
        render: (value?: string) => value || '-',
      },
      ...(hiddenColumns.includes('target')
        ? []
        : [
            {
              // 2026-09-17 布局收敛:目标方言 + 目标库合并为"目标"
              title: '目标',
              dataIndex: 'databaseName',
              width: 150,
              render: (_: unknown, record: ModelingModelRecord) => {
                const db = record.databaseName;
                const dialect = MODELING_DIALECT_LABELS[record.dialect || ''] || record.dialect;
                return db || dialect ? `${db || '-'}（${dialect || '-'}）` : '-';
              },
            },
          ]),
      ...(hiddenColumns.includes('directory')
        ? []
        : [
            {
              title: '业务域',
              dataIndex: 'domainId',
              width: 150,
              render: (value?: number) =>
                value ? domainNameById.get(value) || '-' : <span className="text-[#667085]">未分类</span>,
            },
          ]),
      ...(hiddenColumns.includes('tags')
        ? []
        : [
            {
              title: '标签',
              dataIndex: 'tagIds',
              width: 120,
              render: (value?: number[]) =>
                value?.length ? (
                  <Space size={4} wrap>
                    {value.map((tagId) => (
                      <Tag key={tagId}>{tagNameById.get(tagId) || tagId}</Tag>
                    ))}
                  </Space>
                ) : (
                  <span className="text-[#667085]">-</span>
                ),
            },
          ]),
      {
        title: '状态',
        dataIndex: 'status',
        width: 80,
        render: (value: string) => (
          <Tooltip title={MODELING_STATUS_HINTS[value]}>
            <Tag>{MODELING_STATUS_LABELS[value] || value}</Tag>
          </Tooltip>
        ),
      },
      {
        title: '更新时间',
        dataIndex: 'updateTime',
        width: 160,
        render: (value?: string) => <span className="whitespace-nowrap">{formatModelingTimeMinute(value)}</span>,
      },
      {
        title: '操作',
        key: 'actions',
        width: 180,
        render: (_, record) => (
          <Space size={0}>
            <Button type="link" size="small" onClick={() => setEditTarget(record)}>
              编辑
            </Button>
            <Dropdown
              menu={{
                items: [
                  { key: 'detail', label: '详情' },
                  { key: 'move', label: '移动' },
                  { key: 'tags', label: '标签' },
                  { key: 'delete', label: '删除', danger: true },
                ],
                onClick: ({ key }) => {
                  if (key === 'detail') {
                    history.push(`/modeling/models/${record.id}`);
                  } else if (key === 'tags') {
                    setTagTarget(record);
                  } else if (key === 'delete') {
                    handleDelete(record);
                  }
                },
              }}
            >
              <Button type="link" size="small">
                更多
                <DownOutlined style={{ fontSize: 10 }} />
              </Button>
            </Dropdown>
          </Space>
        ),
      },
    ],
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [actionId, domainNameById, tagNameById, hiddenColumns],
  );

  if (searchParams.get('sourceJourney') === 'ods') return (
    <div className="modeling-studio-page modeling-studio-detail">
      <ModelingStudioHeader area="physical" title="ODS · 来源建模" eyebrow="数据建设 / 来源到 ODS"
        description="核对真实来源表，选择或创建 ODS 物理目标；保存后返回同一来源。资产纳管与本操作相互独立。"
        actions={<Button onClick={() => history.push('/modeling')}>返回模型总览</Button>} />
      <OdsSourceJourney />
    </div>
  );

  return (
    <div className="modeling-studio-page modeling-studio-catalog">
      <aside className="modeling-studio-catalog-rail">
      <ModelTreePane
        domainTree={domainTree}
        semanticLoading={optionsLoading}
        selectedDomainId={domainFilter}
        width={treeWidth}
        collapsed={treeCollapsed}
        onSelect={(domainId) => {
          // 域树过滤 = 该域全部业务过程(与右侧"全部业务域"下拉同一状态)
          setDomainFilter(domainId);
          if (domainId != null) setProcessFilter(undefined);
          setPageNo(1);
        }}
        onResizeStart={handleTreeResizeStart}
        onCollapsedChange={setTreeCollapsed}
        onGoSemantic={goSemantic}
      />
      </aside>

      <div className="modeling-studio-catalog-body">
        <ModelingStudioHeader area="catalog" title="数仓模型设计中心"
          description="围绕业务过程组织逻辑与物理设计。选择分层进入真实模型；版本、标准与加工证据分别核验。"
          context={<><span>当前项目 #{currentProjectId ?? '未选定'}</span><span>当前筛选：{total} 个物理模型</span></>}
          actions={<>
            <Input.Search allowClear placeholder="搜索模型名称或编码" className="!w-[230px]"
              onSearch={handleSearch} />
            <Button onClick={() => history.push(journeyProcess
              ? processToLogical(journeyProcess.id) : '/modeling/logical')}>逻辑模型</Button>
            <Button onClick={() => history.push('/modeling/mainline')}>分层主线</Button>
            <Dropdown menu={{ items: [
              { key: 'recycle', label: '回收站' },
              { key: 'tags', label: '标签管理' },
            ], onClick: ({ key }) => {
              if (key === 'recycle') setRecycleOpen(true);
              if (key === 'tags') setTagsManageOpen(true);
            } }}>
              <Button>管理 <DownOutlined /></Button>
            </Dropdown>
            <Button type="primary" onClick={() => setWizardOpen(true)}
              disabled={Boolean(journeyProblem) || !journeyReady || optionsLoading || !can('modeling:create')}>
              新建物理草稿
            </Button>
          </>} />
        {journeyProblem && <div role="alert" className="mb-3">
          <span className="text-red-600">{journeyProblem}</span>
          <Button onClick={() => history.push('/semantic/processes')}>返回业务过程</Button>
          <Button onClick={clearJourney}>查看全部物理模型</Button>
        </div>}
        <ModelingStartGuide
          process={journeyProcess}
          onBusiness={() => history.push(journeyProcess ? processToSemantic(journeyProcess.id) : '/semantic/processes')}
          onLogical={() => history.push(journeyProcess ? processToLogical(journeyProcess.id) : '/modeling/logical')}
          onSource={() => history.push('/data-source')}
          onCreate={() => setWizardOpen(true)}
          canCreate={can('modeling:create') && !Boolean(journeyProblem) && journeyReady && !optionsLoading}
          loading={optionsLoading || !journeyReady}
        />
        <section className="modeling-studio-layer-panel" aria-label="模型分层导航">
          <div className="modeling-studio-section-header">
            <div>
              <span className="modeling-studio-overline">物理设计工作区</span>
              <h2>按数仓分层查找模型</h2>
              <p>仅展示当前项目已配置的分层。切换后重新查询服务端，不用当前页数据推断项目总量。</p>
            </div>
            <span className="modeling-studio-hint">ODS 来源贴合 · 非 ODS 需审阅逻辑依据与数据标准</span>
          </div>
          <div className="modeling-studio-layer-list" role="group" aria-label="选择数仓分层">
            <button type="button" className={'modeling-studio-layer-item' + (!layerFilter ? ' is-active' : '')}
              aria-pressed={!layerFilter} onClick={() => { setLayerFilter(undefined); setDbFilter(undefined); setPageNo(1); }}>
              全部模型
            </button>
            {layers.map((layer) => <button key={layer.code} type="button"
              className={'modeling-studio-layer-item' + (layerFilter === layer.code ? ' is-active' : '')}
              aria-pressed={layerFilter === layer.code}
              onClick={() => { setLayerFilter(layer.code); setDbFilter(undefined); setPageNo(1); }}>
              {layer.name || layer.code} · {layer.code}
            </button>)}
          </div>
        </section>
        {journeyProcess && <div className="mb-3 flex flex-wrap items-center gap-3 text-[12px] text-[#667085]">
          <span>当前过程筛选：<strong>{journeyProcess.name}</strong>（{journeyProcess.code}）</span>
          {can('data-development:read') && <Tooltip
            title="仅进入现有 DataDev 工作台；不传递模型版本、不创建任务、不声明交接批准">
            <Button type="link" size="small" onClick={() => history.push('/data-development')}>
              打开 DataDev 工作台（手动）
            </Button>
          </Tooltip>}
          <Button type="link" size="small" onClick={clearJourney}>清除过程筛选</Button>
        </div>}
        <section className="modeling-studio-catalog-results" aria-label="模型查询结果">
          <div className="modeling-studio-section-header">
            <div>
              <span className="modeling-studio-overline">已保存的模型对象</span>
              <h2>物理模型库 <span className="!text-[12px] !font-normal !text-[#7a8798]">共 {total} 条查询结果</span></h2>
              <p>选择模型继续编辑、检查来源映射或查看冻结版本。</p>
            </div>
          </div>
          <div className="mb-3 mt-4 flex flex-wrap items-center gap-2">
          {/* 目录 vs 标签职责提示(2026-09-17):目录=层级分类,标签=横向标记 */}
          <Tooltip title="目录=层级分类(如 交易/订单)；标签=横向标记(如 核心模型/临时模型)">
            <span className="cursor-help text-[13px] text-[#667085]">标签筛选</span>
          </Tooltip>
          <Select
            allowClear
            mode="multiple"
            maxTagCount={3}
            placeholder="全部标签"
            className="min-w-[220px]"
            value={selectedTagIds}
            options={tags.map((tag) => ({ value: tag.id!, label: tag.name! }))}
            onChange={(values) => {
              setSelectedTagIds(values);
              setPageNo(1);
            }}
          />
          {/* 2026-09-17:业务域筛选(与业务过程互斥,选中域 = 该域全部过程) */}
          <Select
            allowClear
            showSearch
            optionFilterProp="label"
            placeholder="全部业务域"
            className="min-w-[140px]"
            value={domainFilter}
            options={flatDomains.map((domain) => ({ value: domain.id, label: domain.name }))}
            onChange={(value) => {
              if (processParam !== null) clearJourney();
              setDomainFilter(value);
              setProcessFilter(undefined);
              setPageNo(1);
            }}
          />
          <Select
            allowClear
            showSearch
            optionFilterProp="label"
            placeholder="全部业务过程"
            className="min-w-[160px]"
            value={processFilter}
            options={processes.map((process) => ({
              value: process.id,
              label: process.name,
            }))}
            onChange={(value) => {
              const next = new URLSearchParams(searchParams);
              if (value) next.set('processId', String(value));
              else next.delete('processId');
              setSearchParams(next, { replace: true });
              setProcessFilter(value);
              setDomainFilter(undefined);
              setPageNo(1);
            }}
          />
          <Select
            allowClear
            placeholder="全部状态"
            className="min-w-[110px]"
            value={statusFilter}
            options={Object.keys(MODELING_STATUS_LABELS).map((status) => ({
              value: status,
              label: MODELING_STATUS_LABELS[status],
            }))}
            onChange={(value) => {
              setStatusFilter(value);
              setPageNo(1);
            }}
          />
          <Select
            allowClear
            placeholder="全部目标库"
            className="min-w-[140px]"
            value={dbFilter}
            options={[
              ...new Set(layers.map((layer) => layer.databaseName).filter((name): name is string => Boolean(name))),
            ].map((name) => ({ value: name, label: name }))}
            onChange={(value) => {
              // 目标库是分层配置的派生属性:按库名反查分层,落到分层筛选(后端以 layer_code 过滤)
              setDbFilter(value);
              setLayerFilter(value ? layers.find((layer) => layer.databaseName === value)?.code : undefined);
              setPageNo(1);
            }}
          />
          {/* 列设置(2026-09-17):低频列可隐藏,适配窄屏 */}
          <Dropdown
            menu={{
              items: [
                {
                  key: 'target',
                  label: (
                    <Checkbox
                      checked={!hiddenColumns.includes('target')}
                      onChange={(event) => toggleHiddenColumn('target', event.target.checked)}
                    >
                      目标
                    </Checkbox>
                  ),
                },
                {
                  key: 'directory',
                  label: (
                    <Checkbox
                      checked={!hiddenColumns.includes('directory')}
                      onChange={(event) => toggleHiddenColumn('directory', event.target.checked)}
                    >
                      业务域
                    </Checkbox>
                  ),
                },
                {
                  key: 'tags',
                  label: (
                    <Checkbox
                      checked={!hiddenColumns.includes('tags')}
                      onChange={(event) => toggleHiddenColumn('tags', event.target.checked)}
                    >
                      标签
                    </Checkbox>
                  ),
                },
              ],
            }}
          >
            <Button size="small">列设置</Button>
          </Dropdown>
          {keyword ||
          selectedTagIds.length ||
          layerFilter ||
          domainFilter ||
          processFilter ||
          statusFilter ||
          dbFilter ? (
            <Button
              size="small"
              onClick={() => {
                setKeyword('');
                setSelectedTagIds([]);
                setLayerFilter(undefined);
                setDomainFilter(undefined);
                setProcessFilter(undefined);
                setStatusFilter(undefined);
                setDbFilter(undefined);
                setPageNo(1);
              }}
            >
              重置筛选
            </Button>
          ) : null}
        </div>

        <Table<ModelingModelRecord>
          rowKey="id"
          loading={loading}
          columns={columns}
          dataSource={records}
          rowClassName={() => 'cursor-pointer'}
          // 点行即进详情(与资产目录一致);行内链接/按钮自己处理点击,不重复跳转。
          onRow={(record) => ({
            onClick: (event) => {
              const target = event.target as HTMLElement;
              if (target.closest('a, button, input, .ant-checkbox-wrapper, .ant-dropdown-trigger')) return;
              if (window.getSelection()?.toString()) return;
              history.push(`/modeling/models/${record.id}`);
            },
          })}
          locale={{
            emptyText: (
              <YakEmpty
                compact
                title={
                  keyword || selectedTagIds.length || domainFilter ? '没有符合筛选条件的模型' : '还没有物理模型'
                }
                description={
                  keyword || selectedTagIds.length || domainFilter
                    ? '调整筛选条件或重置后再试'
                    : '点击右上角新建物理模型'
                }
              />
            ),
          }}
          pagination={{
            current: pageNo,
            pageSize,
            total,
            showSizeChanger: true,
            showQuickJumper: true,
            pageSizeOptions: PAGE_SIZE_OPTIONS,
            showTotal: (t, range) => `第 ${range[0]}-${range[1]} 条，共 ${t} 条`,
            onChange: (nextPage, nextPageSize) => {
              setPageNo(nextPageSize !== pageSize ? 1 : nextPage);
              setPageSize(nextPageSize);
            },
          }}
        />
        </section>
      </div>

      <ModelEditModal
        open={Boolean(editTarget)}
        model={editTarget}
        layers={layers}
        processes={processes}
        domainTree={domainTree}
        onClose={() => setEditTarget(undefined)}
        onSaved={async () => {
          await loadRecords(
            pageNo,
            pageSize,
            keyword,
            selectedTagIds,
            layerFilter,
            processFilter,
            statusFilter,
            domainFilter,
          );
          await loadOrganization();
        }}
      />

      <ModelCreateWizardDrawer
        open={wizardOpen}
        initialProcessId={journeyProcess?.id}
        loading={creating}
        layers={layers}
        processes={journeyProcess && !processes.some(x => x.id === journeyProcess.id)
          ? [journeyProcess, ...processes] : processes}
        domainTree={domainTree}
        onGoLogical={(id) => {
          setWizardOpen(false);
          history.push(processToLogical(id));
        }}
        onGoSource={() => {
          setWizardOpen(false);
          history.push('/data-source');
        }}
        onClose={() => setWizardOpen(false)}
        onSubmit={(payload) => void handleWizardSubmit(payload)}
      />

      <TagManageModal
        open={tagsManageOpen}
        onClose={() => setTagsManageOpen(false)}
        onChanged={async () => {
          await loadOrganization();
        }}
      />

      <RecycleBinDrawer
        open={recycleOpen}
        onClose={() => setRecycleOpen(false)}
        onChanged={async () => {
          await loadRecords(
            pageNo,
            pageSize,
            keyword,
            selectedTagIds,
            layerFilter,
            processFilter,
            statusFilter,
            domainFilter,
          );
        }}
      />

      <ModelTagAssignModal
        open={Boolean(tagTarget)}
        model={tagTarget}
        tags={tags}
        onClose={() => setTagTarget(undefined)}
        onSaved={async () => {
          await loadRecords(
            pageNo,
            pageSize,
            keyword,
            selectedTagIds,
            layerFilter,
            processFilter,
            statusFilter,
            domainFilter,
          );
        }}
      />
    </div>
  );
};

export default ModelingWorkspace;
