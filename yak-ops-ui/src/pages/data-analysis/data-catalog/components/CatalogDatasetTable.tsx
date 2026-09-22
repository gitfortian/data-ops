import YakOpsEmpty from '@/components/YakOpsEmpty';
import { YakButton, YakEmpty } from '@/components/ui';
import type {
  CatalogDataset,
  CatalogDatasetStatus,
} from '@/services/data-analysis';
import { isQueryableDatasetSourceType } from '@/services/dataset';
import { Input, Pagination, Popconfirm, Select, Table, Tag, Tooltip } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { BarChart3, Clock3, Search, TableProperties } from 'lucide-react';
import {
  QUERYABLE_SOURCE_TYPE_OPTIONS,
  SOURCE_TYPE_LABELS,
} from '../constants';
import type { CatalogSourceTypeFilter, CatalogStatusFilter } from '../types';
import {
  formatDateTime,
  getDatasetVersionSourceSummary,
  getSchemaSummary,
} from '../utils';

interface CatalogDatasetTableProps {
  scopeTitle: string;
  scopeCount: number;
  datasets: CatalogDataset[];
  filteredCount: number;
  keyword: string;
  status: CatalogStatusFilter;
  sourceType: CatalogSourceTypeFilter;
  current: number;
  pageSize: number;
  isLoading: boolean;
  loadError: string;
  statusUpdatingId: string;
  onKeywordChange: (keyword: string) => void;
  onStatusChange: (status: CatalogStatusFilter) => void;
  onSourceTypeChange: (sourceType: CatalogSourceTypeFilter) => void;
  onPageChange: (current: number, pageSize: number) => void;
  onReset: () => void;
  onReload: () => void;
  onSelectDataset: (dataset: CatalogDataset) => void;
  onToggleStatus: (dataset: CatalogDataset) => void;
}

export function CatalogDatasetTable({
  scopeTitle,
  scopeCount,
  datasets,
  filteredCount,
  keyword,
  status,
  sourceType,
  current,
  pageSize,
  isLoading,
  loadError,
  statusUpdatingId,
  onKeywordChange,
  onStatusChange,
  onSourceTypeChange,
  onPageChange,
  onReset,
  onReload,
  onSelectDataset,
  onToggleStatus,
}: CatalogDatasetTableProps) {
  const columns: ColumnsType<CatalogDataset> = [
    {
      title: '数据集',
      dataIndex: 'name',
      width: 290,
      render: (_, record) => (
        <div className="flex min-w-0 items-center gap-2 py-1">
          <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-[5px] bg-[#f4f5f7] text-[#667085]">
            <TableProperties size={15} />
          </div>
          <div className="min-w-0 flex-1">
            <button
              type="button"
              className="max-w-full truncate border-0 bg-transparent p-0 text-left text-[14px] font-medium text-[#161823] hover:underline"
              onClick={(event) => {
                event.stopPropagation();
                onSelectDataset(record);
              }}
            >
              {record.name}
            </button>
            <div className="mt-0.5 truncate text-[12px] text-[#8a8f99]">
              {record.description || record.sourceTaskName || '暂无描述'}
            </div>
          </div>
        </div>
      ),
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (value: CatalogDatasetStatus) => (
        <Tag
          bordered={false}
          className={
            value === 'ONLINE'
              ? 'm-0 bg-[#f1f3f5] text-[12px] text-[#344054]'
              : 'm-0 bg-[#f5f5f6] text-[12px] text-[#8a8f99]'
          }
        >
          {value === 'ONLINE' ? '已上线' : '已下线'}
        </Tag>
      ),
    },
    {
      title: '来源',
      width: 210,
      render: (_, record) => {
        if (!record.currentVersion) {
          return <span className="text-[12px] text-[#8a8f99]">尚无当前版本</span>;
        }
        const source = getDatasetVersionSourceSummary(
          record.currentVersion,
          record.sourceTaskName,
        );
        return (
          <div className="min-w-0">
            <div className="truncate text-[14px] text-[#344054]">
              {source.title}
            </div>
            <div className="mt-0.5 text-[12px] text-[#8a8f99]">
              {SOURCE_TYPE_LABELS[record.currentVersion.sourceType]} · {source.detail}
            </div>
          </div>
        );
      },
    },
    {
      title: 'Schema',
      width: 145,
      render: (_, record) => {
        const summary = getSchemaSummary(record);
        return (
          <div className="text-[14px] text-[#344054]">
            <div>{summary.fields} 个字段</div>
            <div className="mt-0.5 text-[12px] text-[#8a8f99]">
              {summary.dimensions} 维度 · {summary.metrics} 指标
            </div>
          </div>
        );
      },
    },
    {
      title: '版本',
      width: 92,
      render: (_, record) => (
        <div>
          <div className="text-[14px] font-medium text-[#344054]">
            {record.currentVersion ? `DV${record.currentVersion.versionNo}` : '-'}
          </div>
          <div className="mt-0.5 text-[12px] text-[#8a8f99]">
            {record.versions.length} 个版本
          </div>
        </div>
      ),
    },
    {
      title: '消费',
      width: 115,
      render: (_, record) => (
        <div className="flex items-center gap-1.5 text-[14px] text-[#344054]">
          <BarChart3 size={13} className="text-[#667085]" /> {record.analysisCount} Analysis
        </div>
      ),
    },
    {
      title: '更新时间',
      width: 160,
      render: (_, record) => (
        <div className="flex items-center gap-1.5 text-[13px] text-[#667085]">
          <Clock3 size={12} className="text-[#8a8f99]" />
          {formatDateTime(record.updateTime || record.createTime)}
        </div>
      ),
    },
    {
      title: '操作',
      fixed: 'right',
      width: 170,
      render: (_, record) => {
        const hasQueryableVersion = Boolean(
          record.currentVersion
          && isQueryableDatasetSourceType(record.currentVersion.sourceType),
        );
        const canCreateAnalysis = record.status === 'ONLINE' && hasQueryableVersion;
        const createAnalysisTip = !record.currentVersion
          ? 'Dataset 尚无当前版本'
          : !hasQueryableVersion
            ? `${SOURCE_TYPE_LABELS[record.currentVersion.sourceType]}尚未接入 Dataset Query Runtime`
            : record.status === 'ONLINE'
              ? '使用当前 Dataset 创建分析'
              : 'Dataset 上线后才能创建 Analysis';
        return (
          <div className="flex items-center gap-1">
            <Tooltip title={createAnalysisTip}>
              <YakButton
                type="link"
                size="small"
                disabled={!canCreateAnalysis}
                href={
                  canCreateAnalysis
                    ? `/data-analysis/chart-analysis?datasetId=${encodeURIComponent(record.id)}`
                    : undefined
                }
                onClick={(event) => event.stopPropagation()}
              >
                创建分析
              </YakButton>
            </Tooltip>
            <Popconfirm
              title={record.status === 'ONLINE' ? '确认下线这个 Dataset？' : '确认上线这个 Dataset？'}
              description={
                record.status === 'ONLINE'
                  ? '下线后现有 Analysis 将无法继续查询。'
                  : '上线后可继续用于图表分析和仪表盘。'
              }
              okText="确认"
              cancelText="取消"
              onConfirm={() => onToggleStatus(record)}
            >
              <YakButton
                type="text"
                size="small"
                loading={statusUpdatingId === record.id}
                onClick={(event) => event.stopPropagation()}
              >
                {record.status === 'ONLINE' ? '下线' : '上线'}
              </YakButton>
            </Popconfirm>
          </div>
        );
      },
    },
  ];

  return (
    <main className="flex min-w-0 flex-1 flex-col overflow-hidden px-4 py-3">
      <div className="shrink-0 border-b border-[#e4e7ec] pb-2">
        <div className="flex min-w-0 flex-nowrap items-center gap-3 overflow-x-auto">
          <div className="flex min-w-0 shrink-0 items-center gap-2">
            <span className="max-w-[260px] truncate text-[14px] font-semibold text-[#161823]">
              {scopeTitle}
            </span>
            <span className="text-[12px] text-[#8a8f99]">{scopeCount} 个 Dataset</span>
          </div>

          <div className="ml-auto flex shrink-0 items-center gap-2">
            <Input
              allowClear
              variant="filled"
              value={keyword}
              prefix={<Search size={14} className="text-[#8a8f99]" />}
              placeholder="搜索名称、字段、来源"
              className="w-[220px]"
              onChange={(event) => onKeywordChange(event.target.value)}
            />
            <Select
              variant="filled"
              value={status}
              className="w-[112px]"
              onChange={onStatusChange}
              options={[
                { label: '全部状态', value: 'ALL' },
                { label: '已上线', value: 'ONLINE' },
                { label: '已下线', value: 'OFFLINE' },
              ]}
            />
            <Select
              variant="filled"
              value={sourceType}
              className="w-[142px]"
              onChange={onSourceTypeChange}
              options={[
                { label: '全部来源', value: 'ALL' },
                ...QUERYABLE_SOURCE_TYPE_OPTIONS,
              ]}
            />
            <YakButton onClick={onReset}>重置</YakButton>
          </div>
        </div>
      </div>

      <div className="min-h-0 flex-1 overflow-auto pt-2">
        {loadError ? (
          <div className="flex h-full items-center justify-center">
            <YakEmpty
              title="数据目录加载失败"
              description={loadError}
              className="min-h-[360px]"
            />
            <div className="absolute mt-28">
              <YakButton onClick={onReload}>重新加载</YakButton>
            </div>
          </div>
        ) : (
          <Table<CatalogDataset>
            rowKey="id"
            size="small"
            bordered
            pagination={false}
            loading={isLoading}
            columns={columns}
            dataSource={datasets}
            scroll={{ x: 1200 }}
            locale={{
              emptyText: isLoading ? null : (
                <div className="flex min-h-[300px] items-center justify-center">
                  <YakOpsEmpty
                    width={180}
                    height={120}
                    title="当前目录暂无 Dataset"
                    description="发布或切换目录后可在这里查看 Dataset。"
                    showCaption
                  />
                </div>
              ),
            }}
            onRow={(record) => ({
              onClick: () => onSelectDataset(record),
              style: { cursor: 'pointer' },
            })}
          />
        )}
      </div>

      <div className="flex shrink-0 justify-end border-t border-[#f0f2f5] pt-3">
        <Pagination
          size="small"
          current={current}
          pageSize={pageSize}
          total={filteredCount}
          showSizeChanger
          showTotal={(total) => `共 ${total} 条`}
          onChange={onPageChange}
        />
      </div>
    </main>
  );
}
