import type { ModelingCreatePayload, ModelingDerivePayload, ModelingDerivePreview } from '@/services/modeling/types';
import { CODE_PATTERN } from './structureRules';

export const DERIVE_LAYERS = ['DWD', 'DIM', 'DWS', 'ADS'];
export const DERIVE_FUNCTIONS = ['SUM', 'COUNT', 'COUNT_DISTINCT', 'MAX', 'MIN', 'AVG'];
export const DERIVE_PERIODS = [
  { value: '1h', label: '小时' }, { value: '1d', label: '日' }, { value: '1w', label: '周' },
  { value: '1m', label: '月' }, { value: 'ALL', label: '全量' },
];
export interface DeriveConfig {
  scdType: 'SCD1' | 'SCD2';
  statPeriod: string;
  appCode: string;
  appName: string;
  upstreamLayer: 'DWS' | 'DWD';
  /** undefined uses the Owner's default; [] is an explicit empty selection and must be rejected. */
  upstreamModelIds?: number[];
}
export type DeriveFieldDraft = ModelingDerivePreview['fields'][number] & {
  key: string;
  transformExpr: string;
}
export const deriveFieldDrafts = (preview: ModelingDerivePreview): DeriveFieldDraft[] =>
  preview.fields.map((field, index) => ({ ...field,
    key: JSON.stringify([field.sourceTable ?? null, field.sourceColumn, index]),
    include: field.technical || field.include, transformExpr: '',
  }));
export const isBusinessDeriveField = (field: DeriveFieldDraft) => !field.technical && !field.convention;
export const selectedDeriveUpstreams = (config: DeriveConfig, preview?: ModelingDerivePreview) =>
  config.upstreamModelIds ?? (preview?.upstreamModels ?? []).filter(item => item.selected).map(item => item.modelId);

export function inspectDeriveDesign(base: ModelingCreatePayload, config: DeriveConfig,
  preview: ModelingDerivePreview | undefined, fields: DeriveFieldDraft[]) {
  const errors: string[] = [];
  const warnings: string[] = [];
  const layer = base.layerCode || '';
  const aggregate = layer === 'DWS' || layer === 'ADS';
  if (!preview) errors.push('请先读取当前配置的上游与字段');
  if (!base.processId || !DERIVE_LAYERS.includes(layer)) errors.push('请选择正式业务过程和支持的目标分层');
  if (preview && (preview.processId !== base.processId || preview.layerCode !== layer)) errors.push('预览与当前业务过程或分层不一致，请重新读取');
  if (preview && !preview.supported) errors.push(preview.unsupportedReason || '当前目标暂不支持派生');
  if (layer === 'ADS' && preview?.supported && preview.upstreamLayer !== config.upstreamLayer) errors.push('上游分层与当前预览不一致，请重新读取');
  if (preview?.existingModel) errors.push(`当前过程在此层已有模型 ${preview.existingModel.code}，请打开已有模型继续设计`);
  if (preview?.sources.some(source => !source.ready && source.tableRole !== 'DIM')) errors.push('必要的过程来源尚未就绪，请先核对 ODS 模型');
  const upstreams = selectedDeriveUpstreams(config, preview);
  if (aggregate && !upstreams.length) errors.push('请至少选择一个上游模型');
  if (aggregate && preview && upstreams.some(id => !preview.upstreamModels?.some(model => model.modelId === id))) errors.push('所选上游不在当前预览中，请重新读取');
  if (aggregate && !DERIVE_PERIODS.some(item => item.value === config.statPeriod)) errors.push('请选择有效统计周期');
  if (layer === 'ADS' && (!config.appCode.trim() || !config.appName.trim())) errors.push('请填写应用编码与应用名称');
  if (layer === 'ADS' && (config.appCode.trim().length > 64 || config.appName.trim().length > 128)) errors.push('应用编码或名称超过允许长度');
  const selected = fields.filter(field => field.include || field.technical);
  const business = selected.filter(isBusinessDeriveField);
  if (!business.length) errors.push('请至少纳入一个业务字段');
  const names = new Set<string>();
  for (const field of selected) {
    const name = field.landingField.trim();
    if (!CODE_PATTERN.test(name)) errors.push(`目标字段 ${name || field.sourceColumn} 的名称不合法`);
    if (names.has(name.toLowerCase())) errors.push(`目标字段 ${name} 重复，请改名或排除其中一项`);
    names.add(name.toLowerCase());
    if (field.transformExpr.length > 1024) errors.push(`字段 ${name} 的表达式超过 1024 字符`);
    if (aggregate && isBusinessDeriveField(field)) {
      if (!['DIMENSION', 'MEASURE'].includes(field.fieldRole || '')) errors.push(`字段 ${name} 需要确认分组或度量角色`);
      if (field.fieldRole === 'MEASURE' && !DERIVE_FUNCTIONS.includes(field.aggregateFunc || '')) errors.push(`度量 ${name} 需要选择聚合函数`);
    }
  }
  if (business.some(field => !field.stdFieldId)) warnings.push('部分业务字段未关联标准字段，创建后仍需核对治理覆盖。');
  if (aggregate && !business.some(field => field.fieldRole === 'DIMENSION')) warnings.push(`当前没有分组字段；${layer === 'DWS' ? 'DWS 将不设置分组主键' : 'ADS 默认不设主键'}，请确认全局汇总用途。`);
  if (aggregate) warnings.push('统计周期保存为设计约定；日期分桶、过滤和重算需要在数据开发中实现。');
  if (aggregate && upstreams.length > 1) warnings.push('选择多个上游只记录来源；连接键与 JOIN 逻辑需要在数据开发中确认。');
  if (business.some(field => ['AVG', 'COUNT_DISTINCT'].includes(field.aggregateFunc || ''))) warnings.push('均值和去重计数需核对跨分组、跨周期的可加性，后续不能直接累加。');
  if (layer === 'ADS' && config.upstreamLayer === 'DWS') warnings.push('应用层再次聚合汇总结果时，需核对上游粒度与可加性。');
  if (layer === 'DIM' && config.scdType === 'SCD2') warnings.push('SCD2 在本次创建中补充结构约定列；拉链加载和历史维护仍需数据开发实现。');
  return { errors: [...new Set(errors)], warnings, business,
    groupNames: aggregate ? business.filter(field => field.fieldRole === 'DIMENSION').map(field => field.landingField.trim()) : [],
  };
}

export function buildDerivePayload(base: ModelingCreatePayload, config: DeriveConfig,
  preview: ModelingDerivePreview, fields: DeriveFieldDraft[]): ModelingDerivePayload {
  const report = inspectDeriveDesign(base, config, preview, fields);
  if (report.errors.length) throw new Error(report.errors[0]);
  const aggregate = base.layerCode === 'DWS' || base.layerCode === 'ADS';
  return {
    processId: base.processId!, layerCode: base.layerCode!, code: base.code, name: base.name,
    dialect: base.dialect, description: base.description, directoryId: base.directoryId,
    ...(base.layerCode === 'DIM' ? { scdType: config.scdType } : {}),
    ...(aggregate ? { upstreamModelIds: selectedDeriveUpstreams(config, preview), statPeriod: config.statPeriod } : {}),
    ...(base.layerCode === 'ADS' ? { appCode: config.appCode.trim(), appName: config.appName.trim(), upstreamLayer: config.upstreamLayer } : {}),
    fields: fields.map(field => ({
      sourceTable: field.sourceTable, sourceColumn: field.sourceColumn, landingField: field.landingField.trim(),
      stdFieldId: field.stdFieldId, transformExpr: field.transformExpr.trim() || undefined,
      include: field.technical || field.include, technical: field.technical,
      ...(aggregate && isBusinessDeriveField(field) ? { fieldRole: field.fieldRole || undefined,
        aggregateFunc: field.fieldRole === 'MEASURE' ? field.aggregateFunc || undefined : undefined } : {}),
    })),
  };
}
