import { history, useSearchParams } from '@umijs/max';
import { Alert, Button, Input, Modal, Select, Space, Spin, Tag, message } from 'antd';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useSecurityProject } from '@/contexts/SecurityProjectContext';
import usePermissionAccess from '@/hooks/usePermissionAccess';
import { listAllDataSources } from '@/services/data-source/api';
import { getEntityDetail } from '@/services/metadata/api';
import { commitOdsManualImport, createModelingModel, getModelingModel, getModelingStructure } from '@/services/modeling/api';
import { getOdsSourceCandidates, previewModelingImportColumns } from '@/services/modeling/import';
import { listModelingMappings } from '@/services/modeling/mapping';
import type { OdsSourceLookup } from '@/services/modeling/import';
import type { ModelingImportColumnView, ModelingModelRecord } from '@/services/modeling/types';
import { MODELING_DIALECT_OPTIONS, dialectOfDbType } from '@/pages/modeling/constants';
import { listSemanticLayers } from '@/services/semantic/api';
import OdsSourceSelector from './OdsSourceSelector';
import { buildLiveSourceReturnPath } from '@/utils/liveSourceOdsJourney';
import './OdsSourceJourney.less';

const positiveId = (raw: string | null): number | null => {
  if (!raw || !/^[1-9]\d*$/.test(raw)) return null;
  const value = Number(raw);
  return Number.isSafeInteger(value) ? value : null;
};
const validName = (value: string | null): value is string =>
  Boolean(value && value.trim().length > 0 && value.length <= 128);

const OdsSourceJourney = () => {
  const [params] = useSearchParams();
  const { can } = usePermissionAccess();
  const { currentProject } = useSecurityProject();
  const projectId = currentProject?.id;
  const datasourceId = positiveId(params.get('datasourceId'));
  const database = params.get('database') ?? '';
  const schema = params.get('schema') || undefined;
  const table = params.get('table') ?? '';
  const entityId = positiveId(params.get('entityId'));
  const draftId = positiveId(params.get('draftId'));
  const valid = datasourceId != null && validName(database) && validName(table)
    && (!schema || validName(schema));
  // The origin is a known enumeration, never an arbitrary redirect URL.
  // This hint only affects where navigation returns; source ownership is still
  // checked independently by DataSource and Modeling on every read/write.
  const liveSourceReturn = datasourceId != null && params.get('origin') === 'live-source'
    ? buildLiveSourceReturnPath({ dataSourceId: datasourceId, database, schema, table })
    : null;
  const fromModeling = params.get('origin') === 'modeling-home';
  const sourceReturn = fromModeling ? '/modeling?view=physical' : liveSourceReturn ??
    `/data-asset/catalog?view=entity&datasourceId=${datasourceId || ''}${entityId ? `&entityId=${entityId}` : ''}${database ? `&databaseName=${encodeURIComponent(database)}` : ''}`;

  const modelDetailPath = (modelId: number): string => {
    const path = `/modeling/models/${modelId}`;
    if (!liveSourceReturn || datasourceId == null) return path;
    const query = new URLSearchParams({
      origin: 'live-source',
      datasourceId: String(datasourceId),
      database, table,
    });
    if (schema) query.set('schema', schema);
    return `${path}?${query}`;
  };

  const [lookup, setLookup] = useState<OdsSourceLookup | null>(null);
  const [failure, setFailure] = useState('');
  const [loading, setLoading] = useState(false);
  const [reload, setReload] = useState(0);
  const [sourceVerified, setSourceVerified] = useState(false);
  const [dialect, setDialect] = useState('');
  const [columns, setColumns] = useState<ModelingImportColumnView[] | null>(null);
  const [targetNames, setTargetNames] = useState<Record<string, string>>({});
  const [previewBusy, setPreviewBusy] = useState(false);
  const [code, setCode] = useState(() =>
    (`ods_${database}_${schema || ''}_${table}`).toLowerCase().replace(/[^a-z0-9_]/g, '_').slice(0, 120));
  const [name, setName] = useState(() => `ODS ${table}`);
  const [savedDraft, setSavedDraft] = useState<ModelingModelRecord | null>(null);
  const [creating, setCreating] = useState(false);
  const [committing, setCommitting] = useState(false);
  const [savingDesign, setSavingDesign] = useState(false);
  const [creationUnknown, setCreationUnknown] = useState(false);
  const [commitUnknown, setCommitUnknown] = useState(false);
  const [dirty, setDirty] = useState(false);
  const [savedId, setSavedId] = useState<number | null>(null);
  const readEpoch = useRef(0);
  const sourceIdentity = useRef('');
  const autoPreviewKey = useRef('');

  const navigateSafely = (path: string) => {
    if (dirty && !savedId) {
      Modal.confirm({
        title: '当前 ODS 字段映射尚未保存',
        content: '可以继续编辑并保存，也可以放弃本次未提交的字段映射。已保存的空模型草稿仍在 Modeling Owner，可从其 ID 重新进入。',
        okType: 'danger', okText: '放弃未保存编辑并离开', cancelText: '继续编辑',
        onOk: () => history.push(path),
      });
      return;
    }
    history.push(path);
  };

  useEffect(() => {
    if (!dirty || savedId) return;
    const warn = (e: BeforeUnloadEvent) => { e.preventDefault(); e.returnValue = ''; };
    window.addEventListener('beforeunload', warn);
    return () => window.removeEventListener('beforeunload', warn);
  }, [dirty, savedId]);

  const refresh = useCallback(async () => {
    const ticket = ++readEpoch.current;
    const sourceKey = JSON.stringify([projectId, datasourceId, database, schema, table, entityId]);
    const changedSource = sourceIdentity.current !== sourceKey;
    sourceIdentity.current = sourceKey;
    setLoading(true);
    setFailure('');
    setCommitUnknown(false);
    setLookup(null);
    setSourceVerified(false);
    // A draftId-only redirect must not erase a preview/mapping the user just confirmed.
    if (changedSource) {
      setColumns(null); setSavedId(null); setDirty(false); setCreationUnknown(false); setCommitUnknown(false);
      setPreviewBusy(false); setCreating(false); setCommitting(false);
      setCode((`ods_${database}_${schema || ''}_${table}`).toLowerCase().replace(/[^a-z0-9_]/g, '_').slice(0, 120));
      setName(`ODS ${table}`);
      setDialect('');
    }
    setSavedDraft(null);
    if (!valid || datasourceId == null) {
      setFailure('来源坐标不完整或非法；请从已发现的物理表进入 ODS 建模。');
      setLoading(false);
      return;
    }
    try {
      const [sources, response, entity, resumed, targetLayers] = await Promise.all([
        listAllDataSources(),
        getOdsSourceCandidates({ datasourceId, database, schema, table }),
        entityId ? getEntityDetail(entityId) : Promise.resolve(null),
        draftId ? getModelingModel(draftId) : Promise.resolve(null),
        listSemanticLayers().catch(() => []),
      ]);
      if (ticket !== readEpoch.current) return;
      // URL hints never grant access. Confirm both the active project's DataSource owner
      // and, if provided, the specific Metadata table identity before suggesting writes.
      const validSource = (sources.bizData ?? []).find(s => Number(s.id) === datasourceId);
      if (!validSource) throw new Error('当前项目无法核验来源数据源；未按“没有 ODS”继续。');
      if (entity && !(entity.entity.typeName === 'table'
          && entity.entity.facts.providerType === 'HARVESTED'
          && Number(entity.entity.facts.dataSourceId) === datasourceId
          && entity.entity.facts.databaseName === database
          && (entity.entity.facts.schemaName || undefined) === schema
          && entity.entity.facts.tableName === table)) {
        throw new Error('元数据实体与导航来源不一致；拒绝跨源建模。');
      }
      if (resumed && resumed.layerCode !== 'ODS') {
        throw new Error('此草稿 ID 不属于 ODS；请从模型目录打开正确模型。');
      }
      if (resumed?.sourceDatasourceId != null) {
        // A bound ODS is a continuation, never a "new ODS" draft.
        // The server must independently find that *same* model by exact source.
        const sameSource = Number(resumed.sourceDatasourceId) === datasourceId
          && resumed.sourceDatabase === database
          && (resumed.sourceSchema || undefined) === schema
          && resumed.sourceTable === table;
        const exactCandidate = response.state === 'READY'
          && response.candidates.some(candidate => Number(candidate.modelId) === Number(resumed.id));
        if (!sameSource || !exactCandidate) {
          throw new Error('已保存 ODS 与当前来源或精确候选不一致；请到模型目录核对，禁止重复首导。');
        }
      } else if (resumed && resumed.status !== 'DRAFT') {
        throw new Error('空草稿状态已变化，请到原模型查看，不能继续首次导入。');
      }
      setSourceVerified(true);
      setLookup(response);
      // A newly bound draft is now an existing candidate; do not show an
      // empty-draft save form or try to commit its structure a second time.
      if (resumed?.sourceDatasourceId == null && resumed) setSavedDraft(resumed);
      // Infer ODS target dialect only from configured target connection, never from the physical source.
      const layer = targetLayers.find(item => item.code === 'ODS' && item.status === 'ENABLED');
      const targetSource = (sources.bizData ?? []).find(item =>
        layer?.datasourceId != null && Number(item.id) === Number(layer.datasourceId));
      const inferred = targetSource ? dialectOfDbType(targetSource.dbType) : undefined;
      if (inferred) setDialect(current => current || inferred);
    } catch (error) {
      if (ticket === readEpoch.current)
        setFailure(error instanceof Error ? error.message : '来源和模型核验失败；请重试。');
    } finally {
      if (ticket === readEpoch.current) setLoading(false);
    }
  }, [projectId, datasourceId, database, schema, table, entityId, draftId, valid, reload]);

  useEffect(() => {
    if (!valid) return;
    void refresh();
    return () => { readEpoch.current += 1; };
  }, [refresh, valid]);

  const preview = useCallback(async () => {
    const ticket = readEpoch.current;
    if (!sourceVerified || !lookup || lookup.state !== 'READY' || lookup.candidates.length > 0
        || datasourceId == null) return;
    setPreviewBusy(true);
    setColumns(null);
    try {
      const next = (await previewModelingImportColumns(datasourceId, database, table, schema))
        .filter(col => !col.technical);
      if (ticket !== readEpoch.current) return;
      if (!next.length) throw new Error('当前物理表没有可核验的真实源列。');
      const names: Record<string, string> = {};
      next.forEach(col => { names[col.name] = col.name; });
      setTargetNames(names);
      setColumns(next);
      setDirty(true);
    } catch (error) {
      if (ticket === readEpoch.current)
        setFailure(error instanceof Error ? error.message : '物理表列读取失败。');
    } finally {
      if (ticket === readEpoch.current) setPreviewBusy(false);
    }
  }, [sourceVerified, lookup, datasourceId, database, schema, table]);

  // Re-fetch real columns automatically after the physical table is selected.
  // Retry after a failure is explicit; avoid an infinite query loop.
  useEffect(() => {
    if (!valid || !sourceVerified || lookup?.state !== 'READY'
        || lookup.candidates.length > 0 || columns || previewBusy) return;
    const key = JSON.stringify([projectId, datasourceId, database, schema, table]);
    if (autoPreviewKey.current === key) return;
    autoPreviewKey.current = key;
    void preview();
  }, [valid, sourceVerified, lookup, columns, previewBusy, preview, projectId,
      datasourceId, database, schema, table]);

  const physicalColumns = useMemo(() => columns ?? [], [columns]);
  const targetValues = physicalColumns.map(col => (targetNames[col.name] || '').trim());
  const validMappings = physicalColumns.length > 0
    && targetValues.every(v => /^[A-Za-z_][A-Za-z0-9_]*$/.test(v))
    && new Set(targetValues.map(v => v.toLowerCase())).size === targetValues.length;

  // Two Owner writes, one user-visible confirmation. Do not re-route between
  // creating an identified draft and committing its full source mappings.
  const createDraft = async (): Promise<ModelingModelRecord | null> => {
    const ticket = readEpoch.current;
    if (!sourceVerified || lookup?.state !== 'READY' || lookup.candidates.length > 0
        || !can('modeling:create') || !dialect || !code || !validMappings
        || savedDraft || creationUnknown) return null;
    setCreating(true);
    try {
      const draft = await createModelingModel({
        code: code.trim(), name: name.trim(), dialect, layerCode: 'ODS',
      });
      if (ticket !== readEpoch.current) return null;
      if (!draft.id) throw new Error('创建模型未返回有效草稿 ID');
      setSavedDraft(draft);
      // Stable browser recovery identity without retriggering useSearchParams,
      // refresh() and epoch invalidation mid-save.
      const query = new URLSearchParams(params.toString());
      query.set('draftId', String(draft.id));
      window.history.replaceState(window.history.state, '', '/modeling?' + query.toString());
      return draft;
    } catch (error) {
      if (ticket === readEpoch.current) {
        setCreationUnknown(true);
        setFailure((error instanceof Error ? error.message : '草稿创建结果未知')
          + '；请先在物理模型库按编码核对已有草稿，不能盲目重复创建。');
      }
      return null;
    } finally { if (ticket === readEpoch.current) setCreating(false); }
  };

  // A write receipt (or a timed-out response) is not sufficient evidence of a saved ODS.
  // Re-read the same project's original Modeling objects, including every source column.
  const verifySavedOds = async (modelId: number): Promise<boolean> => {
    if (datasourceId == null || !physicalColumns.length) return false;
    const [model, structure, mappings] = await Promise.all([
      getModelingModel(modelId), getModelingStructure(modelId), listModelingMappings(modelId),
    ]);
    const identityMatches = model.id != null && Number(model.id) === modelId
      && model.layerCode === 'ODS' && model.status === 'DRAFT'
      && model.sourceDatasourceId === datasourceId
      && model.sourceDatabase === database && (model.sourceSchema || undefined) === schema
      && model.sourceTable === table;
    if (!identityMatches) return false;
    const savedColumns = new Set((structure.columns ?? []).map(col => col.columnName?.toLowerCase()));
    if (!physicalColumns.every(col => savedColumns.has(targetNames[col.name]?.trim().toLowerCase()))) return false;
    if (mappings.filter(row => row.mapped).length !== physicalColumns.length) return false;
    return physicalColumns.every(col => mappings.some(row =>
      row.mapped && row.targetColumn?.toLowerCase() === targetNames[col.name]?.trim().toLowerCase()
      && row.sourceDatasourceId === datasourceId && row.sourceDatabase === database
      && (row.sourceSchema || undefined) === schema && row.sourceTable === table
      && row.sourceColumn?.toLowerCase() === col.name.toLowerCase()
      && !row.transformExpr));
  };

  const commit = async (draft: ModelingModelRecord) => {
    const ticket = readEpoch.current;
    if (!draft.id || !can('modeling:update') || !validMappings || datasourceId == null
        || lookup?.state !== 'READY' || lookup.candidates.length > 0 || !sourceVerified) return;
    setCommitting(true);
    try {
      // Re-read persisted version and optimistic definition. Never trust an old local draft.
      const [live, structure] = await Promise.all([
        getModelingModel(draft.id), getModelingStructure(draft.id),
      ]);
      if (ticket !== readEpoch.current) return;
      if (live.status !== 'DRAFT' || live.layerCode !== 'ODS' || live.sourceDatasourceId != null
          || !structure.definition || (structure.columns ?? []).length !== 0) {
        throw new Error('草稿已变化或有字段/来源映射；请在原模型核验，禁止覆盖。');
      }
      const receipt = await commitOdsManualImport(draft.id, {
        expectedDefinition: structure.definition,
        sourceDatasourceId: datasourceId, sourceDatabase: database, sourceSchema: schema,
        sourceTable: table,
        expectedSourceColumns: physicalColumns.map(col => ({
          name: col.name, typeName: col.typeName, size: col.size ?? null,
          scale: col.scale ?? null, nullable: col.nullable ?? true,
          primaryKey: col.primaryKey ?? false,
        })),
        structure: {
          tableName: live.code || code,
          tableComment: name,
          columns: physicalColumns.map(col => ({
            columnName: targetNames[col.name].trim(), dataType: col.typeName,
            length: /CHAR|DECIMAL|NUMERIC/i.test(col.typeName) && (col.size ?? 0) > 0 ? col.size : undefined,
            scale: /DECIMAL|NUMERIC/i.test(col.typeName) && (col.scale ?? 0) >= 0 ? col.scale : undefined,
            nullable: col.nullable ?? true,
            comment: col.remarks || undefined,
          })),
          primaryKey: physicalColumns.filter(col => col.primaryKey)
            .map(col => targetNames[col.name].trim()),
          indexes: [], tableProperties: {},
        },
        selections: physicalColumns.map(col => ({
          targetColumn: targetNames[col.name].trim(), sourceColumn: col.name,
        })),
      });
      if (ticket !== readEpoch.current) return;
      if (receipt.status !== 'SAVED_AND_VERIFIED' || receipt.modelId !== Number(draft.id)
          || receipt.confirmedMappingCount !== physicalColumns.length) {
        throw new Error('写入回执尚不足以证明模型与全部来源列已保存；请打开模型核对。');
      }
      if (!(await verifySavedOds(Number(draft.id)))) {
        throw new Error('保存接口已返回，但模型结构或来源映射回读不一致；请核对原模型，勿重复写入。');
      }
      if (ticket !== readEpoch.current) return;
      setSavedId(Number(draft.id));
      setDirty(false);
      setFailure('');
      message.success('ODS 设计、真实来源和逐列映射已重新读取确认。未部署、未同步。');
    } catch (error) {
      // The POST can have committed even when its HTTP reply was lost. Never retry the
      // mutation from a timeout; check current persisted evidence first.
      if (ticket !== readEpoch.current) return;
      let persisted = false;
      try { persisted = await verifySavedOds(Number(draft.id)); } catch { /* owner read unavailable */ }
      if (ticket !== readEpoch.current) return;
      if (persisted) {
        setSavedId(Number(draft.id));
        setDirty(false);
        setFailure('');
        message.success('写入响应未确认，但已重新读取到完整 ODS 设计和来源映射。');
      } else {
        setCommitUnknown(true);
        setFailure((error instanceof Error ? error.message : '来源映射保存结果未知')
          + '；请先重新核验草稿和来源，禁止直接重复提交。');
      }
    } finally { if (ticket === readEpoch.current) setCommitting(false); }
  };


  // Modeling-home entry begins without a table and shows the same verified
  // picker used by DataSource instead of creating an unrelated ODS wizard.
  if (!valid) {
    const malformed = ['datasourceId', 'database', 'table', 'schema'].some(key => params.has(key));
    if (malformed) return <Alert type="error" showIcon message="来源身份不完整，无法进入 ODS 设计"
      description="请从物理模型首页重新选择数据源、数据库、Schema 和真实表。" />;
    return <OdsSourceSelector />;
  }

  const saveOdsDesign = async () => {
    if (savingDesign || creating || committing || creationUnknown || commitUnknown || !validMappings
        || !dialect || !can('modeling:update')) return;
    setSavingDesign(true);
    try {
      const draft = savedDraft ?? await createDraft();
      if (draft) await commit(draft);
    } finally { setSavingDesign(false); }
  };

  const newTarget = lookup?.state === 'READY' && lookup.candidates.length === 0 && sourceVerified;

  return (
    <div className="ods-workbench" data-testid="ods-source-workbench">
      <header className="ods-workbench-head">
        <div>
          <h1>ODS 物理建模</h1>
          <p>确认来源和字段后保存一份完整的 ODS 模型设计；建表、发布和同步分别进行。</p>
        </div>
        <Space wrap>
          <Button onClick={() => navigateSafely(sourceReturn)}>
            {fromModeling ? '返回物理模型' : liveSourceReturn ? '返回数据源表结构' : '返回来源物理表'}
          </Button>

        </Space>
      </header>

      <section className="ods-workbench-origin" aria-label="当前来源对象">
        <div className="ods-workbench-source-info">
          <span className="ods-workbench-field-label">当前来源</span>
          <strong>{table || '来源待核验'}</strong>
          <span>数据源 #{datasourceId || '—'} · {database || '—'} / {schema || '默认 Schema'}</span>
        </div>
        <Tag color={sourceVerified ? 'success' : 'warning'}>
          {sourceVerified ? '当前项目来源已核验' : '来源等待核验'}
        </Tag>
      </section>

      <main className="ods-workbench-main" aria-label="ODS 字段与来源设计">
        {loading && <div className="ods-workbench-card ods-workbench-wait"><Spin tip="正在核验来源和已保存 ODS" /></div>}
        {failure && <Alert className="ods-workbench-alert" type="error" showIcon message={failure}
          description="查询或提交失败不等于模型不存在。请先核验持久草稿，避免重复创建。"
          action={<Button size="small" onClick={() => setReload(v => v + 1)}>重新核验</Button>} />}
        {lookup?.state === 'UNVERIFIED' && <Alert className="ods-workbench-alert"
          type="warning" showIcon message="不能确认来源身份" description={lookup.reason} />}

        {lookup?.state === 'READY' && lookup.candidates.length > 0 && (
          <section className="ods-workbench-card" aria-label="已有同源 ODS">
            <div className="ods-workbench-section-title">
              <div><h2>已有同源 ODS</h2><p>已按当前数据源、数据库、Schema 和表精确查找，优先复用。</p></div>
              <Tag>{lookup.candidates.length} 个候选</Tag>
            </div>
            <Alert className="ods-workbench-alert" type="info" showIcon
              message={lookup.candidates.length === 1
                ? '找到一个已有 ODS，请优先打开原模型' : `找到 ${lookup.candidates.length} 个同源候选，请人工选择`}
              description="不会覆盖原模型；源表变化请在物理模型中核对来源及字段差异。" />
            <div className="ods-workbench-candidate-list">
              {lookup.candidates.map(candidate => (
                <article key={candidate.modelId} className="ods-workbench-candidate">
                  <div><strong>{candidate.name}</strong>
                    <span>{candidate.code} · #{candidate.modelId} · {candidate.status}</span>
                  </div>
                  <Button type="primary" onClick={() => history.push(modelDetailPath(candidate.modelId))}>
                    打开已保存 ODS
                  </Button>
                </article>
              ))}
            </div>
          </section>
        )}

        {newTarget && (
          <>
            <section className="ods-workbench-card" aria-label="源表列结构">
              <div className="ods-workbench-section-title">
                <div>
                  <h2>源表字段与映射</h2>
                  <p>选定源表后自动核验来源字段、类型、主键与可空性；请审阅自动建立的一一映射。</p>
                </div>
                <Button type="primary" loading={previewBusy} disabled={Boolean(savedId)}
                  onClick={() => void preview()}>{physicalColumns.length ? '重新读取字段' : '重试读取源字段'}</Button>
              </div>
              <Alert className="ods-workbench-alert" type="info" showIcon
                message={lookup?.reason || '当前项目尚无精确同源 ODS'}
                description="目标查找结果与实时字段预览是两种独立证据。" />
              {physicalColumns.length ? (
                <>
                  <div className="ods-workbench-count">已核验 {physicalColumns.length} 个源列 · 目标字段名可修改，源列信息不可修改</div>
                  <div className="ods-workbench-mapping" role="region" tabIndex={0} aria-label="源列与 ODS 字段映射，可横向滚动">
                    <div className="ods-workbench-mapping-row ods-workbench-mapping-head">
                      <span>源表字段</span><span>类型 / 约束</span><span>ODS 字段名</span>
                    </div>
                    {physicalColumns.map(col => (
                      <div key={col.name} className="ods-workbench-mapping-row">
                        <div className="ods-workbench-mapping-source">
                          <strong>{col.name}</strong>
                          {col.remarks && <small>{col.remarks}</small>}
                        </div>
                        <div className="ods-workbench-mapping-type">
                          <span>{col.typeName}{col.size ? `(${col.size})` : ''}</span>
                          {col.primaryKey ? <Tag color="blue">源主键</Tag> : null}
                          <small>{col.nullable === false ? '不可空' : '可空'}</small>
                        </div>
                        <Input aria-label={`ODS 字段 ${col.name}`} value={targetNames[col.name] ?? ''}
                          disabled={Boolean(savedId)}
                          onChange={e => { setDirty(true); setTargetNames(v => ({ ...v, [col.name]: e.target.value })); }}
                        />
                      </div>
                    ))}
                  </div>
                  {!validMappings && <Alert className="ods-workbench-alert"
                    type="error" showIcon message="字段名必须合法且互不重复；未满足时不允许提交。" />}
                </>
              ) : <div className="ods-workbench-empty">
                {previewBusy ? '正在自动读取当前源表字段…' : '暂无已核验的源字段；读取失败时可重新尝试。'}
                本操作不会采集元数据或执行建表。
              </div>}
            </section>

            <section className="ods-workbench-card" aria-label="目标 ODS 草稿配置">
              <div className="ods-workbench-section-title">
                <div><h2>目标 ODS 模型</h2>
                  <p>模型名称和编码已按来源推荐；检查 ODS 目标数据库方言，一次保存完整设计。</p></div>
                {savedDraft?.id ? <Tag color="blue">草稿 #{savedDraft.id}</Tag> : null}
              </div>
              {savedDraft ? (
                <Alert className="ods-workbench-alert" type={savedId ? 'success' : 'warning'} showIcon
                  message={savedId ? `ODS 设计已完整保存 · #${savedId}` : `已找到可恢复草稿 #${savedDraft.id}`}
                  description="刷新后使用真实草稿 ID 恢复。失败后先核对已有模型，禁止重复创建同一目标。" />
              ) : (
                <div className="ods-workbench-form">
                  <label>模型编码
                    <Input aria-label="ODS 编码" placeholder="例如 ods_mkt_coupon" value={code}
                      onChange={e => { setDirty(true); setCode(e.target.value); }} />
                  </label>
                  <label>模型名称
                    <Input aria-label="ODS 名称" placeholder="例如 ODS 优惠券表" value={name}
                      onChange={e => { setDirty(true); setName(e.target.value); }} />
                  </label>
                  <label>目标方言
                    <Select aria-label="目标方言" className="ods-workbench-select"
                      value={dialect || undefined} onChange={v => { setDirty(true); setDialect(v); }}
                      options={MODELING_DIALECT_OPTIONS} placeholder="选择 ODS 目标方言（不是业务源数据库）" />
                  </label>
                </div>
              )}
              <p className="ods-workbench-note">ODS 不强制逻辑模型或资产纳管。技术列、分区及索引可在保存后进入物理模型继续设计；本步骤不执行 DDL。</p>
            </section>
          </>
        )}

        {newTarget && (
          <footer className="ods-workbench-actionbar" aria-label="保存与后续操作">
            <div className="ods-workbench-action-copy">
              <strong>{savedId ? 'ODS 设计已保存' : savedDraft ? '继续保存 ODS 设计'
                : physicalColumns.length ? '确认来源与目标配置后保存' : '正在自动核验来源字段'}</strong>
              <span>{savedId ? '模型、字段和全部来源映射已经回读确认，可以继续完善物理设计。'
                : savedDraft ? '已有可恢复草稿，点击保存将原子提交结构和全部字段映射。'
                : '点击一次保存，系统会自动处理可恢复草稿及全列映射；不会执行 DDL。'}</span>
            </div>
            <Space wrap>
              {savedId ? (
                <>
                  <Button onClick={() => history.push(modelDetailPath(savedId))}>打开 ODS</Button>
                  <Button type="primary" onClick={() => history.push(sourceReturn)}>
                    {fromModeling ? '返回物理模型' : '保存并返回来源'}
                  </Button>
                </>
              ) : (
                <Button type="primary" loading={savingDesign || creating || committing}
                  disabled={!validMappings || !dialect || creationUnknown || commitUnknown
                    || !/^[A-Za-z0-9_][A-Za-z0-9_-]{0,127}$/.test(code)
                    || !name.trim() || !can('modeling:update')
                    || (!savedDraft && !can('modeling:create'))}
                  onClick={() => void saveOdsDesign()}>保存 ODS 设计</Button>
              )}
            </Space>
          </footer>
        )}
        <div className="ods-workbench-bottom-note">来源身份、候选模型和保存回执均由服务端核验；元数据采集、资产纳管与物理模型部署是独立操作。</div>
      </main>
    </div>
  );
};

export default OdsSourceJourney;
