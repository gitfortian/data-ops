import { history, useSearchParams } from '@umijs/max';
import { Alert, Button, Input, Modal, Select, Space, Spin, Tag, Typography, message } from 'antd';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useSecurityProject } from '@/contexts/SecurityProjectContext';
import usePermissionAccess from '@/hooks/usePermissionAccess';
import { listAllDataSources } from '@/services/data-source/api';
import { getEntityDetail } from '@/services/metadata/api';
import { commitOdsManualImport, createModelingModel, getModelingModel, getModelingStructure } from '@/services/modeling/api';
import { getOdsSourceCandidates, previewModelingImportColumns } from '@/services/modeling/import';
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
      setSavedId(Number(savedDraft.id));
      setDirty(false);
      message.success('ODS 草稿、结构及逐列来源映射已在 Modeling Owner 保存。');
    } catch (error) {
      if (ticket === readEpoch.current)
        setFailure(error instanceof Error ? error.message : '来源映射保存结果未知，请按草稿 ID 核查原域持久化记录与映射，勿自动重提。');
    } finally { if (ticket === readEpoch.current) setCommitting(false); }
  };


  const existing = lookup?.state === 'READY' && lookup.candidates.length > 0;
  const newTarget = lookup?.state === 'READY' && lookup.candidates.length === 0 && sourceVerified;
  const stage = savedId ? 4 : savedDraft ? 3 : physicalColumns.length ? 3
    : sourceVerified && lookup?.state === 'READY' ? 2 : 1;

  return (
    <div className="ods-workbench" data-testid="ods-source-workbench">
      <header className="ods-workbench-head">
        <div>
          <p className="ods-workbench-eyebrow">MODELING / ODS · 来源驱动建模</p>
          <h1>从已核验源表建立 ODS</h1>
          <p>查找现有目标 → 读取真实源列 → 审阅映射 → 保存模型与来源 → 返回同一源表</p>
        </div>
        <Space wrap>
          <Button onClick={() => navigateSafely(sourceReturn)}>
            {liveSourceReturn ? '返回数据源表结构' : '返回来源物理表'}
          </Button>
          <Button type="text" onClick={() => navigateSafely('/modeling')}>模型工作台</Button>
        </Space>
      </header>

      <nav aria-label="ODS 建模进度" className="ods-workbench-progress">
        {[
          ['01', '来源身份', sourceVerified ? '已通过当前项目核验' : '正在核验'],
          ['02', '目标 ODS', existing ? '已有同源候选' : newTarget ? '可创建新目标' : '等待精确查找'],
          ['03', '源列与映射', physicalColumns.length ? `已读取 ${physicalColumns.length} 列` : '等待实时结构预览'],
          ['04', '保存与返回', savedId ? '已获持久化回执' : savedDraft ? '已保存空草稿，待提交字段' : '未完成'],
        ].map(([number, title, detail], index) => (
          <div key={number}
            className={`ods-workbench-progress-item ${index + 1 === stage ? 'active' : ''} ${index + 1 < stage || savedId && index === 3 ? 'complete' : ''}`}>
            <span className="ods-workbench-progress-number">{number}</span>
            <div><strong>{title}</strong><small>{detail}</small></div>
          </div>
        ))}
      </nav>

      <div className="ods-workbench-grid">
        <aside className="ods-workbench-origin" aria-label="当前来源对象">
          <div className="ods-workbench-card">
            <p className="ods-workbench-eyebrow">SOURCE IDENTITY</p>
            <h2>当前原始表</h2>
            <div className="ods-workbench-origin-name">{table || '来源待核验'}</div>
            <dl>
              <div><dt>数据源 ID</dt><dd>#{datasourceId || '—'}</dd></div>
              <div><dt>数据库</dt><dd>{database || '—'}</dd></div>
              <div><dt>Schema</dt><dd>{schema || '默认 Schema'}</dd></div>
              <div><dt>源表</dt><dd>{table || '—'}</dd></div>
            </dl>
            <Tag color={sourceVerified ? 'success' : 'warning'}>
              {sourceVerified ? '已验证当前项目来源' : '等待当前项目身份核验'}
            </Tag>
          </div>
          <div className="ods-workbench-origin-note">
            <strong>ODS 是贴源例外</strong>
            <p>不要求先创建逻辑模型、标准字段或纳管资产。字段映射要基于真实源列；本页仅保存模型定义，不执行 DDL。</p>
          </div>
        </aside>

        <main className="ods-workbench-main" aria-label="ODS 字段与来源设计">
          {loading && <div className="ods-workbench-card ods-workbench-wait"><Spin tip="正在核验来源和已保存 ODS" /></div>}
          {failure && <Alert className="ods-workbench-alert" type="error" showIcon message={failure}
            description="失败或超时不表示来源、空草稿或结构不存在。请先核验后继续，避免重复提交。"
            action={<Button size="small" onClick={() => setReload(v => v + 1)}>重新核验</Button>} />}
          {lookup?.state === 'UNVERIFIED' && <Alert className="ods-workbench-alert"
            type="warning" showIcon message="不能确认来源身份" description={lookup.reason} />}

          {existing && (
            <section className="ods-workbench-card" aria-label="已有同源 ODS">
              <p className="ods-workbench-eyebrow">MATCHED MODELS</p>
              <h2>先使用现有同源 ODS</h2>
              <Alert className="ods-workbench-alert" type="info" showIcon
                message={lookup.candidates.length === 1
                  ? '找到一个已有 ODS，请优先打开原模型' : `找到 ${lookup.candidates.length} 个同源候选，请人工选择`}
                description="当前候选只基于精确来源查找。不覆盖任何模型；如源结构发生变化，请在原模型核验版本与差异。" />
              <div className="ods-workbench-candidate-list">
                {lookup.candidates.map(candidate => (
                  <article key={candidate.modelId} className="ods-workbench-candidate">
                    <div>
                      <strong>{candidate.name}</strong>
                      <div><Tag>{candidate.code}</Tag><Tag>{candidate.status}</Tag><span>#{candidate.modelId}</span></div>
                    </div>
                    <Button type="primary" onClick={() => history.push(modelDetailPath(candidate.modelId))}>打开已保存 ODS</Button>
                  </article>
                ))}
              </div>
            </section>
          )}

          {newTarget && (
            <>
              <section className="ods-workbench-card" aria-label="源表列结构">
                <div className="ods-workbench-section-title">
                  <div><p className="ods-workbench-eyebrow">01 / LIVE SOURCE COLUMNS</p>
                    <h2>核对实时源表与字段</h2>
                    <p>先读取本次真实源列，不能把历史 Metadata Run 当作当前物理表结构。</p>
                  </div>
                  <Tag>{physicalColumns.length ? `已读取 ${physicalColumns.length} 个源列` : '尚未预览源列'}</Tag>
                </div>
                <div className="ods-workbench-action-row">
                  <Button type="primary" loading={previewBusy} disabled={Boolean(savedId)}
                    onClick={() => void preview()}>读取当前物理列并核对</Button>
                  {physicalColumns.length ? <span>逐列核对完成后方可保存结构与映射。</span>
                    : <span>这是只读预览，不写元数据或 ODS。</span>}
                </div>
                {physicalColumns.length > 0 && (
                  <div className="ods-workbench-mapping" role="region" tabIndex={0} aria-label="源列与 ODS 字段映射，可横向滚动">
                    <div className="ods-workbench-mapping-row ods-workbench-mapping-head">
                      <span>源表字段</span><span>源类型 / 约束</span><span>目标 ODS 字段名（可修改）</span>
                    </div>
                    {physicalColumns.map(col => (
                      <div key={col.name} className="ods-workbench-mapping-row">
                        <div className="ods-workbench-mapping-source">
                          <strong>{col.name}</strong>
                          {col.remarks && <small>{col.remarks}</small>}
                        </div>
                        <div className="ods-workbench-mapping-type">
                          <Tag>{col.typeName}{col.size ? `(${col.size})` : ''}</Tag>
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
                )}
                {physicalColumns.length > 0 && !validMappings && <Alert className="ods-workbench-alert"
                  type="error" showIcon message="字段名必须合法且互不重复；未满足时不允许提交。" />}
              </section>

              <section className="ods-workbench-card" aria-label="目标 ODS 草稿配置">
                <div className="ods-workbench-section-title">
                  <div><p className="ods-workbench-eyebrow">02 / PHYSICAL TARGET</p>
                    <h2>确认 ODS 模型身份</h2>
                    <p>保存空草稿后保留真实 ID；第二次提交才原子保存源列结构与逐列映射。</p>
                  </div>
                  {savedDraft && <Tag color="blue">物理模型 #{savedDraft.id}</Tag>}
                </div>
                {savedDraft ? (
                  <Alert className="ods-workbench-alert" type={savedId ? 'success' : 'warning'} showIcon
                    message={`已保存空草稿 #${savedDraft.id}，字段映射${savedId ? '已提交' : '尚未提交'}`}
                    description="刷新后按 URL 的 draftId 和 Modeling 服务端事实恢复。失败后应先按模型 ID 或编码核对，不能再创建同名空模型。" />
                ) : (
                  <div className="ods-workbench-form">
                    <label>ODS 模型编码
                      <Input aria-label="ODS 编码" placeholder="例如 ods_trade_order" value={code}
                        onChange={e => { setDirty(true); setCode(e.target.value); }} />
                    </label>
                    <label>ODS 模型名称
                      <Input aria-label="ODS 名称" placeholder="例如 ODS 订单" value={name}
                        onChange={e => { setDirty(true); setName(e.target.value); }} />
                    </label>
                    <label>目标数据库方言
                      <Select aria-label="目标方言" className="ods-workbench-select" placeholder="选择目标方言"
                        value={dialect || undefined} onChange={v => { setDirty(true); setDialect(v); }}
                        options={MODELING_DIALECT_OPTIONS} />
                    </label>
                  </div>
                )}
                <div className="ods-workbench-tech-note">
                  <strong>技术字段与分区</strong>
                  <p>此处仅镜像并映射真实源列。额外的 ETL 时间列、分区及索引请在保存后的物理表结构工作区单独设计；不把技术列伪装成源列。</p>
                </div>
              </section>
            </>
          )}
        </main>

        <aside className="ods-workbench-next" aria-label="下一步操作">
          <section className="ods-workbench-next-card">
            <p className="ods-workbench-eyebrow">NEXT ACTION</p>
            <h2>{savedId ? 'ODS 已成功保存' : existing ? '优先复用现有 ODS'
              : savedDraft ? '提交真实结构和映射' : physicalColumns.length ? '保存可恢复草稿' : '先核对源列'}</h2>
            <p>{savedId ? '已取得 Modeling 服务端保存和逐列映射回执；建议返回同一来源核对闭环。'
              : existing ? '已有精确同源目标，直接打开原模型继续审阅。'
              : savedDraft ? '空草稿仍不是完整 ODS；请确认映射后提交，并核对持久化回执。'
              : physicalColumns.length ? '已读取实时源列。完成字段命名与物理身份核对后，保存模型草稿。'
              : '当前来源核验通过后，请先读取实时结构、确认字段，再保存 ODS。'}</p>
            {newTarget && !savedId && (!savedDraft ? (
              <Button className="ods-workbench-next-primary" type="primary"
                loading={creating}
                disabled={!validMappings || !dialect || !/^[A-Za-z0-9_][A-Za-z0-9_-]{0,127}$/.test(code)
                  || !name.trim() || !can('modeling:create')}
                onClick={() => void createDraft()}>保存可恢复的空 ODS 草稿</Button>
            ) : (
              <Button className="ods-workbench-next-primary" type="primary"
                loading={committing} disabled={!validMappings || !can('modeling:update')}
                onClick={() => void commit()}>确认并原子保存结构与映射</Button>
            ))}
            {savedId ? (
              <div className="ods-workbench-next-actions">
                <Button type="primary" onClick={() => history.push(sourceReturn)}>保存并返回来源</Button>
                <Button onClick={() => history.push(modelDetailPath(savedId))}>打开 ODS</Button>
              </div>
            ) : savedDraft?.id ? (
              <Button block onClick={() => history.push(modelDetailPath(Number(savedDraft.id)))}>
                打开已保存的模型 #{savedDraft.id}
              </Button>
            ) : null}
          </section>
          <section className="ods-workbench-next-card">
            <p className="ods-workbench-eyebrow">TRUTH & SAFETY</p>
            <h3>真实保存的判定</h3>
            <ul>
              <li>当前项目和数据源身份已核验。</li>
              <li>ODS 同源候选按精确库、Schema、表查找。</li>
              <li>原子提交核对源列和目标字段全量回执。</li>
              <li>未保存的编辑离开前会提示。</li>
            </ul>
            <p>资产纳管是并行治理；保存设计不执行 SQL/DDL，不代表已部署或完成数据同步。</p>
          </section>
        </aside>
      </div>
    </div>
  );
};

export default OdsSourceJourney;
