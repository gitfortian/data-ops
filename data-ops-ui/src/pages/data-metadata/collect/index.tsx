import Table from '@/components/ReadableTable';
import { Button, Input, message, Modal, Select, Space,  Tag, Tooltip } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useCallback, useEffect, useMemo, useState } from 'react';

import { YakButton, YakEmpty } from '@/components/ui';
import { usePermissionAccess } from '@/hooks/usePermissionAccess';
import {
  changeCollectJobEnabled,
  deleteCollectJob,
  dryRunCollectJob,
  pageCollectJobs,
  runCollectJob,
} from '@/services/metadata/api';
import type {
  CollectJobQueryParams,
  CollectJobRecord,
  CollectProviderType,
  CollectRunRecord,
} from '@/services/metadata/types';
import { listAllDataSources } from '@/services/data-source/api';
import { PageHeader } from '../shared';
import CollectJobDrawer from './components/CollectJobDrawer';
import RunHistoryDrawer from './components/RunHistoryDrawer';
import RunResultModal from './components/RunResultModal';
import { PROVIDER_COLORS, PROVIDER_LABELS, formatMetadataTime } from './constants';

const MetadataCollectPage = () => {
  const { can } = usePermissionAccess();
  const canCreate = can('data-metadata:create');
  const canUpdate = can('data-metadata:update');
  const canDelete = can('data-metadata:delete');

  const [records, setRecords] = useState<CollectJobRecord[]>([]);
  const [total, setTotal] = useState(0);
  const [pageNo, setPageNo] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [keyword, setKeyword] = useState('');
  const [providerType, setProviderType] = useState<CollectProviderType | ''>('');
  const [enabledFilter, setEnabledFilter] = useState<'' | 'true' | 'false'>('');
  const [loading, setLoading] = useState(false);
  /** 一轮采集是同步的:按钮转圈即"本轮在跑"(工单 116 结论)。 */
  const [busyJob, setBusyJob] = useState<{ id: number; action: 'dry-run' | 'run' } | null>(null);

  const [drawerOpen, setDrawerOpen] = useState(false);
  const [editing, setEditing] = useState<CollectJobRecord | null>(null);
  const [historyJob, setHistoryJob] = useState<CollectJobRecord | null | undefined>(undefined);
  const [runResult, setRunResult] = useState<{ run: CollectRunRecord; title: string } | null>(null);
  const [dataSourceNames, setDataSourceNames] = useState<Record<string, string>>({});

  useEffect(() => {
    listAllDataSources()
      .then((result) => {
        const map: Record<string, string> = {};
        (result.bizData ?? []).forEach((ds) => {
          if (ds.id != null) map[String(ds.id)] = ds.name ?? String(ds.id);
        });
        setDataSourceNames(map);
      })
      .catch(() => setDataSourceNames({}));
  }, []);

  const loadJobs = useCallback(
    async (targetPageNo: number, targetPageSize: number) => {
      setLoading(true);
      try {
        const query: CollectJobQueryParams = {
          pageNo: targetPageNo,
          pageSize: targetPageSize,
          keyword: keyword.trim() || undefined,
          providerType: providerType || undefined,
          enabled: enabledFilter === '' ? undefined : enabledFilter === 'true',
        };
        const result = await pageCollectJobs(query);
        setRecords(result.records ?? []);
        setTotal(result.total ?? 0);
      } catch {
        setRecords([]);
        setTotal(0);
      } finally {
        setLoading(false);
      }
    },
    [keyword, providerType, enabledFilter],
  );

  useEffect(() => {
    void loadJobs(pageNo, pageSize);
  }, [pageNo, pageSize, loadJobs]);

  const reload = () => loadJobs(pageNo, pageSize);

  const openCreate = () => {
    setEditing(null);
    setDrawerOpen(true);
  };

  const openEdit = (job: CollectJobRecord) => {
    setEditing(job);
    setDrawerOpen(true);
  };

  const execute = async (
    job: CollectJobRecord,
    action: 'dry-run' | 'run',
  ) => {
    setBusyJob({ id: job.id, action });
    try {
      const run = action === 'dry-run' ? await dryRunCollectJob(job.id) : await runCollectJob(job.id);
      setRunResult({
        run,
        title: action === 'dry-run' ? `dry-run 预演 · ${job.jobName}` : `立即运行 · ${job.jobName}`,
      });
      if (action === 'dry-run' && run.status === 'SUCCESS') message.success('预演通过,现在可以启用该任务');
    } catch {
      // 全局错误提示已展示业务原因(连不上数据源等)
    } finally {
      setBusyJob(null);
      await reload();
    }
  };

  const toggleEnabled = (job: CollectJobRecord) => {
    const next = !job.enabled;
    if (next && !job.dryRunPassed) {
      message.warning('启用前必须先通过一次 dry-run 预演');
      return;
    }
    Modal.confirm({
      title: `${next ? '启用' : '停用'}任务`,
      content: next
        ? `确定启用「${job.jobName}」?启用后由调度按 cron 周期执行。`
        : `确定停用「${job.jobName}」?停用只撤闹钟,运行历史保留。`,
      okText: next ? '启用' : '停用',
      cancelText: '取消',
      onOk: async () => {
        try {
          await changeCollectJobEnabled(job.id, next);
          message.success(`已${next ? '启用' : '停用'}`);
        } catch {
          // 全局错误提示已展示原因(如缺预演 / 引擎登记失败)
        } finally {
          await reload();
        }
      },
    });
  };

  const removeJob = (job: CollectJobRecord) => {
    Modal.confirm({
      title: '删除任务',
      content: `确定删除「${job.jobName}」?软删并撤闹钟;运行历史与已采目录保留。`,
      okText: '删除',
      okType: 'danger',
      cancelText: '取消',
      onOk: async () => {
        try {
          await deleteCollectJob(job.id);
          message.success('已删除');
        } catch {
          // 全局错误提示已展示原因
        } finally {
          await reload();
        }
      },
    });
  };

  const scopeText = useMemo(
    () =>
      (job: CollectJobRecord): string => {
        if (job.providerType === 'REGISTERED') return `登记实体 · ${job.typeName ?? '-'}`;
        const ds = job.dataSourceId != null ? dataSourceNames[String(job.dataSourceId)] : undefined;
        const scope = [job.databaseName, job.schemaName].filter(Boolean).join('.');
        return [ds ?? `#${job.dataSourceId}`, scope || '全部库', job.tablePattern].filter(Boolean).join(' · ');
      },
    [dataSourceNames],
  );

  const columns: ColumnsType<CollectJobRecord> = [
    {
      title: '任务',
      dataIndex: 'jobName',
      width: 220,
      ellipsis: true,
      render: (name: string, job) => (
        <div>
          <div className="truncate">{name}</div>
          <div className="text-[12px] text-[#98a2b3]">{job.jobCode}</div>
        </div>
      ),
    },
    {
      title: '通道',
      dataIndex: 'providerType',
      width: 100,
      render: (value: CollectProviderType) => (
        <Tag color={PROVIDER_COLORS[value]}>{PROVIDER_LABELS[value]}</Tag>
      ),
    },
    {
      title: '作用域',
      key: 'scope',
      ellipsis: true,
      render: (_, job) => scopeText(job),
    },
    {
      title: '调度',
      dataIndex: 'cronExpression',
      width: 140,
      render: (value?: string | null) => (
        <Tooltip title="运行历史里可看每轮实际耗时">
          <code className="font-mono text-[12px] text-[#344054]">{value || '-'}</code>
        </Tooltip>
      ),
    },
    {
      title: '状态',
      key: 'status',
      width: 150,
      render: (_, job) => (
        <Space size={4}>
          <Tag color={job.enabled ? 'green' : 'default'}>{job.enabled ? '启用中' : '停用'}</Tag>
          {!job.dryRunPassed &&
            (job.enabled ? (
              <Tag color="warning">启用中但未预演</Tag>
            ) : (
              <Tooltip title="dry-run 预演通过后才允许启用;改动作用域后需重新预演">
                <Tag>未预演</Tag>
              </Tooltip>
            ))}
        </Space>
      ),
    },
    {
      title: '更新时间',
      dataIndex: 'updateTime',
      width: 160,
      render: (value?: string) => formatMetadataTime(value),
    },
    {
      title: '操作',
      key: 'action',
      width: 300,
      fixed: 'right' as const,
      render: (_, job) => {
        const busy = busyJob?.id === job.id;
        return (
          <Space size={0}>
            <Button type="link" size="small" disabled={!canUpdate || busy} onClick={() => openEdit(job)}>
              编辑
            </Button>
            <Button
              type="link"
              size="small"
              disabled={!canCreate || busy}
              loading={busy && busyJob?.action === 'dry-run'}
              onClick={() => void execute(job, 'dry-run')}
            >
              预演
            </Button>
            <Button
              type="link"
              size="small"
              disabled={!canCreate || busy}
              loading={busy && busyJob?.action === 'run'}
              onClick={() => void execute(job, 'run')}
            >
              立即运行
            </Button>
            <Button type="link" size="small" onClick={() => setHistoryJob(job)}>
              历史
            </Button>
            <Button
              type="link"
              size="small"
              danger={job.enabled}
              disabled={!canUpdate}
              onClick={() => toggleEnabled(job)}
            >
              {job.enabled ? '停用' : '启用'}
            </Button>
            <Button type="link" size="small" danger disabled={!canDelete} onClick={() => removeJob(job)}>
              删除
            </Button>
          </Space>
        );
      },
    },
  ];

  const hasFilter = Boolean(keyword || providerType || enabledFilter);

  return (
    <div className="flex min-h-[calc(100dvh-64px)] flex-col bg-white px-6 pb-4 pt-5 text-[#242731] max-md:px-4">
      <PageHeader
        title="采集与对账"
        subtitle="管理采集范围、执行计划与对账结果，跟踪技术目录的变化。"
        extra={
          canCreate ? (
            <YakButton
              type="primary"
              className="!h-9 !rounded-lg !px-4 !text-white"
              onClick={openCreate}
            >
              新建任务
            </YakButton>
          ) : null
        }
      />

      <div className="mt-4 flex flex-wrap items-center gap-3">
        <Input.Search
          allowClear
          placeholder="按名称或编码搜索"
          className="!w-[240px]"
          onSearch={(value) => {
            setKeyword(value.trim());
            setPageNo(1);
          }}
        />
        <Select
          allowClear
          placeholder="通道"
          className="!w-[140px]"
          value={providerType || undefined}
          onChange={(value) => {
            setProviderType((value ?? '') as CollectProviderType | '');
            setPageNo(1);
          }}
          options={(Object.keys(PROVIDER_LABELS) as CollectProviderType[]).map((value) => ({
            value,
            label: PROVIDER_LABELS[value],
          }))}
        />
        <Select
          allowClear
          placeholder="状态"
          className="!w-[120px]"
          value={enabledFilter || undefined}
          onChange={(value) => {
            setEnabledFilter((value ?? '') as '' | 'true' | 'false');
            setPageNo(1);
          }}
          options={[
            { value: 'true', label: '启用中' },
            { value: 'false', label: '停用' },
          ]}
        />
      </div>

      <div className="mt-4 flex-1">
        <Table<CollectJobRecord>
          rowKey="id"
          columns={columns}
          dataSource={records}
          loading={loading}
          scroll={{ x: 1240 }}
          locale={{
            emptyText: hasFilter ? (
              <YakEmpty compact title="没有符合条件的任务" description="调整筛选条件或重置后再试" />
            ) : (
              <YakEmpty
                compact
                title="还没有采集任务"
                description="新建一个物理采集任务对数据源采表/列结构，或建一个投影对账任务兜底写时登记的漏网"
              >
                {canCreate && (
                  <YakButton
                    type="primary"
                    className="!mt-3 !rounded-lg !text-white"
                    onClick={openCreate}
                  >
                    新建任务
                  </YakButton>
                )}
              </YakEmpty>
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

      <CollectJobDrawer
        open={drawerOpen}
        editing={editing}
        onClose={() => setDrawerOpen(false)}
        onSaved={() => {
          setDrawerOpen(false);
          void reload();
        }}
      />
      <RunHistoryDrawer
        open={historyJob !== undefined}
        job={historyJob ?? null}
        onClose={() => setHistoryJob(undefined)}
      />
      <RunResultModal
        run={runResult?.run ?? null}
        title={runResult?.title ?? ''}
        onClose={() => setRunResult(null)}
      />
    </div>
  );
};

export default MetadataCollectPage;
