import YakOpsEmpty from '@/components/YakOpsEmpty';
import {
  SecurityPagination,
  SecurityQueryTable,
} from '@/components/security';
import {
  listDatasetsForManagement,
  offlineDataset,
  onlineDataset,
  type DatasetManagementItem,
  type DatasetSourceType,
  type DatasetStatus,
} from '@/services/dataset';
import { BRAND_THEME } from '@/styles/brand';
import { history } from '@umijs/max';
import { ConfigProvider, Tag, message } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useCallback, useEffect, useMemo, useState } from 'react';

import DatasetFilterBar, {
  type DatasetListFilters,
} from './components/DatasetFilterBar';
import DatasetRowActions from './components/DatasetRowActions';

const DEFAULT_PAGE_SIZE = 20;

const SOURCE_TYPE_LABELS: Record<DatasetSourceType, string> = {
  QUERY_REVISION: 'SQL 任务',
  SQL_QUERY: 'Standalone SQL',
  TABLE: '数据表',
  VIEW: '视图',
};

const DEFAULT_FILTERS: DatasetListFilters = {
  keyword: '',
  status: 'ALL',
  sourceType: 'ALL',
};

const formatDateTime = (value?: string) => {
  if (!value) return '-';
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString();
};

export default function DatasetManagementPage() {
  const [datasets, setDatasets] = useState<DatasetManagementItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState('');
  const [filters, setFilters] = useState<DatasetListFilters>(DEFAULT_FILTERS);
  const [pageNum, setPageNum] = useState(1);
  const [pageSize, setPageSize] = useState(DEFAULT_PAGE_SIZE);
  const [statusUpdatingId, setStatusUpdatingId] = useState('');

  const loadDatasets = useCallback(async () => {
    setLoading(true);
    setLoadError('');
    try {
      setDatasets(await listDatasetsForManagement());
    } catch (error) {
      setDatasets([]);
      setLoadError(error instanceof Error ? error.message : '加载 Dataset 失败');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadDatasets();
  }, [loadDatasets]);

  const filteredDatasets = useMemo(() => {
    const normalized = filters.keyword.trim().toLowerCase();
    return datasets.filter((dataset) => {
      if (filters.status !== 'ALL' && dataset.status !== filters.status) {
        return false;
      }
      if (
        filters.sourceType !== 'ALL' &&
        dataset.currentVersion?.sourceType !== filters.sourceType
      ) {
        return false;
      }
      if (!normalized) return true;

      return [
        dataset.name,
        dataset.description,
        dataset.currentVersion?.dataSourceId || '',
        dataset.currentVersion?.sourceTaskAssetId || '',
        ...dataset.fields.flatMap((field) => [
          field.displayName,
          field.physicalName,
          field.description || '',
        ]),
      ].some((value) => value.toLowerCase().includes(normalized));
    });
  }, [datasets, filters]);

  useEffect(() => {
    const maxPage = Math.max(1, Math.ceil(filteredDatasets.length / pageSize));
    if (pageNum > maxPage) setPageNum(maxPage);
  }, [filteredDatasets.length, pageNum, pageSize]);

  const visibleDatasets = useMemo(() => {
    const start = (pageNum - 1) * pageSize;
    return filteredDatasets.slice(start, start + pageSize);
  }, [filteredDatasets, pageNum, pageSize]);

  const toggleStatus = useCallback(
    async (dataset: DatasetManagementItem) => {
      setStatusUpdatingId(dataset.id);
      try {
        if (dataset.status === 'ONLINE') {
          await offlineDataset(dataset.id);
          message.success(`Dataset「${dataset.name}」已下线`);
        } else {
          await onlineDataset(dataset.id);
          message.success(`Dataset「${dataset.name}」已上线`);
        }
        await loadDatasets();
      } catch (error) {
        message.error(
          error instanceof Error ? error.message : '更新 Dataset 状态失败',
        );
      } finally {
        setStatusUpdatingId('');
      }
    },
    [loadDatasets],
  );

  const columns = useMemo<ColumnsType<DatasetManagementItem>>(
    () => [
      {
        title: 'Dataset',
        dataIndex: 'name',
        minWidth: 280,
        render: (_value, record) => (
          <div className="min-w-0">
            <button
              type="button"
              className="max-w-full truncate border-0 bg-transparent p-0 text-left text-[14px] font-medium text-[#242731] transition-colors hover:text-[#fe2c55]"
              onClick={() => history.push(`/dataset/${record.id}`)}
            >
              {record.name}
            </button>
            <div className="mt-1 line-clamp-1 text-[12px] text-slate-400">
              {record.description || '暂无描述'}
            </div>
          </div>
        ),
      },
      {
        title: '状态',
        dataIndex: 'status',
        width: 100,
        render: (value: DatasetStatus) => (
          <Tag
            bordered={false}
            color={value === 'ONLINE' ? 'success' : 'default'}
          >
            {value === 'ONLINE' ? '已上线' : '已下线'}
          </Tag>
        ),
      },
      {
        title: '来源类型',
        width: 160,
        render: (_, record) =>
          record.currentVersion
            ? SOURCE_TYPE_LABELS[record.currentVersion.sourceType]
            : '-',
      },
      {
        title: '当前版本',
        width: 110,
        render: (_, record) =>
          record.currentVersion
            ? `DV${record.currentVersion.versionNo}`
            : '-',
      },
      {
        title: '字段数',
        dataIndex: 'fields',
        width: 90,
        render: (fields: DatasetManagementItem['fields']) => fields.length,
      },
      {
        title: '更新时间',
        dataIndex: 'updateTime',
        width: 180,
        render: (value?: string, record) =>
          formatDateTime(value || record.createTime),
      },
      {
        title: '操作',
        key: 'action',
        fixed: 'right',
        width: 156,
        render: (_, record) => (
          <DatasetRowActions
            dataset={record}
            loading={statusUpdatingId === record.id}
            onDetail={(dataset) => history.push(`/dataset/${dataset.id}`)}
            onToggleStatus={(dataset) => void toggleStatus(dataset)}
          />
        ),
      },
    ],
    [statusUpdatingId, toggleStatus],
  );

  return (
    <ConfigProvider theme={BRAND_THEME}>
      <section className="box-border flex min-h-[calc(100vh-64px)] flex-col overflow-hidden bg-slate-50/50 p-6 text-[#161823]">
        <div className="mb-4 flex shrink-0 items-center gap-2">
          <h1 className="m-0 text-[18px] font-semibold text-[#282828]">
            数据集
          </h1>
        </div>

        <div className="shrink-0">
          <DatasetFilterBar
            total={filteredDatasets.length}
            loading={loading}
            onSearch={(nextFilters) => {
              setPageNum(1);
              setFilters(nextFilters);
            }}
            onRefresh={() => void loadDatasets()}
            onOpenReleaseCenter={() =>
              history.push('/data-development/releases')
            }
          />

          <SecurityQueryTable<DatasetManagementItem>
            rowKey="id"
            loading={loading}
            columns={columns}
            dataSource={visibleDatasets}
            pagination={false}
            bordered
            scroll={{ x: 'max-content' }}
            locale={{
              emptyText: (
                <div className="flex min-h-[300px] items-center justify-center">
                  <YakOpsEmpty
                    width={180}
                    height={120}
                    title={loadError ? 'Dataset 加载失败' : '暂无 Dataset'}
                    description={
                      loadError || '请先从数据开发发布中心发布可消费的数据集。'
                    }
                    showCaption
                  />
                </div>
              ),
            }}
          />
        </div>

        <div className="min-h-6 flex-1" />

        <SecurityPagination
          current={pageNum}
          pageSize={pageSize}
          total={filteredDatasets.length}
          disabled={loading}
          onChange={(nextPage, nextPageSize) => {
            setPageNum(nextPageSize === pageSize ? nextPage : 1);
            setPageSize(nextPageSize);
          }}
        />
      </section>
    </ConfigProvider>
  );
}
