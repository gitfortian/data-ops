import YakOpsEmpty from '@/components/YakOpsEmpty';
import { YakButton, YakTab } from '@/components/ui';
import type {
  CatalogDataset,
  CatalogDatasetFieldRole,
  CatalogDatasetSourceType,
} from '@/services/data-analysis';
import { isQueryableDatasetSourceType } from '@/services/dataset';
import { Popconfirm, Table, Tag, Tooltip } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { BarChart3, GitBranch, Rows3, Sigma, TableProperties } from 'lucide-react';
import {
  FIELD_ROLE_LABELS,
  FIELD_TYPE_LABELS,
  SOURCE_TYPE_LABELS,
} from '../constants';
import type { CatalogDetailTab } from '../types';
import {
  formatDateTime,
  getDatasetVersionSourceSummary,
  getSchemaSummary,
} from '../utils';
import DatasetLineageTab from './DatasetLineageTab';

interface CatalogDatasetDetailProps {
  dataset: CatalogDataset;
  activeTab: CatalogDetailTab;
  statusUpdatingId: string;
  onTabChange: (tab: CatalogDetailTab) => void;
  onToggleStatus: (dataset: CatalogDataset) => void;
}

export function CatalogDatasetDetail({
  dataset,
  activeTab,
  statusUpdatingId,
  onTabChange,
  onToggleStatus,
}: CatalogDatasetDetailProps) {
  const summary = getSchemaSummary(dataset);
  const detailTabs = [
    { key: 'fields', label: `字段信息 ${summary.fields}` },
    { key: 'versions', label: `版本历史 ${dataset.versions.length}` },
    { key: 'overview', label: '基本信息' },
    { key: 'lineage', label: '血缘' },
  ] as const;

  const fieldColumns: ColumnsType<CatalogDataset['fields'][number]> = [
    {
      title: '字段名称',
      dataIndex: 'displayName',
      width: 180,
      render: (value: string, record) => (
        <div>
          <div className="text-[14px] font-medium text-[#161823]">{value}</div>
          <div className="mt-0.5 text-[12px] text-[#8a8f99]">{record.physicalName}</div>
        </div>
      ),
    },
    {
      title: '类型',
      dataIndex: 'dataType',
      width: 90,
      render: (value: string) => (
        <span className="text-[14px] text-[#344054]">
          {FIELD_TYPE_LABELS[value] || value}
        </span>
      ),
    },
    {
      title: '角色',
      dataIndex: 'defaultRole',
      width: 90,
      render: (value: CatalogDatasetFieldRole) => (
        <span className="inline-flex items-center gap-1 text-[14px] text-[#344054]">
          {value === 'MEASURE' ? <Sigma size={13} /> : <Rows3 size={13} />}
          {FIELD_ROLE_LABELS[value]}
        </span>
      ),
    },
    {
      title: '可空',
      dataIndex: 'nullable',
      width: 70,
      render: (value: boolean) => (
        <span className="text-[14px] text-[#344054]">{value ? '是' : '否'}</span>
      ),
    },
    {
      title: '描述',
      dataIndex: 'description',
      render: (value?: string) => (
        <span className="text-[13px] text-[#667085]">{value || '-'}</span>
      ),
    },
  ];

  const versionColumns: ColumnsType<CatalogDataset['versions'][number]> = [
    {
      title: 'Dataset 版本',
      dataIndex: 'versionNo',
      width: 120,
      render: (value: number) => (
        <span className="text-[14px] font-medium text-[#161823]">DV{value}</span>
      ),
    },
    {
      title: '来源类型',
      dataIndex: 'sourceType',
      width: 120,
      render: (value: CatalogDatasetSourceType) => (
        <span className="text-[14px] text-[#344054]">{SOURCE_TYPE_LABELS[value]}</span>
      ),
    },
    {
      title: '来源',
      render: (_, record) => {
        const source = getDatasetVersionSourceSummary(record, dataset.sourceTaskName);
        return (
          <div>
            <div className="text-[14px] text-[#344054]">{source.title}</div>
            <div className="mt-0.5 text-[12px] text-[#8a8f99]">{source.detail}</div>
          </div>
        );
      },
    },
    {
      title: '发布时间',
      dataIndex: 'createTime',
      width: 165,
      render: (value?: string) => (
        <span className="text-[13px] text-[#667085]">{formatDateTime(value)}</span>
      ),
    },
    {
      title: '状态',
      width: 90,
      render: (_, record) => record.id === dataset.currentVersionId ? (
        <Tag bordered={false} className="m-0 bg-[#f1f3f5] text-[12px] text-[#344054]">
          当前版本
        </Tag>
      ) : (
        <span className="text-[12px] text-[#8a8f99]">历史版本</span>
      ),
    },
  ];

  const datasetType = (() => {
    switch (dataset.currentVersion?.sourceType) {
      case 'QUERY_REVISION':
        return 'SQL 任务数据集';
      case 'SQL_QUERY':
        return 'Standalone SQL 数据集';
      case 'TABLE':
        return '数据表 Dataset（未接入查询运行时）';
      case 'VIEW':
        return '视图 Dataset（未接入查询运行时）';
      default:
        return '-';
    }
  })();
  const currentSource = dataset.currentVersion
    ? getDatasetVersionSourceSummary(dataset.currentVersion, dataset.sourceTaskName)
    : undefined;
  const hasQueryableVersion = Boolean(
    dataset.currentVersion
    && isQueryableDatasetSourceType(dataset.currentVersion.sourceType),
  );
  const canCreateAnalysis = dataset.status === 'ONLINE' && hasQueryableVersion;
  const createAnalysisTip = !dataset.currentVersion
    ? 'Dataset 尚无当前版本'
    : !hasQueryableVersion
      ? `${SOURCE_TYPE_LABELS[dataset.currentVersion.sourceType]}尚未接入 Dataset Query Runtime`
      : dataset.status === 'ONLINE'
        ? '使用当前 Dataset 创建分析'
        : 'Dataset 上线后才能创建 Analysis';

  const detailItems = [
    { label: '类型', value: datasetType },
    {
      label: '所属目录',
      value: dataset.directoryPath || (dataset.sourceNodeId ? '根目录' : '未分组'),
    },
    { label: '来源', value: currentSource?.title || '-' },
    { label: '数据描述', value: dataset.description || '无' },
    { label: '更新时间', value: formatDateTime(dataset.updateTime || dataset.createTime) },
    { label: '创建时间', value: formatDateTime(dataset.createTime) },
  ];

  return (
    <main className="flex min-w-0 flex-1 flex-col overflow-hidden bg-white">
      <div className="flex min-h-[74px] shrink-0 items-center gap-3 px-5 py-3">
        <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-[6px] bg-[#f4f5f7] text-[#667085]">
          <TableProperties size={17} />
        </div>
        <div className="min-w-0 flex-1">
          <div className="truncate text-[15px] font-semibold leading-6 text-[#161823]">
            {dataset.name}
          </div>
          <div className="mt-0.5 flex min-w-0 items-center gap-3 overflow-hidden text-[12px] text-[#667085]">
            <span className="shrink-0">
              最近更新：{formatDateTime(dataset.updateTime || dataset.createTime)}
            </span>
            <span className="h-3 w-px shrink-0 bg-[#dfe3e8]" />
            <span className="shrink-0">创建时间：{formatDateTime(dataset.createTime)}</span>
          </div>
        </div>

        <div className="flex shrink-0 items-center gap-2">
          <YakButton href="/data-development/releases" icon={<GitBranch size={13} />}>
            发布中心
          </YakButton>
          <Popconfirm
            title={dataset.status === 'ONLINE' ? '确认下线这个 Dataset？' : '确认上线这个 Dataset？'}
            description={
              dataset.status === 'ONLINE'
                ? '下线后现有 Analysis 将无法继续查询。'
                : '上线后可继续用于图表分析和仪表盘。'
            }
            okText="确认"
            cancelText="取消"
            onConfirm={() => onToggleStatus(dataset)}
          >
            <YakButton loading={statusUpdatingId === dataset.id}>
              {dataset.status === 'ONLINE' ? '下线' : '上线'}
            </YakButton>
          </Popconfirm>
          <Tooltip title={createAnalysisTip}>
            <span>
              <YakButton
                type="primary"
                icon={<BarChart3 size={13} />}
                disabled={!canCreateAnalysis}
                href={
                  canCreateAnalysis
                    ? `/data-analysis/chart-analysis?datasetId=${encodeURIComponent(dataset.id)}`
                    : undefined
                }
              >
                创建分析
              </YakButton>
            </span>
          </Tooltip>
        </div>
      </div>

      <div className="shrink-0 px-2">
        <YakTab
          activeKey={activeTab}
          items={detailTabs.map((tab) => ({ key: tab.key, label: tab.label }))}
          onChange={(key) => onTabChange(key as CatalogDetailTab)}
        />
      </div>

      <div className="flex min-h-0 flex-1 overflow-hidden">
        <section className="min-w-0 flex-1 overflow-auto px-3 py-3">
          {activeTab === 'fields' ? (
            <Table
              rowKey="fieldId"
              size="small"
              bordered
              pagination={false}
              columns={fieldColumns}
              dataSource={dataset.fields}
              scroll={{ x: 760 }}
              locale={{
                emptyText: (
                  <div className="flex min-h-[280px] items-center justify-center">
                    <YakOpsEmpty
                      width={170}
                      height={114}
                      title="当前 Dataset 暂无字段"
                      description="发布包含字段定义的版本后会在这里展示。"
                      showCaption
                    />
                  </div>
                ),
              }}
            />
          ) : activeTab === 'versions' ? (
            <Table
              rowKey="id"
              size="small"
              bordered
              pagination={false}
              columns={versionColumns}
              dataSource={[...dataset.versions].sort((left, right) => right.versionNo - left.versionNo)}
              scroll={{ x: 760 }}
              locale={{
                emptyText: (
                  <div className="flex min-h-[280px] items-center justify-center">
                    <YakOpsEmpty
                      width={170}
                      height={114}
                      title="暂无 Dataset 版本"
                      description="发布新版本后会在这里展示版本历史。"
                      showCaption
                    />
                  </div>
                ),
              }}
            />
          ) : activeTab === 'lineage' ? (
            <DatasetLineageTab key={dataset.id} dataset={dataset} />
          ) : (
            <div className="max-w-[980px]">
              <div className="grid grid-cols-4 gap-3">
                {[
                  { label: '字段', value: summary.fields, icon: Rows3 },
                  { label: '维度', value: summary.dimensions, icon: Rows3 },
                  { label: '指标', value: summary.metrics, icon: Sigma },
                  { label: 'Analysis', value: dataset.analysisCount, icon: BarChart3 },
                ].map((item) => {
                  const Icon = item.icon;
                  return (
                    <div key={item.label} className="border border-[#e4e7ec] p-3">
                      <div className="flex items-center gap-1.5 text-[12px] text-[#667085]">
                        <Icon size={12} /> {item.label}
                      </div>
                      <div className="mt-2 text-[22px] font-semibold text-[#161823]">
                        {item.value}
                      </div>
                    </div>
                  );
                })}
              </div>
              <div className="mt-3 border border-[#e4e7ec] p-4">
                <div className="text-[14px] font-medium text-[#161823]">Dataset 描述</div>
                <div className="mt-2 text-[13px] leading-5 text-[#667085]">
                  {dataset.description || '暂无描述'}
                </div>
              </div>
            </div>
          )}
        </section>

        <aside className="w-[272px] shrink-0 overflow-y-auto border-l border-[#dfe3e8] bg-[#fafbfc] px-5 py-4">
          <div className="text-[14px] font-semibold text-[#161823]">数据详情</div>
          <div className="mt-4 space-y-4">
            {detailItems.map((item) => (
              <div key={item.label}>
                <div className="text-[12px] leading-5 text-[#667085]">{item.label}</div>
                <div className="mt-0.5 break-words text-[13px] leading-5 text-[#161823]">
                  {item.value}
                </div>
              </div>
            ))}
          </div>
        </aside>
      </div>
    </main>
  );
}
