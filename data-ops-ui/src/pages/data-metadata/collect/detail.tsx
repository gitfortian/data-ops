import { history, useParams, useSearchParams } from '@umijs/max';
import { Alert, Button, Descriptions, message, Modal, Spin, Tabs, Tag } from 'antd';
import { ArrowLeft } from 'lucide-react';
import { useEffect, useRef, useState } from 'react';
import { useSecurityProject } from '@/contexts/SecurityProjectContext';
import { usePermissionAccess } from '@/hooks/usePermissionAccess';
import { dryRunCollectJob, getCollectJob, runCollectJob } from '@/services/metadata/api';
import type { CollectJobRecord, CollectRunRecord } from '@/services/metadata/types';
import { readCollectTaskOrigin, verifiedCollectTaskReturnPath } from '@/services/metadata/collectionHandoff';
import { formatMetadataTime } from '@/services/metadata/presentation';
import CollectedJobTables from './components/CollectedJobTables';
import CollectionJourneyContinuation from './components/CollectionJourneyContinuation';
import RunHistoryDrawer from './components/RunHistoryDrawer';
import RunResultModal from './components/RunResultModal';
import { supportsCollectTaskExecution } from './collectTaskCapabilities';
import './detail.less';

/** A task detail must not replace persisted Metadata Owner facts with URL hints. */
function CollectTaskDetail() {
  const { id } = useParams<{ id: string }>();
  const [params] = useSearchParams();
  const sourceHint = params.get('dataSourceId');
  const origin = readCollectTaskOrigin(params);
  const { can } = usePermissionAccess();
  const [job, setJob] = useState<CollectJobRecord | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);
  const [version, setVersion] = useState(0);
  const [historyOpen, setHistoryOpen] = useState(false);
  const [activeTab, setActiveTab] = useState('tables');
  const [showJourney, setShowJourney] = useState(true);
  const [executing, setExecuting] = useState<'dry-run' | 'run' | null>(null);
  const [latestRun, setLatestRun] = useState<CollectRunRecord | null>(null);
  const [runResult, setRunResult] = useState<CollectRunRecord | null>(null);
  const [resultUnknown, setResultUnknown] = useState(false);
  const [refreshWarning, setRefreshWarning] = useState(false);
  const operationEpoch = useRef(0);
  const jobId = id && /^[1-9]\d*$/.test(id) && Number.isSafeInteger(Number(id)) ? Number(id) : undefined;
  const canRead = can('data-metadata:read');
  const canExecute = can('data-metadata:create');
  const sourceReturnPath = job ? verifiedCollectTaskReturnPath(job, origin) : null;

  useEffect(() => {
    const requestEpoch = ++operationEpoch.current;
    setJob(null);
    setError(false);
    setLoading(true);
    setExecuting(null);
    setLatestRun(null);
    setRunResult(null);
    setResultUnknown(false);
    setRefreshWarning(false);
    setActiveTab('tables');
    setShowJourney(true);
    setHistoryOpen(false);
    if (!jobId || !canRead) {
      setLoading(false);
      setError(true);
      return;
    }
    void getCollectJob(jobId)
      .then((result) => {
        if (requestEpoch !== operationEpoch.current) return;
        if (
          result.id !== jobId ||
          (sourceHint !== null && (String(result.dataSourceId) !== sourceHint || result.providerType !== 'HARVESTED'))
        ) throw new Error('Task scope mismatch');
        setJob(result);
        if (result.providerType !== 'HARVESTED') setActiveTab('history');
      })
      .catch(() => {
        if (requestEpoch === operationEpoch.current) setError(true);
      })
      .finally(() => {
        if (requestEpoch === operationEpoch.current) setLoading(false);
      });
    return () => { operationEpoch.current += 1; };
  }, [jobId, canRead, sourceHint, version]);

  const execute = async (currentJob: CollectJobRecord, mode: 'dry-run' | 'run') => {
    if (!canExecute || !canRead || !supportsCollectTaskExecution(currentJob.providerType)
        || executing || loading || !job || job.id !== currentJob.id) return;
    const epoch = operationEpoch.current;
    setExecuting(mode);
    setResultUnknown(false);
    setRefreshWarning(false);
    try {
      const receipt = mode === 'dry-run'
        ? await dryRunCollectJob(currentJob.id)
        : await runCollectJob(currentJob.id);
      if (epoch !== operationEpoch.current) return;
      if (receipt.jobId !== currentJob.id || !Number.isSafeInteger(receipt.runId) || receipt.runId <= 0
          || receipt.dryRun !== (mode === 'dry-run')) {
        throw new Error('Unverified collection run receipt');
      }
      setLatestRun(receipt);
      setRunResult(receipt);
      // Refresh the authoritative persisted Job: dryRunPassed and scope may
      // have changed. We never infer a successful preview from local state.
      try {
        const fresh = await getCollectJob(currentJob.id);
        if (epoch !== operationEpoch.current) return;
        if (fresh.id !== currentJob.id || fresh.providerType !== currentJob.providerType
            || fresh.dataSourceId !== currentJob.dataSourceId
            || (sourceHint !== null && String(fresh.dataSourceId) !== sourceHint)) {
          setRefreshWarning(true);
          setJob(null);
          setError(true);
          return;
        }
        const scopeChanged = fresh.databaseName !== currentJob.databaseName
          || fresh.schemaName !== currentJob.schemaName
          || fresh.tablePattern !== currentJob.tablePattern
          || fresh.collectColumns !== currentJob.collectColumns;
        setJob(fresh);
        if (scopeChanged) {
          // An exact run receipt is historical; do not present it as success
          // for a task whose current saved definition changed meanwhile.
          setLatestRun(null);
          setResultUnknown(true);
          setRefreshWarning(true);
        }
      } catch {
        if (epoch === operationEpoch.current) setRefreshWarning(true);
      }
    } catch {
      if (epoch !== operationEpoch.current) return;
      // Failed/timeout requests cannot prove whether the server started or
      // finished. Never generate a runId or optimistically mark success.
      setResultUnknown(true);
      setLatestRun(null);
      Modal.warning({
        title: '暂未收到可信的采集执行结果',
        content: '请求可能已执行，请先通过该任务的运行历史核对，不要立即重复运行。',
        okText: '核对运行历史',
        onOk: () => setHistoryOpen(true),
      });
    } finally {
      if (epoch === operationEpoch.current) setExecuting(null);
    }
  };

  const confirmRun = (currentJob: CollectJobRecord) => {
    if (!canExecute || !canRead || executing || loading || !job
        || job.id !== currentJob.id || !supportsCollectTaskExecution(job.providerType)) return;
    const epoch = operationEpoch.current;
    Modal.confirm({
      title: '确认正式采集已保存的任务范围？',
      content: (
        <Descriptions size="small" column={1} items={[
          { key: 'name', label: '任务', children: currentJob.jobName },
          { key: 'ds', label: '数据源', children: String(currentJob.dataSourceId ?? '—') },
          { key: 'db', label: '库', children: currentJob.databaseName || '全部库' },
          { key: 'schema', label: 'Schema', children: currentJob.schemaName || '全部 Schema' },
          { key: 'tables', label: '表匹配范围', children: currentJob.tablePattern || '全部表' },
        ]} />
      ),
      okText: '确认并采集',
      cancelText: '继续核对范围',
      onOk: () => {
        if (epoch !== operationEpoch.current) {
          message.warning('任务上下文已变化，请重新读取后再确认采集');
          return;
        }
        return execute(currentJob, 'run');
      },
    });
  };

  return (
    <main className="metadata-task-detail">
      <div className="metadata-task-detail-nav">
        <Button type="text" icon={<ArrowLeft size={16} />}
          onClick={() => history.push(
            sourceHint ? `/data-metadata/collect?dataSourceId=${encodeURIComponent(sourceHint)}` : '/data-metadata/collect',
          )}>返回采集任务</Button>
        {sourceReturnPath ? (
          <Button onClick={() => history.push(sourceReturnPath)}>返回原来源表</Button>
        ) : null}
      </div>
      <Spin spinning={loading}>
        {error ? (
          <Alert type="error" showIcon message="未能读取任务详情"
            description="请确认当前项目、任务来源和读取权限。"
            action={<Button onClick={() => setVersion((value) => value + 1)}>重试</Button>} />
        ) : job ? (
          <>
            <header>
              <div className="metadata-task-detail-eyebrow">元数据采集 / 已保存任务 #{job.id}</div>
              <h1>{job.jobName}</h1>
              <p>{job.jobCode} · 采集任务的定义、运行与结果分别以 Metadata 服务端为准</p>
              <div className="metadata-task-detail-flags">
                <Tag>{job.providerType === 'HARVESTED' ? '数据源采集' : '系统对账'}</Tag>
                <Tag color={job.dryRunPassed ? 'success' : 'default'}>
                  {job.dryRunPassed ? '当前范围已通过预演闸门' : '当前范围待预演'}
                </Tag>
                <Tag color={job.enabled ? 'processing' : 'default'}>{job.enabled ? '已启用调度' : '未启用调度'}</Tag>
              </div>
            </header>
            {refreshWarning ? (
              <Alert type="warning" showIcon className="metadata-task-detail-alert"
                message="执行后的任务状态需要核对"
                description="任务当前配置读取失败或执行期间作用域发生变化，请核对运行历史和最新任务范围；旧运行不能冒充新范围的采集结果。"
                action={<Button size="small" onClick={() => setVersion((value) => value + 1)}>重新核验任务</Button>} />
            ) : null}
            {job.providerType === 'HARVESTED' && showJourney ? (
              <CollectionJourneyContinuation
                job={job}
                sourceName={`数据源 ${job.dataSourceId ?? '—'}`}
                run={latestRun}
                resultUnknown={resultUnknown}
                busy={Boolean(executing)}
                canExecute={canExecute}
                sourceReturn={sourceReturnPath ? () => history.push(sourceReturnPath) : undefined}
                onPreview={() => void execute(job, 'dry-run')}
                onConfirmRun={() => confirmRun(job)}
                onHistory={() => setHistoryOpen(true)}
                onOpenJob={() => setActiveTab('tables')}
                onDismiss={() => setShowJourney(false)}
              />
            ) : job.providerType !== 'HARVESTED' ? (
              <Alert type="info" showIcon className="metadata-task-detail-alert"
                message="系统对账任务仅可查看当前已保存配置和运行历史；不支持直接采集" />
            ) : (
              <div className="metadata-task-detail-restore"><Button onClick={() => setShowJourney(true)}>继续采集任务步骤</Button></div>
            )}
            <section className="metadata-task-detail-settings" aria-label="采集任务持久配置">
              <h2>已保存的采集定义</h2>
              <Descriptions bordered size="small" column={{ xs: 1, sm: 2 }} items={[
                { key: 'database', label: '库名', children: job.databaseName || '全部库' },
                { key: 'schema', label: 'Schema', children: job.schemaName || '—' },
                { key: 'pattern', label: '表名匹配式', children: job.tablePattern || '全部表' },
                { key: 'columns', label: '字段结构',
                  children: job.collectColumns == null ? '—' : job.collectColumns ? '包含字段' : '仅表结构' },
                { key: 'schedule', label: '调度',
                  children: <Tag color={job.enabled ? 'processing' : 'default'}>{job.enabled ? '已启用' : '未启用'}</Tag> },
                { key: 'cron', label: '调度表达式', children: job.cronExpression || '—' },
                { key: 'updated', label: '配置更新时间', children: formatMetadataTime(job.updateTime) },
                { key: 'provider', label: '采集通道', children: job.providerType === 'HARVESTED' ? '物理采集' : '投影对账' },
              ]} />
            </section>
            <Tabs className="metadata-task-detail-tabs" activeKey={activeTab} onChange={setActiveTab} items={[
              ...(job.providerType === 'HARVESTED' && job.dataSourceId ? [{
                key: 'tables', label: '当前目录关联表',
                children: <CollectedJobTables job={job} sourceId={String(job.dataSourceId)} />,
              }] : []),
              {
                key: 'history', label: '运行历史',
                children: <Button onClick={() => setHistoryOpen(true)}>查看运行历史及本次执行结果</Button>,
              },
            ]} />
            {historyOpen ? <RunHistoryDrawer open job={job} onClose={() => setHistoryOpen(false)} /> : null}
            <RunResultModal run={runResult} title={runResult?.dryRun ? `采集预览 · ${job.jobName}` : `采集结果 · ${job.jobName}`}
              onClose={() => setRunResult(null)} />
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
