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

  return (
    <div className="mx-auto max-w-[1080px] space-y-4 px-6 py-6">
      <Space>
        <Button onClick={() => navigateSafely(sourceReturn)}>{liveSourceReturn ? '返回数据源表结构' : '返回来源物理表'}</Button>
        <Button type="link" onClick={() => navigateSafely('/modeling')}>建模工作台</Button>
      </Space>
      <Typography.Title level={4}>从物理表继续 ODS 设计</Typography.Title>
      <Typography.Paragraph type="secondary">
        来源 #{datasourceId || '-'} / {database} / {schema || '默认 Schema'} / {table}。
        资产待治理是并行操作，不要求资产上架或 Asset 写权限。这里只保存模型草稿，不执行 DDL。
      </Typography.Paragraph>
      {loading && <Spin tip="正在核验来源和已保存 ODS" />}
      {failure && <Alert type="error" showIcon message={failure}
        action={<Button size="small" onClick={() => setReload(v => v + 1)}>重新核验</Button>} />}
      {lookup?.state === 'UNVERIFIED' && <Alert type="warning" showIcon
        message="不能确认来源身份" description={lookup.reason} />}
      {lookup?.state === 'READY' && lookup.candidates.length > 0 && (
        <div className="space-y-2">
          <Alert type="info" showIcon message={lookup.candidates.length === 1
            ? '找到一个已有 ODS，请优先打开原模型' : `找到 ${lookup.candidates.length} 个同源候选，请人工选择`}
            description="不覆盖原模型、字段或来源映射；源表结构变化请在模型中人工审阅。" />
          {lookup.candidates.map(candidate => (
            <div key={candidate.modelId} className="flex items-center justify-between rounded border border-solid border-[#eaecf0] p-3">
              <Space><strong>{candidate.name}</strong><Tag>{candidate.code}</Tag><Tag>{candidate.status}</Tag></Space>
              <Button type="primary" onClick={() => history.push(`/modeling/models/${candidate.modelId}`)}>
                打开已保存 ODS
              </Button>
            </div>
          ))}
        </div>
      )}
      {lookup?.state === 'READY' && lookup.candidates.length === 0 && sourceVerified && (
        <div className="space-y-4 rounded border border-solid border-[#eaecf0] bg-white p-4">
          <Alert type="info" showIcon message={lookup.reason}
            description="先预览当前物理表并确认逐列映射，再保存空 ODS 草稿与其结构。历史 Metadata Run 不是本次实时预览。" />
          <Space wrap>
            <Button loading={previewBusy} onClick={() => void preview()}>读取当前物理列并核对</Button>
            <Tag>{physicalColumns.length ? `已核对 ${physicalColumns.length} 个源列` : '尚未预览源列'}</Tag>
          </Space>
          {physicalColumns.length > 0 && (
            <div className="space-y-2">
              <Typography.Text strong>逐列映射核对（源列 → ODS 字段）</Typography.Text>
              {physicalColumns.map(col => (
                <div key={col.name} className="grid grid-cols-3 items-center gap-3">
                  <span className="truncate">{col.name} <Tag>{col.typeName}</Tag></span>
                  <span className="text-[#667085]">→</span>
                  <Input aria-label={`ODS 字段 ${col.name}`} value={targetNames[col.name] ?? ''}
                    disabled={Boolean(savedId)} onChange={e => { setDirty(true); setTargetNames(v => ({ ...v, [col.name]: e.target.value })); }} />
                </div>
              ))}
              {!validMappings && <Alert type="error" message="字段名必须合法且互不重复；未满足时不允许提交。" />}
            </div>
          )}
          {savedDraft ? (
            <Alert type="warning" showIcon
              message={`已保存空草稿 #${savedDraft.id}，字段映射${savedId ? '已提交' : '尚未提交'}`}
              description="刷新后使用 URL 中的 draftId 和原域持久化事实恢复；如果提交失败，先核对这个草稿，不能重复创建。" />
          ) : (
            <div className="grid grid-cols-1 gap-3 md:grid-cols-3">
              <Input aria-label="ODS 编码" placeholder="ODS 模型编码" value={code} onChange={e => { setDirty(true); setCode(e.target.value); }} />
              <Input aria-label="ODS 名称" placeholder="ODS 模型名称" value={name} onChange={e => { setDirty(true); setName(e.target.value); }} />
              <Select aria-label="目标方言" placeholder="选择目标方言" value={dialect || undefined}
                onChange={v => { setDirty(true); setDialect(v); }} options={MODELING_DIALECT_OPTIONS} />
            </div>
          )}
          {!savedId && (
            <Space>
              {!savedDraft ? <Button type="primary" loading={creating} disabled={!validMappings || !dialect
                || !/^[A-Za-z0-9_][A-Za-z0-9_-]{0,127}$/.test(code) || !name.trim() || !can('modeling:create')}
                onClick={() => void createDraft()}>保存可恢复的空 ODS 草稿</Button> :
                <Button type="primary" loading={committing} disabled={!validMappings || !can('modeling:update')}
                  onClick={() => void commit()}>确认并原子保存结构与映射</Button>}
            </Space>
          )}
          {savedDraft?.id && (
            <Button onClick={() => history.push(`/modeling/models/${savedDraft.id}`)}>
              打开已保存的模型 #{savedDraft.id}
            </Button>
          )}
          {savedId && (
            <Alert type="success" showIcon message="ODS 已保存，逐列映射有持久化回执"
              action={<Space>
                <Button type="primary" onClick={() => history.push(sourceReturn)}>保存并返回来源</Button>
                <Button onClick={() => history.push(`/modeling/models/${savedId}`)}>打开 ODS</Button>
              </Space>} />
          )}
        </div>
      )}
    </div>
  );
};

export default OdsSourceJourney;
