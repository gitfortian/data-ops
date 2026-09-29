import {
  ArrowLeftOutlined,
  BarChartOutlined,
  CloudUploadOutlined,
  DatabaseOutlined,
  LinkOutlined,
  PlayCircleOutlined,
  ReloadOutlined,
} from '@ant-design/icons';
import YakOpsEmpty from '@/components/YakOpsEmpty';
import { YakButton, YakTab } from '@/components/ui';
import {
  createDatasetVersion,
  getDatasetForManagement,
  isQueryableDatasetSourceType,
  offlineDataset,
  onlineDataset,
  queryDataset,
  type DatasetManagementDetail,
  type DatasetManagementField,
  type DatasetManagementVersion,
  type DatasetQueryResult,
  type DatasetSourceType,
} from '@/services/dataset';
import { BRAND_THEME } from '@/styles/brand';
import { history, useParams } from '@umijs/max';
import {
  Alert,
  Button,
  ConfigProvider,
  InputNumber,
  Popconfirm,
  Select,
  Spin,
  Table,
  Tag,
  Tooltip,
  message,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useCallback, useEffect, useMemo, useState } from 'react';

import DatasetQueryDiagnostics from './DatasetQueryDiagnostics';
import {
  DETAIL_TABLE_CLASS,
  DetailItem,
  MetricTile,
  SectionCard,
} from './components/DatasetDetailPrimitives';

const SOURCE_TYPE_LABELS: Record<DatasetSourceType, string> = {
  QUERY_REVISION: 'SQL 任务版本',
  SQL_QUERY: 'Standalone SQL',
  TABLE: '数据表',
  VIEW: '视图',
};

const FIELD_TYPE_LABELS: Record<string, string> = {
  STRING: '字符串',
  NUMBER: '数值',
  DATE: '日期',
  DATETIME: '日期时间',
  BOOLEAN: '布尔',
  UNKNOWN: '未知',
};

const formatDateTime = (value?: string) => {
  if (!value) return '-';
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString();
};

const sourceSummary = (version?: DatasetManagementVersion) => {
  if (!version) return '尚未绑定版本';
  if (version.sourceType === 'QUERY_REVISION') {
    return version.sourceTaskAssetId
      ? `TaskAsset #${version.sourceTaskAssetId} · Revision V${version.sourceTaskRevisionNo || '-'}`
      : 'SQL TaskAsset';
  }
  if (version.sourceType === 'SQL_QUERY') {
    return version.dataSourceId
      ? `Standalone SQL · 数据源 ${version.dataSourceId}`
      : 'Standalone SQL';
  }
  return SOURCE_TYPE_LABELS[version.sourceType];
};

type DetailTab =
  | 'overview'
  | 'schema'
  | 'versions'
  | 'query'
  | 'diagnostics'
  | 'source';

function DatasetQueryPlayground({
  dataset,
}: {
  dataset: DatasetManagementDetail;
}) {
  const sortedVersions = useMemo(
    () =>
      [...dataset.versions].sort(
        (left, right) => right.versionNo - left.versionNo,
      ),
    [dataset.versions],
  );
  const [versionNo, setVersionNo] = useState<number | undefined>(
    dataset.currentVersion?.versionNo,
  );
  const [limit, setLimit] = useState(50);
  const [running, setRunning] = useState(false);
  const [result, setResult] = useState<DatasetQueryResult>();
  const [error, setError] = useState('');

  useEffect(() => {
    setVersionNo(dataset.currentVersion?.versionNo);
    setResult(undefined);
    setError('');
  }, [dataset.currentVersion?.versionNo, dataset.id]);

  const selectedVersion = sortedVersions.find(
    (version) => version.versionNo === versionNo,
  );
  const queryable = Boolean(
    selectedVersion && isQueryableDatasetSourceType(selectedVersion.sourceType),
  );
  const disabledReason =
    dataset.status !== 'ONLINE'
      ? 'Dataset 当前已下线，Query Runtime 不接受查询。'
      : !selectedVersion
        ? '请选择一个 Dataset 版本。'
        : !queryable
          ? `${SOURCE_TYPE_LABELS[selectedVersion.sourceType]}尚未接入 Dataset Query Runtime。`
          : '';

  const runQuery = async () => {
    if (!versionNo || disabledReason) return;
    setRunning(true);
    setError('');
    try {
      setResult(
        await queryDataset(dataset.id, {
          versionNo,
          dimensions: [],
          metrics: [],
          filters: [],
          sorts: [],
          limit,
          timeoutSeconds: 30,
        }),
      );
    } catch (queryError) {
      setResult(undefined);
      setError(
        queryError instanceof Error ? queryError.message : 'Dataset 查询失败',
      );
    } finally {
      setRunning(false);
    }
  };

  const previewColumns = useMemo<ColumnsType<Record<string, unknown>>>(
    () =>
      (result?.columns || []).map((column, index) => ({
        title:
          result?.bindings[index]?.displayName || column.label || column.name,
        dataIndex: `c${index}`,
        key: `c${index}`,
        width: 180,
        ellipsis: true,
        render: (value: unknown) => (value == null ? '-' : String(value)),
      })),
    [result],
  );

  const previewRows = useMemo(
    () =>
      (result?.rows || []).map((row, rowIndex) => {
        const record: Record<string, unknown> = { key: rowIndex };
        row.forEach((value, columnIndex) => {
          record[`c${columnIndex}`] = value;
        });
        return record;
      }),
    [result],
  );

  return (
    <SectionCard
      title="Query Playground"
      extra={
        <span className="text-[12px] font-normal text-[#9aa0aa]">
          原始数据预览，不修改 Dataset 版本语义
        </span>
      }
    >
      <div className="space-y-4 p-5 pt-1">
        <div className="flex flex-wrap items-end gap-3 rounded-md bg-[#f7f7f8] p-4">
          <div>
            <div className="mb-1.5 text-[12px] text-[#667085]">
              Dataset 版本
            </div>
            <Select
              value={versionNo}
              variant="filled"
              className="w-[220px]"
              placeholder="请选择版本"
              options={sortedVersions.map((version) => ({
                value: version.versionNo,
                label: `DV${version.versionNo} · ${SOURCE_TYPE_LABELS[version.sourceType]}`,
              }))}
              onChange={setVersionNo}
            />
          </div>

          <div>
            <div className="mb-1.5 text-[12px] text-[#667085]">
              最大返回行数
            </div>
            <InputNumber
              min={1}
              max={1000}
              value={limit}
              variant="filled"
              className="w-[150px]"
              onChange={(value) => setLimit(value || 50)}
            />
          </div>

          <Tooltip title={disabledReason}>
            <span>
              <YakButton
                type="primary"
                icon={<PlayCircleOutlined />}
                loading={running}
                disabled={Boolean(disabledReason)}
                onClick={() => void runQuery()}
              >
                执行查询
              </YakButton>
            </span>
          </Tooltip>
        </div>

        {disabledReason ? (
          <Alert type="info" showIcon message={disabledReason} />
        ) : null}
        {error ? (
          <Alert type="error" showIcon message="查询失败" description={error} />
        ) : null}

        {result ? (
          <div className="overflow-hidden rounded-md border border-solid border-[#eceef1]">
            <div className="flex flex-wrap items-center gap-4 bg-[#f7f7f8] px-4 py-2 text-[12px] text-[#667085]">
              <span>版本：DV{result.datasetVersionNo}</span>
              <span>返回：{result.returnedRows} 行</span>
              <span>耗时：{result.elapsedMillis} ms</span>
              {result.queryId ? <span>Query ID：{result.queryId}</span> : null}
              {result.truncated ? <Tag color="warning">结果已截断</Tag> : null}
            </div>
            <Table<Record<string, unknown>>
              rowKey="key"
              size="small"
              pagination={false}
              columns={previewColumns}
              dataSource={previewRows}
              scroll={{ x: 'max-content', y: 420 }}
              locale={{ emptyText: '查询成功，但没有返回数据' }}
              className={DETAIL_TABLE_CLASS}
            />
          </div>
        ) : !error ? (
          <div className="flex min-h-[300px] items-center justify-center rounded-md bg-[#f7f7f8]">
            <YakOpsEmpty
              width={170}
              height={112}
              title="等待执行查询"
              description="选择不可变 Dataset 版本后，可直接预览该版本数据。"
              showCaption
            />
          </div>
        ) : null}
      </div>
    </SectionCard>
  );
}

export default function DatasetDetailPage() {
  const { id = '' } = useParams<{ id: string }>();
  const [dataset, setDataset] = useState<DatasetManagementDetail>();
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState('');
  const [activeTab, setActiveTab] = useState<DetailTab>('overview');
  const [statusUpdating, setStatusUpdating] = useState(false);
  const [versionCreating, setVersionCreating] = useState(false);

  const loadDataset = useCallback(async () => {
    if (!id) return;
    setLoading(true);
    setLoadError('');
    try {
      setDataset(await getDatasetForManagement(id));
    } catch (error) {
      setDataset(undefined);
      setLoadError(
        error instanceof Error ? error.message : '加载 Dataset 详情失败',
      );
    } finally {
      setLoading(false);
    }
  }, [id]);

  useEffect(() => {
    void loadDataset();
  }, [loadDataset]);

  const toggleStatus = async () => {
    if (!dataset) return;
    setStatusUpdating(true);
    try {
      const next =
        dataset.status === 'ONLINE'
          ? await offlineDataset(dataset.id)
          : await onlineDataset(dataset.id);
      setDataset(next);
      message.success(
        dataset.status === 'ONLINE' ? 'Dataset 已下线' : 'Dataset 已上线',
      );
    } catch (error) {
      message.error(
        error instanceof Error ? error.message : '更新 Dataset 状态失败',
      );
    } finally {
      setStatusUpdating(false);
    }
  };

  const publishNextVersion = async () => {
    if (!dataset) return;
    setVersionCreating(true);
    try {
      setDataset(await createDatasetVersion(dataset.id));
      message.success('Dataset 新版本已发布');
    } catch (error) {
      message.error(
        error instanceof Error ? error.message : '发布 Dataset 新版本失败',
      );
    } finally {
      setVersionCreating(false);
    }
  };

  if (loading) {
    return (
      <div className="flex min-h-[calc(100vh-64px)] items-center justify-center bg-[#f7f7f8]">
        <Spin size="large" />
      </div>
    );
  }

  if (!dataset) {
    return (
      <div className="flex min-h-[calc(100vh-64px)] items-center justify-center bg-[#f7f7f8]">
        <YakOpsEmpty
          width={190}
          height={126}
          title="Dataset 不可用"
          description={loadError || 'Dataset 不存在或当前项目不可见。'}
          showCaption
        />
      </div>
    );
  }

  const currentVersion = dataset.currentVersion;
  const queryable = Boolean(
    currentVersion && isQueryableDatasetSourceType(currentVersion.sourceType),
  );
  const canCreateAnalysis = dataset.status === 'ONLINE' && queryable;
  const canCreateVersion = currentVersion?.sourceType === 'QUERY_REVISION';
  const dimensions = dataset.fields.filter(
    (field) => field.defaultRole === 'DIMENSION',
  ).length;
  const measures = dataset.fields.filter(
    (field) => field.defaultRole === 'MEASURE',
  ).length;
  const online = dataset.status === 'ONLINE';

  const fieldColumns: ColumnsType<DatasetManagementField> = [
    {
      title: '字段',
      dataIndex: 'displayName',
      width: 220,
      render: (value: string, record) => (
        <div>
          <div className="font-medium text-[#30343b]">{value}</div>
          <div className="mt-0.5 text-[11px] text-[#9aa0aa]">
            {record.physicalName}
          </div>
        </div>
      ),
    },
    {
      title: '类型',
      dataIndex: 'dataType',
      width: 110,
      render: (value: string) => FIELD_TYPE_LABELS[value] || value,
    },
    {
      title: '角色',
      dataIndex: 'defaultRole',
      width: 110,
      render: (value: DatasetManagementField['defaultRole']) => (
        <Tag bordered={false} color={value === 'MEASURE' ? 'processing' : 'default'}>
          {value === 'MEASURE' ? '指标' : '维度'}
        </Tag>
      ),
    },
    {
      title: '可空',
      dataIndex: 'nullable',
      width: 80,
      render: (value: boolean) => (value ? '是' : '否'),
    },
    {
      title: '描述',
      dataIndex: 'description',
      minWidth: 240,
      render: (value?: string) => value || '-',
    },
  ];

  const versionColumns: ColumnsType<DatasetManagementVersion> = [
    {
      title: '版本',
      dataIndex: 'versionNo',
      width: 100,
      render: (value: number, record) => (
        <div className="flex items-center gap-2">
          <span className="font-medium text-[#30343b]">DV{value}</span>
          {record.id === dataset.currentVersionId ? (
            <Tag bordered={false}>当前</Tag>
          ) : null}
        </div>
      ),
    },
    {
      title: '来源类型',
      dataIndex: 'sourceType',
      width: 150,
      render: (value: DatasetSourceType) => SOURCE_TYPE_LABELS[value],
    },
    {
      title: '来源快照',
      minWidth: 300,
      render: (_, record) => sourceSummary(record),
    },
    {
      title: '创建时间',
      dataIndex: 'createTime',
      width: 180,
      render: (value?: string) => formatDateTime(value),
    },
  ];

  const tabItems = [
    {
      key: 'overview' as const,
      label: '基本信息',
      children: (
        <div className="space-y-3">
          <SectionCard title="数据集概览">
            <div className="grid grid-cols-2 gap-3 p-5 pt-1 lg:grid-cols-4">
              <MetricTile
                label="字段"
                value={dataset.fields.length}
                hint="当前版本字段合同"
              />
              <MetricTile
                label="维度"
                value={dimensions}
                hint="默认维度字段"
              />
              <MetricTile
                label="指标"
                value={measures}
                hint="默认度量字段"
              />
              <MetricTile
                label="版本"
                value={dataset.versions.length}
                hint="不可变 DatasetVersion"
              />
            </div>
          </SectionCard>

          <SectionCard title="基础信息">
            <div className="grid gap-x-10 gap-y-6 p-5 pt-1 sm:grid-cols-2 xl:grid-cols-3">
              <DetailItem label="Dataset ID">{dataset.id}</DetailItem>
              <DetailItem label="状态">
                {online ? '已上线' : '已下线'}
              </DetailItem>
              <DetailItem label="当前版本">
                {currentVersion ? `DV${currentVersion.versionNo}` : '-'}
              </DetailItem>
              <DetailItem label="当前来源">
                {sourceSummary(currentVersion)}
              </DetailItem>
              <DetailItem label="创建时间">
                {formatDateTime(dataset.createTime)}
              </DetailItem>
              <DetailItem label="更新时间">
                {formatDateTime(dataset.updateTime || dataset.createTime)}
              </DetailItem>
            </div>
          </SectionCard>
        </div>
      ),
    },
    {
      key: 'schema' as const,
      label: `字段 Schema ${dataset.fields.length}`,
      children: (
        <SectionCard
          title="字段 Schema"
          extra={
            <span className="text-[12px] font-normal text-[#9aa0aa]">
              共 {dataset.fields.length} 个字段
            </span>
          }
        >
          <div className="p-5 pt-1">
            <Table<DatasetManagementField>
              rowKey="fieldId"
              pagination={false}
              columns={fieldColumns}
              dataSource={[...dataset.fields].sort(
                (left, right) => left.sortOrder - right.sortOrder,
              )}
              scroll={{ x: 880 }}
              locale={{ emptyText: '当前版本暂无字段定义' }}
              className={DETAIL_TABLE_CLASS}
            />
          </div>
        </SectionCard>
      ),
    },
    {
      key: 'versions' as const,
      label: `版本历史 ${dataset.versions.length}`,
      children: (
        <SectionCard
          title="版本历史"
          extra={
            <span className="text-[12px] font-normal text-[#9aa0aa]">
              当前 {currentVersion ? `DV${currentVersion.versionNo}` : '-'}
            </span>
          }
        >
          <div className="p-5 pt-1">
            <Table<DatasetManagementVersion>
              rowKey="id"
              pagination={false}
              columns={versionColumns}
              dataSource={[...dataset.versions].sort(
                (left, right) => right.versionNo - left.versionNo,
              )}
              scroll={{ x: 900 }}
              locale={{ emptyText: '暂无 Dataset 版本' }}
              className={DETAIL_TABLE_CLASS}
            />
          </div>
        </SectionCard>
      ),
    },
    {
      key: 'query' as const,
      label: 'Query Playground',
      children: <DatasetQueryPlayground dataset={dataset} />,
    },
    {
      key: 'diagnostics' as const,
      label: '运行诊断',
      children: (
        <SectionCard title="运行诊断">
          <div className="p-5 pt-1">
            <DatasetQueryDiagnostics datasetId={dataset.id} />
          </div>
        </SectionCard>
      ),
    },
    {
      key: 'source' as const,
      label: '来源信息',
      children: (
        <div className="space-y-3">
          <SectionCard title="来源信息">
            <div className="grid gap-x-10 gap-y-6 p-5 pt-1 sm:grid-cols-2 xl:grid-cols-3">
              <DetailItem label="来源类型">
                {currentVersion
                  ? SOURCE_TYPE_LABELS[currentVersion.sourceType]
                  : '-'}
              </DetailItem>
              <DetailItem label="来源快照">
                {sourceSummary(currentVersion)}
              </DetailItem>
              <DetailItem label="TaskAsset ID">
                {currentVersion?.sourceTaskAssetId || '-'}
              </DetailItem>
              <DetailItem label="TaskRevision ID">
                {currentVersion?.sourceTaskRevisionId || '-'}
              </DetailItem>
              <DetailItem label="TaskRevision No.">
                {currentVersion?.sourceTaskRevisionNo || '-'}
              </DetailItem>
              <DetailItem label="数据源 ID">
                {currentVersion?.dataSourceId || '-'}
              </DetailItem>
            </div>
          </SectionCard>

          {currentVersion?.sql ? (
            <SectionCard title="冻结 SQL">
              <div className="p-5 pt-1">
                <pre className="m-0 max-h-[520px] overflow-auto whitespace-pre-wrap break-words rounded-md bg-[#181a1f] p-4 font-mono text-[12px] leading-5 text-[#d6d9df]">
                  {currentVersion.sql}
                </pre>
              </div>
            </SectionCard>
          ) : null}

          {currentVersion &&
          !isQueryableDatasetSourceType(currentVersion.sourceType) ? (
            <Alert
              type="warning"
              showIcon
              message={`${SOURCE_TYPE_LABELS[currentVersion.sourceType]}当前仅保留 Dataset 合同值，尚未接入 Query Runtime。`}
            />
          ) : null}
        </div>
      ),
    },
  ];

  const currentSourceLabel = currentVersion
    ? SOURCE_TYPE_LABELS[currentVersion.sourceType]
    : '暂无来源';

  return (
    <ConfigProvider theme={BRAND_THEME}>
      <div className="min-h-[calc(100vh-64px)] bg-[#f7f7f8] text-[#161823]">
        <div className="mx-auto w-full max-w-[1800px] px-4 pb-8 pt-0 lg:px-5">
          <div className="mb-2 flex h-10 items-center">
            <Button
              type="text"
              icon={<ArrowLeftOutlined />}
              className="!h-9 !px-1 !text-[14px] !font-semibold !text-[#30343b]"
              onClick={() => history.push('/dataset')}
            >
              返回数据集列表
            </Button>
          </div>

          <section className="rounded-lg bg-white">
            <div className="grid min-h-[176px] gap-6 px-5 py-6 lg:px-6 xl:grid-cols-[116px_minmax(0,1fr)_430px] xl:items-center">
              <div className="relative flex h-[116px] w-[116px] shrink-0 items-center justify-center overflow-hidden rounded-lg bg-[#f7f7f8]">
                <div className="absolute h-[78px] w-[78px] rounded-full bg-white shadow-[0_8px_22px_rgba(22,24,35,0.05)]" />
                <DatabaseOutlined className="relative z-10 text-[38px] text-[#5d6470]" />
                <span className="absolute bottom-3 z-10 rounded-md bg-white px-2 py-1 text-[10px] font-medium text-[#667085] shadow-sm">
                  {currentVersion ? `DV${currentVersion.versionNo}` : 'Dataset'}
                </span>
              </div>

              <div className="min-w-0">
                <div className="max-w-[620px] truncate text-[16px] font-semibold leading-6 text-[#161823]">
                  {dataset.name}
                </div>
                <div className="mt-1 max-w-[720px] line-clamp-2 text-[12px] leading-5 text-[#8a8f98]">
                  {dataset.description || '暂无描述'}
                </div>

                <div className="mt-2 flex flex-wrap items-center gap-2 text-[11px] leading-4 text-[#667085]">
                  <span
                    className={`inline-block h-[10px] w-[10px] rounded-full ${
                      online ? 'bg-[#20c77a]' : 'bg-[#98a2b3]'
                    }`}
                  />
                  <span>{online ? '已上线' : '已下线'}</span>
                  <span className="text-[#d0d3d8]">·</span>
                  <span>
                    {currentVersion
                      ? `当前版本 DV${currentVersion.versionNo}`
                      : '尚无版本'}
                  </span>
                </div>

                <div className="mt-2 flex min-w-0 flex-wrap items-center gap-1.5 text-[11px] leading-4 text-[#8a8f98]">
                  <span className="max-w-[220px] truncate">
                    {currentSourceLabel}
                  </span>
                  <span className="text-[10px] text-[#b0b5bd]">→</span>
                  <span className="max-w-[420px] truncate">
                    {sourceSummary(currentVersion)}
                  </span>
                </div>
              </div>

              <div className="flex min-w-0 flex-wrap items-center justify-start gap-2 xl:justify-end">
                <Tooltip title="刷新 Dataset 详情">
                  <YakButton
                    iconOnly
                    icon={<ReloadOutlined />}
                    onClick={() => void loadDataset()}
                  />
                </Tooltip>

                <YakButton
                  icon={<LinkOutlined />}
                  href={`/data-analysis/lineage?datasetId=${encodeURIComponent(
                    dataset.id,
                  )}`}
                >
                  查看血缘
                </YakButton>

                <Tooltip
                  title={
                    canCreateVersion
                      ? '从来源 TaskAsset 当前版本生成新的不可变 DatasetVersion'
                      : '仅 SQL TaskAsset 来源支持此操作'
                  }
                >
                  <span>
                    <Popconfirm
                      title="确认发布新的 DatasetVersion？"
                      description="新版本会冻结来源 TaskAsset 当前 Revision，不会修改历史版本。"
                      okText="发布"
                      cancelText="取消"
                      disabled={!canCreateVersion}
                      onConfirm={() => void publishNextVersion()}
                    >
                      <YakButton
                        icon={<CloudUploadOutlined />}
                        disabled={!canCreateVersion}
                        loading={versionCreating}
                      >
                        发布新版本
                      </YakButton>
                    </Popconfirm>
                  </span>
                </Tooltip>

                <Popconfirm
                  title={
                    online
                      ? '确认下线这个 Dataset？'
                      : '确认上线这个 Dataset？'
                  }
                  description={
                    online
                      ? '下线后下游 Analysis、仪表盘和大屏将无法继续查询。'
                      : '上线后可重新被下游消费。'
                  }
                  okText="确认"
                  cancelText="取消"
                  onConfirm={() => void toggleStatus()}
                >
                  <YakButton loading={statusUpdating}>
                    {online ? '下线' : '上线'}
                  </YakButton>
                </Popconfirm>

                <Tooltip
                  title={
                    canCreateAnalysis
                      ? '使用当前 Dataset 创建分析'
                      : 'Dataset 需在线且来源类型已接入 Query Runtime'
                  }
                >
                  <span>
                    <YakButton
                      type="primary"
                      icon={<BarChartOutlined />}
                      disabled={!canCreateAnalysis}
                      href={
                        canCreateAnalysis
                          ? `/data-analysis/chart-analysis?datasetId=${encodeURIComponent(
                              dataset.id,
                            )}`
                          : undefined
                      }
                    >
                      创建分析
                    </YakButton>
                  </span>
                </Tooltip>
              </div>
            </div>
          </section>

          <div className="px-5 lg:px-6">
            <YakTab
              activeKey={activeTab}
              items={tabItems.map(({ key, label }) => ({ key, label }))}
              onChange={(key) => setActiveTab(key as DetailTab)}
            />
          </div>

          <div className="mt-3">
            {tabItems.find((item) => item.key === activeTab)?.children}
          </div>
        </div>
      </div>
    </ConfigProvider>
  );
}
