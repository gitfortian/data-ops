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
  const sourceReturn = liveSourceReturn ??
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
  const [dirty, setDirty] = useState(false);
  const [savedId, setSavedId] = useState<number | null>(null);
  const readEpoch = useRef(0);
  const sourceIdentity = useRef('');

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
    setLookup(null);
    setSourceVerified(false);
    // A draftId-only redirect must not erase a preview/mapping the user just confirmed.
    if (changedSource) {
      setColumns(null); setSavedId(null); setDirty(false);
      setPreviewBusy(false); setCreating(false); setCommitting(false);
    }
    setSavedDraft(null);
    if (!valid || datasourceId == null) {
      setFailure('来源坐标不完整或非法；请从已发现的物理表进入 ODS 建模。');
      setLoading(false);
      return;
    }
    try {
      const [sources, response, entity, resumed] = await Promise.all([
        listAllDataSources(),
        getOdsSourceCandidates({ datasourceId, database, schema, table }),
        entityId ? getEntityDetail(entityId) : Promise.resolve(null),
        draftId ? getModelingModel(draftId) : Promise.resolve(null),
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
      if (resumed && (resumed.layerCode !== 'ODS' || resumed.status !== 'DRAFT'
          || resumed.sourceDatasourceId != null)) {
        throw new Error('已保存的模型已被修改或绑定来源，请直接打开模型复核，不能再次首导。');
      }
      setSourceVerified(true);
      setLookup(response);
      if (resumed) setSavedDraft(resumed);
      const inferred = dialectOfDbType(validSource.dbType);
      if (inferred) setDialect(inferred);
    } catch (error) {
      if (ticket === readEpoch.current)
        setFailure(error instanceof Error ? error.message : '来源和模型核验失败；请重试。');
    } finally {
      if (ticket === readEpoch.current) setLoading(false);
    }
  }, [projectId, datasourceId, database, schema, table, entityId, draftId, valid, reload]);

  useEffect(() => {
    void refresh();
    return () => { readEpoch.current += 1; };
  }, [refresh]);

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

  const physicalColumns = useMemo(() => columns ?? [], [columns]);
  const targetValues = physicalColumns.map(col => (targetNames[col.name] || '').trim());
  const validMappings = physicalColumns.length > 0
    && targetValues.every(v => /^[A-Za-z_][A-Za-z0-9_]*$/.test(v))
    && new Set(targetValues.map(v => v.toLowerCase())).size === targetValues.length;

  const createDraft = async () => {
    const ticket = readEpoch.current;
    if (!sourceVerified || lookup?.state !== 'READY' || lookup.candidates.length > 0
        || !can('modeling:create') || !dialect || !code || !validMappings || savedDraft) return;
    setCreating(true);
    try {
      const created = await createModelingModel({
        code: code.trim(), name: name.trim(), dialect, layerCode: 'ODS',
      });
      if (ticket !== readEpoch.current) return;
      if (!created.id) throw new Error('创建接口未返回可恢复的模型 ID。');
      setSavedDraft(created);
      const next = new URLSearchParams(params.toString());
      next.set('draftId', String(created.id));
      history.replace(`/modeling?${next.toString()}`);
      message.info('空 ODS 草稿已保存。字段和来源映射尚未提交，请核对后继续。');
    } catch (error) {
      if (ticket === readEpoch.current)
        setFailure(error instanceof Error ? error.message : '草稿保存结果未知；请按模型编码检查当前项目已有草稿，勿直接重复创建。');
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
      && model.layerCode === 'ODS' && model.sourceDatasourceId === datasourceId
      && model.sourceDatabase === database && (model.sourceSchema || undefined) === schema
      && model.sourceTable === table;
    if (!identityMatches) return false;
    const savedColumns = new Set((structure.columns ?? []).map(col => col.columnName?.toLowerCase()));
    if (!physicalColumns.every(col => savedColumns.has(targetNames[col.name]?.trim().toLowerCase()))) return false;
    return physicalColumns.every(col => mappings.some(row =>
      row.mapped && row.targetColumn?.toLowerCase() === targetNames[col.name]?.trim().toLowerCase()
      && row.sourceDatasourceId === datasourceId && row.sourceDatabase === database
      && (row.sourceSchema || undefined) === schema && row.sourceTable === table
      && row.sourceColumn?.toLowerCase() === col.name.toLowerCase()
      && !row.transformExpr));
  };

  const commit = async () => {
    const ticket = readEpoch.current;
    if (!savedDraft?.id || !can('modeling:update') || !validMappings || datasourceId == null
        || lookup?.state !== 'READY' || lookup.candidates.length > 0 || !sourceVerified) return;
    setCommitting(true);
    try {
      // Re-read persisted version and optimistic definition. Never trust an old local draft.
      const [live, structure] = await Promise.all([
        getModelingModel(savedDraft.id), getModelingStructure(savedDraft.id),
      ]);
      if (ticket !== readEpoch.current) return;
      if (live.status !== 'DRAFT' || live.layerCode !== 'ODS' || live.sourceDatasourceId != null
          || !structure.definition || (structure.columns ?? []).length !== 0) {
        throw new Error('草稿已变化或有字段/来源映射；请在原模型核验，禁止覆盖。');
      }
      const receipt = await commitOdsManualImport(savedDraft.id, {
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
      if (receipt.status !== 'SAVED_AND_VERIFIED' || receipt.modelId !== Number(savedDraft.id)
          || receipt.confirmedMappingCount !== physicalColumns.length) {
        throw new Error('写入回执尚不足以证明模型与全部来源列已保存；请打开模型核对。');
      }
      if (!(await verifySavedOds(Number(savedDraft.id)))) {
        throw new Error('保存接口已返回，但模型结构或来源映射回读不一致；请核对原模型，勿重复写入。');
      }
      if (ticket !== readEpoch.current) return;
      setSavedId(Number(savedDraft.id));
      setDirty(false);
      setFailure('');
      message.success('ODS 设计、真实来源和逐列映射已重新读取确认。未部署、未同步。');
    } catch (error) {
      // The POST can have committed even when its HTTP reply was lost. Never retry the
      // mutation from a timeout; check current persisted evidence first.
      if (ticket !== readEpoch.current) return;
      let persisted = false;
      try { persisted = await verifySavedOds(Number(savedDraft.id)); } catch { /* owner read unavailable */ }
      if (ticket !== readEpoch.current) return;
      if (persisted) {
        setSavedId(Number(savedDraft.id));
        setDirty(false);
        setFailure('');
        message.success('写入响应未确认，但已重新读取到完整 ODS 设计和来源映射。');
      } else {
        setFailure((error instanceof Error ? error.message : '来源映射保存结果未知')
          + '；请先重新核验草稿和来源，禁止直接重复提交。');
      }
    } finally { if (ticket === readEpoch.current) setCommitting(false); }
  };


  const newTarget = lookup?.state === 'READY' && lookup.candidates.length === 0 && sourceVerified;

  return (
    <div className="ods-workbench" data-testid="ods-source-workbench">
      <header className="ods-workbench-head">
        <div>
          <h1>ODS 贴源建模</h1>
          <p>核对来源表，选择已有 ODS 或创建新草稿。保存后可返回原始表。</p>
        </div>
        <Space wrap>
          <Button onClick={() => navigateSafely(sourceReturn)}>
            {liveSourceReturn ? '返回数据源表结构' : '返回来源物理表'}
          </Button>
          <Button type="text" onClick={() => navigateSafely('/modeling')}>建模工作台</Button>
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
                  <p>读取实时源表结构，核对字段类型和主键，再确认对应的 ODS 字段。</p>
                </div>
                <Button type="primary" loading={previewBusy} disabled={Boolean(savedId)}
                  onClick={() => void preview()}>读取当前物理列并核对</Button>
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
              ) : <div className="ods-workbench-empty">尚未读取本次实时字段。点击右上方操作开始核对；不会因此执行采集或建表。</div>}
            </section>

            <section className="ods-workbench-card" aria-label="目标 ODS 草稿配置">
              <div className="ods-workbench-section-title">
                <div><h2>目标 ODS 模型</h2>
                  <p>创建可恢复的物理模型草稿，再原子保存表结构与逐列来源映射。</p></div>
                {savedDraft?.id ? <Tag color="blue">草稿 #{savedDraft.id}</Tag> : null}
              </div>
              {savedDraft ? (
                <Alert className="ods-workbench-alert" type={savedId ? 'success' : 'warning'} showIcon
                  message={`已保存空草稿 #${savedDraft.id}，字段映射${savedId ? '已提交' : '尚未提交'}`}
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
                      options={MODELING_DIALECT_OPTIONS} placeholder="选择目标数据库方言" />
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
              <strong>{savedId ? 'ODS 已成功保存' : savedDraft ? '待提交字段与来源映射'
                : physicalColumns.length ? '确认字段后保存草稿' : '请先读取真实源列'}</strong>
              <span>{savedId ? '已核验 Modeling 服务端保存回执，可返回原表或打开模型。'
                : savedDraft ? '空草稿不是完整 ODS；请确认字段映射后提交。'
                : physicalColumns.length ? '保存草稿只建立模型身份，不代表结构和映射已提交。'
                : '未读取源列前不能创建 ODS 草稿。'}</span>
            </div>
            <Space wrap>
              {savedId ? (
                <>
                  <Button onClick={() => history.push(modelDetailPath(savedId))}>打开 ODS</Button>
                  <Button type="primary" onClick={() => history.push(sourceReturn)}>保存并返回来源</Button>
                </>
              ) : !savedDraft ? (
                <Button type="primary" loading={creating}
                  disabled={!validMappings || !dialect
                    || !/^[A-Za-z0-9_][A-Za-z0-9_-]{0,127}$/.test(code)
                    || !name.trim() || !can('modeling:create')}
                  onClick={() => void createDraft()}>保存可恢复的空 ODS 草稿</Button>
              ) : (
                <>
                  <Button onClick={() => history.push(modelDetailPath(Number(savedDraft.id)))}>打开已保存的模型 #{savedDraft.id}</Button>
                  <Button type="primary" loading={committing}
                    disabled={!validMappings || !can('modeling:update')}
                    onClick={() => void commit()}>确认并原子保存结构与映射</Button>
                </>
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
