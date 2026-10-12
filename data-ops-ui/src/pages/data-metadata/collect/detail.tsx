import { history, useParams, useSearchParams } from '@umijs/max';
import { Alert, Button, Descriptions, Spin, Tabs, Tag } from 'antd';
import { ArrowLeft } from 'lucide-react';
import { useEffect, useState } from 'react';
import { useSecurityProject } from '@/contexts/SecurityProjectContext';
import { usePermissionAccess } from '@/hooks/usePermissionAccess';
import { getCollectJob } from '@/services/metadata/api';
import type { CollectJobRecord } from '@/services/metadata/types';
import { formatMetadataTime } from '@/services/metadata/presentation';
import CollectedJobTables from './components/CollectedJobTables';
import RunHistoryDrawer from './components/RunHistoryDrawer';
import './detail.less';

function CollectTaskDetail() {
  const { id } = useParams<{ id: string }>();
  const [params] = useSearchParams();
  const sourceHint = params.get('dataSourceId');
  const { can } = usePermissionAccess();
  const [job, setJob] = useState<CollectJobRecord | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);
  const [version, setVersion] = useState(0);
  const [historyOpen, setHistoryOpen] = useState(false);
  const jobId = id && /^[1-9]\d*$/.test(id) && Number.isSafeInteger(Number(id)) ? Number(id) : undefined;
  const canRead = can('data-metadata:read');
  useEffect(() => {
    let active = true;
    setJob(null);
    setError(false);
    setLoading(true);
    if (!jobId || !canRead) {
      setLoading(false);
      setError(true);
      return;
    }
    void getCollectJob(jobId)
      .then((result) => {
        if (!active) return;
        if (
          result.id !== jobId ||
          (sourceHint !== null && (String(result.dataSourceId) !== sourceHint || result.providerType !== 'HARVESTED'))
        )
          throw new Error('Task scope mismatch');
        setJob(result);
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
  }, [jobId, canRead, sourceHint, version]);
  return (
    <main className="metadata-task-detail">
      <Button
        type="text"
        icon={<ArrowLeft size={16} />}
        onClick={() =>
          history.push(
            sourceHint
              ? `/data-metadata/collect?dataSourceId=${encodeURIComponent(sourceHint)}`
              : '/data-metadata/collect',
          )
        }
      >
        返回采集任务
      </Button>
      <Spin spinning={loading}>
        {error ? (
          <Alert
            type="error"
            showIcon
            message="未能读取任务详情"
            description="请确认当前项目、任务来源和读取权限。"
            action={<Button onClick={() => setVersion((value) => value + 1)}>重试</Button>}
          />
        ) : job ? (
          <>
            <header>
              <h1>{job.jobName}</h1>
              <p>{job.jobCode}</p>
            </header>
            <Descriptions
              bordered
              size="small"
              column={{ xs: 1, sm: 2 }}
              items={[
                { key: 'database', label: '库名', children: job.databaseName || '全部库' },
                { key: 'schema', label: 'Schema', children: job.schemaName || '—' },
                { key: 'pattern', label: '表名匹配式', children: job.tablePattern || '全部表' },
                {
                  key: 'columns',
                  label: '字段结构',
                  children: job.collectColumns == null ? '—' : job.collectColumns ? '包含字段' : '仅表结构',
                },
                {
                  key: 'schedule',
                  label: '调度',
                  children: (
                    <Tag color={job.enabled ? 'processing' : 'default'}>{job.enabled ? '已启用' : '未启用'}</Tag>
                  ),
                },
                { key: 'cron', label: '调度表达式', children: job.cronExpression || '—' },
                { key: 'updated', label: '配置更新时间', children: formatMetadataTime(job.updateTime) },
                {
                  key: 'provider',
                  label: '采集通道',
                  children: job.providerType === 'HARVESTED' ? '物理采集' : '投影对账',
                },
              ]}
            />
            <Tabs
              className="metadata-task-detail-tabs"
              items={[
                ...(job.providerType === 'HARVESTED' && job.dataSourceId
                  ? [
                      {
                        key: 'tables',
                        label: '已采集表',
                        children: <CollectedJobTables job={job} sourceId={String(job.dataSourceId)} />,
                      },
                    ]
                  : []),
                {
                  key: 'history',
                  label: '运行历史',
                  children: <Button onClick={() => setHistoryOpen(true)}>查看运行历史</Button>,
                },
              ]}
            />
            {historyOpen ? <RunHistoryDrawer open job={job} onClose={() => setHistoryOpen(false)} /> : null}
          </>
        ) : null}
      </Spin>
    </main>
  );
}

export default function ScopedCollectTaskDetail() {
  const { currentProject } = useSecurityProject();
  const { permissionCodes } = usePermissionAccess();
  return <CollectTaskDetail key={JSON.stringify([currentProject?.id, permissionCodes])} />;
}
