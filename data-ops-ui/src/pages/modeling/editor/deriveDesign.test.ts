import type { ModelingDerivePreview } from '@/services/modeling/types';
import { buildDerivePayload, deriveFieldDrafts, inspectDeriveDesign } from './deriveDesign';
import type { DeriveConfig } from './deriveDesign';

const base = { name: '每日交易汇总', code: 'dws_trade_1d', processId: 7, layerCode: 'DWS', dialect: 'MYSQL', domainId: 10 };
const config: DeriveConfig = { scdType: 'SCD1', statPeriod: '1d', appCode: '', appName: '', upstreamLayer: 'DWS' };
const source = (column: string, extra: Partial<ModelingDerivePreview['fields'][number]> = {}): ModelingDerivePreview['fields'][number] => ({
  sourceTable: 'dwd_order', sourceColumn: column, landingField: column, tableRole: 'MAIN',
  dataType: 'BIGINT', include: true, technical: false, convention: false, conflicting: false,
  fieldRole: 'DIMENSION', stdFieldId: 1, ...extra,
});
const preview = (extra: Partial<ModelingDerivePreview> = {}): ModelingDerivePreview => ({
  processId: 7, layerCode: 'DWS', upstreamLayer: 'DWD', supported: true, mode: 'AGGREGATE', sources: [],
  upstreamModels: [{ modelId: 20, code: 'dwd_order', name: '订单', layerCode: 'DWD', selected: true, fieldCount: 2 },
    { modelId: 21, code: 'dwd_other', name: '其他', layerCode: 'DWD', selected: false, fieldCount: 1 }],
  fields: [source('customer_id'), source('amount', { fieldRole: 'MEASURE', aggregateFunc: 'SUM' }),
    source('process_time', { technical: true, sourceTable: undefined, stdFieldId: undefined, fieldRole: null })],
  totalFields: 2, matchedFields: 2, unmatchedFields: 0, conflictFields: 0, technicalFields: 1, conventionFields: 0, governanceRate: 100, warnings: [], ...extra,
});

it('serializes the confirmed Owner selection and strips UI/type/domain metadata from the existing command', () => {
  const evidence = preview();
  const rows = deriveFieldDrafts(evidence);
  rows[0].aggregateFunc = 'SUM'; // A stale measure function must not survive a dimension role.
  const request = buildDerivePayload(base, config, evidence, rows);
  expect(request.upstreamModelIds).toEqual([20]);
  expect(request).not.toHaveProperty('domainId');
  expect(request.fields[0]).not.toHaveProperty('key');
  expect(request.fields[0]).not.toHaveProperty('dataType');
  expect(request.fields[0].aggregateFunc).toBeUndefined();
  expect(request.fields[1]).toMatchObject({ sourceTable: 'dwd_order', sourceColumn: 'amount', fieldRole: 'MEASURE', aggregateFunc: 'SUM', stdFieldId: 1 });
  expect(request.fields[2].fieldRole).toBeUndefined();
  expect(inspectDeriveDesign(base, config, evidence, rows).groupNames).toEqual(['customer_id']);
});

it('rejects explicit empty or unknown upstream IDs instead of falling back to all models', () => {
  const evidence = preview();
  expect(() => buildDerivePayload(base, { ...config, upstreamModelIds: [] }, evidence, deriveFieldDrafts(evidence))).toThrow('至少选择一个上游');
  expect(() => buildDerivePayload(base, { ...config, upstreamModelIds: [999] }, evidence, deriveFieldDrafts(evidence))).toThrow('不在当前预览');
});

it('rejects case-insensitive duplicate aliases, invalid measure functions and technical name collisions', () => {
  const evidence = preview();
  const rows = deriveFieldDrafts(evidence);
  rows[1].landingField = 'CUSTOMER_ID';
  expect(() => buildDerivePayload(base, config, evidence, rows)).toThrow('重复');
  rows[1].landingField = 'amount';
  rows[1].aggregateFunc = null;
  expect(() => buildDerivePayload(base, config, evidence, rows)).toThrow('聚合函数');
  rows[1].aggregateFunc = 'SUM';
  rows[0].landingField = 'process_time';
  rows[2].include = false;
  expect(() => buildDerivePayload(base, config, evidence, rows)).toThrow('重复');
});

it('keeps conflict and required ODS readiness as blockers', () => {
  const conflict = preview({ existingModel: { modelId: 9, code: 'existing', name: '已有' } });
  expect(() => buildDerivePayload(base, config, conflict, deriveFieldDrafts(conflict))).toThrow('已有模型');
  const unavailable = preview({ sources: [{ bindingId: 1, datasourceId: 2, sourceTable: 'orders', tableRole: 'MAIN', ready: false, fieldCount: 0 }] });
  expect(() => buildDerivePayload(base, config, unavailable, deriveFieldDrafts(unavailable))).toThrow('来源尚未就绪');
});

it('requires a fresh ADS upstream layer and trims the application binding', () => {
  const adsBase = { ...base, layerCode: 'ADS' };
  const evidence = preview({ layerCode: 'ADS', upstreamLayer: 'DWS', mode: 'APPLICATION' });
  const adsConfig: DeriveConfig = { ...config, appCode: '  ops  ', appName: '  经营看板  ' };
  expect(() => buildDerivePayload(adsBase, { ...adsConfig, upstreamLayer: 'DWD' }, evidence, deriveFieldDrafts(evidence))).toThrow('上游分层');
  const request = buildDerivePayload(adsBase, adsConfig, evidence, deriveFieldDrafts(evidence));
  expect(request).toMatchObject({ appCode: 'ops', appName: '经营看板', upstreamLayer: 'DWS' });
});

it('keeps DIM SCD conventions separate from business fields and warns about actual history loading', () => {
  const dimBase = { ...base, layerCode: 'DIM' };
  const evidence = preview({ layerCode: 'DIM', mode: 'INHERIT', upstreamLayer: 'ODS',
    fields: [source('customer_id'), source('dim_customer_sk', { convention: true, stdFieldId: undefined, sourceTable: undefined })] });
  const dimConfig: DeriveConfig = { ...config, scdType: 'SCD2' };
  const rows = deriveFieldDrafts(evidence);
  const report = inspectDeriveDesign(dimBase, dimConfig, evidence, rows);
  expect(report.business).toHaveLength(1);
  expect(report.warnings.join(' ')).toContain('拉链加载');
  expect(buildDerivePayload(dimBase, dimConfig, evidence, rows)).toMatchObject({ scdType: 'SCD2' });
  expect(buildDerivePayload(dimBase, dimConfig, evidence, rows)).not.toHaveProperty('upstreamModelIds');
});

it('does not turn AVG, COUNT_DISTINCT or multi-upstream design into an execution claim', () => {
  const evidence = preview();
  const rows = deriveFieldDrafts(evidence);
  rows[1].aggregateFunc = 'COUNT_DISTINCT';
  const report = inspectDeriveDesign(base, { ...config, upstreamModelIds: [20, 21] }, evidence, rows);
  expect(report.errors).toEqual([]);
  expect(report.warnings.join(' ')).toContain('可加性');
  expect(report.warnings.join(' ')).toContain('JOIN');
  expect(report.warnings.join(' ')).toContain('日期分桶');
});
