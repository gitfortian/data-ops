import { history } from '@umijs/max';
import { Button, Input, Select, Space, Table, Tag, message } from 'antd';
import { CheckCircle2, ChevronDown, Database, RefreshCw, Settings2 } from 'lucide-react';
import { useCallback, useEffect, useRef, useState } from 'react';
import { useSecurityProject } from '@/contexts/SecurityProjectContext';
import usePermissionAccess from '@/hooks/usePermissionAccess';
import { useLatestOperation } from '@/hooks/useLatestOperation';
import { listAllDataSources } from '@/services/data-source/api';
import {
  getModelingVersion, inspectOdsPhysicalTarget, listOdsTargetObservations, recordOdsTargetObservation,
  type OdsPhysicalTargetInspection, type OdsTargetObservation,
} from '@/services/modeling/api';
import type { ModelingModelRecord } from '@/services/modeling/types';
import { buildIntegrationCreatePath } from '@/services/integration/sourceHandoff';
import { discoverOdsExecutionsBefore, type OdsExecutionEvidence } from '@/services/integration/odsExecutionEvidence';
import ModelDdlPanel from './ModelDdlPanel';
import './ModelPhysicalTablePanel.less';

interface Props { model: ModelingModelRecord; defaultTargetId?: number; defaultDatabase?: string }
const formatTime = (value?: string) => value
  ? (value.endsWith('Z') ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : value.replace('T', ' ').slice(0, 19)) : '—';
const issues: Record<string, string> = { MISSING: '缺少字段', UNEXPECTED: '额外字段', TYPE_OR_NULLABILITY_REVIEW: '类型或可空性不同' };
const runLabels: Record<string, string> = {
  SUCCEEDED: '成功', FAILED: '失败', RUNNING: '运行中', PENDING: '等待中', QUEUED: '排队中',
  CANCELLED: '已取消', CANCELED: '已取消', UNKNOWN: '未知',
};

export default function ModelPhysicalTablePanel(props: Props) {
  const { currentProject } = useSecurityProject();
  const { canAll } = usePermissionAccess();
  if (!canAll(['modeling:read'])) return <div className="model-physical-empty">无模型读取权限</div>;
  const allowed = canAll(['modeling:read', 'resource:data-source:read']);
  const mayRecord = allowed && canAll(['modeling:update']);
  const scope = JSON.stringify([currentProject?.id, props.model.id, props.model.publishedVersionId,
    props.model.latestVersionNo, allowed, mayRecord]);
  return <PhysicalTableContent key={scope} {...props} scope={scope} allowed={allowed} mayRecord={mayRecord} />;
}

function PhysicalTableContent({ model, defaultTargetId, defaultDatabase = '', scope, allowed, mayRecord }:
  Props & { scope: string; allowed: boolean; mayRecord: boolean }) {
  const modelId = Number(model.id);
  const versionNo = Number(model.latestVersionNo);
  const hasVersion = Boolean(model.publishedVersionId && Number.isSafeInteger(versionNo) && versionNo > 0);
  const [sources, setSources] = useState<{ id: number; name: string; dbType?: string }[]>([]);
  const [sourcesLoading, setSourcesLoading] = useState(false);
  const [sourcesError, setSourcesError] = useState('');
  const [targetId, setTargetId] = useState(defaultTargetId);
  const [database, setDatabase] = useState(defaultDatabase);
  const [schema, setSchema] = useState('');
  const [targetEdited, setTargetEdited] = useState(false);
  const [configOpen, setConfigOpen] = useState(!defaultTargetId || !defaultDatabase);
  const [publishedTable, setPublishedTable] = useState('');
  const [snapshotError, setSnapshotError] = useState('');
  const [report, setReport] = useState<OdsPhysicalTargetInspection>();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [recording, setRecording] = useState(false);
  const [observations, setObservations] = useState<OdsTargetObservation[]>([]);
  const [observationsError, setObservationsError] = useState('');
  const [historyOpen, setHistoryOpen] = useState(false);
  const [fieldsOpen, setFieldsOpen] = useState(false);
  const [executionObservationId, setExecutionObservationId] = useState<number>();
  const [executionCursors, setExecutionCursors] = useState<number[]>([Number.MAX_SAFE_INTEGER]);
  const [executionNextCursor, setExecutionNextCursor] = useState(Number.MAX_SAFE_INTEGER);
  const [executionHistory, setExecutionHistory] = useState<OdsExecutionEvidence[]>([]);
  const [executionHasMore, setExecutionHasMore] = useState(false);
  const [executionLoading, setExecutionLoading] = useState(false);
  const [executionError, setExecutionError] = useState('');
  const beginRead = useLatestOperation(scope);
  const beginRecord = useLatestOperation(scope);
  const beginHistory = useLatestOperation(scope);
  const beginExecution = useLatestOperation(scope);
  const autoInspected = useRef('');

  const loadObservations = useCallback(async () => {
    if (!allowed) return;
    const current = beginHistory();
    setObservationsError('');
    try {
      const rows = await listOdsTargetObservations(modelId);
      if (current()) setObservations(rows ?? []);
    } catch {
      if (current()) setObservationsError('核验记录暂不可读取');
    }
  }, [allowed, modelId, beginHistory]);

  useEffect(() => {
    let active = true;
    void loadObservations();
    if (allowed) {
      setSourcesLoading(true);
      void listAllDataSources().then(page => {
        if (active) setSources((page.bizData ?? []).filter(row => Number.isSafeInteger(Number(row.id)))
          .map(row => ({ id: Number(row.id), name: row.name || '未命名数据源', dbType: row.dbType })));
      }).catch(() => { if (active) setSourcesError('数据源目录暂不可读取'); })
        .finally(() => { if (active) setSourcesLoading(false); });
    }
    if (hasVersion) void getModelingVersion(modelId, versionNo).then(snapshot => {
      if (active && String(snapshot.structure.modelId) === String(modelId)
          && snapshot.version.versionNo === versionNo
          && snapshot.version.id === model.publishedVersionId) {
        setPublishedTable(snapshot.structure.tableName || snapshot.structure.modelCode || '');
      }
    }).catch(() => { if (active) setSnapshotError('发布版本信息暂不可读取'); });
    return () => { active = false; };
  }, [allowed, hasVersion, modelId, model.publishedVersionId, versionNo, loadObservations]);

  useEffect(() => {
    if (targetEdited) return;
    beginRead(); setReport(undefined); setBusy(false); setError(''); setSchema('');
    setTargetId(defaultTargetId); setDatabase(defaultDatabase);
    setConfigOpen(!defaultTargetId || !defaultDatabase);
  }, [defaultTargetId, defaultDatabase, targetEdited, beginRead]);

  const changeTarget = () => {
    beginRead(); beginRecord(); beginExecution();
    setTargetEdited(true); setReport(undefined); setError(''); setBusy(false); setRecording(false);
    setExecutionHistory([]); setExecutionObservationId(undefined); setExecutionError('');
    setExecutionLoading(false); setExecutionHasMore(false); setFieldsOpen(false);
  };

  const inspect = useCallback(async () => {
    if (!allowed || !hasVersion || !targetId || !database.trim()) return;
    const current = beginRead();
    setBusy(true); setReport(undefined); setError('');
    try {
      const result = await inspectOdsPhysicalTarget(modelId, versionNo, targetId, database.trim(), schema.trim() || undefined);
      if (!current()) return;
      if (result.modelId !== modelId || result.versionNo !== versionNo
          || result.versionId !== model.publishedVersionId || result.targetDatasourceId !== targetId
          || result.targetDatabase !== database.trim()
          || (schema.trim() && (result.targetSchema || '') !== schema.trim())) {
        throw new Error('目标或发布版本已变化，请刷新模型后重试');
      }
      setSchema(result.targetSchema || ''); setReport(result);
    } catch (failure) {
      if (current()) setError(failure instanceof Error ? failure.message : '目标库暂不可核验');
    } finally { if (current()) setBusy(false); }
  }, [allowed, hasVersion, modelId, versionNo, model.publishedVersionId, targetId, database, schema, beginRead]);

  useEffect(() => {
    const key = JSON.stringify([defaultTargetId, defaultDatabase]);
    if (!targetEdited && allowed && hasVersion && targetId && database.trim()
        && targetId === defaultTargetId && database === defaultDatabase && autoInspected.current !== key) {
      autoInspected.current = key;
      void inspect();
    }
  }, [targetEdited, allowed, hasVersion, defaultTargetId, defaultDatabase, targetId, database, inspect]);

  const recordObservation = async () => {
    if (!mayRecord || !targetId || report?.state !== 'COLUMN_MATCH_NEEDS_DDL_REVIEW') return;
    const current = beginRecord();
    setRecording(true);
    try {
      const saved = await recordOdsTargetObservation(modelId, versionNo, targetId, database.trim(), schema.trim() || undefined);
      if (!current()) return;
      const rows = await listOdsTargetObservations(modelId);
      if (!current()) return;
      const confirmed = rows.find(row => row.id === saved.id);
      if (!confirmed || confirmed.modelId !== modelId || confirmed.versionNo !== versionNo
          || confirmed.modelVersionId !== report.versionId || confirmed.targetDatasourceId !== targetId
          || confirmed.targetDatabase !== database.trim() || (confirmed.targetSchema || '') !== schema.trim()
          || confirmed.targetTable !== report.targetTable
          || confirmed.observedCatalogSha256 !== saved.observedCatalogSha256) {
        throw new Error('保存回执与核验记录不一致，请刷新记录核对');
      }
      setObservations(rows); setObservationsError(''); message.success('核验记录已保存');
    } catch (failure) {
      if (current()) message.error(failure instanceof Error ? failure.message : '保存结果未确认，请刷新记录核对');
    } finally { if (current()) setRecording(false); }
  };

  const readExecutions = useCallback(async (observationId: number, cursors = [Number.MAX_SAFE_INTEGER]) => {
    if (!allowed || !hasVersion) return;
    const current = beginExecution();
    setExecutionLoading(true); setExecutionError(''); setExecutionHistory([]);
    setExecutionObservationId(observationId); setExecutionCursors(cursors);
    try {
      const before = cursors[cursors.length - 1];
      const page = await discoverOdsExecutionsBefore(modelId, versionNo, observationId, before, 50);
      if (!current()) return;
      if (!Array.isArray(page.matches) || !Number.isSafeInteger(page.nextBeforeId)
          || page.nextBeforeId <= 0 || page.nextBeforeId > before
          || (page.moreCandidates && page.nextBeforeId === before)) throw new Error('运行记录查询返回了无效游标');
      setExecutionHistory(page.matches); setExecutionNextCursor(page.nextBeforeId); setExecutionHasMore(page.moreCandidates);
    } catch (failure) {
      if (current()) { setExecutionHasMore(false); setExecutionError(failure instanceof Error ? failure.message : '运行记录暂不可读取'); }
    } finally { if (current()) setExecutionLoading(false); }
  }, [allowed, hasVersion, modelId, versionNo, beginExecution]);

  const targetObservations = observations.filter(row => row.modelId === modelId
    && row.modelVersionId === model.publishedVersionId && row.versionNo === versionNo
    && row.targetDatasourceId === targetId && row.targetDatabase === database.trim()
    && (row.targetSchema || '') === schema.trim()
    && (!report || row.targetTable === report.targetTable));
  const latestObservationId = targetObservations[0]?.id;
  useEffect(() => {
    if (latestObservationId) void readExecutions(latestObservationId);
    else { beginExecution(); setExecutionObservationId(undefined); setExecutionHistory([]); setExecutionError(''); setExecutionHasMore(false); setExecutionLoading(false); }
  }, [latestObservationId, readExecutions, beginExecution]);
  const matchingObservation = report?.state === 'COLUMN_MATCH_NEEDS_DDL_REVIEW'
    ? targetObservations.find(row => row.status === 'RECENT_CATALOG_OBSERVATION'
      && row.observedCatalogSha256 === report.observedCatalogSha256) : undefined;
  const sourceId = Number(model.sourceDatasourceId);
  const mayCreateSync = allowed && Boolean(matchingObservation) && Number.isSafeInteger(sourceId) && sourceId > 0;
  const tableExists = report?.tableExists ?? (report && ['COLUMN_MATCH_NEEDS_DDL_REVIEW', 'STRUCTURE_DIFFERS'].includes(report.state) ? true : null);
  const tableStatus = busy ? '核验中' : error ? '无法核验' : !report ? '未核验'
    : report.state === 'TABLE_NOT_FOUND' ? '未找到'
    : report.state === 'TARGET_NOT_TABLE' ? (report.tableType ? '对象类型不匹配' : '对象类型未知')
    : tableExists ? '已检测到' : '无法核验';
  const matchStatus = busy ? '核验中' : report?.state === 'COLUMN_MATCH_NEEDS_DDL_REVIEW' ? '字段一致'
    : report?.state === 'STRUCTURE_DIFFERS' ? (report.differences.length ? `${report.differences.length} 项差异` : '存在差异')
    : report?.state === 'TARGET_NOT_TABLE' ? '对象类型不支持' : '—';
  const targetName = sources.find(row => row.id === targetId)?.name || (targetId ? `数据源 #${targetId}` : '未配置目标数据源');
  const fullTable = [database.trim(), schema.trim(), report?.targetTable || publishedTable].filter(Boolean).join('.');

  return <div className="model-physical-table">
    <header className="model-physical-header">
      <div><h2>物理表</h2><span>{hasVersion ? `发布版本 V${versionNo}` : '尚未发布'}</span></div>
      <Button icon={<RefreshCw size={14} />} loading={busy}
        disabled={!allowed || !hasVersion || !targetId || !database.trim()}
        onClick={() => void inspect()}>刷新核验</Button>
    </header>

    <section className="model-physical-card">
      <div className="model-physical-target-heading">
        <div className="model-physical-target-icon"><Database size={20} aria-hidden /></div>
        <div className="model-physical-target-name"><h3>{targetName}</h3><code>{fullTable || '请选择目标库位置'}</code></div>
        <Button type="text" size="small" icon={<Settings2 size={14} />} onClick={() => setConfigOpen(!configOpen)}>
          {configOpen ? '收起配置' : '修改目标'}
        </Button>
      </div>
      {configOpen && <div className="model-physical-target-form">
        <label>目标数据源<Select aria-label="目标数据源" showSearch optionFilterProp="label" value={targetId}
          placeholder="选择数据源" loading={sourcesLoading} disabled={!allowed}
          options={sources.map(row => ({ value: row.id, label: `${row.name} (${row.dbType || '未知类型'})` }))}
          onChange={value => { changeTarget(); setTargetId(value); }} /></label>
        <label>目标库<Input aria-label="目标库" value={database} placeholder="精确数据库名" disabled={!allowed}
          onChange={event => { changeTarget(); setDatabase(event.target.value); }} /></label>
        <label>Schema<Input aria-label="目标 Schema" value={schema} placeholder="如 public" disabled={!allowed}
          onChange={event => { changeTarget(); setSchema(event.target.value); }} /></label>
      </div>}
      {sourcesError && <p className="model-physical-error" role="alert">{sourcesError}</p>}
      <div className="model-physical-status-grid">
        <div><span>表状态</span><strong className={tableExists && !error && report?.state !== 'TARGET_NOT_TABLE' ? 'is-success' : ''}>{tableStatus}</strong></div>
        <div><span>字段比对</span><strong className={report?.state === 'STRUCTURE_DIFFERS' ? 'is-warning' : ''}>{matchStatus}</strong></div>
        <div><span>最近核验</span><strong className="is-time">{formatTime(report?.inspectedAt)}</strong></div>
      </div>
      {!allowed ? <p className="model-physical-notice">无目标库读取权限</p>
        : !hasVersion ? <p className="model-physical-notice">发布模型后可核验目标表</p>
        : error ? <p className="model-physical-error" role="alert">{error}</p>
        : report?.state === 'TABLE_NOT_FOUND' ? <p className="model-physical-notice">当前账号可见目录未找到该表，请检查目标位置及表可见权限。</p>
        : report?.state === 'NOT_CHECKABLE' || report?.state === 'TARGET_NOT_TABLE'
          ? <p className="model-physical-notice">{report.limitations[0] || '请检查连接与读取权限后重试'}</p> : null}
    </section>

    <section className="model-physical-card">
      <div className="model-physical-card-heading"><h3>结构核验</h3>
        <span className="model-physical-muted">列名、类型、可空性</span>
        {Boolean(report?.columns?.length) && <Button type="link" size="small" onClick={() => setFieldsOpen(!fieldsOpen)}>
          {fieldsOpen ? '收起实际字段' : '查看实际字段'}
        </Button>}
      </div>
      {!report || busy ? <div className="model-physical-empty">{busy ? '正在读取目标表…' : '核验后查看实际字段及差异'}</div>
        : report.state === 'COLUMN_MATCH_NEEDS_DDL_REVIEW' ? <div className="model-physical-match">
          <CheckCircle2 size={17} aria-hidden />目标表 {report.observedColumns} 个字段与 V{versionNo} 的核验项一致
        </div>
        : report.differences.length > 0 ? <Table size="small" pagination={false} scroll={{ x: 560 }}
          rowKey={row => `${row.column}:${row.issue}`} dataSource={report.differences} columns={[
            { title: '字段', dataIndex: 'column' },
            { title: '差异', dataIndex: 'issue', render: (value: string) => <Tag color="warning">{issues[value] || value}</Tag> },
            { title: `版本 V${versionNo}`, dataIndex: 'expectedType', render: value => value || '—' },
            { title: '目标实际', dataIndex: 'observedType', render: value => value || '—' },
          ]} /> : <div className="model-physical-empty">暂无可比较的字段信息</div>}
      {fieldsOpen && <Table size="small" rowKey="name" pagination={{ pageSize: 10 }} scroll={{ x: 650 }}
        dataSource={report?.columns ?? []} columns={[
          { title: '字段名', dataIndex: 'name' }, { title: '类型', dataIndex: 'dataType' },
          { title: '长度', dataIndex: 'length', render: value => value ?? '—' },
          { title: '小数位', dataIndex: 'scale', render: value => value ?? '—' },
          { title: '可空', dataIndex: 'nullable', render: value => value ? '是' : '否' },
          { title: '主键', dataIndex: 'primaryKey', render: value => value ? '是' : '否' },
          { title: '注释', dataIndex: 'comment', render: value => value || '—' },
        ]} />}
      <details className="model-physical-scope"><summary>核验范围</summary>
        <p>本次读取目标表元数据，比对当前发布版本的列名、类型与可空性。索引、分区、默认值及表数据需单独核对。</p>
      </details>
      {snapshotError && <p className="model-physical-error" role="alert">{snapshotError}</p>}
    </section>

    <ModelDdlPanel model={model} scope={scope} />

    <section className="model-physical-card">
      <div className="model-physical-card-heading"><h3>数据同步</h3>
        <Button size="small" type="primary" disabled={!mayCreateSync} onClick={() => history.push(buildIntegrationCreatePath('batch', {
          dataSourceId: String(sourceId), odsModelId: modelId, odsVersionNo: versionNo,
          odsTargetObservationId: matchingObservation!.id,
        }))}>配置离线同步</Button>
      </div>
      <div className="model-physical-sync-controls">
        <Select aria-label="同步关联的核验记录" placeholder="选择核验记录" value={executionObservationId}
          disabled={!allowed || !targetObservations.length || executionLoading} onChange={id => void readExecutions(id)}
          options={targetObservations.map(row => ({ value: row.id, label: `#${row.id} · ${formatTime(row.observedAt)}` }))} />
        {report?.state === 'COLUMN_MATCH_NEEDS_DDL_REVIEW' && <Button disabled={!mayRecord || recording} loading={recording}
          onClick={() => void recordObservation()}>保存核验记录</Button>}
        {executionObservationId && <Button size="small" loading={executionLoading}
          onClick={() => void readExecutions(executionObservationId, executionCursors)}>刷新运行记录</Button>}
      </div>
      {!matchingObservation && <p className="model-physical-muted">目标字段核验一致并保存记录后，可前往同步配置。</p>}
      {observationsError && <p className="model-physical-error" role="alert">{observationsError}</p>}
      {executionError ? <p className="model-physical-error" role="alert">{executionError}</p>
        : <Table size="small" rowKey="executionId" loading={executionLoading} pagination={false} scroll={{ x: 650 }}
          dataSource={executionHistory} locale={{ emptyText: executionObservationId ? '本次查询未找到关联运行记录' : '选择核验记录后查看关联运行' }} columns={[
            { title: '同步任务', dataIndex: 'jobDefinitionId', render: (id: number) => <Button type="link" size="small"
              onClick={() => history.push(`/sync/batch-link-up/${encodeURIComponent(String(id))}/config/single?scene=edit`)}>任务 #{id}</Button> },
            { title: '执行编号', dataIndex: 'executionId', render: (id: number) => `#${id}` },
            { title: '任务版本', dataIndex: 'jobRevisionNo', render: (no: number) => `V${no}` },
            { title: '状态', dataIndex: 'status', render: (value: string) => <Tag color={value === 'SUCCEEDED' ? 'green' : value === 'FAILED' ? 'red' : 'default'}>{runLabels[value] || value || '未知'}</Tag> },
            { title: '提交行数', dataIndex: 'sinkCommittedRows', render: value => value ?? '未知' },
            { title: '结束时间', dataIndex: 'endTime', render: formatTime },
            { title: '失败原因', dataIndex: 'errorMessage', ellipsis: true, render: value => value || '—' },
          ]} />}
      {executionObservationId && <div className="model-physical-run-paging">
        <span>查询批次 {executionCursors.length}{executionHasMore ? ' · 还有记录可查询' : ''}</span>
        <Space><Button size="small" disabled={executionLoading || executionCursors.length <= 1}
          onClick={() => void readExecutions(executionObservationId, executionCursors.slice(0, -1))}>上一批</Button>
          <Button size="small" disabled={executionLoading || !executionHasMore}
            onClick={() => void readExecutions(executionObservationId, [...executionCursors, executionNextCursor])}>继续查询</Button></Space>
      </div>}
      <p className="model-physical-muted model-physical-run-note">运行与提交行数来自同步任务记录。</p>
    </section>

    <section className="model-physical-card">
      <div className="model-physical-card-heading">
        <button type="button" className="model-physical-fold" aria-expanded={historyOpen} onClick={() => setHistoryOpen(!historyOpen)}>
          <h3>核验记录</h3><ChevronDown size={15} className={historyOpen ? 'is-expanded' : ''} aria-hidden />
        </button>
        <Button type="text" size="small" onClick={() => void loadObservations()} disabled={!allowed}>刷新记录</Button>
      </div>
      {historyOpen && <Table size="small" rowKey="id" pagination={{ pageSize: 5 }} scroll={{ x: 650 }} dataSource={observations} columns={[
        { title: '记录', dataIndex: 'id', render: (id: number) => `#${id}` },
        { title: '版本', dataIndex: 'versionNo', render: (no: number) => `V${no}` },
        { title: '目标', render: (_, row: OdsTargetObservation) => [row.targetDatabase, row.targetSchema, row.targetTable].filter(Boolean).join('.') },
        { title: '核验时间', dataIndex: 'observedAt', render: formatTime },
        { title: '记录状态', dataIndex: 'status', render: (value: string) => value === 'RECENT_CATALOG_OBSERVATION' ? '近期记录' : value === 'VERSION_CHANGED' ? '版本已变化' : '记录已过期' },
      ]} />}
    </section>
  </div>;
}
