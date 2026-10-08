import { useEffect, useState } from 'react';
import { Alert, Button, Input, Space } from 'antd';
import StructuredSuggestionPanel from './StructuredSuggestionPanel';
import MetricChangeReviewResult, { ReviewStatements } from './MetricChangeReviewResult';
import { useSecurityProject } from '@/contexts/SecurityProjectContext';
import { useResourceScope } from '@/hooks/useLatestOperation';
import { getMetricChangeReviewContext, type MetricChangeReviewContext } from '@/services/metric/changeReview';
import { parseMetricChangeReview, validMetricChangeReviewSource, type MetricChangeReviewSuggestion } from '@/services/agent/metricChangeReview';
import type { MetricChangeReviewTarget } from '@/services/agent/governance';

export default function MetricChangeReviewPanel({ metricId, version, disabled }: { metricId: number; version: number; disabled: boolean }) {
  const { currentProject } = useSecurityProject();
  const scope = JSON.stringify([currentProject?.id, metricId, version, disabled]);
  const capture = useResourceScope(scope);
  const [context, setContext] = useState<{ scope: string; value: MetricChangeReviewContext } | null>(null);
  const [question, setQuestion] = useState('');
  const [error, setError] = useState(false);
  const [retry, setRetry] = useState(0);
  const [locked, setLocked] = useState(false);
  useEffect(() => { setQuestion(''); }, [scope]);
  useEffect(() => {
    const isCurrent = capture(); let cancelled = false;
    setContext(null); setError(false);
    if (disabled || !currentProject?.id) return;
    void getMetricChangeReviewContext(metricId, version).then(value => {
      if (cancelled || !isCurrent()) return;
      if (!value || value.metricId !== metricId || value.version !== version
        || !['READY', 'NO_BASELINE', 'UNCHANGED'].includes(value.status)) throw new Error('上下文不一致');
      if (value.status !== 'NO_BASELINE') {
        const v = { ...value, definition: value.definition ?? '' };
        if (!validMetricChangeReviewSource(v) || ![value.publicationEventId, value.publishedVersion, value.publishedVersionId]
          .every(n => Number.isSafeInteger(n) && Number(n) > 0)
          || (value.status === 'READY') !== !!value.differences.length) throw new Error('版本对无法核对');
      }
      setContext({ scope, value });
    }).catch(() => { if (!cancelled && isCurrent()) setError(true); });
    return () => { cancelled = true; };
  }, [scope, capture, retry]);
  if (disabled) return <Alert type="info" message="请确认指标读取、Agent 运行与会话读取权限后准备变更核对。" />;
  if (error) return <Alert type="error" message="版本变更上下文暂不可用，请重新准备或返回原页面核对。" action={<Button onClick={() => setRetry(v => v + 1)}>重试读取</Button>} />;
  if (!context || context.scope !== scope) return <p>正在核对已保存草稿与生效发布版本…</p>;
  if (context.value.status === 'NO_BASELINE') return <Alert type="info" message="暂无生效发布版本可比较，请沿原单版本解释及首次发布流程继续。" />;
  if (context.value.status === 'UNCHANGED') return <Alert type="info" message="已保存草稿与生效发布版本的白名单定义一致，无需 AI 变更解释。" />;
  const source = { ...context.value, definition: context.value.definition! };
  const target: MetricChangeReviewTarget = { metricId, version, publishedVersion: source.publishedVersion!,
    publicationEventId: source.publicationEventId!, definition: source.definition, businessQuestion: question.trim() };
  return <>
    <Space wrap><Button disabled={locked} onClick={() => setRetry(v => v + 1)}>重新准备版本与证据</Button>
      <a href={`/metric/impact?metricId=${metricId}`}>在原影响页面继续核对</a></Space>
    <Input.TextArea disabled={locked} value={question} onChange={e => setQuestion(e.target.value)} maxLength={512} placeholder="发布前希望核对的重点（可空）" />
    <StructuredSuggestionPanel<MetricChangeReviewTarget, MetricChangeReviewSuggestion['candidates'][number], MetricChangeReviewSuggestion>
      key={JSON.stringify([scope, retry, target])} target={target} definition={source.definition} disabled={disabled}
      title="AI 指标版本变更核对" notice="仅比较已保存事实，不包含未保存编辑。AI 说明与检查项需人工核对，完整门禁和关系覆盖需回原页面；验证与发布仍须显式执行。"
      summary={<><p>指标 #{metricId} · 生效发布 v{source.publishedVersion} → 已保存草稿 v{version}</p><MetricChangeReviewResult source={source} /></>}
      question="解释固定发布版本与当前已保存草稿的差异，引用实际事实并准备人工检查项；保留验证和关系证据缺口，不判断发布获准。"
      generateLabel="解释版本变更" adoptLabel="" sourceLabel="只读生成时事实，不决定发布资格"
      bindTarget={metricChangeReview => ({ purpose: 'METRIC_CHANGE_REVIEW', metricChangeReview })} selectTarget={v => v?.metricChangeReview}
      parse={parseMetricChangeReview} validate={async value => value} candidateKey={() => 'review'} onActivityChange={setLocked}
      verifyResult={async value => {
        const current = await getMetricChangeReviewContext(metricId, version);
        if (current.status !== 'READY' || current.definition !== value.expectedDefinition
          || current.publicationEventId !== value.target.publicationEventId || current.publishedVersion !== value.target.publishedVersion) {
          throw new Error('发布或证据已变化');
        }
      }}
      renderCandidate={c => <><ReviewStatements values={c.statements} label="AI 变更说明" /><ReviewStatements values={c.checks} label="人工检查建议" /></>} />
  </>;
}
