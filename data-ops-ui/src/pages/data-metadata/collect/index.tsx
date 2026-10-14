import Table from '@/components/ReadableTable';
import { Alert, Button, Dropdown, Input, message, Modal, Segmented, Select, Descriptions, Tag, Tooltip } from 'antd';
import { ArrowRightOutlined, MoreOutlined, PlusOutlined, ReloadOutlined, SearchOutlined } from '@ant-design/icons';
import { history, useSearchParams } from '@umijs/max';
import { readCollectionDefinitionHint, collectionJobCovers } from '@/services/metadata/collectionHandoff';
import type { CollectionSourceScope } from '@/services/metadata/collectionHandoff';
import { buildLiveSourceReturnPath } from '@/utils/liveSourceOdsJourney';
import type { ColumnsType } from 'antd/es/table';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useSecurityProject } from '@/contexts/SecurityProjectContext';

import { YakButton, YakEmpty } from '@/components/ui';
import { usePermissionAccess } from '@/hooks/usePermissionAccess';
import {
  changeCollectJobEnabled,
  deleteCollectJob,
  dryRunCollectJob,
  getCollectJob,
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
import { collectTaskDetailPath } from '@/services/metadata/collectionHandoff';
import './index.less';
import CollectJobDrawer from './components/CollectJobDrawer';
import type { CollectJobInitialScope } from './components/CollectJobDrawer';
import RunHistoryDrawer from './components/RunHistoryDrawer';
import RunResultModal from './components/RunResultModal';
import CollectionJourneyContinuation from './components/CollectionJourneyContinuation';
import { PROVIDER_LABELS, formatMetadataTime, describeCollectSchedule } from './constants';
import { canEnableCollectSchedule, supportsCollectTaskExecution } from './collectTaskCapabilities';

const MetadataCollectPage = () => {
  const { can } = usePermissionAccess();
  const { currentProject } = useSecurityProject();
  const projectId = currentProject?.id;
  const [searchParams, setSearchParams] = useSearchParams();
  const definitionAction = searchParams.get('action');
  const definitionHint = useMemo(() => readCollectionDefinitionHint(searchParams), [searchParams]);
  const rawSourceHint = searchParams.get('dataSourceId');
  const parsedSourceHint = Number(rawSourceHint);
  const sourceHintId =
    rawSourceHint && /^[1-9]\d*$/.test(rawSourceHint) && Number.isSafeInteger(parsedSourceHint)
      ? parsedSourceHint
      : undefined;
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
  const [definitionScope, setDefinitionScope] = useState<CollectJobInitialScope>();
  const [definitionError, setDefinitionError] = useState('');
  const [editing, setEditing] = useState<CollectJobRecord | null>(null);
  const [historyJob, setHistoryJob] = useState<CollectJobRecord | null | undefined>(undefined);
  const [runResult, setRunResult] = useState<{ run: CollectRunRecord; title: string } | null>(null);
  const [continuedJob, setContinuedJob] = useState<CollectJobRecord | null>(null);
  const [continuedRun, setContinuedRun] = useState<CollectRunRecord | null>(null);
  const [continuedResultUnknown, setContinuedResultUnknown] = useState(false);
  const [sourceOrigin, setSourceOrigin] = useState<CollectionSourceScope | null>(null);
  const continuationScopeEpoch = useRef(0);
  useEffect(() => {
    // A saved task receipt from another Project or source-scoped page cannot be
    // silently reused as a current-context journey.
    continuationScopeEpoch.current += 1;
    setContinuedJob(null);
    setContinuedRun(null);
    setContinuedResultUnknown(false);
    setSourceOrigin(null);
    setBusyJob(null);
    setRunResult(null);
  }, [projectId, rawSourceHint]);
  const [dataSourceNames, setDataSourceNames] = useState<Record<string, string>>({});
  const [sourceHint, setSourceHint] = useState<{ id: number; name: string } | null>(null);
  const [sourceHintError, setSourceHintError] = useState('');
  const [jobsLoadError, setJobsLoadError] = useState(false);
  const jobsRequestSeq = useRef(0);
  const sourceHintPending = rawSourceHint !== null && !sourceHint && !sourceHintError;

  useEffect(() => {
    if (definitionAction !== 'define') return;
    let active = true;
    const consumeHint = () => {
      const params = new URLSearchParams(searchParams);
      ['action', 'jobId', 'databaseName', 'schemaName', 'sourceTableName'].forEach((key) => params.delete(key));
      setSearchParams(params, { replace: true });
    };
    if (!definitionHint.valid || sourceHintError) {
      setDefinitionError(sourceHintError || '任务定义导航参数不合法，请重新选择来源');
      consumeHint();
      return;
    }
    if (!sourceHint || sourceHint.id !== definitionHint.sourceId) return;
    const verifiedSource = sourceHint;
    const originScope: CollectionSourceScope | null =
      definitionHint.databaseName && definitionHint.sourceTableName
        ? {
            dataSourceId: String(verifiedSource.id),
            databaseName: definitionHint.databaseName,
            schemaName: definitionHint.schemaName,
            tableName: definitionHint.sourceTableName,
          }
        : null;
    const openDefinition = async () => {
      try {
        if (definitionHint.jobId) {
          if (!canUpdate) throw new Error('当前账号没有修改采集任务的权限');
          const job = await getCollectJob(definitionHint.jobId);
          if (!active) return;
          if (
            job.id !== definitionHint.jobId ||
            job.providerType !== 'HARVESTED' ||
            job.dataSourceId !== verifiedSource.id
          )
            throw new Error('任务不属于当前核验的数据源，不能打开定义');
          // Existing tasks keep their persisted scope and schedule, independent of source-table hints.
          setEditing(job);
          setDefinitionScope(undefined);
        } else {
          if (!canCreate) throw new Error('当前账号没有创建采集任务的权限');
          setEditing(null);
          setDefinitionScope({
            databaseName: definitionHint.databaseName,
            schemaName: definitionHint.schemaName,
            sourceTableName: definitionHint.sourceTableName,
            jobName: `${(definitionHint.databaseName || verifiedSource.name).slice(0, 124)}结构采集`,
          });
        }
        if (!active) return;
        setSourceOrigin(originScope);
        setDefinitionError('');
        setDrawerOpen(true);
        consumeHint();
      } catch (error) {
        if (!active) return;
        setDefinitionError(error instanceof Error ? error.message : '无法核验采集任务定义，请重试');
        consumeHint();
      }
    };
    void openDefinition();
    return () => {
      active = false;
    };
  }, [
    definitionAction,
    definitionHint,
    sourceHint,
    sourceHintError,
    canCreate,
    canUpdate,
    searchParams,
    setSearchParams,
  ]);

  useEffect(() => {
    let active = true;
    setSourceHint(null);
    setSourceHintError('');
    setDataSourceNames({});
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
            setSourceHintError('数据源不存在或当前项目无权访问');
          }
        }
      })
      .catch(() => {
        if (!active) return;
        setDataSourceNames({});
        if (rawSourceHint !== null) setSourceHintError('未能读取数据源，请稍后重试');
      });
    return () => {
      active = false;
    };
  }, [rawSourceHint, sourceHintId, projectId]);

  const loadJobs = useCallback(
    async (targetPageNo: number, targetPageSize: number) => {
      const ticket = ++jobsRequestSeq.current;
      // An unverified cross-domain URL must not silently become a global job list.
      if (rawSourceHint !== null && (!sourceHintId || sourceHint?.id !== sourceHintId)) {
        setRecords([]);
        setTotal(0);
        setLoading(false);
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
        if (ticket !== jobsRequestSeq.current) return;
        setRecords(result.records ?? []);
        setTotal(result.total ?? 0);
      } catch {
        if (ticket !== jobsRequestSeq.current) return;
        setRecords([]);
        setTotal(0);
        setJobsLoadError(true);
      } finally {
        if (ticket === jobsRequestSeq.current) setLoading(false);
      }
    },
    [keyword, providerType, enabledFilter, rawSourceHint, sourceHintId, sourceHint, projectId],
  );

  useEffect(() => {
    void loadJobs(pageNo, pageSize);
    return () => {
      jobsRequestSeq.current += 1;
    };
  }, [pageNo, pageSize, loadJobs]);

  useEffect(() => {
    const timer = window.setTimeout(() => {
      setKeyword(searchText.trim());
      setPageNo(1);
    }, 300);
    return () => window.clearTimeout(timer);
  }, [searchText]);

  const reload = () => loadJobs(pageNo, pageSize);

  const openCreate = () => {
    setSourceOrigin(null);
    setEditing(null);
    setDefinitionScope(undefined);
    setDrawerOpen(true);
  };

  const openEdit = (job: CollectJobRecord) => {
    setSourceOrigin(null);
    setEditing(job);
    setDefinitionScope(undefined);
    setDrawerOpen(true);
  };

  const execute = async (job: CollectJobRecord, action: 'dry-run' | 'run') => {
    if (!canCreate || busyJob) return;
    if (!supportsCollectTaskExecution(job.providerType)) {
      message.warning('此类任务暂不支持执行');
      return;
    }
    const scopeEpoch = continuationScopeEpoch.current;
    setBusyJob({ id: job.id, action });
    try {
      const run = action === 'dry-run' ? await dryRunCollectJob(job.id) : await runCollectJob(job.id);
      if (scopeEpoch !== continuationScopeEpoch.current) return;
      if (continuedJob?.id === job.id) {
        const confirmed = run.jobId === job.id && Number.isSafeInteger(run.runId) && run.runId > 0
          && run.dryRun === (action === 'dry-run');
        if (confirmed) {
          setContinuedRun(run);
          setContinuedResultUnknown(false);
        } else {
          setContinuedRun(null);
          setContinuedResultUnknown(true);
        }
      }
      setRunResult({
        run,
        title: action === 'dry-run' ? `采集预览 · ${job.jobName}` : `采集结果 · ${job.jobName}`,
      });
    } catch {
      if (scopeEpoch !== continuationScopeEpoch.current) return;
      if (continuedJob?.id === job.id) {
        setContinuedRun(null);
        setContinuedResultUnknown(true);
      }
      // Timeout/rejection has no trustworthy runId, so we cannot infer cancellation or
      // associate any similarly-timed history row with this request.
      Modal.warning({
        title: '暂未收到执行结果',
        content: '任务可能仍在执行，请查看运行历史后再决定是否重新采集。',
        okText: '核对任务历史',
        onOk: () => setHistoryJob(job),
      });
    } finally {
      if (scopeEpoch === continuationScopeEpoch.current) {
        setBusyJob(null);
        await reload();
      }
    }
  };

  const confirmRun = (job: CollectJobRecord) => {
    if (!canCreate || busyJob || !supportsCollectTaskExecution(job.providerType)) return;
    Modal.confirm({
      className: 'collect-execute-confirm',
      title: '开始采集？',
      content: (
        <div className="collect-confirm-scope">
          <h3>{job.jobName}</h3>
          <Descriptions
            size="small"
            column={1}
            items={[
              {
                key: 'source',
                label: '数据源',
                children: dataSourceNames[String(job.dataSourceId)] || '数据源 ' + job.dataSourceId,
              },
              { key: 'database', label: '库名', children: job.databaseName || '全部库' },
              { key: 'schema', label: 'Schema', children: job.schemaName || '未限定' },
              { key: 'table', label: '表范围', children: job.tablePattern || '全部表' },
            ]}
          />
          <p>采集完成后将更新元数据目录。</p>
        </div>
      ),
      okText: '开始采集',
      cancelText: '取消',
      onOk: () => execute(job, 'run'),
    });
  };

  const toggleEnabled = (job: CollectJobRecord) => {
    if (!supportsCollectTaskExecution(job.providerType)) {
      message.warning('此类任务暂不支持定时调度');
      return;
    }
    const next = !job.enabled;
    if (next && !job.dryRunPassed) {
      message.warning('请先完成采集预览，再启用定时调度');
      return;
    }
    Modal.confirm({
      title: `${next ? '启用' : '停用'}任务`,
      content: next
        ? `确定启用「${job.jobName}」？启用后将按已保存的计划执行采集。`
        : `确定停用「${job.jobName}」？停用后暂停定时采集，历史记录保留。`,
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
      content: `确定删除「${job.jobName}」？已采集的元数据和运行历史将保留。`,
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
        return [ds ?? `数据源 ${job.dataSourceId}`, scope || '全部库', job.tablePattern].filter(Boolean).join(' · ');
      },
    [dataSourceNames],
  );

  const columns: ColumnsType<CollectJobRecord> = [
    {
      title: '采集任务',
      dataIndex: 'jobName',
      width: 200,
      render: (name: string, job) => (
        <div className="collect-task-identity">
          <Button type="link" onClick={() => history.push(collectTaskDetailPath(job.id))}>
            {name}
          </Button>
          <span>{job.jobCode}</span>
        </div>
      ),
    },
    {
      title: '数据源与范围',
      key: 'scope',
      width: 260,
      render: (_, job) => (
        <div className="collect-task-scope">
          <span title={scopeText(job)}>{scopeText(job)}</span>
          <small>{PROVIDER_LABELS[job.providerType]}</small>
        </div>
      ),
    },
    {
      title: '调度计划',
      key: 'schedule',
      width: 145,
      render: (_, job) => (
        <Tooltip title={job.cronExpression || '未设置定时计划'}>
          <span className="collect-task-schedule">{describeCollectSchedule(job.cronExpression)}</span>
        </Tooltip>
      ),
    },
    {
      title: '状态',
      key: 'status',
      width: 150,
      render: (_, job) => (
        <div className="collect-task-status">
          <Tag color={job.enabled ? 'processing' : 'default'}>{job.enabled ? '已启用' : '未启用'}</Tag>
          {job.providerType === 'HARVESTED' ? (
            <Tooltip title={job.dryRunPassed ? '当前采集范围已通过预览验证' : '启用定时调度前需先通过采集预览'}>
              <small className={job.dryRunPassed ? 'verified' : ''}>{job.dryRunPassed ? '预览已通过' : '待预览'}</small>
            </Tooltip>
          ) : (
            <small>仅配置</small>
          )}
        </div>
      ),
    },
    {
      title: '更新时间',
      dataIndex: 'updateTime',
      width: 165,
      render: (value?: string) => <span className="collect-muted">{formatMetadataTime(value)}</span>,
    },
    {
      title: '操作',
      key: 'action',
      width: 220,
      fixed: 'right',
      render: (_, job) => {
        const busy = busyJob?.id === job.id;
        const runnable = supportsCollectTaskExecution(job.providerType);
        return (
          <div className="collect-task-actions">
            <Tooltip title={runnable ? '查看预计变更，不更新目录' : '暂不支持执行'}>
              <Button
                type="text"
                disabled={!canCreate || Boolean(busyJob) || !runnable}
                loading={busy && busyJob?.action === 'dry-run'}
                onClick={() => void execute(job, 'dry-run')}
              >
                采集预览
              </Button>
            </Tooltip>
            <Button
              type="primary"
              disabled={!canCreate || Boolean(busyJob) || !runnable}
              loading={busy && busyJob?.action === 'run'}
              onClick={() => confirmRun(job)}
            >
              立即采集
            </Button>
            <Dropdown
              trigger={['click']}
              menu={{
                items: [
                  { key: 'history', label: '运行历史' },
                  { key: 'edit', label: '编辑任务', disabled: !canUpdate || Boolean(busyJob) },
                  {
                    key: 'enabled',
                    label: job.enabled ? '停用调度' : '启用调度',
                    disabled:
                      !canUpdate || Boolean(busyJob) || (job.enabled ? !runnable : !canEnableCollectSchedule(job)),
                  },
                  { type: 'divider' },
                  { key: 'delete', label: '删除任务', danger: true, disabled: !canDelete || Boolean(busyJob) },
                ],
                onClick: ({ key }) => {
                  if (key === 'history') setHistoryJob(job);
                  if (key === 'edit') openEdit(job);
                  if (key === 'enabled') toggleEnabled(job);
                  if (key === 'delete') removeJob(job);
                },
              }}
            >
              <Button type="text" aria-label={'更多任务操作：' + job.jobName} icon={<MoreOutlined />} />
            </Dropdown>
          </div>
        );
      },
    },
  ];

  const hasFilter = Boolean(keyword || providerType || enabledFilter);
  const returnToSource = continuedJob && sourceOrigin
    && collectionJobCovers(continuedJob, sourceOrigin)
    && sourceOrigin.databaseName && sourceOrigin.tableName
    ? buildLiveSourceReturnPath({
        dataSourceId: Number(sourceOrigin.dataSourceId),
        database: sourceOrigin.databaseName,
        schema: sourceOrigin.schemaName,
        table: sourceOrigin.tableName,
      })
    : null;

  return (
    <main className="metadata-collect-page">
      <header className="collect-page-header">
        <div className="collect-page-title">
          <h1>采集任务</h1>
          {sourceHint ? <Tag>{sourceHint.name}</Tag> : null}
        </div>
        <div className="collect-page-header-actions">
          <Button onClick={() => history.push('/data-asset/catalog?view=entity')}>
            元数据目录 <ArrowRightOutlined />
          </Button>
          {canCreate ? (
            <YakButton type="primary" icon={<PlusOutlined />} onClick={openCreate}>
              新建采集任务
            </YakButton>
          ) : null}
        </div>
      </header>
      {sourceHintError ? <Alert type="warning" showIcon message={sourceHintError} /> : null}
      {definitionError ? <Alert type="warning" showIcon message={definitionError} /> : null}
      {jobsLoadError ? (
        <Alert
          type="error"
          showIcon
          message="未能读取采集任务"
          action={<Button onClick={() => void reload()}>重试</Button>}
        />
      ) : null}
      {continuedJob && !sourceHintError ? (
        <CollectionJourneyContinuation
          job={continuedJob}
          sourceName={dataSourceNames[String(continuedJob.dataSourceId)] || `数据源 ${continuedJob.dataSourceId}`}
          run={continuedRun}
          resultUnknown={continuedResultUnknown}
          busy={Boolean(busyJob)}
          canExecute={canCreate && supportsCollectTaskExecution(continuedJob.providerType)}
          sourceReturn={returnToSource ? () => history.push(returnToSource) : undefined}
          onPreview={() => void execute(continuedJob, 'dry-run')}
          onConfirmRun={() => confirmRun(continuedJob)}
          onHistory={() => setHistoryJob(continuedJob)}
          onOpenJob={() => history.push(collectTaskDetailPath(continuedJob.id, sourceHint?.id ? String(sourceHint.id) : undefined))}
          onDismiss={() => { setContinuedJob(null); setContinuedRun(null); setContinuedResultUnknown(false); }}
        />
      ) : null}
      <section className="collect-list-panel">
        <div className="collect-list-toolbar">
          {!sourceHint ? (
            <Segmented
              options={[
                { value: '', label: '全部任务' },
                { value: 'HARVESTED', label: '数据源采集' },
                { value: 'REGISTERED', label: '系统对账' },
              ]}
              value={providerType}
              onChange={(value) => {
                setProviderType(value as CollectProviderType | '');
                setPageNo(1);
              }}
            />
          ) : (
            <span className="collect-task-total">
              {loading || sourceHintPending ? '读取中…' : jobsLoadError || sourceHintError ? '—' : total + ' 个任务'}
            </span>
          )}
          <div className="collect-list-filters">
            <Input
              className="collect-search"
              variant="borderless"
              prefix={<SearchOutlined />}
              allowClear
              aria-label="搜索采集任务"
              placeholder="搜索任务名称或编码"
              value={searchText}
              onChange={(event) => {
                setSearchText(event.target.value);
                if (!event.target.value) {
                  setKeyword('');
                  setPageNo(1);
                }
              }}
              onPressEnter={() => {
                setKeyword(searchText.trim());
                setPageNo(1);
              }}
            />
            <Select
              allowClear
              placeholder="任务状态"
              value={enabledFilter || undefined}
              onChange={(value) => {
                setEnabledFilter((value ?? '') as '' | 'true' | 'false');
                setPageNo(1);
              }}
              options={[
                { value: 'true', label: '已启用' },
                { value: 'false', label: '未启用' },
              ]}
            />
            {hasFilter ? (
              <Button
                type="text"
                onClick={() => {
                  setKeyword('');
                  setSearchText('');
                  setProviderType('');
                  setEnabledFilter('');
                  setPageNo(1);
                }}
              >
                重置
              </Button>
            ) : null}
            <Button
              type="text"
              aria-label="刷新采集任务"
              icon={<ReloadOutlined />}
              loading={loading}
              onClick={() => void reload()}
            />
          </div>
        </div>
        <Table<CollectJobRecord>
          rowKey="id"
          size="middle"
          columns={columns}
          dataSource={records}
          loading={loading || sourceHintPending}
          scroll={{ x: 1140 }}
          locale={{
            emptyText: sourceHintError ? (
              <YakEmpty compact title="数据源暂不可用" />
            ) : sourceHintPending ? (
              <YakEmpty compact title="正在读取数据源" />
            ) : jobsLoadError ? (
              <YakEmpty compact title="任务读取失败，请重试" />
            ) : hasFilter ? (
              <YakEmpty compact title="没有匹配的任务" />
            ) : (
              <YakEmpty compact title="暂无采集任务">
                {canCreate ? (
                  <Button type="primary" onClick={openCreate}>
                    新建采集任务
                  </Button>
                ) : null}
              </YakEmpty>
            ),
          }}
          pagination={{
            current: pageNo,
            pageSize,
            total,
            showSizeChanger: true,
            showTotal: (count) => '共 ' + count + ' 个任务',
            onChange: (nextPageNo, nextPageSize) => {
              setPageNo(nextPageNo);
              setPageSize(nextPageSize);
            },
          }}
        />
      </section>

      <CollectJobDrawer
        open={drawerOpen}
        editing={editing}
        initialDataSourceId={sourceHint?.id}
        initialScope={definitionScope}
        onClose={() => setDrawerOpen(false)}
        onSaved={(saved) => {
          setDrawerOpen(false);
          if (saved.id > 0 && saved.providerType === 'HARVESTED'
              && (sourceHint == null || saved.dataSourceId === sourceHint.id)) {
            setContinuedJob(saved);
            setContinuedRun(null);
            setContinuedResultUnknown(false);
          }
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
        sourceNames={dataSourceNames}
        onClose={() => setRunResult(null)}
      />
    </main>
  );
};

export default MetadataCollectPage;
