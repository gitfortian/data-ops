import { useEffect, useRef, useState } from 'react';
import { Alert, Button, Input, Select, Space } from 'antd';
import { useResourceScope } from '@/hooks/useLatestOperation';
import { useSecurityProject } from '@/contexts/SecurityProjectContext';
import { agentChatApi } from '@/services/agent';
import type { MetricDraftTarget } from '@/services/agent/governance';
import { getMetricDraftContext, parseMetricDraft, readMetricDraftTarget, type MetricDraftContext, type MetricDefinitionDraft, type MetricDraftSuggestion } from '@/services/agent/metricDraft';
import StructuredSuggestionPanel from './StructuredSuggestionPanel';

interface Props {
  metricId?: number; version?: number; metricType: 'ATOMIC' | 'DERIVED' | 'COMPOSITE'; modelId?: number; refMetricId?: number;
  upstreamOptions: { id: number; code: string; name: string }[]; disabled: boolean;
  onApply: (draft: MetricDefinitionDraft, formula: string) => void;
}
const SYMBOLS: Record<string, string> = { ADD: '+', SUB: '-', MUL: '*', DIV: '/', LPAREN: '(', RPAREN: ')' };
export default function MetricDefinitionPanel(props: Props) {
  const { currentProject } = useSecurityProject();
  const [requirement, setRequirement] = useState('');
  const [upstreamIds, setUpstreamIds] = useState<number[]>([]);
  const [prepared, setPrepared] = useState<{ scope: string; target: MetricDraftTarget; context: MetricDraftContext }>();
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const busy = useRef<string>();
  const target: MetricDraftTarget = { metricId: props.metricId ?? null, version: props.version ?? null, metricType: props.metricType,
    modelId: props.metricType === 'ATOMIC' ? props.modelId ?? null : null,
    upstreamIds: props.metricType === 'DERIVED' ? props.refMetricId ? [props.refMetricId] : [] : props.metricType === 'COMPOSITE' ? upstreamIds : [], requirement: requirement.trim() };
  const scope = JSON.stringify([currentProject?.id, target, props.disabled]);
  const capture = useResourceScope(scope);
  useEffect(() => { setLoading(false); setError(''); }, [scope]);
  const prepare = async () => {
    if (busy.current === scope || props.disabled) return;
    const isCurrent = capture(); busy.current = scope; setLoading(true); setPrepared(undefined); setError('');
    try {
      const input = readMetricDraftTarget(target);
      const context = await getMetricDraftContext(input);
      if (!isCurrent()) return;
      if (!context || !/^[a-f0-9]{64}$/.test(context.definition)) throw new Error('草稿上下文无法核对');
      setPrepared({ scope, target: input, context });
    } catch { if (isCurrent()) setError('指标草稿上下文暂不可用，请核对需求及所选依赖后重试。'); }
    finally { if (busy.current === scope) busy.current = undefined; if (isCurrent()) setLoading(false); }
  };
  return <Space direction="vertical" className="w-full">
    <Alert type="info" message="AI 指标定义草稿：先选择模型或上游，再明确业务需求。候选需人工核对，带入后单独保存、验证与发布。" />
    <Input.TextArea value={requirement} maxLength={512} disabled={props.disabled} placeholder="说明业务目标、统计粒度、周期及限定条件；缺项会列为待确认问题。"
      onChange={e => { setRequirement(e.target.value); setPrepared(undefined); setLoading(false); }} />
    {props.metricType === 'COMPOSITE' && <Select mode="multiple" value={upstreamIds} className="w-full" disabled={props.disabled}
      placeholder="明确选择最多五个上游指标" options={props.upstreamOptions.map(m => ({ value: m.id, label: `${m.name}（${m.code}）` }))}
      onChange={ids => { setUpstreamIds(ids); setPrepared(undefined); setLoading(false); }} />}
    <Button disabled={props.disabled || !requirement.trim() || upstreamIds.length > 5} loading={loading} onClick={() => void prepare()}>核对所选依赖</Button>
    {error && <Alert type="error" message={error} />}
    {prepared?.scope === scope && <StructuredSuggestionPanel<MetricDraftTarget, MetricDefinitionDraft, MetricDraftSuggestion>
      key={JSON.stringify([currentProject?.id, prepared.target, prepared.context.definition])}
      target={prepared.target} definition={prepared.context.definition} disabled={props.disabled}
      title="AI 指标定义候选" notice="这些是尚未保存的 AI 草稿，领域检查不代替业务复核或精确版本验证。"
      summary={<><p>来源字段 {prepared.context.fields.length} 项</p>{prepared.context.upstream.map(u => <p key={u.id}>依赖 {u.name}（{u.code}）v{u.version}</p>)}</>}
      question="按绑定类型、需求和所选依赖准备指标定义草稿；信息不足返回问题，不猜测业务口径。"
      generateLabel="生成指标定义草稿" adoptLabel="带入原指标表单"
      bindTarget={metricDraft => ({ purpose: 'METRIC_DRAFT', metricDraft })} selectTarget={v => v?.metricDraft}
      parse={parseMetricDraft} validate={agentChatApi.validateMetricDraft} candidateKey={() => 'definition'}
      sourceLabel="来自本轮授权依赖和模型字段"
      renderCandidate={c => <><p>{c.name} · {c.period}</p><p>{c.description}</p>
        {c.aggregation && <p>{c.aggregation}({c.field})</p>}
        {c.qualifiers.map((q, i) => <p key={i}>{q.field} {q.op} {q.value}</p>)}
        {!!c.tokens.length && <p>{c.tokens.map(t => t.operator === 'REF' ? prepared.context.upstream.find(u => u.id === t.metricId)?.code : SYMBOLS[t.operator]).join(' ')}</p>}</>}
      onApply={c => props.onApply(c, c.tokens.map(t => t.operator === 'REF' ? prepared.context.upstream.find(u => u.id === t.metricId)!.code : SYMBOLS[t.operator]).join(' '))} />}
  </Space>;
}
