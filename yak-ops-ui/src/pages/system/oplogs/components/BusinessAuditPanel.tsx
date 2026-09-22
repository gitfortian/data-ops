import {
  FilterOutlined,
  ReloadOutlined,
  SearchOutlined,
} from '@ant-design/icons';
import { history } from '@umijs/max';
import {
  DatePicker,
  Input,
  Popover,
  Select,
  Space,
  Tag,
  Typography,
  message,
  type TableProps,
} from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import { useCallback, useEffect, useMemo, useState } from 'react';

import {
  SecurityPagination,
  SecurityQueryTable,
} from '@/components/security';
import { YakButton } from '@/components/ui';
import {
  getAuditFilterOptions,
  queryAuditOperations,
  type AuditFilterOption,
  type AuditFilterOptions,
  type AuditOperationQuery,
  type AuditOperationSummary,
} from '@/services/audit';

import AuditOperationDetailDrawer from './AuditOperationDetailDrawer';

interface FilterState {
  keyword?: string;
  actor?: string;
  projectId?: string;
  operationType?: string;
  resourceType?: string;
  status?: string;
  source?: string;
  timeRange?: [Dayjs, Dayjs];
}

type AdvancedFilterState = Pick<
  FilterState,
  'operationType' | 'resourceType' | 'status' | 'source'
>;

const EMPTY_OPTIONS: AuditFilterOptions = {
  actors: [],
  projects: [],
  operationTypes: [],
  resourceTypes: [],
  statuses: [],
  sources: [],
};

const statusMeta = (status?: string) => {
  if (status === 'FAILED') return { label: '失败', color: 'error' as const };
  if (status === 'SUCCEEDED') return { label: '成功', color: 'success' as const };
  if (status === 'RUNNING') return { label: '运行中', color: 'processing' as const };
  return { label: status || '-', color: 'default' as const };
};

const formatDuration = (durationMillis?: number) => {
  if (durationMillis == null) return '-';
  if (durationMillis < 1000) return `${durationMillis} ms`;
  if (durationMillis < 60_000) return `${(durationMillis / 1000).toFixed(1)} s`;
  const minutes = Math.floor(durationMillis / 60_000);
  const seconds = Math.floor((durationMillis % 60_000) / 1000);
  return `${minutes}m ${seconds}s`;
};

const selectOptions = (
  options: AuditFilterOption[],
  labeler?: (option: AuditFilterOption) => string,
) =>
  options.map((option) => ({
    value: option.value,
    label: labeler ? labeler(option) : option.label,
  }));

const buildQuery = (
  filters: FilterState,
  page: number,
  size: number,
): AuditOperationQuery => ({
  page,
  size,
  keyword: filters.keyword?.trim() || undefined,
  actor: filters.actor,
  projectId: filters.projectId ? Number(filters.projectId) : undefined,
  operationType: filters.operationType,
  resourceType: filters.resourceType,
  status: filters.status,
  source: filters.source,
  startTime: filters.timeRange?.[0]
    .startOf('day')
    .format('YYYY-MM-DDTHH:mm:ss'),
  endTime: filters.timeRange?.[1]
    .endOf('day')
    .format('YYYY-MM-DDTHH:mm:ss'),
});

const getAdvancedFilters = (filters: FilterState): AdvancedFilterState => ({
  operationType: filters.operationType,
  resourceType: filters.resourceType,
  status: filters.status,
  source: filters.source,
});

export default function BusinessAuditPanel() {
  const [rows, setRows] = useState<AuditOperationSummary[]>([]);
  const [options, setOptions] = useState<AuditFilterOptions>(EMPTY_OPTIONS);
  const [filters, setFilters] = useState<FilterState>({});
  const [loading, setLoading] = useState(false);
  const [selectedOperationId, setSelectedOperationId] = useState<string>();
  const [advancedOpen, setAdvancedOpen] = useState(false);
  const [draftAdvanced, setDraftAdvanced] = useState<AdvancedFilterState>({});
  const [pagination, setPagination] = useState({ current: 1, pageSize: 20, total: 0 });

  const loadOptions = useCallback(async () => {
    try {
      setOptions(await getAuditFilterOptions());
    } catch {
      void message.error('加载审计筛选项失败');
    }
  }, []);

  const loadPage = useCallback(
    async (
      page: number,
      pageSize: number,
      nextFilters: FilterState,
    ) => {
      setLoading(true);
      try {
        const data = await queryAuditOperations(buildQuery(nextFilters, page, pageSize));
        setRows(data.bizData ?? []);
        setPagination({
          current: Number(data.pagination?.pageNo ?? page),
          pageSize: Number(data.pagination?.pageSize ?? pageSize),
          total: Number(data.pagination?.total ?? 0),
        });
      } catch {
        void message.error('加载业务审计记录失败');
      } finally {
        setLoading(false);
      }
    },
    [],
  );

  useEffect(() => {
    void loadOptions();
    void loadPage(1, 20, {});

    const deepLinkOperationId = new URLSearchParams(history.location.search)
      .get('operationId')
      ?.trim();
    if (deepLinkOperationId) {
      setSelectedOperationId(deepLinkOperationId);
    }
  }, [loadOptions, loadPage]);

  const columns: TableProps<AuditOperationSummary>['columns'] = useMemo(
    () => [
      {
        title: '时间',
        dataIndex: 'startedAt',
        width: 170,
        render: (value: string) => dayjs(value).format('YYYY-MM-DD HH:mm:ss'),
      },
      {
        title: '操作',
        key: 'operation',
        width: 250,
        render: (_, record) => (
          <div className="min-w-0">
            <div className="truncate font-medium text-slate-800">
              {record.operationName || record.operationType}
            </div>
            <Typography.Text type="secondary" className="text-xs">
              {record.operationType}
            </Typography.Text>
          </div>
        ),
      },
      {
        title: '操作人',
        key: 'actor',
        width: 150,
        render: (_, record) => (
          <div>
            <div>{record.actorName || record.actorId || '-'}</div>
            {record.actorName && record.actorId ? (
              <div className="text-xs text-slate-400">{record.actorId}</div>
            ) : null}
          </div>
        ),
      },
      {
        title: '项目空间',
        key: 'project',
        width: 160,
        render: (_, record) =>
          record.projectName || (record.projectId ? `#${record.projectId}` : '全局'),
      },
      {
        title: '资源',
        key: 'resource',
        width: 220,
        render: (_, record) => (
          <div className="min-w-0">
            <div className="truncate">
              {record.resourceName || record.resourceId || '-'}
            </div>
            {record.resourceType ? (
              <div className="text-xs text-slate-400">{record.resourceType}</div>
            ) : null}
          </div>
        ),
      },
      {
        title: '状态',
        dataIndex: 'status',
        width: 100,
        render: (value: string) => {
          const meta = statusMeta(value);
          return (
            <Tag bordered={false} color={meta.color} className="m-0">
              {meta.label}
            </Tag>
          );
        },
      },
      {
        title: '来源',
        dataIndex: 'source',
        width: 100,
      },
      {
        title: '耗时',
        dataIndex: 'durationMillis',
        width: 100,
        render: (value?: number) => formatDuration(value),
      },
      {
        title: '操作',
        key: 'action',
        fixed: 'right',
        width: 80,
        render: (_, record) => (
          <YakButton
            type="link"
            size="small"
            onClick={() => setSelectedOperationId(record.operationId)}
          >
            查看
          </YakButton>
        ),
      },
    ],
    [],
  );

  const search = () => {
    void loadPage(1, pagination.pageSize, filters);
  };

  const reset = () => {
    const empty: FilterState = {};
    setFilters(empty);
    setDraftAdvanced({});
    setAdvancedOpen(false);
    void loadPage(1, pagination.pageSize, empty);
  };

  const refresh = () => {
    void loadOptions();
    void loadPage(pagination.current, pagination.pageSize, filters);
  };

  const applyAdvanced = () => {
    const nextFilters = { ...filters, ...draftAdvanced };
    setFilters(nextFilters);
    setAdvancedOpen(false);
    void loadPage(1, pagination.pageSize, nextFilters);
  };

  const resetAdvanced = () => {
    const nextFilters: FilterState = {
      ...filters,
      operationType: undefined,
      resourceType: undefined,
      status: undefined,
      source: undefined,
    };
    setFilters(nextFilters);
    setDraftAdvanced({});
    setAdvancedOpen(false);
    void loadPage(1, pagination.pageSize, nextFilters);
  };

  const advancedCount = [
    filters.operationType,
    filters.resourceType,
    filters.status,
    filters.source,
  ].filter(Boolean).length;

  const advancedContent = (
    <div className="w-[440px] max-w-[calc(100vw-48px)] p-1">
      <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
        <div>
          <div className="mb-1.5 text-xs font-medium text-slate-500">操作类型</div>
          <Select
            allowClear
            showSearch
            optionFilterProp="label"
            value={draftAdvanced.operationType}
            variant="filled"
            options={selectOptions(options.operationTypes)}
            placeholder="全部操作类型"
            className="w-full"
            onChange={(value) =>
              setDraftAdvanced((current) => ({
                ...current,
                operationType: value,
              }))
            }
          />
        </div>

        <div>
          <div className="mb-1.5 text-xs font-medium text-slate-500">资源类型</div>
          <Select
            allowClear
            showSearch
            optionFilterProp="label"
            value={draftAdvanced.resourceType}
            variant="filled"
            options={selectOptions(options.resourceTypes)}
            placeholder="全部资源类型"
            className="w-full"
            onChange={(value) =>
              setDraftAdvanced((current) => ({
                ...current,
                resourceType: value,
              }))
            }
          />
        </div>

        <div>
          <div className="mb-1.5 text-xs font-medium text-slate-500">状态</div>
          <Select
            allowClear
            value={draftAdvanced.status}
            variant="filled"
            options={selectOptions(
              options.statuses,
              (option) => statusMeta(option.value).label,
            )}
            placeholder="全部状态"
            className="w-full"
            onChange={(value) =>
              setDraftAdvanced((current) => ({
                ...current,
                status: value,
              }))
            }
          />
        </div>

        <div>
          <div className="mb-1.5 text-xs font-medium text-slate-500">来源</div>
          <Select
            allowClear
            value={draftAdvanced.source}
            variant="filled"
            options={selectOptions(options.sources)}
            placeholder="全部来源"
            className="w-full"
            onChange={(value) =>
              setDraftAdvanced((current) => ({
                ...current,
                source: value,
              }))
            }
          />
        </div>
      </div>

      <div className="mt-4 flex justify-end gap-2 border-t border-slate-100 pt-3">
        <YakButton onClick={resetAdvanced}>清空</YakButton>
        <YakButton type="primary" onClick={applyAdvanced}>
          应用筛选
        </YakButton>
      </div>
    </div>
  );

  return (
    <div className="flex h-full min-h-0 flex-1 flex-col">
      <div className="mb-4 flex shrink-0 flex-wrap items-center gap-2">
        <Input
          allowClear
          value={filters.keyword}
          variant="filled"
          onChange={(event) =>
            setFilters((current) => ({ ...current, keyword: event.target.value }))
          }
          onPressEnter={search}
          placeholder="操作名 / Operation ID / 资源 / 摘要"
          className="w-[280px] max-w-full"
        />

        <DatePicker.RangePicker
          value={filters.timeRange}
          variant="filled"
          onChange={(value) =>
            setFilters((current) => ({
              ...current,
              timeRange:
                value?.[0] && value?.[1] ? [value[0], value[1]] : undefined,
            }))
          }
          className="w-[230px]"
        />

        <Space.Compact>
          <Select
            allowClear
            showSearch
            optionFilterProp="label"
            value={filters.projectId}
            variant="filled"
            options={selectOptions(options.projects)}
            onChange={(value) =>
              setFilters((current) => ({ ...current, projectId: value }))
            }
            placeholder="项目空间"
            className="w-[150px]"
          />
          <Select
            allowClear
            showSearch
            optionFilterProp="label"
            value={filters.actor}
            variant="filled"
            options={selectOptions(options.actors)}
            onChange={(value) =>
              setFilters((current) => ({ ...current, actor: value }))
            }
            placeholder="操作人"
            className="w-[140px]"
          />
        </Space.Compact>

        <Popover
          trigger="click"
          placement="bottomRight"
          open={advancedOpen}
          content={advancedContent}
          onOpenChange={(open) => {
            setAdvancedOpen(open);
            if (open) setDraftAdvanced(getAdvancedFilters(filters));
          }}
        >
          <YakButton icon={<FilterOutlined />}>
            更多筛选{advancedCount > 0 ? ` (${advancedCount})` : ''}
          </YakButton>
        </Popover>

        <div className="flex items-center gap-2 xl:ml-auto">
          <YakButton type="primary" icon={<SearchOutlined />} onClick={search}>
            查询
          </YakButton>
          <YakButton onClick={reset}>重置</YakButton>
          <YakButton icon={<ReloadOutlined />} onClick={refresh} loading={loading}>
            刷新
          </YakButton>
        </div>
      </div>

      <div className="min-h-0 flex-1 overflow-y-auto overflow-x-hidden">
        <SecurityQueryTable<AuditOperationSummary>
          rowKey="operationId"
          columns={columns}
          dataSource={rows}
          loading={loading}
          pagination={false}
          scroll={{ x: 'max-content' }}
        />
      </div>

      {pagination.total > 0 ? (
        <div className="shrink-0 pt-3">
          <SecurityPagination
            current={pagination.current}
            pageSize={pagination.pageSize}
            total={pagination.total}
            disabled={loading}
            bordered={false}
            onChange={(page, pageSize) => {
              void loadPage(page, pageSize, filters);
            }}
          />
        </div>
      ) : null}

      <AuditOperationDetailDrawer
        operationId={selectedOperationId}
        open={Boolean(selectedOperationId)}
        onClose={() => setSelectedOperationId(undefined)}
      />
    </div>
  );
}
