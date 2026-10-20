import { history, useSearchParams } from '@umijs/max';
import { Alert, Button, Modal, Select, Spin, message } from 'antd';
import { ArrowLeft, ArrowUpRight, Database, Search } from 'lucide-react';
import { useCallback, useEffect, useRef, useState } from 'react';
import { useSecurityProject } from '@/contexts/SecurityProjectContext';
import usePermissionAccess from '@/hooks/usePermissionAccess';
import LiveSchemaExplorer from '@/components/data-source/LiveSchemaExplorer';
import type { DataSourceRecord } from '@/services/data-source';
import { getDataSource, listAllDataSources } from '@/services/data-source/api';
import { collectDefinitionPath, findCoveringCollectionJobs } from '@/services/metadata/collectionHandoff';
import type { CollectionSourceScope } from '@/services/metadata/collectionHandoff';
import type { CollectJobRecord } from '@/services/metadata/types';
import { buildLiveSourceOdsPath, parseLiveSourceReturn } from '@/utils/liveSourceOdsJourney';
import type { LiveSourceCoordinate } from '@/utils/liveSourceOdsJourney';
import './index.less';

function DataSourceExplorePage() {
  const [query] = useSearchParams();
  const { currentProject } = useSecurityProject();
  const { can } = usePermissionAccess();
  const canRead = can('resource:data-source:read');
  const canOpenOds = canRead && can('modeling:read');
  const canCreateCollect = can('data-metadata:read') && can('data-metadata:create');
  const canUpdateCollect = can('data-metadata:read') && can('data-metadata:update');
  const canCollect = canCreateCollect || canUpdateCollect;
  const [sources, setSources] = useState<DataSourceRecord[]>([]);
  const [sourceId, setSourceId] = useState<number>();
  const [detail, setDetail] = useState<DataSourceRecord>();
  const [busy, setBusy] = useState(true);
  const [error, setError] = useState('');
  const [defining, setDefining] = useState(false);
  const [candidates, setCandidates] = useState<CollectJobRecord[]>();
  const [candidateScope, setCandidateScope] = useState<CollectionSourceScope>();
  const generation = useRef(0);
  const projectId = currentProject?.id;
  const urlId = query.get('sourceId') || '';
  const coordinatesRequested = ['database', 'schema', 'table'].some(key => query.has(key));
  const restored = coordinatesRequested ? parseLiveSourceReturn(query) : null;
  const initialSourceId = /^[1-9]\d*$/.test(urlId) && Number.isSafeInteger(Number(urlId))
    ? Number(urlId) : undefined;

  useEffect(() => {
    const request = ++generation.current;
    setSources([]); setSourceId(undefined); setDetail(undefined); setError(''); setBusy(true);
    if (!projectId || !canRead) {
      setBusy(false);
      return () => { generation.current += 1; };
    }
    if ((urlId && !initialSourceId) || (coordinatesRequested && !restored)) {
      setError('来源地址不完整或不合法；请重新选择源表，禁止按同名表猜测恢复。');
      setBusy(false);
      return () => { generation.current += 1; };
    }
    void listAllDataSources().then(async response => {
      if (request !== generation.current) return;
      const available = response.bizData || [];
      setSources(available);
      if (initialSourceId) {
        const permitted = available.some(item => Number(item.id) === initialSourceId);
        if (!permitted) throw new Error('当前项目无法访问此数据源，请重新选择。');
        const exact = await getDataSource(initialSourceId);
        if (request !== generation.current) return;
        if (Number(exact.id) !== initialSourceId) throw new Error('源端身份核验失败。');
        setSourceId(initialSourceId);
        setDetail(exact);
      }
    }).catch(e => {
      if (request === generation.current) setError(e instanceof Error ? e.message : '读取数据源失败');
    }).finally(() => { if (request === generation.current) setBusy(false); });
    return () => { generation.current += 1; };
  }, [projectId, canRead, urlId, initialSourceId, coordinatesRequested, restored?.database, restored?.schema, restored?.table]);

  const chooseSource = useCallback((next: number) => {
    if (!sources.some(item => Number(item.id) === next)) return;
    // Never carry a prior selected table across a source change.
    history.replace('/data-source/explore?sourceId=' + encodeURIComponent(String(next)));
  }, [sources]);

  const openOds = (coordinate: LiveSourceCoordinate) => {
    if (!canOpenOds || !sourceId || Number(coordinate.dataSourceId) !== sourceId) return;
    const path = buildLiveSourceOdsPath(coordinate);
    if (path) history.push(path);
    else message.warning('来源库表身份未完整核验，无法进入 ODS 建模。');
  };

  const defineCollect = async (scope: CollectionSourceScope) => {
    if (!canCollect || !sourceId || Number(scope.dataSourceId) !== sourceId || defining) return;
    const ticket = generation.current;
    setDefining(true);
    try {
      const matches = await findCoveringCollectionJobs(scope);
      if (ticket !== generation.current) return;
      if (matches.length === 1) {
        history.push(canUpdateCollect ? collectDefinitionPath(scope, matches[0].id)
          : '/data-metadata/collect?dataSourceId=' + encodeURIComponent(String(sourceId)));
      } else if (matches.length > 1) {
        setCandidates(matches); setCandidateScope(scope);
      } else if (canCreateCollect) {
        history.push(collectDefinitionPath(scope));
      } else {
        message.warning('没有适用的采集任务，且当前账号无创建权限。');
      }
    } catch {
      if (ticket === generation.current) message.warning('无法核实采集任务覆盖范围；请到采集任务页核对。');
    } finally { if (ticket === generation.current) setDefining(false); }
  };

  const selected = sources.find(item => Number(item.id) === sourceId);
  return <main className="source-page source-explore-page" data-testid="data-source-explore">
    <header className="source-page-header">
      <div><h1>数据探查</h1><p className="source-page-subtitle">实时核对数据源的 Database、Schema、表和字段；选择真实 TABLE 后可继续 ODS 建模。</p></div>
      <Button icon={<ArrowLeft size={15} />} onClick={() => history.push('/data-source')}>数据源管理</Button>
    </header>
    <section className="source-explore-scope" aria-label="选择探查范围">
      <div className="source-explore-section-title">
        <Database size={16} /><strong>选择数据源</strong>
        {selected ? <span className="source-workspace-caption">{selected.dbType || '未知类型'} · {selected.environmentName || selected.environment || '未分类'}</span> : null}
      </div>
      {!canRead ? <Alert showIcon type="warning" message="没有当前项目的数据源读取权限" /> : null}
      {!projectId ? <Alert showIcon type="info" message="请先选择项目" /> : null}
      {error ? <Alert className="!my-3" type="error" showIcon message={error} /> : null}
      <Spin spinning={busy}>
        <Select showSearch allowClear className="w-full max-w-[520px]" optionFilterProp="label"
          placeholder="选择当前项目的数据源" aria-label="选择探查数据源"
          disabled={!projectId || !canRead || busy}
          value={selected ? sourceId : undefined}
          onChange={(value?: number) => {
            if (value) chooseSource(value);
            else history.replace('/data-source/explore');
          }}
          options={sources.map(item => ({ value: Number(item.id), label: (item.name || '未命名数据源') + ' · ' + (item.dbType || '未知类型') }))} />
      </Spin>
    </section>
    {sourceId && selected && detail && canRead ? <section className="source-explore-browser" aria-label="物理数据库结构">
      <div className="source-explore-section-title">
        <Search size={16} /><strong>物理表与字段</strong>
        <span className="source-workspace-caption">结构探查只读；元数据采集和 ODS 保存是后续独立操作</span>
      </div>
      <LiveSchemaExplorer key={projectId + ':' + sourceId} sourceId={String(sourceId)} canRead
        initialTable={restored?.dataSourceId === sourceId ? {
          database: restored.database, schema: restored.schema, table: restored.table,
        } : undefined}
        onOpenOds={canOpenOds ? openOds : undefined}
        onDefineTask={canCollect ? (scope) => void defineCollect(scope) : undefined}
        definitionLoading={defining} />
    </section> : !busy && !error ? (
      <section className="source-explore-empty">选择数据源后，浏览实时库表结构及字段信息。</section>
    ) : null}
    <Modal open={Boolean(candidates)} title="选择已有采集任务"
      onCancel={() => setCandidates(undefined)}
      footer={<Button onClick={() => setCandidates(undefined)}>取消</Button>}>
      <p className="source-workspace-caption">以下采集任务覆盖当前范围，请先确认已有配置，避免重复创建。</p>
      {candidates?.map(job => <Button key={job.id} block className="!my-2 !h-auto !py-2"
        onClick={() => {
          setCandidates(undefined);
          if (candidateScope) history.push(canUpdateCollect
            ? collectDefinitionPath(candidateScope, job.id)
            : '/data-metadata/collect?dataSourceId=' + encodeURIComponent(String(sourceId)));
        }}><span className="flex w-full items-center justify-between gap-3">
          <span>{job.jobName}</span><ArrowUpRight size={14} />
        </span></Button>)}
    </Modal>
  </main>;
}

export default function ScopedExplorePage() {
  const { currentProject } = useSecurityProject();
  const { permissionCodes } = usePermissionAccess();
  return <DataSourceExplorePage key={JSON.stringify([currentProject?.id, permissionCodes])} />;
}
