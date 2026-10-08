import MetricChangeReviewResult, { ReviewStatements } from './MetricChangeReviewResult';
import { Alert, Button, Card, Space, Typography } from 'antd';
import type { ScenarioHistory, ScenarioSuggestion } from '@/services/agent/scenarioHistory';

const titles: Record<ScenarioSuggestion['kind'], string> = {
  METRIC_CHANGE_REVIEW: '指标发布前版本变更核对结果',
  STANDARD_MATCH: '类型标准匹配结果', MODEL_MAPPING: '来源字段映射结果',
  METRIC_EXPLANATION: '指标口径解释结果', METRIC_DRAFT: '指标定义草稿结果',
};
const metricTypes = { ATOMIC: '原子', DERIVED: '派生', COMPOSITE: '复合' };
const periods = { DAY: '日', WEEK: '周', MONTH: '月' };
const operators: Record<string, string> = { ADD: '+', SUB: '-', MUL: '×', DIV: '÷', LPAREN: '(', RPAREN: ')' };

export default function ScenarioHistoryCard({ review }: { review: ScenarioHistory }) {
  if (review.status !== 'READY') return review.status === 'NONE' ? null : <Alert type="info" showIcon
    message="场景结果尚未核对" description="只有最新已完成轮次的唯一关联历史可以展示为场景结果。请刷新会话核对；此处保留原文。" />;
  const v = review.value;
  return <Card size="small" title={titles[v.kind]}>
    <Space direction="vertical" className="w-full">
      <Typography.Text type="secondary">历史生成结果 · Skill v{v.skillVersion} · 尚未核验当前有效性</Typography.Text>
      <Alert type="info" message={v.kind === 'METRIC_EXPLANATION' && v.target.view === 'SNAPSHOT'
        ? `历史快照 v${v.target.version}，仅供阅读；当前验证、发布与消费证据需分别核对。`
        : '候选与说明仅供人工核对。返回原页面后重新核验、选择处理；保存、验证与发布分别完成。'} />
      {v.truncated && <Alert type="warning" message="生成时目录已截断，结果不代表全部可用候选。" />}
      {v.kind === 'METRIC_CHANGE_REVIEW' && <>
        <p>指标 #{v.target.metricId} · 生成时发布 v{v.target.publishedVersion} → 已保存草稿 v{v.target.version}</p>
        <MetricChangeReviewResult source={v.source} />
        {v.candidates.map((c, i) => <Card size="small" key={i}><ReviewStatements values={c.statements} label="AI 变更说明" />
          <ReviewStatements values={c.checks} label="人工检查建议" /></Card>)}
      </>}
      {v.kind === 'STANDARD_MATCH' && <>
        <p>模型 #{v.target.modelId} · 字段 {v.target.columnName} · 当时输入类型 {v.target.dataType}</p>
        {v.fieldDescription && <p>字段说明草稿：{v.fieldDescription}</p>}
        {v.candidates.map(c => <Card size="small" key={c.standardId}><p>{c.name}（{c.code}）v{c.version} · {c.stdType}</p><p>{c.reason}</p></Card>)}
      </>}
      {v.kind === 'MODEL_MAPPING' && <>
        <p>模型 #{v.target.modelId} · 目标字段 {v.target.columnName} · 当时目标类型 {v.targetType}</p>
        <p>所选来源：{v.target.database}.{v.target.table}</p>
        {v.candidates.map(c => <Card size="small" key={c.sourceColumn}><p>来源字段 {c.sourceColumn} · 类型 {c.type ?? '未提供'} · {c.nullable ? '可空' : '不可空'}</p><p>{c.reason}</p></Card>)}
      </>}
      {v.kind === 'METRIC_EXPLANATION' && <>
        <p>指标 #{v.target.metricId} · {v.target.view === 'SNAPSHOT' ? '历史快照' : '生成时已保存草稿'} v{v.target.version}</p>
        {v.candidates.map((c, index) => <Card size="small" key={index}><p>业务说明草稿：{c.businessDescription}</p>
          {c.statements.map((s, i) => <div key={i}><p>AI 解读：{s.text}</p>{s.evidence.map(f => <p key={f.key}>
            原始事实 · {f.label}：<code className="whitespace-pre-wrap break-all">{f.value}</code></p>)}</div>)}
        </Card>)}
      </>}
      {v.kind === 'METRIC_DRAFT' && <>
        <p>{metricTypes[v.target.metricType]}指标 · {v.target.metricId ? `指标 #${v.target.metricId} v${v.target.version}` : '新建未保存草稿'}</p>
        <p>用户需求：{v.target.requirement}</p>
        {v.target.modelId && <p>所选模型 #{v.target.modelId}</p>}
        {v.source.upstream.map(u => <p key={u.id}>当时依赖 {u.name}（{u.code}）v{u.version}</p>)}
        {v.candidates.map((c, i) => <Card size="small" key={i}><p>{c.name} · {periods[c.period]}周期</p><p>{c.description}</p>
          {c.aggregation && <p>聚合：{c.aggregation}({c.field})</p>}
          {c.qualifiers.map((q, index) => <p key={index}>限定：{q.field} {q.op} {q.value}</p>)}
          {!!c.tokens.length && <p>组合：{c.tokens.map(t => t.operator === 'REF'
            ? v.source.upstream.find(u => u.id === t.metricId)?.code : operators[t.operator]).join(' ')}</p>}
        </Card>)}
      </>}
      {!v.candidates.length && <p>本轮没有候选；请核对待确认项与原来源。</p>}
      {v.questions.map((q, i) => <Alert key={i} type="warning" message={`待确认：${q}`} />)}
      <Button href={review.sourcePath}>返回原页面核对</Button>
      {v.kind === 'METRIC_DRAFT' && !v.target.metricId && <Typography.Text type="secondary">返回指标列表后重新选择或新建，未保存的表单不会自动恢复。</Typography.Text>}
    </Space>
  </Card>;
}
