import { Button, Input, Modal, message, Select, Space, Statistic, Table, Tag, TreeSelect } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useCallback, useEffect, useState } from 'react';
import { useModel, useNavigate, useSearchParams } from '@umijs/max';
import { YakButton, YakEmpty } from '@/components/ui';
import {
  changeMetricStatus,
  deleteMetric,
  getMetricPublicationSummaries,
  getMetricStats,
  listMetricTags,
  pageMetrics,
} from '@/services/metric/api';
import type { MetricRecord, MetricStatus, MetricTagRecord, MetricType } from '@/services/metric/types';
import { getSemanticDomainTree } from '@/services/semantic/api';
import { toDomainTreeData } from '@/services/semantic/domainTree';
import type { SemanticDomainNode } from '@/services/semantic/types';
import {
  formatMetricTime,
  METRIC_STATUS_COLORS,
  METRIC_STATUS_LABELS,
  METRIC_STATUS_OPTIONS,
  METRIC_TYPE_COLORS,
  METRIC_TYPE_LABELS,
  METRIC_TYPE_OPTIONS,
  STAT_PERIOD_LABELS,
} from '../constants';
import MetricEditModal from './components/MetricEditModal';

const parsePositiveId = (value: string | null): number | null => {
  if (!value) return null;
  const parsed = Number(value);
  return Number.isInteger(parsed) && parsed > 0 ? parsed : null;
};

const publicationReadFailureState = (error: unknown): 'FORBIDDEN' | 'UNAVAILABLE' => {
  const response = error as { status?: number; response?: { status?: number } } | null;
  const status = response?.status ?? response?.response?.status;
  return status === 401 || status === 403 ? 'FORBIDDEN' : 'UNAVAILABLE';
};

const MetricManagePage = () => {
  const navigate = useNavigate();
  const { initialState } = useModel('@@initialState');
  const currentUserName = initialState?.currentUser?.userName;
  const [entryParams, setEntryParams] = useSearchParams();
  const initialTagIds = (entryParams.get('tagIds') ?? '')
    .split(',')
    .map((v) => Number(v))
    .filter((v) => Number.isInteger(v) && v > 0);
  const initialDomainId = parsePositiveId(entryParams.get('domainId'));
  const initialProcessId = parsePositiveId(entryParams.get('processId'));
  const [records, setRecords] = useState<MetricRecord[]>([]);
  const [total, setTotal] = useState(0);
  const [pageNo, setPageNo] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [keyword, setKeyword] = useState('');
  const [metricType, setMetricType] = useState<MetricType | ''>('');
  const [status, setStatus] = useState<MetricStatus | ''>('');
  const [domainId, setDomainId] = useState<number | null>(initialDomainId);
  const [processId, setProcessId] = useState<number | null>(initialProcessId);
  const [owner, setOwner] = useState('');
  const [ownerDraft, setOwnerDraft] = useState('');
  const [tagIds, setTagIds] = useState<number[]>(initialTagIds);
  const [domainTree, setDomainTree] = useState<SemanticDomainNode[]>([]);
  const [allTags, setAllTags] = useState<MetricTagRecord[]>([]);
  const [loading, setLoading] = useState(false);
  const [editModalOpen, setEditModalOpen] = useState(false);
  const [editing, setEditing] = useState<MetricRecord | null>(null);
  const [stats, setStats] = useState({ total: 0, atomic: 0, derived: 0, composite: 0 });
  const [publicationVersions, setPublicationVersions] = useState<Record<number, number>>({});
  const [publicationState, setPublicationState] = useState<
    'LOADING' | 'READY' | 'UNAVAILABLE' | 'FORBIDDEN'
  >('LOADING');

  const syncBusinessContext = (nextDomainId: number | null, nextProcessId: number | null) => {
    const next = new URLSearchParams(entryParams);
    if (nextDomainId) next.set('domainId', String(nextDomainId));
    else next.delete('domainId');
    if (nextProcessId) next.set('processId', String(nextProcessId));
    else next.delete('processId');
    setEntryParams(next, { replace: true });
  };

  const loadMetrics = useCallback(
    async (targetPageNo: number, targetPageSize: number) => {
      setLoading(true);
      setPublicationState('LOADING');
      try {
        const result = await pageMetrics({
          pageNo: targetPageNo,
          pageSize: targetPageSize,
          keyword: keyword.trim() || undefined,
          metricType: metricType || undefined,
          status: status || undefined,
          domainId: domainId ?? undefined,
          processId: processId ?? undefined,
          owner: owner.trim() || undefined,
          tagIds: tagIds.length ? tagIds : undefined,
        });
        const nextRecords = result.records ?? [];
        setRecords(nextRecords);
        setTotal(result.total ?? 0);
        if (!nextRecords.length) {
          setPublicationVersions({});
          setPublicationState('READY');
        } else {
          try {
            const summaries = await getMetricPublicationSummaries(nextRecords.map((record) => record.id));
            setPublicationVersions(Object.fromEntries(
              summaries.map((summary) => [summary.metricId, summary.metricVersion]),
            ));
            setPublicationState('READY');
          } catch (error) {
            setPublicationVersions({});
            setPublicationState(publicationReadFailureState(error));
          }
        }
      } catch {
        setRecords([]);
        setTotal(0);
        setPublicationVersions({});
        setPublicationState('UNAVAILABLE');
        message.error('加载指标列表失败');
      } finally {
        setLoading(false);
      }
    },
    [keyword, metricType, status, domainId, processId, owner, tagIds],
  );

  useEffect(() => {
    void loadMetrics(pageNo, pageSize);
  }, [pageNo, pageSize, loadMetrics]);

  useEffect(() => {
    getSemanticDomainTree()
      .then((tree) => setDomainTree(tree ?? []))
      .catch(() => {
        // 域树加载失败仅影响筛选，不提示
      });
    listMetricTags()
      .then((tags) => setAllTags(tags ?? []))
      .catch(() => {
        // 标签加载失败仅影响筛选
      });
  }, []);

  useEffect(() => {
    getMetricStats()
      .then(setStats)
      .catch(() => {
        // 统计加载失败不影响主功能
      });
  }, [records]);

  const handleSearch = (value: string) => {
    setKeyword(value.trim());
    setPageNo(1);
  };

  const openCreate = () => {
    setEditing(null);
    setEditModalOpen(true);
  };

  const openEdit = (record: MetricRecord) => {
    setEditing(record);
    setEditModalOpen(true);
  };

  const toggleStatus = (record: MetricRecord) => {
    const next: MetricStatus = record.status === 'ENABLED' ? 'DISABLED' : 'ENABLED';
    const action = next === 'DISABLED' ? '停用' : '启用';
    Modal.confirm({
      title: `${action}指标`,
      content: `确定${action}「${record.metricName}（${record.metricCode}）」？`,
      okText: action,
      cancelText: '取消',
      onOk: async () => {
        try {
          await changeMetricStatus(record.id, next);
          message.success(`已${action}`);
        } catch {
          // API 报错已由全局错误提示统一展示（含业务原因），此处仅防 onOk 重抛挂住弹窗
        } finally {
          await loadMetrics(pageNo, pageSize);
        }
      },
    });
  };

  const removeMetric = (record: MetricRecord) => {
    Modal.confirm({
      title: '删除指标',
      content: `确定删除「${record.metricName}（${record.metricCode}）」？关联的依赖、标签、版本和使用记录将一并清除。`,
      okText: '删除',
      okType: 'danger',
      cancelText: '取消',
      onOk: async () => {
        try {
          await deleteMetric(record.id);
          message.success('已删除');
        } catch {
          // API 报错已由全局错误提示统一展示（含"被引用不可删除"等原因），此处仅防 onOk 重抛挂住弹窗
        } finally {
          await loadMetrics(pageNo, pageSize);
        }
      },
    });
  };

  const columns: ColumnsType<MetricRecord> = [
    {
      title: '指标编码',
      dataIndex: 'metricCode',
      width: 140,
      render: (code: string, record) => (
        <Button type="link" size="small" onClick={() => navigate(`/metric/manage/${record.id}`)}>
          {code}
        </Button>
      ),
    },
    {
      title: '指标名称',
      dataIndex: 'metricName',
      width: 150,
      ellipsis: true,
    },
    {
      title: '业务域',
      dataIndex: 'domainName',
      width: 100,
      ellipsis: true,
      render: (value?: string) => value || '-',
    },
    {
      title: '类型',
      dataIndex: 'metricType',
      width: 90,
      render: (value: MetricType) => <Tag color={METRIC_TYPE_COLORS[value]}>{METRIC_TYPE_LABELS[value]}</Tag>,
    },
    {
      title: '业务过程',
      dataIndex: 'processName',
      width: 110,
      ellipsis: true,
      render: (value?: string, record?: MetricRecord) => (record?.metricType === 'COMPOSITE' ? '-' : value || '-'),
    },
    {
      title: '口径',
      dataIndex: 'caliberName',
      width: 100,
      ellipsis: true,
      render: (value?: string) => value || '-',
    },
    {
      title: '引用原子指标',
      dataIndex: 'refMetricName',
      width: 130,
      ellipsis: true,
      render: (value?: string, record?: MetricRecord) => (record?.metricType === 'ATOMIC' ? '-' : value || '-'),
    },
    {
      title: '数据来源 DWD',
      dataIndex: 'modelName',
      width: 120,
      ellipsis: true,
      render: (value?: string, record?: MetricRecord) => (record?.metricType === 'ATOMIC' ? value || '-' : '-'),
    },
    {
      title: '统计周期',
      dataIndex: 'statPeriod',
      width: 80,
      render: (value: keyof typeof STAT_PERIOD_LABELS) => STAT_PERIOD_LABELS[value] ?? value,
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 70,
      render: (value: MetricStatus) => <Tag color={METRIC_STATUS_COLORS[value]}>{METRIC_STATUS_LABELS[value]}</Tag>,
    },
    {
      title: '发布版本',
      key: 'publicationVersion',
      width: 220,
      render: (_: unknown, record: MetricRecord) => {
        if (publicationState === 'LOADING') return <Tag>发布状态读取中</Tag>;
        if (publicationState === 'FORBIDDEN') return <Tag color="orange">无权读取发布状态</Tag>;
        if (publicationState === 'UNAVAILABLE') return <Tag color="orange">发布状态暂不可用</Tag>;
        const publishedVersion = publicationVersions[record.id];
        if (publishedVersion == null) return <Tag>未发布</Tag>;
        return (
          <Space size={2} wrap>
            <Tag color="green">Published v{publishedVersion}</Tag>
            {publishedVersion === record.version
              ? <Tag color="green">草稿与发布版一致</Tag>
              : <Tag color="orange">草稿 v{record.version} 已偏离发布版</Tag>}
          </Space>
        );
      },
    },
    { title: '当前版本', dataIndex: 'version', width: 75, align: 'right' as const },
    { title: '负责人', dataIndex: 'owner', width: 80, ellipsis: true, render: (value?: string) => value || '-' },
    {
      title: '更新时间',
      dataIndex: 'updateTime',
      width: 160,
      render: (value?: string) => formatMetricTime(value),
    },
    {
      title: '操作',
      key: 'action',
      width: 200,
      fixed: 'right' as const,
      render: (_, record) => (
        <Space size={4}>
          <Button type="link" size="small" onClick={() => navigate(`/metric/manage/${record.id}`)}>
            详情
          </Button>
          <Button type="link" size="small" onClick={() => openEdit(record)}>
            编辑
          </Button>
          <Button type="link" size="small" danger={record.status === 'ENABLED'} onClick={() => toggleStatus(record)}>
            {record.status === 'ENABLED' ? '停用' : '启用'}
          </Button>
          <Button type="link" size="small" danger onClick={() => removeMetric(record)}>
            删除
          </Button>
        </Space>
      ),
    },
  ];

  const hasFilter = Boolean(keyword || metricType || status || domainId || processId || owner || tagIds.length);

  return (
    <div className="flex min-h-[calc(100dvh-64px)] flex-col bg-white px-6 pb-4 pt-5 text-[#242731] max-md:px-4">
      {/* Header */}
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <div className="text-[20px] font-semibold leading-7">指标管理</div>
          <div className="mt-1 text-[13px] text-[#667085]">定义和管理原子、派生、复合指标，支撑数据分析与业务决策</div>
        </div>
        <YakButton type="primary" className="!h-9 !rounded-lg !px-4 !text-white" onClick={openCreate}>
          新建指标
        </YakButton>
      </div>

      {/* Stats cards */}
      <div className="mt-4 grid grid-cols-4 gap-4">
        <div className="rounded-lg border border-[#e5e7eb] px-4 py-3">
          <Statistic title="指标总数" value={stats.total} valueStyle={{ fontSize: 22 }} />
        </div>
        <div className="rounded-lg border border-[#e5e7eb] px-4 py-3">
          <Statistic title="原子指标" value={stats.atomic} valueStyle={{ fontSize: 22, color: '#242731' }} />
        </div>
        <div className="rounded-lg border border-[#e5e7eb] px-4 py-3">
          <Statistic title="派生指标" value={stats.derived} valueStyle={{ fontSize: 22, color: '#242731' }} />
        </div>
        <div className="rounded-lg border border-[#e5e7eb] px-4 py-3">
          <Statistic title="复合指标" value={stats.composite} valueStyle={{ fontSize: 22, color: '#242731' }} />
        </div>
      </div>

      {/* Filters */}
      <div className="mt-4 flex flex-wrap items-center gap-3">
        <Input.Search allowClear placeholder="按编码或名称搜索" className="!w-[240px]" onSearch={handleSearch} />
        <Select
          allowClear
          placeholder="指标类型"
          className="!w-[140px]"
          value={metricType || undefined}
          onChange={(value) => {
            setMetricType((value ?? '') as MetricType | '');
            setPageNo(1);
          }}
          options={METRIC_TYPE_OPTIONS.filter((option: { label: string; value: string }) => option.value !== '')}
        />
        <Select
          allowClear
          placeholder="状态"
          className="!w-[120px]"
          value={status || undefined}
          onChange={(value) => {
            setStatus((value ?? '') as MetricStatus | '');
            setPageNo(1);
          }}
          options={METRIC_STATUS_OPTIONS.filter((option: { label: string; value: string }) => option.value !== '')}
        />
        <TreeSelect
          allowClear
          placeholder="业务域"
          className="!w-[160px]"
          value={domainId ?? undefined}
          treeData={toDomainTreeData(domainTree)}
          onChange={(value) => {
            const nextDomainId = typeof value === 'number' ? value : null;
            setDomainId(nextDomainId);
            setProcessId(null);
            syncBusinessContext(nextDomainId, null);
            setPageNo(1);
          }}
        />
        {processId ? (
          <Tag
            closable
            onClose={() => {
              setProcessId(null);
              syncBusinessContext(domainId, null);
              setPageNo(1);
            }}
          >
            业务过程 #{processId}
          </Tag>
        ) : null}
        <Select
          allowClear
          mode="multiple"
          maxTagCount="responsive"
          placeholder="标签"
          className="!w-[200px]"
          value={tagIds}
          options={allTags.map((tag) => ({ label: tag.tagName, value: tag.id }))}
          onChange={(value: number[]) => {
            setTagIds(value ?? []);
            setPageNo(1);
          }}
        />
        <Input.Search
          allowClear
          placeholder="负责人"
          className="!w-[180px]"
          value={ownerDraft}
          onChange={(e) => {
            setOwnerDraft(e.target.value);
            // allowClear 清空不会触发 onSearch，需在此同步落筛
            if (!e.target.value && owner) {
              setOwner('');
              setPageNo(1);
            }
          }}
          onSearch={(value) => {
            setOwner(value.trim());
            setPageNo(1);
          }}
        />
        {currentUserName ? (
          <Button
            type={owner === currentUserName ? 'primary' : 'default'}
            onClick={() => {
              const next = owner === currentUserName ? '' : currentUserName;
              setOwnerDraft(next);
              setOwner(next);
              setPageNo(1);
            }}
          >
            只看我的
          </Button>
        ) : null}
      </div>

      {/* Table */}
      <div className="mt-4 flex-1">
        <Table<MetricRecord>
          rowKey="id"
          columns={columns}
          dataSource={records}
          loading={loading}
          scroll={{ x: 1400 }}
          locale={{
            emptyText: (
              <YakEmpty
                compact
                title={hasFilter ? '没有符合筛选条件的指标' : '暂无指标'}
                description={hasFilter ? '调整筛选条件或重置后再试' : '点击右上角「新建指标」创建第一个指标'}
              />
            ),
          }}
          pagination={{
            current: pageNo,
            pageSize,
            total,
            showSizeChanger: true,
            showTotal: (count) => `共 ${count} 条`,
            onChange: (nextPageNo, nextPageSize) => {
              setPageNo(nextPageNo);
              setPageSize(nextPageSize);
            },
          }}
        />
      </div>

      <MetricEditModal
        open={editModalOpen}
        editing={editing}
        onClose={() => setEditModalOpen(false)}
        onSaved={() => {
          setEditModalOpen(false);
          void loadMetrics(pageNo, pageSize);
        }}
      />
    </div>
  );
};

export default MetricManagePage;
