import { Alert, Checkbox, Descriptions, Drawer, Input, Modal, Result, Select, Space, Spin, Steps, Table, Tag, Typography } from 'antd';
import type { TableColumnsType } from 'antd';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { YakButton } from '@/components/ui';
import { deriveModelingModel, previewModelingDerive } from '@/services/modeling/api';
import type { ModelingCreatePayload, ModelingDerivePreview, ModelingDeriveReceipt } from '@/services/modeling/types';
import { listSemanticProcessFields } from '@/services/semantic/api';
import type { SemanticFieldRecord } from '@/services/semantic/types';
import { buildDerivePayload, DERIVE_FUNCTIONS, DERIVE_PERIODS, deriveFieldDrafts, inspectDeriveDesign,
  isBusinessDeriveField, selectedDeriveUpstreams } from '../editor/deriveDesign';
import type { DeriveConfig, DeriveFieldDraft } from '../editor/deriveDesign';

interface Props {
  base: ModelingCreatePayload;
  projectId?: number;
  processName?: string;
  grain?: string;
  canCreate: boolean;
  onClose: () => void;
  onBack: () => void;
  onOpenModel: (id: number, tab: string) => void;
}
const INITIAL_CONFIG: DeriveConfig = {
  scdType: 'SCD1', statPeriod: '1d', appCode: '', appName: '', upstreamLayer: 'DWS',
};

export default function ModelDeriveDrawer({ base, projectId, processName, grain, canCreate,
  onClose, onBack, onOpenModel }: Props) {
  const layer = base.layerCode || '';
  const aggregate = layer === 'DWS' || layer === 'ADS';
  const [config, setConfig] = useState<DeriveConfig>(INITIAL_CONFIG);
  const [preview, setPreview] = useState<ModelingDerivePreview>();
  const [previewConfig, setPreviewConfig] = useState<string>();
  const [fields, setFields] = useState<DeriveFieldDraft[]>([]);
  const [fieldsEdited, setFieldsEdited] = useState(false);
  const [standards, setStandards] = useState<SemanticFieldRecord[]>([]);
  const [standardsError, setStandardsError] = useState(false);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string>();
  const [stage, setStage] = useState<'design' | 'review'>('design');
  const [receipt, setReceipt] = useState<ModelingDeriveReceipt>();
  const requestEpoch = useRef(0);
  const inFlight = useRef(false);
  const active = useRef(true);
  const configRef = useRef(config);
  configRef.current = config;
  const scope = JSON.stringify([projectId, base]);
  const scopeRef = useRef(scope);
  scopeRef.current = scope;
  const configChanged = previewConfig !== JSON.stringify(config);
  const hasConfigurationEdits = JSON.stringify(config) !== JSON.stringify(INITIAL_CONFIG);
  const report = useMemo(() => inspectDeriveDesign(base, config, preview, fields), [base, config, preview, fields]);

  const readPreview = useCallback(async (requested: DeriveConfig) => {
    if (!base.processId || projectId === undefined) return;
    if (aggregate && requested.upstreamModelIds && !requested.upstreamModelIds.length) {
      setError('请至少选择一个上游模型；清空选择不会按默认全选读取。');
      return;
    }
    const epoch = ++requestEpoch.current;
    const expectedScope = JSON.stringify([projectId, base]);
    const expectedConfig = JSON.stringify(requested);
    const current = () => active.current && epoch === requestEpoch.current
      && scopeRef.current === expectedScope && JSON.stringify(configRef.current) === expectedConfig;
    setLoading(true);
    setError(undefined);
    setPreviewConfig(undefined);
    setStage('design');
    try {
      const result = await previewModelingDerive(base.processId, layer, base.dialect,
        layer === 'DIM' ? requested.scdType : undefined, aggregate ? requested.upstreamModelIds : undefined,
        aggregate ? requested.statPeriod : undefined, layer === 'ADS' ? requested.appCode : undefined,
        layer === 'ADS' ? requested.appName : undefined, layer === 'ADS' ? requested.upstreamLayer : undefined);
      if (!current()) return;
      if (!result || result.processId !== base.processId || result.layerCode !== layer) throw new Error('预览身份与当前模型设计不一致，请重新读取');
      setPreview(result);
      setFields(deriveFieldDrafts(result));
      setFieldsEdited(false);
      setPreviewConfig(expectedConfig);
    } catch (cause) {
      if (current()) setError(cause instanceof Error ? cause.message : '上游预览暂时无法读取，请重试');
    } finally {
      if (current()) setLoading(false);
    }
  }, [aggregate, base, layer, projectId]);

  useEffect(() => {
    active.current = true;
    void readPreview(configRef.current);
    let current = true;
    setStandards([]);
    setStandardsError(false);
    if (base.processId && projectId !== undefined) void listSemanticProcessFields(base.processId).then(result => {
      if (current) setStandards(result ?? []);
    }).catch(() => { if (current) setStandardsError(true); });
    return () => { current = false; active.current = false; requestEpoch.current += 1; };
  }, [readPreview, base.processId, projectId]);

  const updateConfig = (change: Partial<DeriveConfig>) => {
    requestEpoch.current += 1;
    setLoading(false);
    setError(undefined);
    setConfig(previous => ({ ...previous, ...change }));
    setStage('design');
  };
  const refreshPreview = () => {
    if (fieldsEdited) Modal.confirm({ className: 'modeling-confirm', title: '重新读取会替换当前字段设计',
      content: '当前纳入选择、目标字段名、标准关联与聚合编辑将按新的上游预览重新初始化。',
      okText: '重新读取', cancelText: '保留字段设计', onOk: () => readPreview(config),
    });
    else void readPreview(config);
  };
  const editField = (key: string, change: Partial<DeriveFieldDraft>) => {
    setFields(previous => previous.map(field => field.key === key ? { ...field, ...change } : field));
    setFieldsEdited(true);
  };
  const leave = (next: () => void) => {
    if (inFlight.current) return;
    if (!receipt && (fieldsEdited || configChanged || hasConfigurationEdits)) Modal.confirm({ className: 'modeling-confirm', title: '模型设计尚未创建',
      content: '离开会放弃此处的上游配置与字段设计。返回基本信息会保留之前填写的模型信息。',
      okText: '放弃字段设计并继续', cancelText: '继续设计', onOk: next,
    });
    else next();
  };
  const submit = async () => {
    if (inFlight.current || receipt || !canCreate || stage !== 'review' || configChanged || !preview || report.errors.length) return;
    const payload = buildDerivePayload(base, config, preview, fields);
    inFlight.current = true;
    setSaving(true);
    setError(undefined);
    const expectedScope = scopeRef.current;
    try {
      const result = await deriveModelingModel(payload);
      if (!active.current || scopeRef.current !== expectedScope) return;
      if (!result || !Number.isSafeInteger(result.modelId) || result.modelId <= 0) throw new Error('未取得可核对的创建回执，请先检查模型列表');
      setReceipt(result);
    } catch (cause) {
      if (active.current && scopeRef.current === expectedScope) setError(
        `${cause instanceof Error ? cause.message : '创建未取得成功回执'}。当前设计已保留，请核对已有模型后再决定是否重试。`);
    } finally {
      inFlight.current = false;
      if (active.current && scopeRef.current === expectedScope) setSaving(false);
    }
  };

  const columns: TableColumnsType<DeriveFieldDraft> = [
    { title: '纳入', width: 65, render: (_, field) => <Checkbox aria-label={`纳入 ${field.sourceTable || ''}.${field.sourceColumn}`}
      checked={field.include || field.technical} disabled={field.technical || loading || configChanged}
      onChange={event => editField(field.key, { include: event.target.checked })} /> },
    { title: '来源字段', width: 210, render: (_, field) => <div>
      <div className="break-all text-[12px]">{field.sourceColumn}</div>
      <div className="break-all text-[11px] text-[#98a2b3]">{field.sourceTable || (field.technical ? '目标层技术列' : '维度结构约定')}</div>
      {(field.technical || field.convention) && <Tag className="!mt-1 !text-[10px]">{field.technical ? '技术列' : '维度约定'}</Tag>}
    </div> },
    { title: '目标字段', width: 170, render: (_, field) => <Input size="small" aria-label={`目标字段 ${field.sourceTable || ''}.${field.sourceColumn}`}
      value={field.landingField} maxLength={128} disabled={field.technical || loading || configChanged}
      onChange={event => editField(field.key, { landingField: event.target.value })} /> },
    { title: '预览类型', dataIndex: 'dataType', width: 100 },
    { title: '标准字段', width: 200, render: (_, field) => {
      if (!isBusinessDeriveField(field)) return <span className="text-[#98a2b3]">结构约定</span>;
      const options = standards.filter(item => item.status === 'ENABLED').map(item => ({ value: item.id, label: `${item.name}（${item.code}）` }));
      if (field.stdFieldId && !options.some(item => item.value === field.stdFieldId)) options.push({ value: field.stdFieldId, label: field.stdFieldName || `已引用 #${field.stdFieldId}` });
      return <Select size="small" showSearch optionFilterProp="label" className="w-full" placeholder="尚未关联"
        aria-label={`标准字段 ${field.sourceTable || ''}.${field.sourceColumn}`} value={field.stdFieldId}
        disabled={standardsError || loading || configChanged} options={options}
        onChange={value => editField(field.key, { stdFieldId: value })} />;
    } },
    ...(aggregate ? [
      { title: '字段角色', width: 125, render: (_: unknown, field: DeriveFieldDraft) => isBusinessDeriveField(field) ? <Select
        size="small" className="w-full" aria-label={`字段角色 ${field.sourceTable || ''}.${field.sourceColumn}`}
        disabled={loading || configChanged} value={field.fieldRole}
        options={[{ value: 'DIMENSION', label: '分组维度' }, { value: 'MEASURE', label: '度量' }]}
        onChange={value => editField(field.key, { fieldRole: value, aggregateFunc: value === 'MEASURE' ? field.aggregateFunc : null })} /> : '—' },
      { title: '聚合', width: 145, render: (_: unknown, field: DeriveFieldDraft) => isBusinessDeriveField(field) ? <Select
        size="small" className="w-full" aria-label={`聚合函数 ${field.sourceTable || ''}.${field.sourceColumn}`}
        disabled={loading || configChanged || field.fieldRole !== 'MEASURE'} value={field.aggregateFunc || undefined}
        placeholder="选择函数" options={DERIVE_FUNCTIONS.map(value => ({ value, label: value }))}
        onChange={value => editField(field.key, { aggregateFunc: value })} /> : '—' },
    ] : []),
    { title: '转换 / 口径表达式', width: 220, render: (_, field) => isBusinessDeriveField(field) ? <Input size="small"
      aria-label={`转换表达式 ${field.sourceTable || ''}.${field.sourceColumn}`} value={field.transformExpr}
      disabled={loading || configChanged} maxLength={1024} placeholder="人工确认，创建不执行"
      onChange={event => editField(field.key, { transformExpr: event.target.value })} /> : '—' },
  ];
  const blocked = configChanged || loading || report.errors.length > 0;
  return <Drawer open width="min(1440px, 100vw)" destroyOnHidden maskClosable={false} keyboard={!saving} closable={!saving}
    onClose={() => leave(onClose)} title={<div>
      <div className="text-[18px] font-semibold">{layer} · 从上游设计模型</div>
      <div className="mt-1 text-[12px] font-normal text-[#667085]">{base.name} · {base.code} · {processName || `业务过程 #${base.processId}`}</div>
    </div>}
    footer={<div className="modeling-dialog-actions">
      {!receipt && <YakButton disabled={saving} onClick={() => stage === 'review' ? setStage('design') : leave(onBack)}>{stage === 'review' ? '返回字段设计' : '返回基本信息'}</YakButton>}
      <YakButton disabled={saving} onClick={() => leave(onClose)}>{receipt ? '完成' : '取消'}</YakButton>
      {!receipt && <YakButton type="primary" disabled={!canCreate || blocked} loading={saving}
        onClick={() => stage === 'design' ? setStage('review') : void submit()}>{stage === 'design' ? '复核设计' : '确认创建模型草稿'}</YakButton>}
    </div>}
    styles={{ body: { background: '#f8fafc', padding: 24 } }}>
    <div className="modeling-step-strip"><Steps size="small" current={receipt ? 2 : stage === 'review' ? 1 : 0}
      items={[{ title: '上游与字段' }, { title: '复核设计' }, { title: '创建回执' }]} /></div>
    {receipt ? <div className="rounded-xl border border-[#e4e9f0] bg-white p-5">
      <Result status="success" title="模型草稿已创建" subTitle={`${receipt.name}（${receipt.code}）· 模型 #${receipt.modelId}`}
        extra={<Space wrap><YakButton type="primary" onClick={() => onOpenModel(receipt.modelId, 'structure')}>打开表结构</YakButton>
          <YakButton onClick={() => onOpenModel(receipt.modelId, 'layer-mapping')}>核对分层映射</YakButton></Space>} />
      <Descriptions bordered size="small" column={3} items={[
        { key: 'columns', label: '实际字段', children: receipt.columnCount },
        { key: 'governed', label: '已关联标准', children: receipt.governedCount },
        { key: 'unmatched', label: '待治理', children: receipt.unmatchedCount },
        { key: 'backfilled', label: '上游治理回填', children: receipt.backfilledCount },
        { key: 'skipped', label: '来源映射未写入', children: receipt.sourceMappingSkipped },
      ]} />
      <Alert className="!mt-4" type="info" showIcon message="下一步核对已保存结构、映射与版本"
        description="创建未发布模型或执行加工。汇总/应用层的上游为模型，来源由分层映射表达；单列数据源映射未写入数量不代表已完成来源核验。" />
    </div> : <>
      {error && <Alert className="!mb-4" type="error" showIcon message={error} />}
      {!configChanged && !loading && preview?.existingModel && <Alert className="!mb-4" type="warning" showIcon message="同一过程在目标层已有模型"
        description={<div>{preview.existingModel.name} · {preview.existingModel.code}<YakButton className="!ml-3" size="small"
          onClick={() => leave(() => onOpenModel(preview.existingModel!.modelId, 'structure'))}>打开已有模型</YakButton></div>} />}
      {stage === 'design' ? <>
        <section className="mb-4 rounded-xl border border-[#e4e9f0] bg-white p-4">
          <div className="mb-3 text-[14px] font-semibold">上游与设计约定</div>
          <div className="flex flex-wrap items-end gap-4">
            {layer === 'DIM' && <label className="text-[12px] text-[#667085]">维度历史策略<Select className="!mt-1 block w-[220px]" aria-label="维度历史策略" value={config.scdType}
              options={[{ value: 'SCD1', label: 'SCD1 · 覆盖属性' }, { value: 'SCD2', label: 'SCD2 · 保留历史结构' }]} onChange={value => updateConfig({ scdType: value })} /></label>}
            {layer === 'ADS' && <label className="text-[12px] text-[#667085]">上游分层<Select className="!mt-1 block w-[180px]" aria-label="上游分层" value={config.upstreamLayer}
              options={[{ value: 'DWS', label: 'DWS 汇总模型' }, { value: 'DWD', label: 'DWD 明细模型' }]}
              onChange={value => updateConfig({ upstreamLayer: value, upstreamModelIds: undefined })} /></label>}
            {aggregate && <label className="text-[12px] text-[#667085]">统计周期<Select className="!mt-1 block w-[140px]" aria-label="统计周期" value={config.statPeriod}
              options={DERIVE_PERIODS} onChange={value => updateConfig({ statPeriod: value })} /></label>}
            {layer === 'ADS' && <>
              <label className="text-[12px] text-[#667085]">应用编码<Input className="!mt-1 block w-[200px]" aria-label="应用编码" value={config.appCode} maxLength={64} onChange={event => updateConfig({ appCode: event.target.value })} /></label>
              <label className="text-[12px] text-[#667085]">应用名称<Input className="!mt-1 block w-[200px]" aria-label="应用名称" value={config.appName} maxLength={128} onChange={event => updateConfig({ appName: event.target.value })} /></label>
            </>}
            <YakButton disabled={loading} onClick={refreshPreview}>读取上游与字段</YakButton>
          </div>
          {aggregate && preview?.upstreamModels && <div className="mt-4">
            <div className="mb-1 text-[12px] text-[#667085]">参与的上游模型（至少一个）</div>
            <Select mode="multiple" className="w-full" aria-label="参与的上游模型"
              disabled={loading || (layer === 'ADS' && preview.upstreamLayer !== config.upstreamLayer)}
              value={layer === 'ADS' && preview.upstreamLayer !== config.upstreamLayer ? [] : selectedDeriveUpstreams(config, preview)}
              options={preview.upstreamModels.map(model => ({ value: model.modelId, label: `${model.name}（${model.code}）· ${model.fieldCount} 字段` }))}
              onChange={value => updateConfig({ upstreamModelIds: value })} />
          </div>}
          {!aggregate && <p className="mb-0 mt-3 text-[12px] text-[#667085]">业务粒度：{grain || '尚未声明，请在业务过程和逻辑设计中核对'}</p>}
          {!configChanged && !loading && !!preview?.sources?.length && <div className="mt-3 flex flex-wrap gap-2">{preview.sources.map(source => <Tag
            key={source.bindingId} color={source.ready ? 'default' : 'warning'}>{source.sourceTable} · {source.tableRole} · {source.ready
              ? `${aggregate ? preview.upstreamLayer || '上游模型' : 'ODS'} #${source.odsModelId}`
              : aggregate ? '上游待准备' : 'ODS 待准备'}</Tag>)}</div>}
        </section>
        {configChanged && !loading && <Alert className="!mb-4" type="warning" showIcon message="请读取当前配置后继续设计"
          description="字段草稿来自上一次预览，当前配置尚未核对；重新读取前会提示替换已编辑字段。" />}
        {standardsError && <Alert className="!mb-4" type="warning" showIcon message="过程标准字段暂时无法读取；保留已继承引用，暂不能新增标准关联。" />}
        <Spin spinning={loading}>
          <section className="rounded-xl border border-[#e4e9f0] bg-white p-4">
            <div className="mb-3 flex flex-wrap items-center justify-between gap-2"><span className="text-[14px] font-semibold">字段设计</span>
              <span className="text-[12px] text-[#667085]">纳入 {fields.filter(field => field.include || field.technical).length} 字段 · 业务字段 {report.business.length}</span></div>
            <Table<DeriveFieldDraft> size="small" rowKey="key" columns={columns} dataSource={fields} scroll={{ x: aggregate ? 1350 : 1050 }}
              pagination={{ pageSize: 10, showSizeChanger: false, showTotal: total => `共 ${total} 个候选字段` }}
              locale={{ emptyText: loading ? '正在读取真实上游结构…' : '读取上游后在此确认纳入字段' }} />
          </section>
        </Spin>
      </> : <section className="rounded-xl border border-[#e4e9f0] bg-white p-5">
        <Typography.Title level={5} className="!mt-0">本次将创建的模型设计</Typography.Title>
        <Descriptions bordered size="small" column={2} items={[
          { key: 'name', label: '模型', children: `${base.name}（${base.code}）` },
          { key: 'layer', label: '目标', children: `${layer} · ${base.dialect}` },
          { key: 'process', label: '业务过程', children: processName || `#${base.processId}` },
          { key: 'columns', label: '结构', children: `${fields.filter(field => field.include || field.technical).length} 字段 / ${report.business.length} 业务字段` },
          ...(aggregate ? [{ key: 'groups', label: '分组字段', children: report.groupNames.join('、') || '无分组字段' },
            { key: 'period', label: '统计周期', children: DERIVE_PERIODS.find(item => item.value === config.statPeriod)?.label },
            { key: 'pk', label: '主键规则', children: layer === 'DWS' ? report.groupNames.join('、') || '不设主键' : 'ADS 默认不设主键' }] : []),
        ]} />
        <Table<DeriveFieldDraft> size="small" className="mt-4" rowKey="key" dataSource={fields.filter(field => field.include || field.technical)} pagination={{ pageSize: 10, showSizeChanger: false }}
          columns={[{ title: '目标字段', dataIndex: 'landingField' }, { title: '预览类型', dataIndex: 'dataType' },
            { title: '来源', render: (_, field) => field.technical || field.convention ? '目标结构约定' : `${field.sourceTable || ''}.${field.sourceColumn}` },
            { title: '角色 / 聚合', render: (_, field) => field.fieldRole === 'MEASURE' ? field.aggregateFunc : field.fieldRole === 'DIMENSION' ? '分组' : '—' }]} />
        <Alert className="!mt-4" type="info" showIcon message="预览是当前设计证据，提交时服务端会重新校验上游"
          description="本次命令创建模型、结构和相应映射；不冻结预览、不发布模型，也不执行 DDL 或加工 SQL。" />
      </section>}
      {!configChanged && !loading && !!report.errors.length && preview && <Alert className="!mt-4" type="warning" showIcon message="设计仍需处理"
        description={<ul className="m-0 pl-4">{report.errors.map(item => <li key={item}>{item}</li>)}</ul>} />}
      {!configChanged && !loading && preview && !!(report.warnings.length || preview.warnings?.length) && <Alert className="!mt-4" type="info" showIcon message="复核事项"
        description={<ul className="m-0 pl-4">{[...new Set([...report.warnings, ...(preview?.warnings || [])])].map(item => <li key={item}>{item}</li>)}</ul>} />}
    </>}
  </Drawer>;
}
