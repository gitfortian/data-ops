/** Context is a source selection only; the backend resolves all facts and permissions. */
import { consumerVersionSourcePath, readConsumerVersionImpactTarget, type ConsumerVersionImpactTarget } from './consumerVersionImpact';
import { modelStructureReviewSourcePath, readModelStructureReviewTarget, type ModelStructureReviewTarget } from './modelStructureReview';
export interface StandardMatchTarget { modelId: number; columnName: string; dataType: string; businessDescription: string; keyword: string }
export interface ModelMappingTarget { modelId: number; columnName: string; datasourceId: number; database: string; table: string; businessDescription: string; keyword: string }
export interface MetricExplanationTarget { metricId: number; version: number; businessQuestion: string; view?: 'SNAPSHOT' }
export interface MetricDraftTarget { metricId: number | null; version: number | null; metricType: 'ATOMIC' | 'DERIVED' | 'COMPOSITE'; modelId: number | null; upstreamIds: number[]; requirement: string }
export interface MetricChangeReviewTarget { metricId: number; version: number; publishedVersion: number; publicationEventId: number; definition: string; businessQuestion: string }
type ExistingGovernanceTarget = { assetId: number; qualityExecutionNo?: never; qualityBaselineExecutionNo?: never; qualityMonitorId?: never; purpose?: 'ASSET_DESCRIPTION' | 'ASSET_IMPACT'; standardMatch?: never; modelMapping?: never; metricExplanation?: never; metricDraft?: never }
  | { assetId?: never; qualityExecutionNo: string; qualityBaselineExecutionNo?: string; qualityMonitorId?: never; purpose?: never; standardMatch?: never; modelMapping?: never; metricExplanation?: never; metricDraft?: never }
  | { assetId?: never; qualityExecutionNo?: never; qualityBaselineExecutionNo?: never; qualityMonitorId: number; purpose: 'QUALITY_RULES'; standardMatch?: never; modelMapping?: never; metricExplanation?: never; metricDraft?: never }
  | { assetId?: never; qualityExecutionNo?: never; qualityBaselineExecutionNo?: never; qualityMonitorId?: never; purpose: 'STANDARD_MATCH'; standardMatch: StandardMatchTarget; modelMapping?: never; metricExplanation?: never; metricDraft?: never }
  | { assetId?: never; qualityExecutionNo?: never; qualityBaselineExecutionNo?: never; qualityMonitorId?: never; standardMatch?: never; purpose: 'MODEL_MAPPING'; modelMapping: ModelMappingTarget; metricExplanation?: never; metricDraft?: never }
  | { assetId?: never; qualityExecutionNo?: never; qualityBaselineExecutionNo?: never; qualityMonitorId?: never; standardMatch?: never; modelMapping?: never; purpose: 'METRIC_EXPLANATION'; metricExplanation: MetricExplanationTarget; metricDraft?: never }
  | { assetId?: never; qualityExecutionNo?: never; qualityBaselineExecutionNo?: never; qualityMonitorId?: never; standardMatch?: never; modelMapping?: never; metricExplanation?: never; purpose: 'METRIC_DRAFT'; metricDraft: MetricDraftTarget };

export type GovernanceTarget = (ExistingGovernanceTarget & { metricChangeReview?: never; consumerVersionImpact?: never; modelStructureReview?: never })
  | { purpose: 'METRIC_CHANGE_REVIEW'; metricChangeReview: MetricChangeReviewTarget; assetId?: never; qualityExecutionNo?: never; qualityBaselineExecutionNo?: never;
      qualityMonitorId?: never; standardMatch?: never; modelMapping?: never; metricExplanation?: never; metricDraft?: never; consumerVersionImpact?: never; modelStructureReview?: never }
  | { purpose: 'CONSUMER_VERSION_IMPACT'; consumerVersionImpact: ConsumerVersionImpactTarget; assetId?: never; qualityExecutionNo?: never;
      qualityBaselineExecutionNo?: never; qualityMonitorId?: never; standardMatch?: never; modelMapping?: never; metricExplanation?: never;
      metricDraft?: never; metricChangeReview?: never; modelStructureReview?: never }
  | { purpose: 'MODEL_STRUCTURE_REVIEW'; modelStructureReview: ModelStructureReviewTarget; assetId?: never; qualityExecutionNo?: never;
      qualityBaselineExecutionNo?: never; qualityMonitorId?: never; standardMatch?: never; modelMapping?: never; metricExplanation?: never;
      metricDraft?: never; metricChangeReview?: never; consumerVersionImpact?: never };

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
  if (target.modelStructureReview) return `模型 #${target.modelStructureReview.modelId} 发布 V${target.modelStructureReview.baselineVersionNo} → 已保存结构变更核对`;
  if (target.consumerVersionImpact) return `${target.consumerVersionImpact.productType}:${target.consumerVersionImpact.productIdentity} 来源版本 ${target.consumerVersionImpact.sourceVersionIdentity} 已知消费影响`;
  if (target.metricChangeReview) return `指标 #${target.metricChangeReview.metricId} 发布 v${target.metricChangeReview.publishedVersion} → 草稿 v${target.metricChangeReview.version} 变更核对`;
  if (target.metricDraft) return `${target.metricDraft.metricId ? `指标 #${target.metricDraft.metricId} v${target.metricDraft.version}` : '新建指标'} 定义草稿`;
  if (target.metricExplanation) return `指标 #${target.metricExplanation.metricId} v${target.metricExplanation.version} ${target.metricExplanation.view === 'SNAPSHOT' ? '历史快照' : '口径解释与说明草稿'}`;
  if (target.modelMapping) return `模型 #${target.modelMapping.modelId} 字段 ${target.modelMapping.columnName} 来源映射建议`;
  if (target.standardMatch) return `模型 #${target.standardMatch.modelId} 字段 ${target.standardMatch.columnName} 类型标准匹配`;
  if (target.qualityMonitorId !== undefined) return `质量监控 #${target.qualityMonitorId} 规则建议`;
  if (target.assetId !== undefined) return `资产 #${target.assetId} ${target.purpose === 'ASSET_DESCRIPTION' ? '描述候选' : target.purpose === 'ASSET_IMPACT' ? '有限影响说明' : '治理解读'}`;
  if (target.qualityBaselineExecutionNo) return `质量执行 ${target.qualityBaselineExecutionNo} → ${target.qualityExecutionNo} 历史比较`;
  return `质量执行 ${target.qualityExecutionNo} 解读与排查`;
}

export function governanceEntryPath(target: GovernanceTarget): string {
  if (target.modelStructureReview) {
    const value = target.modelStructureReview;
    return `/ai-agent?purpose=MODEL_STRUCTURE_REVIEW&reviewModelId=${value.modelId}&reviewBaselineVersionNo=${value.baselineVersionNo}&reviewDefinition=${value.definition}`;
  }
  if (target.consumerVersionImpact) {
    const value = target.consumerVersionImpact;
    return `/ai-agent?purpose=CONSUMER_VERSION_IMPACT&consumerProductType=${value.productType}&consumerProductIdentity=${value.productIdentity}&consumerVersionIdentity=${value.sourceVersionIdentity}`;
  }
  if (target.standardMatch || target.modelMapping || target.metricExplanation || target.metricDraft || target.metricChangeReview) return '/ai-agent';
  if (target.qualityMonitorId !== undefined) return `/ai-agent?qualityMonitorId=${target.qualityMonitorId}`;
  const query = target.assetId !== undefined
    ? `assetId=${target.assetId}${target.purpose ? `&purpose=${target.purpose}` : ''}`
    : `qualityExecutionNo=${encodeURIComponent(target.qualityExecutionNo)}`;
  return `/ai-agent?${query}${target.qualityBaselineExecutionNo ? `&qualityBaselineExecutionNo=${encodeURIComponent(target.qualityBaselineExecutionNo)}` : ''}`;
}

export function parseGovernanceTarget(search: string): GovernanceTarget | null {
  const params = new URLSearchParams(search);
  const reviewKeys = ['reviewModelId', 'reviewBaselineVersionNo', 'reviewDefinition'];
  const consumerKeys = ['consumerProductType', 'consumerProductIdentity', 'consumerVersionIdentity'];
  if (['assetId', 'qualityExecutionNo', 'qualityMonitorId', 'qualityBaselineExecutionNo', 'purpose', ...consumerKeys, ...reviewKeys].some((key) => params.getAll(key).length > 1)) return null;
  const purpose = params.get('purpose');
  if (purpose === 'MODEL_STRUCTURE_REVIEW' || reviewKeys.some((key) => params.has(key))) {
    if (purpose !== 'MODEL_STRUCTURE_REVIEW' || ['assetId', 'qualityExecutionNo', 'qualityMonitorId', 'qualityBaselineExecutionNo', ...consumerKeys].some((key) => params.has(key))) return null;
    const version = params.get('reviewBaselineVersionNo');
    if (!version || !/^[1-9][0-9]{0,9}$/.test(version)) return null;
    try { return { purpose, modelStructureReview: readModelStructureReviewTarget({ modelId: params.get('reviewModelId'),
      baselineVersionNo: Number(version), definition: params.get('reviewDefinition') }) }; }
    catch { return null; }
  }
  if (purpose === 'CONSUMER_VERSION_IMPACT'  || consumerKeys.some((key) => params.has(key))) {
    if (purpose !== 'CONSUMER_VERSION_IMPACT' || ['assetId', 'qualityExecutionNo', 'qualityMonitorId', 'qualityBaselineExecutionNo'].some((key) => params.has(key))) return null;
    try { return { purpose, consumerVersionImpact: readConsumerVersionImpactTarget({ productType: params.get('consumerProductType'),
      productIdentity: params.get('consumerProductIdentity'), sourceVersionIdentity: params.get('consumerVersionIdentity') }) }; }
    catch { return null; }
  }
  const baseline = params.get('qualityBaselineExecutionNo');
  const asset = params.get('assetId');
  const execution = params.get('qualityExecutionNo');
  const monitor = params.get('qualityMonitorId');
  if (purpose !== null && (!asset || execution !== null || monitor !== null || baseline !== null || !['ASSET_IMPACT', 'ASSET_DESCRIPTION'].includes(purpose))) return null;
  if (baseline !== null && (!execution || asset !== null || monitor !== null || !/^[A-Za-z0-9_-]{1,128}$/.test(baseline) || baseline === execution)) return null;
  if (monitor) {
    return !asset && !execution && /^[1-9]\d*$/.test(monitor) && Number.isSafeInteger(Number(monitor))
      ? { qualityMonitorId: Number(monitor), purpose: 'QUALITY_RULES' } : null;
  }
  if (!!asset === !!execution) return null;
  if (asset && /^[1-9]\d*$/.test(asset) && Number.isSafeInteger(Number(asset))) {
    return purpose === 'ASSET_IMPACT' || purpose === 'ASSET_DESCRIPTION' ? { assetId: Number(asset), purpose } : { assetId: Number(asset) };
  }
  if (execution && /^[A-Za-z0-9_-]{1,128}$/.test(execution)) return { qualityExecutionNo: execution, ...(baseline ? { qualityBaselineExecutionNo: baseline } : {}) };
  return null;
}

export function governanceSourcePath(target: GovernanceTarget): string {
  if (target.modelStructureReview) return modelStructureReviewSourcePath(target.modelStructureReview);
  if (target.consumerVersionImpact) return consumerVersionSourcePath(target.consumerVersionImpact);
  if (target.metricChangeReview) return `/metric/manage/${target.metricChangeReview.metricId}`;
  if (target.metricDraft) return target.metricDraft.metricId ? `/metric/manage/${target.metricDraft.metricId}` : '/metric/manage';
  if (target.metricExplanation) return `/metric/manage/${target.metricExplanation.metricId}`;
  if (target.modelMapping) return `/modeling/models/${target.modelMapping.modelId}/mapping`;
  if (target.standardMatch) return `/modeling/models/${target.standardMatch.modelId}`;
  if (target.qualityMonitorId !== undefined) return `/data-quality/monitor/${target.qualityMonitorId}`;
  return target.assetId !== undefined ? `/data-asset/detail/${target.assetId}`
    : `/data-quality/execution/${encodeURIComponent(target.qualityExecutionNo)}`;
}

export function governanceQuestions(target: GovernanceTarget): string[] {
  if (target.modelStructureReview) return ['解释固定发布基准与已保存结构的白名单差异，核验本轮事实；分别列出当前映射检查项、未覆盖范围和原页面人工下一步，不推断历史映射变化或类型兼容，不判断允许发布。'];
  if (target.consumerVersionImpact) return ['说明固定产品和精确来源版本的归属、有效声明与该版本成功使用；核验本轮事实并分开窗口范围和缺口，给出人工兼容性核对项，不判断完整影响或发布安全。'];
  if (target.qualityBaselineExecutionNo) return ['比较选定的基准执行与本次执行，核验各侧事实并按稳定规则 ID 对齐；单列历史定义变化、截断和缺口，给出人工核对步骤，不直接认定质量改善或根因。'];
  if (target.metricChangeReview) return ['解释固定发布版本与已保存草稿的差异，引用事实并列出人工检查步骤；保留验证和关系证据缺口。'];
  if (target.metricDraft) return ['根据绑定需求与依赖准备指标定义草稿；缺少业务信息时列出待确认问题。'];
  if (target.metricExplanation) return ['依据固定已保存版本解释指标口径与事实引用，生成业务说明草稿；缺依据时列出待确认项。'];
  if (target.modelMapping) return ['为绑定目标字段推荐所选源表中的字段；说明业务依据、类型差异和待确认项。'];
  if (target.standardMatch) return ['根据绑定的未保存字段草稿匹配类型标准；信息不足时列出待确认项。'];
  if (target.qualityMonitorId !== undefined) return ['根据当前字段与模板给出质量规则候选；缺业务阈值请先确认。'];
  if (target.purpose === 'ASSET_IMPACT') return ['解释所选资产的一跳结构关系、已接入的业务使用和页面活动，分别核验事实并保留状态、范围与缺口，给出人工核对步骤；不判断完整影响或发布安全。'];
  if (target.purpose === 'ASSET_DESCRIPTION') return ['根据当前资产与字段证据给出资产描述候选；缺业务背景请先确认。'];
  return target.assetId !== undefined
    ? ['解释这个资产的含义、负责人和治理状态，并引用证据。', '这个资产有哪些已证实的治理问题？区分缺失证据和待验证假设。']
    : ['请围绕本次质量执行，区分未通过、执行异常和未执行规则，核验关键状态与实际值/期望值；按本次事实、关注规则、待验证假设与缺口、人工检查步骤、源页面下一步给出排查指引。', '针对本次质量排查，还缺哪些证据或业务背景？请区分源字段缺失与需要我补充的信息，必要时先问我；不要确认尚未验证的根因。'];
}
