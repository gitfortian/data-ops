import Table from '@/components/ReadableTable';
import { YakButton } from '@/components/ui';
import { history } from '@umijs/max';
import { Alert, Button, Input, Space, Spin, Tag } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { ArrowUpRight, RefreshCw, Search } from 'lucide-react';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { listDataSourceDatabases, listDataSourceSchemas } from '@/services/data-source/api';
import { pageCollectRuns } from '@/services/metadata/api';
import {
  collectDefinitionPath,
  collectTaskDetailPath,
  loadSourceCollectionJobs,
} from '@/services/metadata/collectionHandoff';
import { formatMetadataTime } from '@/services/metadata/presentation';
import type { CollectJobRecord, CollectRunRecord } from '@/services/metadata/types';

interface RelationRow {
  key: string;
  databaseName?: string;
  schemaName?: string;
  task: CollectJobRecord | null;
  unavailable?: boolean;
}
const sameIdentifier = (a?: string | null, b?: string | null) =>
  (a || '').trim().toLowerCase() === (b || '').trim().toLowerCase();
const runLabels: Record<string, string> = { RUNNING: '采集中', SUCCESS: '成功', FAILED: '失败', SUSPECT: '已熔断' };
const runColors: Record<string, string> = {
  RUNNING: 'processing',
  SUCCESS: 'success',
  FAILED: 'error',
  SUSPECT: 'warning',
};

type RunReader = (id: number) => Promise<CollectRunRecord | null>;
function TaskRunValue({ taskId, field, readRun }: { taskId: number; field: 'status' | 'time'; readRun: RunReader }) {
  const [run, setRun] = useState<CollectRunRecord | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);
  useEffect(() => {
    let active = true;
    setLoading(true);
    setError(false);
    setRun(null);
    void readRun(taskId)
      .then((result) => {
        if (active) setRun(result);
      })
      .catch(() => {
        if (active) setError(true);
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [taskId, readRun]);
  if (field === 'time')
    return (
      <span title={error ? '正式采集记录暂不可读' : undefined}>
        {loading || error || !run ? '—' : formatMetadataTime(run.finishedAt || run.startedAt)}
      </span>
    );
  if (loading) return <span className="source-workspace-scope">读取中</span>;
  if (error) return <Tag>暂不可读</Tag>;
  if (!run) return <Tag>未采集</Tag>;
  return <Tag color={runColors[run.status || '']}>{runLabels[run.status || ''] || '待核验'}</Tag>;
}

export default function CollectionInfo({ sourceId, canCreate }: { sourceId: string; canCreate: boolean }) {
  const [rows, setRows] = useState<RelationRow[]>([]);
  const [keyword, setKeyword] = useState('');
  const [page, setPage] = useState(1);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);
  const [version, setVersion] = useState(0);
  const runCache = useMemo(() => new Map<number, Promise<CollectRunRecord | null>>(), [sourceId, version]);
  const readRun = useCallback<RunReader>(
    (id) => {
      if (!runCache.has(id))
        runCache.set(
          id,
          pageCollectRuns({ jobId: id, pageNo: 1, pageSize: 1, dryRun: false }).then((result) => {
            const run = result.records[0];
            if (!run && result.total === 0) return null;
            if (!run || run.jobId !== id || run.dryRun !== false || run.triggerType === 'DRY_RUN')
              throw new Error('Unverified formal run');
            return run;
          }),
        );
      return runCache.get(id)!;
    },
    [runCache],
  );

  useEffect(() => {
    let active = true;
    setLoading(true);
    setError(false);
    setRows([]);
    setPage(1);
    const load = async () => {
      try {
        const [databases, jobs] = await Promise.all([
          listDataSourceDatabases(sourceId),
          loadSourceCollectionJobs(sourceId),
        ]);
        if (!active) return;
        // Include saved library scopes even when source discovery no longer returns that library.
        const libraryNames = [
          ...new Set([...databases, ...jobs.flatMap((job) => (job.databaseName ? [job.databaseName] : []))]),
        ];
        const scopes = libraryNames.length ? libraryNames : jobs.length ? [undefined] : [];
        const nextRows: RelationRow[] = [];
        for (let start = 0; start < scopes.length; start += 4) {
          if (!active) return;
          const batch = await Promise.all(
            scopes.slice(start, start + 4).map(async (databaseName) => {
              try {
                const sourceSchemas = await listDataSourceSchemas(sourceId, databaseName);
                const libraryJobs = jobs.filter(
                  (job) => !job.databaseName || sameIdentifier(job.databaseName, databaseName),
                );
                const schemaNames = [
                  ...new Set([
                    ...sourceSchemas,
                    ...libraryJobs.flatMap((job) => (job.schemaName ? [job.schemaName] : [])),
                  ]),
                ];
                return (schemaNames.length ? schemaNames : [undefined]).flatMap((schemaName) => {
                  const applicable = libraryJobs.filter(
                    (job) => !job.schemaName || sameIdentifier(job.schemaName, schemaName),
                  );
                  return (applicable.length ? applicable : [null]).map((task) => ({
                    key: JSON.stringify([databaseName, schemaName, task?.id]),
                    databaseName,
                    schemaName,
                    task,
                  }));
                });
              } catch {
                return [
                  {
                    key: JSON.stringify([databaseName, 'unavailable']),
                    databaseName,
                    schemaName: undefined,
                    task: null,
                    unavailable: true,
                  },
                ];
              }
            }),
          );
          nextRows.push(...batch.flat());
        }
        if (active) setRows(nextRows.sort((a, b) => Number(Boolean(b.task)) - Number(Boolean(a.task))));
      } catch {
        if (active) setError(true);
      } finally {
        if (active) setLoading(false);
      }
    };
    void load();
    return () => {
      active = false;
    };
  }, [sourceId, version]);

  const filtered = rows.filter((row) =>
    [row.databaseName, row.schemaName, row.task?.jobName].some((value) =>
      (value || '').toLowerCase().includes(keyword.trim().toLowerCase()),
    ),
  );
  const columns: ColumnsType<RelationRow> = [
    {
      title: '库名',
      dataIndex: 'databaseName',
      width: 145,
      render: (name?: string) => <strong>{name || '全部库'}</strong>,
    },
    {
      title: 'Schema',
      key: 'schema',
      width: 110,
      render: (_, row) =>
        row.unavailable ? <span className="source-workspace-scope">暂不可读</span> : row.schemaName || '—',
    },
    {
      title: '采集任务',
      key: 'task',
      render: (_, row) =>
        row.unavailable ? '未能核验' : row.task?.jobName || <span className="source-workspace-scope">未配置任务</span>,
    },
    {
      title: '状态',
      key: 'status',
      width: 96,
      render: (_, row) =>
        row.task ? (
          <Tag color={row.task.enabled ? 'processing' : 'default'}>{row.task.enabled ? '已启用' : '未启用'}</Tag>
        ) : (
          '—'
        ),
    },
    {
      title: '最近一次运行状态',
      key: 'runStatus',
      width: 145,
      render: (_, row) => (row.task ? <TaskRunValue taskId={row.task.id} field="status" readRun={readRun} /> : '—'),
    },
    {
      title: '最近一次采集时间',
      key: 'time',
      width: 175,
      render: (_, row) => (row.task ? <TaskRunValue taskId={row.task.id} field="time" readRun={readRun} /> : '—'),
    },
    {
      title: '操作',
      key: 'action',
      width: 105,
      render: (_, row) =>
        row.task ? (
          <Button type="link" size="small" onClick={() => history.push(collectTaskDetailPath(row.task!.id, sourceId))}>
            查看
          </Button>
        ) : !row.unavailable && canCreate ? (
          <Button
            type="link"
            size="small"
            onClick={() =>
              history.push(
                collectDefinitionPath({
                  dataSourceId: sourceId,
                  databaseName: row.databaseName,
                  schemaName: row.schemaName,
                }),
              )
            }
          >
            新建任务
          </Button>
        ) : (
          '—'
        ),
    },
  ];
  return (
    <>
      <div className="source-workspace-toolbar">
        <Input
          className="source-workspace-search"
          variant="borderless"
          prefix={<Search size={16} aria-hidden="true" />}
          aria-label="搜索库、Schema 或任务"
          placeholder="搜索库、Schema 或任务"
          allowClear
          value={keyword}
          onChange={(event) => {
            setKeyword(event.target.value);
            setPage(1);
          }}
        />
        <Space>
          <Button
            aria-label="刷新采集信息"
            icon={<RefreshCw size={14} />}
            onClick={() => setVersion((value) => value + 1)}
          />
          <YakButton
            onClick={() => history.push(`/data-metadata/collect?dataSourceId=${encodeURIComponent(sourceId)}`)}
            icon={<ArrowUpRight size={15} />}
          >
            采集任务管理
          </YakButton>
        </Space>
      </div>
      {error ? (
        <Alert
          type="error"
          showIcon
          message="未能读取库与采集任务关系"
          action={<Button onClick={() => setVersion((value) => value + 1)}>重试</Button>}
        />
      ) : (
        <Spin spinning={loading}>
          <Table
            rowKey="key"
            size="middle"
            columns={columns}
            dataSource={filtered}
            scroll={{ x: 925 }}
            locale={{
              emptyText: loading
                ? '正在读取库与任务关系…'
                : keyword
                  ? '没有匹配的库或任务'
                  : '当前数据源没有可展示的库',
            }}
            pagination={{
              current: page,
              pageSize: 8,
              total: filtered.length,
              showSizeChanger: false,
              hideOnSinglePage: true,
              onChange: setPage,
            }}
          />
        </Spin>
      )}
    </>
  );
}
