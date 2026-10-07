import { useEffect, useState } from 'react';
import { Alert, Card, Radio, Space, Tag, Typography } from 'antd';
import { evidenceCards, verifiedFacts } from '@/services/agent/suggestions';

const states: Record<string, { label: string; color: string; meaning: string }> = {
  OK: { label: '已读取', color: 'blue', meaning: '本次读取成功，请结合核验值判断；不代表业务健康或合规。' },
  EMPTY: { label: '来源为空', color: 'default', meaning: '本次来源未返回记录，不能据此推断无风险或无下游。' },
  NOT_APPLICABLE: { label: '不适用', color: 'default', meaning: '源域声明此项不适用于该对象。' },
  UNAVAILABLE: { label: '暂不可用', color: 'orange', meaning: '本次未取得可用证据，不能视为空记录。' },
  PERMISSION_DENIED: { label: '无权读取', color: 'red', meaning: '当前身份无权读取，请按源页面权限核对。' },
};

export default function GovernanceEvidenceCards({ text }: { text: string }) {
  const [filter, setFilter] = useState('ALL');
  useEffect(() => { setFilter('ALL'); }, [text]);
  const cards = evidenceCards(text);
  const facts = verifiedFacts(text);
  if (!cards.length) return text.includes('```yak-evidence')
    ? <Alert type="warning" showIcon message="本段回答没有可展示的有效证据，请到原会话或源页面核对。" /> : null;
  return <Space direction="vertical" className="w-full">
    <Typography.Text>本段回答提供 {cards.length} 条证据；读取时点属于本次回答，当前事实请回源核对。</Typography.Text>
    <Radio.Group aria-label="按证据状态筛选" value={filter} onChange={(event) => setFilter(event.target.value)}
      style={{ display: 'flex', flexWrap: 'wrap', gap: 4 }}>
      <Radio.Button value="ALL">全部（{cards.length}）</Radio.Button>
      {Object.entries(states).filter(([status]) => cards.some((card) => card.status === status)).map(([status, info]) =>
        <Radio.Button key={status} value={status}>{info.label}（{cards.filter((card) => card.status === status).length}）</Radio.Button>)}
    </Radio.Group>
    {cards.filter((card) => filter === 'ALL' || card.status === filter).map((card) => <Card key={card.id} size="small">
      <Tag>{card.owner}</Tag><Tag color={states[card.status].color}>{states[card.status].label}</Tag>
      <Typography.Text code>{card.id}</Typography.Text>
      <div>来源对象：{card.reference}</div>
      <div>{states[card.status].meaning}</div>
      <div>读取：{card.observedAt || '未知'} · 源更新：{!card.sourceUpdatedAt || card.sourceUpdatedAt === 'unknown' ? '未知' : card.sourceUpdatedAt}</div>
      {facts.filter((fact) => fact.evidenceRef === card.id).map((fact) =>
        <div key={fact.field}>核验值 · {fact.field}：{fact.value}</div>)}
      <a href={card.path} target="_blank" rel="noopener noreferrer">核对来源</a>
    </Card>)}
  </Space>;
}
