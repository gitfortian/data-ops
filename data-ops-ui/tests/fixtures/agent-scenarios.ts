import type { GovernanceTarget, MetricDraftTarget } from '@/services/agent/governance';
import type { SessionContinuation } from '@/services/agent/continuation';
import type { ScenarioSuggestion } from '@/services/agent/scenarioHistory';

const base = { expectedDefinition: 'a'.repeat(64), skillHash: 'b'.repeat(64), skillVersion: 1, truncated: false, questions: ['请核对业务范围'] };
export const receiptText = (value: ScenarioSuggestion) => `生成结果\n\`\`\`yak-${value.kind.toLowerCase().replaceAll('_', '-')}\n${JSON.stringify(value)}\n\`\`\`\n请返回原页面核对`;
export function scenario(kind: ScenarioSuggestion['kind'] = 'STANDARD_MATCH', metricType: MetricDraftTarget['metricType'] = 'ATOMIC', snapshot = false) {
  let value: ScenarioSuggestion;
  let target: GovernanceTarget;
  switch (kind) {
    case 'STANDARD_MATCH': {
      const field = { modelId: 7, columnName: 'customer_id', dataType: 'BIGINT', businessDescription: '客户标识', keyword: '' };
      target = { purpose: kind, standardMatch: field };
      value = { ...base, kind, target: field, fieldDescription: '客户的唯一标识',
        candidates: [{ standardId: 9, version: 2, code: 'customer_id', name: '客户标识标准', stdType: 'BIGINT', reason: '含义和类型一致' }] };
      break;
    }
    case 'MODEL_MAPPING': {
      const field = { modelId: 7, columnName: 'customer_id', datasourceId: 3, database: 'crm', table: 'customer', businessDescription: '客户标识', keyword: '' };
      target = { purpose: kind, modelMapping: field };
      value = { ...base, kind, target: field, sourceDefinition: 'c'.repeat(64), targetType: 'BIGINT',
        candidates: [{ sourceColumn: 'id', type: 'BIGINT', nullable: false, reason: '来源客户主键' }] };
      break;
    }
    case 'METRIC_EXPLANATION': {
      const metric = { metricId: 7, version: 3, businessQuestion: '', ...(snapshot ? { view: 'SNAPSHOT' as const } : {}) };
      target = { purpose: kind, metricExplanation: metric };
      value = { ...base, truncated: false, kind, target: metric, candidates: [{ businessDescription: '每日客户数',
        statements: [{ text: '按日统计客户', evidence: [{ key: 'period', label: '周期', value: 'DAY' }] }] }] };
      break;
    }
    case 'METRIC_DRAFT': {
      const metric: MetricDraftTarget = { metricId: null, version: null, metricType, modelId: metricType === 'ATOMIC' ? 7 : null,
        upstreamIds: metricType === 'ATOMIC' ? [] : [9], requirement: '每日客户数' };
      target = { purpose: kind, metricDraft: metric };
      value = { ...base, truncated: false, kind, target: metric,
        source: { definition: base.expectedDefinition, fields: metricType === 'ATOMIC' ? [{ name: 'id', type: 'BIGINT', description: '客户' }] : [],
          upstream: metricType === 'ATOMIC' ? [] : [{ id: 9, version: 2, code: 'customers', name: '客户基数', type: 'ATOMIC', measure: 'COUNT(id)', filter: '' }] },
        candidates: [{ name: '客户数草稿', description: '每日客户数', period: 'DAY', aggregation: metricType === 'ATOMIC' ? 'COUNT_DISTINCT' : null,
          field: metricType === 'ATOMIC' ? 'id' : null, qualifiers: [], tokens: metricType === 'COMPOSITE' ? [{ operator: 'REF', metricId: 9 }] : [] }] };
      break;
    }
  }
  const continuation: SessionContinuation = { sessionId: 's1', turnId: 't1', status: 'COMPLETED', governanceTarget: target };
  return { value, target, continuation, text: receiptText(value) };
}
