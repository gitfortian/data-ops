/** Context is a source selection only; the backend resolves all facts and permissions. */
export interface StandardMatchTarget { modelId: number; columnName: string; dataType: string; businessDescription: string; keyword: string }
export interface ModelMappingTarget { modelId: number; columnName: string; datasourceId: number; database: string; table: string; businessDescription: string; keyword: string }
export interface MetricExplanationTarget { metricId: number; version: number; businessQuestion: string; view?: 'SNAPSHOT' }
export interface MetricDraftTarget { metricId: number | null; version: number | null; metricType: 'ATOMIC' | 'DERIVED' | 'COMPOSITE'; modelId: number | null; upstreamIds: number[]; requirement: string }
export type GovernanceTarget = { assetId: number; qualityExecutionNo?: never; qualityMonitorId?: never; purpose?: 'ASSET_DESCRIPTION'; standardMatch?: never; modelMapping?: never; metricExplanation?: never; metricDraft?: never }
  | { assetId?: never; qualityExecutionNo: string; qualityMonitorId?: never; purpose?: never; standardMatch?: never; modelMapping?: never; metricExplanation?: never; metricDraft?: never }
  | { assetId?: never; qualityExecutionNo?: never; qualityMonitorId: number; purpose: 'QUALITY_RULES'; standardMatch?: never; modelMapping?: never; metricExplanation?: never; metricDraft?: never }
  | { assetId?: never; qualityExecutionNo?: never; qualityMonitorId?: never; purpose: 'STANDARD_MATCH'; standardMatch: StandardMatchTarget; modelMapping?: never; metricExplanation?: never; metricDraft?: never }
  | { assetId?: never; qualityExecutionNo?: never; qualityMonitorId?: never; standardMatch?: never; purpose: 'MODEL_MAPPING'; modelMapping: ModelMappingTarget; metricExplanation?: never; metricDraft?: never }
  | { assetId?: never; qualityExecutionNo?: never; qualityMonitorId?: never; standardMatch?: never; modelMapping?: never; purpose: 'METRIC_EXPLANATION'; metricExplanation: MetricExplanationTarget; metricDraft?: never }
  | { assetId?: never; qualityExecutionNo?: never; qualityMonitorId?: never; standardMatch?: never; modelMapping?: never; metricExplanation?: never; purpose: 'METRIC_DRAFT'; metricDraft: MetricDraftTarget };

/** Compare the full task scope, independent of JSON key order and nullable projection fields. */
export function sameScenarioTarget(a: unknown, b: unknown): boolean {
  const canonical = (value: unknown): unknown => {
    if (Array.isArray(value)) return value.map(canonical);
    if (value && typeof value === 'object') return Object.fromEntries(Object.entries(value).filter(([, v]) => v != null)
      .sort(([left], [right]) => left.localeCompare(right)).map(([key, v]) => [key, canonical(v)]));
    return value;
  };
  return JSON.stringify(canonical(a)) === JSON.stringify(canonical(b));
}

export function governanceTaskTitle(target: GovernanceTarget): string {
  if (target.metricDraft) return `${target.metricDraft.metricId ? `指标 #${target.metricDraft.metricId} v${target.metricDraft.version}` : '新建指标'} 定义草稿`;
  if (target.metricExplanation) return `指标 #${target.metricExplanation.metricId} v${target.metricExplanation.version} ${target.metricExplanation.view === 'SNAPSHOT' ? '历史快照' : '口径解释与说明草稿'}`;
  if (target.modelMapping) return `模型 #${target.modelMapping.modelId} 字段 ${target.modelMapping.columnName} 来源映射建议`;
  if (target.standardMatch) return `模型 #${target.standardMatch.modelId} 字段 ${target.standardMatch.columnName} 类型标准匹配`;
  if (target.qualityMonitorId !== undefined) return `质量监控 #${target.qualityMonitorId} 规则建议`;
  if (target.assetId !== undefined) return `资产 #${target.assetId} ${target.purpose === 'ASSET_DESCRIPTION' ? '描述候选' : '治理解读'}`;
  return `质量执行 ${target.qualityExecutionNo} 解读与排查`;
}

export function governanceEntryPath(target: GovernanceTarget): string {
  if (target.standardMatch || target.modelMapping || target.metricExplanation || target.metricDraft) return '/ai-agent';
  if (target.qualityMonitorId !== undefined) return `/ai-agent?qualityMonitorId=${target.qualityMonitorId}`;
  const query = target.assetId !== undefined
    ? `assetId=${target.assetId}`
    : `qualityExecutionNo=${encodeURIComponent(target.qualityExecutionNo)}`;
  return `/ai-agent?${query}`;
}

export function parseGovernanceTarget(search: string): GovernanceTarget | null {
  const params = new URLSearchParams(search);
  const asset = params.get('assetId');
  const execution = params.get('qualityExecutionNo');
  const monitor = params.get('qualityMonitorId');
  if (monitor) {
    return !asset && !execution && /^[1-9]\d*$/.test(monitor) && Number.isSafeInteger(Number(monitor))
      ? { qualityMonitorId: Number(monitor), purpose: 'QUALITY_RULES' } : null;
  }
  if (!!asset === !!execution) return null;
  if (asset && /^[1-9]\d*$/.test(asset) && Number.isSafeInteger(Number(asset))) {
    return { assetId: Number(asset) };
  }
  if (execution && /^[A-Za-z0-9_-]{1,128}$/.test(execution)) return { qualityExecutionNo: execution };
  return null;
}

export function governanceSourcePath(target: GovernanceTarget): string {
  if (target.metricDraft) return target.metricDraft.metricId ? `/metric/manage/${target.metricDraft.metricId}` : '/metric/manage';
  if (target.metricExplanation) return `/metric/manage/${target.metricExplanation.metricId}`;
  if (target.modelMapping) return `/modeling/models/${target.modelMapping.modelId}/mapping`;
  if (target.standardMatch) return `/modeling/models/${target.standardMatch.modelId}`;
  if (target.qualityMonitorId !== undefined) return `/data-quality/monitor/${target.qualityMonitorId}`;
  return target.assetId !== undefined ? `/data-asset/detail/${target.assetId}`
    : `/data-quality/execution/${encodeURIComponent(target.qualityExecutionNo)}`;
}

export function governanceQuestions(target: GovernanceTarget): string[] {
  if (target.metricDraft) return ['根据绑定需求与依赖准备指标定义草稿；缺少业务信息时列出待确认问题。'];
  if (target.metricExplanation) return ['依据固定已保存版本解释指标口径与事实引用，生成业务说明草稿；缺依据时列出待确认项。'];
  if (target.modelMapping) return ['为绑定目标字段推荐所选源表中的字段；说明业务依据、类型差异和待确认项。'];
  if (target.standardMatch) return ['根据绑定的未保存字段草稿匹配类型标准；信息不足时列出待确认项。'];
  if (target.qualityMonitorId !== undefined) return ['根据当前字段与模板给出质量规则候选；缺业务阈值请先确认。'];
  if (target.purpose === 'ASSET_DESCRIPTION') return ['根据当前资产与字段证据给出资产描述候选；缺业务背景请先确认。'];
  return target.assetId !== undefined
    ? ['解释这个资产的含义、负责人和治理状态，并引用证据。', '这个资产有哪些已证实的治理问题？区分缺失证据和待验证假设。']
    : ['请围绕本次质量执行，区分未通过、执行异常和未执行规则，核验关键状态与实际值/期望值；按本次事实、关注规则、待验证假设与缺口、人工检查步骤、源页面下一步给出排查指引。', '针对本次质量排查，还缺哪些证据或业务背景？请区分源字段缺失与需要我补充的信息，必要时先问我；不要确认尚未验证的根因。'];
}
