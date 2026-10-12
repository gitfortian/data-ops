import Table from '@/components/ReadableTable';
import { Alert, Button, Dropdown, Input, message, Modal, Segmented, Select, Tag, Tooltip } from 'antd';
import { ArrowRightOutlined, MoreOutlined, PlusOutlined, ReloadOutlined } from '@ant-design/icons';
import { history, useSearchParams } from '@umijs/max';
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
  const [searchParams] = useSearchParams();
  const rawSourceHint = searchParams.get('dataSourceId');
  const parsedSourceHint = Number(rawSourceHint);
  const sourceHintId = rawSourceHint && /^[1-9]\d*$/.test(rawSourceHint) && Number.isSafeInteger(parsedSourceHint)
    ? parsedSourceHint : undefined;
  const canCreate = can('data-metadata:create');
  const canUpdate = can('data-metadata:update');
  const canDelete = can('data-metadata:delete');

  const [records, setRecords] = useState<CollectJobRecord[]>([]);
  const [total, setTotal] = useState(0);
  const [pageNo, setPageNo] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [keyword, setKeyword] = useState('');
  const [searchText, setSearchText] = useState('');
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
  const [sourceHint, setSourceHint] = useState<{ id: number; name: string } | null>(null);
  const [sourceHintError, setSourceHintError] = useState('');
  const [jobsLoadError, setJobsLoadError] = useState(false);

  useEffect(() => {
    let active = true;
    setSourceHint(null);
    setSourceHintError('');
    listAllDataSources()
      .then((result) => {
        if (!active) return;
        const map: Record<string, string> = {};
        (result.bizData ?? []).forEach((ds) => {
          if (ds.id != null) map[String(ds.id)] = ds.name ?? String(ds.id);
        });
        setDataSourceNames(map);
        if (rawSourceHint !== null) {
          if (sourceHintId && map[String(sourceHintId)]) {
            setSourceHint({ id: sourceHintId, name: map[String(sourceHintId)] });
          } else {
            setSourceHintError('来源 ID 不存在或当前项目无权读取；不会根据导航参数创建跨项目任务。');
          }
        }
      })
      .catch(() => {
        if (!active) return;
        setDataSourceNames({});
        if (rawSourceHint !== null) setSourceHintError('无法核验来源所属项目；请稍后重试。');
      });
    return () => { active = false; };
  }, [rawSourceHint, sourceHintId]);

  const loadJobs = useCallback(
    async (targetPageNo: number, targetPageSize: number) => {
      // An unverified cross-domain URL must not silently become a global job list.
      if (rawSourceHint !== null && (!sourceHintId || sourceHint?.id !== sourceHintId)) {
        setRecords([]);
        setTotal(0);
        return;
      }
      setLoading(true);
      setJobsLoadError(false);
      try {
        const query: CollectJobQueryParams = {
          pageNo: targetPageNo,
          pageSize: targetPageSize,
          keyword: keyword.trim() || undefined,
          providerType: providerType || undefined,
          enabled: enabledFilter === '' ? undefined : enabledFilter === 'true',
          dataSourceId: sourceHint?.id,
          ...(sourceHint ? { providerType: 'HARVESTED' as const } : {}),
        };
        const result = await pageCollectJobs(query);
        setRecords(result.records ?? []);
        setTotal(result.total ?? 0);
      } catch {
        setRecords([]);
        setTotal(0);
        setJobsLoadError(true);
      } finally {
        setLoading(false);
      }
    },
    [keyword, providerType, enabledFilter, rawSourceHint, sourceHintId, sourceHint],
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
      // Timeout/rejection has no trustworthy runId, so we cannot infer cancellation or
      // associate any similarly-timed history row with this request.
      Modal.warning({
        title: '本次请求没有取得可信运行回执',
        content: '先核对这个已保存任务的运行历史及当前元数据目录。无法精确关联的历史记录不代表本次采集成功或失败；如再次执行，请先确认前次可能仍在运行。',
        okText: '核对任务历史',
        onOk: () => setHistoryJob(job),
      });
    } finally {
      setBusyJob(null);
      await reload();
    }
  };

  const confirmRun = (job: CollectJobRecord) => {
    Modal.confirm({
      title: '确认正式采集已保存的任务范围？',
      content: `任务「${job.jobName}」将采集数据源 #${job.dataSourceId ?? '-'} / ${job.databaseName || '全部库'} / ${job.schemaName || '默认 Schema'} / 表匹配 ${job.tablePattern || '全部表'}。这不是 dry-run，可能写入部分元数据；超时不能自动重发。`,
      okText: '确认正式采集',
      cancelText: '取消',
      onOk: () => execute(job, 'run'),
    });
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
      width: 235,
      render: (name: string, job) => (
        <div className="min-w-0 py-1">
          <div className="truncate text-[13px] font-semibold text-[#26364a]">{name}</div>
          <div className="mt-1 truncate font-mono text-[11px] text-[#95a0b1]">{job.jobCode}</div>
        </div>
      ),
    },
    {
      title: '来源与采集范围',
      key: 'scope',
      width: 300,
      render: (_, job) => (
        <div className="min-w-0">
          <div className="truncate text-[12px] text-[#47586e]">{scopeText(job)}</div>
          <div className="mt-1">
            <Tag color={PROVIDER_COLORS[job.providerType]}>{PROVIDER_LABELS[job.providerType]}</Tag>
            {job.providerType === 'REGISTERED' ? <Tag color="warning">执行未开放</Tag> : null}
          </div>
        </div>
      ),
    },
    {
      title: '执行安排',
      key: 'schedule',
      width: 160,
      render: (_, job) => (
        <div>
          <span className="text-[12px] text-[#53637a]">{job.enabled ? '定时调度已开启' : '仅手工触发'}</span>
          <div className="mt-1 truncate font-mono text-[11px] text-[#98a4b4]">{job.cronExpression || '无调度计划'}</div>
        </div>
      ),
    },
    {
      title: '任务状态',
      key: 'status',
      width: 152,
      render: (_, job) => (
        <div className="space-y-1">
          <div><Tag color={job.enabled ? 'success' : 'default'}>{job.enabled ? '已启用' : '停用中'}</Tag></div>
          {!job.dryRunPassed ? (
            <Tooltip title="没有当前配置有效的预演结论；预演失败或修改采集范围后均需重新确认">
              <Tag color={job.enabled ? 'warning' : 'default'}>{job.enabled ? '需重新预演' : '尚未通过预演'}</Tag>
            </Tooltip>
          ) : <span className="text-[11px] text-[#448a67]">预演已通过</span>}
        </div>
      ),
    },
    {
      title: '最近更新',
      dataIndex: 'updateTime',
      width: 155,
      render: (value?: string) => <span className="text-[12px] text-[#8591a3]">{formatMetadataTime(value)}</span>,
    },
    {
      title: '操作',
      key: 'action',
      width: 244,
      fixed: 'right' as const,
      render: (_, job) => {
        const busy = busyJob?.id === job.id;
        const runnable = job.providerType === 'HARVESTED';
        return (
          <div className="flex items-center justify-end gap-1">
            <Tooltip title={runnable ? '只预览本任务范围，不写入元数据' : '投影对账执行器尚未开放'}>
              <Button size="small" disabled={!canCreate || busy || !runnable}
                loading={busy && busyJob?.action === 'dry-run'}
                onClick={() => void execute(job, 'dry-run')}>预演</Button>
            </Tooltip>
            <Tooltip title={runnable ? '执行已保存的任务范围' : '投影对账执行器尚未开放'}>
              <Button type="primary" ghost size="small" disabled={!canCreate || busy || !runnable}
                loading={busy && busyJob?.action === 'run'}
                onClick={() => confirmRun(job)}>正式采集</Button>
            </Tooltip>
            <Dropdown
              trigger={['click']}
              menu={{
                items: [
                  { key: 'history', label: '查看运行历史' },
                  { key: 'edit', label: '编辑任务配置', disabled: !canUpdate || busy },
                  { type: 'divider' },
                  { key: 'enabled', label: job.enabled ? '停用定时调度' : '启用定时调度',
                    disabled: !canUpdate || busy || !runnable || (!job.enabled && !job.dryRunPassed) },
                  { key: 'delete', label: '删除任务', danger: true, disabled: !canDelete || busy },
                ],
                onClick: ({ key }) => {
                  if (key === 'history') setHistoryJob(job);
                  if (key === 'edit') openEdit(job);
                  if (key === 'enabled') toggleEnabled(job);
                  if (key === 'delete') removeJob(job);
                },
              }}
            >
              <Button size="small" aria-label={'更多任务操作：' + job.jobName} icon={<MoreOutlined />} />
            </Dropdown>
          </div>
        );
      },
    },
  ];

  const hasFilter = Boolean(keyword || providerType || enabledFilter);

  return (
    <main className="min-h-[calc(100dvh-64px)] bg-[#f7f8fb] px-6 pb-8 pt-6 max-md:px-4">
      <PageHeader
        title="采集任务"
        subtitle="确认来源、保存采集范围，然后预演或手工采集；启用调度是独立操作。"
        extra={
          <>
            <Button icon={<ReloadOutlined />} loading={loading} onClick={() => void reload()}>刷新</Button>
            <Button onClick={() => history.push('/metadata/explorer')}>元数据目录 <ArrowRightOutlined /></Button>
            {canCreate ? (
              <YakButton type="primary" className="!h-9 !rounded-lg !px-4 !text-white" onClick={openCreate}>
                <PlusOutlined /> 新建采集任务
              </YakButton>
            ) : null}
          </>
        }
      />

      <div className="mt-5 grid grid-cols-1 gap-3 lg:grid-cols-3">
        {[
          { number: '01', title: '保存范围', detail: '选数据源和表，保存后不会自动执行' },
          { number: '02', title: '预演核对', detail: '验证读取范围；不写目录数据' },
          { number: '03', title: '正式采集', detail: '明确确认已保存范围，再查看运行回执' },
        ].map((step) => (
          <div key={step.number} className="flex items-start gap-3 rounded-xl border border-solid border-[#e6eaf0] bg-white px-4 py-3">
            <span className="flex h-8 w-8 flex-none items-center justify-center rounded-lg bg-[#edf3fa] text-[11px] font-semibold text-[#5680ae]">{step.number}</span>
            <div className="min-w-0">
              <div className="text-[13px] font-semibold text-[#2d3d51]">{step.title}</div>
              <div className="mt-0.5 text-[12px] leading-5 text-[#8491a4]">{step.detail}</div>
            </div>
          </div>
        ))}
      </div>

      {sourceHint ? (
        <Alert className="mt-4 rounded-xl" type="info" showIcon
          message={'当前来源：' + sourceHint.name + '（#' + sourceHint.id + '）'}
          description="已核验所属项目；列表在服务端按来源精确过滤。运行时始终使用任务已保存的范围，而非导航参数。" />
      ) : null}
      {sourceHintError ? <Alert className="mt-4 rounded-xl" type="warning" showIcon message={sourceHintError} /> : null}
      {jobsLoadError ? (
        <Alert className="mt-4 rounded-xl" type="error" showIcon message="任务读取失败，不能视为没有任务"
          action={<Button size="small" onClick={() => void reload()}>重试</Button>} />
      ) : null}

      <section className="mt-5 overflow-hidden rounded-2xl border border-solid border-[#e6eaf0] bg-white shadow-[0_4px_20px_rgba(15,23,42,0.025)]">
        <div className="border-0 border-b border-solid border-[#edf0f4] px-5 py-4">
          <div className="flex flex-wrap items-center justify-between gap-3">
            <div>
              <h2 className="m-0 text-[16px] font-semibold text-[#253145]">已保存任务</h2>
              <div className="mt-1 text-[12px] text-[#8a97a8]">
                当前筛选下共 {total} 个任务 · 任务停用只关闭定时调度，不影响历史记录
              </div>
            </div>
            {hasFilter ? (
              <Button size="small" onClick={() => {
                setKeyword(''); setSearchText(''); setProviderType(''); setEnabledFilter(''); setPageNo(1);
              }}>清除筛选</Button>
            ) : null}
          </div>
          <div className="mt-4 flex flex-wrap items-center gap-3">
            {!sourceHint ? (
              <Segmented
                options={[
                  { value: '', label: '全部任务' },
                  { value: 'HARVESTED', label: '物理采集' },
                  { value: 'REGISTERED', label: '投影对账' },
                ]}
                value={providerType}
                onChange={(value) => { setProviderType(value as CollectProviderType | ''); setPageNo(1); }}
              />
            ) : null}
            <Input.Search
              allowClear
              placeholder="搜索任务名称或编码"
              className="!w-[230px] max-sm:!w-full"
              value={searchText}
              onChange={(event) => {
                setSearchText(event.target.value);
                if (!event.target.value) { setKeyword(''); setPageNo(1); }
              }}
              onSearch={(value) => { setKeyword(value.trim()); setPageNo(1); }}
            />
            <Select
              allowClear
              placeholder="调度状态"
              className="!w-[132px]"
              value={enabledFilter || undefined}
              onChange={(value) => {
                setEnabledFilter((value ?? '') as '' | 'true' | 'false');
                setPageNo(1);
              }}
              options={[{ value: 'true', label: '已启用' }, { value: 'false', label: '停用中' }]}
            />
          </div>
        </div>
        <Table<CollectJobRecord>
          rowKey="id"
          size="middle"
          columns={columns}
          dataSource={records}
          loading={loading}
          scroll={{ x: 1250 }}
          locale={{
            emptyText: jobsLoadError ? (
              <YakEmpty compact title="任务读取未成功" description="请重试，避免因为未知状态而重复创建任务" />
            ) : hasFilter ? (
              <YakEmpty compact title="当前筛选下没有任务" description="可清除筛选条件或调整关键字" />
            ) : (
              <YakEmpty compact
                title={sourceHint ? '此来源未找到已保存任务' : '还没有采集任务'}
                description={sourceHint ? '核对当前项目与来源后，可为该来源新建物理采集任务' : '先从物理采集开始。投影对账执行器目前尚未开放。'}
              >
                {canCreate ? <Button type="primary" onClick={openCreate}>新建物理采集任务</Button> : null}
              </YakEmpty>
            ),
          }}
          pagination={{
            current: pageNo,
            pageSize,
            total,
            showSizeChanger: true,
            showTotal: (count) => '共 ' + count + ' 条',
            onChange: (nextPageNo, nextPageSize) => {
              setPageNo(nextPageNo);
              setPageSize(nextPageSize);
            },
          }}
        />
        <div className="border-0 border-t border-solid border-[#edf0f4] px-5 py-3 text-[12px] leading-5 text-[#8a97a8]">
          预演不产生目录实体；正式采集可能部分成功。投影对账的写时登记与失败重试已存在，但独立任务执行器尚未开放。
        </div>
      </section>

      <CollectJobDrawer
        open={drawerOpen}
        editing={editing}
        initialDataSourceId={sourceHint?.id}
        onClose={() => setDrawerOpen(false)}
        onSaved={(saved) => {
          setDrawerOpen(false);
          void reload();
          if (!canCreate) return;
          Modal.confirm({
            title: '采集任务已保存，下一步预演范围',
            content: `已保存任务 #${saved.id}「${saved.jobName}」；当前执行范围：${scopeText(saved)}。预演不写入元数据目录，也不会启用定时调度。可以返回列表直接正式采集（需再次确认范围）。`,
            okText: '预演采集范围',
            cancelText: '返回已保存任务',
            onOk: () => execute(saved, 'dry-run'),
          });
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
    </main>
  );
};

export default MetadataCollectPage;
