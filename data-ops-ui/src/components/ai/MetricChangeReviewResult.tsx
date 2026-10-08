import { Alert, Card, Collapse } from 'antd';
import type { MetricChangeReviewSource, ReviewStatement } from '@/services/agent/metricChangeReview';

export function ReviewStatements({ values, label }: { values: ReviewStatement[]; label: string }) {
  return <>{values.map((s, i) => <div key={i}><p>{label}：{s.text}</p>{s.evidence.map(f => <p key={f.key}>
    依据 · {f.label}：<code className="whitespace-pre-wrap break-all">{f.value}</code></p>)}</div>)}</>;
}
export default function MetricChangeReviewResult({ source }: { source: MetricChangeReviewSource }) {
  return <>
    <p>证据读取时点：{source.preparedAt} · 各来源独立读取，完整门禁需回原治理面板核对。</p>
    {source.differences.map(d => <Card size="small" key={d.key} title={d.label}>
      <p>发布版本：<code className="whitespace-pre-wrap break-all">{d.before ?? '（未记录）'}</code></p>
      <p>已保存草稿：<code className="whitespace-pre-wrap break-all">{d.after ?? '（未记录）'}</code></p>
    </Card>)}
    {source.coverage.map(c => <Alert key={c.key} type={c.status === 'READY' || c.status === 'EMPTY' ? 'info' : 'warning'}
      message={`${c.label} · ${c.status}`} description={c.description} />)}
    <Collapse items={[{ key: 'facts', label: '核对本次原始事实', children: source.facts.map(f => <p key={f.key}>
      {f.label}：<code className="whitespace-pre-wrap break-all">{f.value}</code></p>) }]} />
  </>;
}
