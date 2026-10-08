import { useEffect, useState } from 'react';
import { Alert, Button, Input } from 'antd';
import StructuredSuggestionPanel from './StructuredSuggestionPanel';
import { useSecurityProject } from '@/contexts/SecurityProjectContext';
import { useResourceScope } from '@/hooks/useLatestOperation';
import { agentChatApi } from '@/services/agent';
import { getMetricExplanationContext, type MetricExplanationContext } from '@/services/metric/explanation';
import { parseMetricExplanation, type MetricExplanationSuggestion } from '@/services/agent/metricExplanation';
import type { MetricExplanationTarget } from '@/services/agent/governance';

interface Props { metricId: number; version: number; disabled: boolean; snapshot?: boolean; onApply?: (description: string) => void }
export default function MetricExplanationPanel({ metricId, version, disabled, snapshot = false, onApply }: Props) {
  const { currentProject } = useSecurityProject();
  const scope = JSON.stringify([currentProject?.id, metricId, version, disabled, snapshot]);
  const capture = useResourceScope(scope);
  const [context, setContext] = useState<{ scope: string; value: MetricExplanationContext } | null>(null);
  const [error, setError] = useState('');
  const [question, setQuestion] = useState('');
  const [retry, setRetry] = useState(0);
  useEffect(() => {
    const isCurrent = capture(); let cancelled = false;
    setContext(null); setError(''); setQuestion('');
    if (disabled || !currentProject?.id) return;
    void getMetricExplanationContext(metricId, version, snapshot).then(value => {
      if (!cancelled && isCurrent()) {
        if (!value || value.metricId !== metricId || value.version !== version || !/^[a-f0-9]{64}$/.test(value.definition)) throw new Error('指标版本上下文无法核对');
        setContext({ scope, value });
      }
    }).catch(() => { if (!cancelled && isCurrent()) setError('指标版本上下文暂不可用，请重试读取或返回原页面核对。'); });
    return () => { cancelled = true; };
  }, [scope, capture, retry]);
  if (disabled) return <Alert type="info" message="请先保存口径修改，并确认指标读取、Agent 运行与会话读取权限后生成说明。" />;
  if (error) return <Alert type="error" message={error} action={<Button onClick={() => setRetry(v => v + 1)}>重试读取</Button>} />;
  if (!context || context.scope !== scope) return <p>正在核对已保存指标版本…</p>;
  const target: MetricExplanationTarget = { metricId, version, businessQuestion: question.trim(), ...(snapshot ? { view: 'SNAPSHOT' as const } : {}) };
  return <>
    <Input.TextArea value={question} onChange={e => setQuestion(e.target.value)} maxLength={512} placeholder="需要解释的业务问题（可空）" />
    <StructuredSuggestionPanel<MetricExplanationTarget, MetricExplanationSuggestion['candidates'][number], MetricExplanationSuggestion>
      key={JSON.stringify([scope, target])} target={target} definition={context.value.definition} disabled={disabled}
      title="AI 指标口径解释与说明" notice={snapshot ? '依据选定的不可变版本快照，仅供阅读。当前验证、发布、依赖与消费证据需分别核对，AI 解读不代表验证通过。' : '依据已保存草稿版本；AI 解读需核对原始事实，不代表验证或发布通过。带入只改业务说明，仍需人工保存。'}
      summary={<p>指标 #{metricId} · {snapshot ? '精确快照' : '已保存草稿'} v{version}</p>}
      question="解释绑定指标版本的计算口径，引用本轮事实并生成业务说明草稿；缺依据时列出问题。"
      generateLabel="解释口径并生成说明" adoptLabel="带入业务说明"
      sourceLabel="依据当前授权指标版本快照"
      bindTarget={metricExplanation => ({ purpose: 'METRIC_EXPLANATION', metricExplanation })} selectTarget={v => v?.metricExplanation}
      parse={parseMetricExplanation} validate={agentChatApi.validateMetricExplanation} candidateKey={() => 'description'}
      renderCandidate={c => <><p>{c.businessDescription}</p>{c.statements.map((s, i) => <div key={i}>
        <p>AI 解读：{s.text}</p>{s.evidence.map(f => <p key={f.key}>原始事实 · {f.label}：<code className="whitespace-pre-wrap break-all">{f.value}</code></p>)}
      </div>)}</>}
      onApply={!snapshot && onApply ? c => onApply(c.businessDescription) : undefined} />
  </>;
}
