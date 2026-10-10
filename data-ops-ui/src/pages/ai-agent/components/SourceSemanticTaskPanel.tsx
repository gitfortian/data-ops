import React from 'react';
import { Alert, Button, Checkbox, Input, Select, Space, Spin, Tag, Typography } from 'antd';
import {
  sourceSemanticApi, sourceTaskPath,
  type SourceSelection, type SourceTable, type SourceTask,
} from '@/services/agent/sourceSemantic';
import { newSessionId } from '../types';
import SourceSemanticCandidatePanel from './SourceSemanticCandidatePanel';

const terminal = (status: string) => ['COMPLETED', 'CANCELLED'].includes(status);
const Status: React.FC<{ task: SourceTask }> = ({ task }) => (
  <Space wrap>
    <Tag>{task.status}</Tag>
    {task.originalStatus && <Tag color="processing">原 Turn：{task.originalStatus}</Tag>}
    <span>分片 {task.completedChunks}/{task.totalChunks}</span>
    <span>轮次 {task.usedTurns}/{task.maxTurns}</span>
    <span>工具额度 {task.reservedToolCalls}/{task.maxToolCalls}</span>
  </Space>
);

/**
 * Original Agent page handoff. No separate top-level route and no browser-driven
 * next-turn loop. The existing durable AgentTurn dispatcher continues a running turn
 * after the tab closes; each additional slice requires fresh authenticated admission.
 */
const SourceSemanticTaskPanel: React.FC<{ dataSourceId: string; initialTaskId?: string }> = ({
  dataSourceId, initialTaskId,
}) => {
  const [tables, setTables] = React.useState<Array<{ assetKey: string; tableName: string }>>([]);
  const [selectedKey, setSelectedKey] = React.useState<string>();
  const [columns, setColumns] = React.useState<Record<string, SourceTable>>({});
  const [checked, setChecked] = React.useState<Record<string, string[]>>({});
  const [context, setContext] = React.useState('');
  const [page, setPage] = React.useState(1);
  const [task, setTask] = React.useState<SourceTask | null>(null);
  const [reviewed, setReviewed] = React.useState(false);
  const [loading, setLoading] = React.useState(false);
  const [error, setError] = React.useState('');
  const [artifacts, setArtifacts] = React.useState<Record<string, string>>({});

  const refresh = React.useCallback(async (taskId: string) => {
    const value = await sourceSemanticApi.read(taskId);
    setTask(value);
    return value;
  }, []);

  React.useEffect(() => {
    let cancelled = false;
    if (initialTaskId) {
      refresh(initialTaskId).catch((e) => {
        if (!cancelled) setError(String((e as Error).message || e));
      });
    } else {
      sourceSemanticApi.tables(dataSourceId, 1)
        .then((items) => { if (!cancelled) setTables(items); })
        .catch((e) => { if (!cancelled) setError(String((e as Error).message || e)); });
    }
    return () => { cancelled = true; };
  }, [dataSourceId, initialTaskId, refresh]);

  React.useEffect(() => {
    if (!task || terminal(task.status) || task.status === 'PAUSED'
        || task.status === 'INTERRUPTED' || task.status === 'PLANNED') return;
    const timer = setInterval(() => {
      refresh(task.taskId).catch((e) => setError(String((e as Error).message || e)));
    }, 3000);
    return () => clearInterval(timer);
  }, [task?.taskId, task?.status, refresh]);

  const run = async (fn: () => Promise<SourceTask>) => {
    setLoading(true);
    setError('');
    try {
      const next = await fn();
      setTask(next);
      window.history.replaceState(null, '', sourceTaskPath(dataSourceId, next.taskId));
    } catch (e) {
      setError(String((e as Error).message || e));
    } finally {
      setLoading(false);
    }
  };

  const choose = async (assetKey: string) => {
    setSelectedKey(assetKey);
    if (columns[assetKey]) return;
    setLoading(true);
    setError('');
    try {
      const table = await sourceSemanticApi.columns(dataSourceId, assetKey);
      setColumns((old) => ({ ...old, [assetKey]: table }));
      setChecked((old) => ({ ...old, [assetKey]: table.columns.map((c) => c.name) }));
    } catch (e) {
      setError('当前表尚未完成结构采集，或没有访问权限。请先返回数据源采集页面核对：'
        + String((e as Error).message || e));
    } finally {
      setLoading(false);
    }
  };

  const selection: SourceSelection[] = Object.entries(checked)
    .filter(([, list]) => list.length > 0)
    .map(([assetKey, columnNames]) => ({ assetKey, columnNames }));
  const create = async () => {
    const view = await sourceSemanticApi.preview(dataSourceId, selection);
    if (!view.evidence.tables.length) throw new Error('没有可以确认的采集结构');
    return sourceSemanticApi.create({
      dataSourceId, sessionId: newSessionId(), selection, businessContext: context,
    });
  };

  const artifact = async (chunkId: string) => {
    setLoading(true);
    try {
      const result = await sourceSemanticApi.artifact(task!.taskId, chunkId);
      setArtifacts((old) => ({ ...old, [chunkId]: result.markdown }));
    } catch (e) {
      setError(String((e as Error).message || e));
    } finally {
      setLoading(false);
    }
  };

  return (
    <section aria-label="F-039 来源业务语义理解" style={{
      marginBottom: 12, padding: 16, borderRadius: 10, border: '1px solid #e6eaf0',
      maxHeight: '50vh', overflowY: 'auto',
    }}>
      <Typography.Title level={5} style={{ margin: 0 }}>来源结构业务理解</Typography.Title>
      <Typography.Paragraph type="secondary" style={{ marginBottom: 10 }}>
        基于已采集的 Schema 分片分析，不读取业务数据行，不创建正式语义对象。
      </Typography.Paragraph>
      {error && <Alert type="warning" showIcon message={error} style={{ marginBottom: 10 }} />}
      {loading && <Spin size="small" />}
      {!task && <>
        <Space wrap>
          <Select value={selectedKey} style={{ width: 300 }}
            placeholder="选取已采集的物理表"
            showSearch optionFilterProp="label"
            options={tables.map((table) => ({
              value: table.assetKey, label: `${table.tableName} (${table.assetKey})`,
            }))} onChange={choose} />
          <Button disabled={loading || tables.length < 50} onClick={async () => {
            const nextPage = page + 1;
            setLoading(true);
            try {
              const next = await sourceSemanticApi.tables(dataSourceId, nextPage);
              setTables((old) => [...old, ...next]);
              setPage(nextPage);
            } catch (e) { setError(String((e as Error).message || e)); }
            finally { setLoading(false); }
          }}>更多表</Button>
          <Button href="/data-source">返回数据源采集</Button>
        </Space>
        {selectedKey && columns[selectedKey] && (
          <div style={{ marginTop: 12 }}>
            <Typography.Text strong>{columns[selectedKey].name} — 明确选择需要分析的字段</Typography.Text>
            <div style={{ marginTop: 8 }}>
              <Checkbox.Group
                value={checked[selectedKey] ?? []}
                onChange={(value) => setChecked((old) => ({
                  ...old, [selectedKey]: value.map(String),
                }))}
                options={columns[selectedKey].columns.map((c) => ({
                  label: `${c.name} (${c.dataType || '未知类型'})${c.comment ? ' · ' + c.comment : ''}`,
                  value: c.name,
                }))}
                style={{ display: 'flex', flexDirection: 'column', gap: 6 }}
              />
            </div>
          </div>
        )}
        <Typography.Paragraph style={{ marginTop: 10, marginBottom: 4 }}>
          已选择 {selection.length} 张表。每批不超过 20 张表、500 列；超限在后端拒绝。
        </Typography.Paragraph>
        <Input.TextArea value={context} maxLength={1024} showCount rows={2}
          onChange={(e) => setContext(e.target.value)} placeholder="业务背景（用户陈述，需人工确认）" />
        <Button type="primary" style={{ marginTop: 10 }} disabled={!selection.length || loading}
          onClick={() => run(create)}>生成可核对计划</Button>
      </>}
      {task && <>
        <div style={{ marginTop: 10 }}><Status task={task} /></div>
        <Typography.Paragraph type="secondary" copyable={{ text: task.taskId }}>
          任务 ID：{task.taskId}
        </Typography.Paragraph>
        {task.status === 'PLANNED' && (
          <>
            <Typography.Text strong>请核对以下完整计划及来源身份</Typography.Text>
            <pre style={{ whiteSpace: 'pre-wrap', maxHeight: 180, overflow: 'auto',
              border: '1px solid #eee', padding: 10 }}>{task.planMarkdown}</pre>
            <Checkbox checked={reviewed} onChange={(e) => setReviewed(e.target.checked)}>
              我已核对来源、分片与限制；同意按此版本继续
            </Checkbox>
            <div style={{ marginTop: 8 }}>
              <Button type="primary" disabled={!reviewed || loading}
                onClick={() => run(() => sourceSemanticApi.approve(task.taskId, task.planSha256))}>
                确认计划
              </Button>
            </div>
          </>
        )}
        <Space wrap>
          {task.status === 'READY' && <Button type="primary" disabled={loading}
            onClick={() => run(() => sourceSemanticApi.action(task.taskId, 'next'))}>
              执行下一分片
            </Button>}
          {(task.status === 'READY' || task.status === 'RUNNING') && <Button disabled={loading}
            onClick={() => run(() => sourceSemanticApi.action(task.taskId, 'pause'))}>
              {task.status === 'RUNNING' ? '当前分片结束后暂停' : '暂停'}
            </Button>}
          {['PAUSED', 'INTERRUPTED'].includes(task.status) && <Button disabled={loading}
            onClick={() => run(() => sourceSemanticApi.action(task.taskId, 'resume'))}>
              重新核对后恢复
            </Button>}
          {!terminal(task.status) && <Button danger disabled={loading}
            onClick={() => run(() => sourceSemanticApi.action(task.taskId, 'cancel'))}>停止任务</Button>}
          <Button disabled={loading} onClick={() => run(() => refresh(task.taskId))}>刷新进度</Button>
        </Space>
        {(task.status === 'RUNNING' || task.status === 'PAUSE_REQUESTED') &&
          <Alert type="info" showIcon style={{ marginTop: 8 }}
            message="原轮次在服务端执行，关闭页面不会把它当成已完成；刷新可核对真实状态。" />}
        {task.status === 'COMPLETED' &&
          <Alert type="success" showIcon message="全部已核验分片完成。结果仅供业务理解和人工复核。" />}
        {task.status === 'COMPLETED' && <SourceSemanticCandidatePanel taskId={task.taskId} />}
        {Object.entries(task.completedTurnIds).map(([chunkId, turnId]) => (
          <div key={chunkId} style={{ marginTop: 10 }}>
            <Space><Typography.Text>分片 {chunkId.slice(0, 10)} · 原轮次 {turnId}</Typography.Text>
              <Button size="small" onClick={() => artifact(chunkId)}>查看证据结果</Button>
            </Space>
            {artifacts[chunkId] && <pre style={{ whiteSpace: 'pre-wrap',
              maxHeight: 200, overflow: 'auto' }}>{artifacts[chunkId]}</pre>}
          </div>
        ))}
      </>}
    </section>
  );
};

export default SourceSemanticTaskPanel;
