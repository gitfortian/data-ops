import { Card, Space, Tag } from 'antd';
import { evidenceCards, verifiedFacts } from '@/services/agent/suggestions';

export default function GovernanceEvidenceCards({ text }: { text: string }) {
  const facts = verifiedFacts(text);
  return <Space direction="vertical" className="w-full">
    {evidenceCards(text).map((card) => <Card key={card.id} size="small">
      <Tag>{card.owner}</Tag><Tag>{card.status}</Tag><span>{card.reference}</span>
      <div>读取：{card.observedAt} · 源更新：{card.sourceUpdatedAt === 'unknown' ? '未知' : card.sourceUpdatedAt}</div>
      {facts.filter((fact) => fact.evidenceRef === card.id).map((fact) =>
        <div key={fact.field}>核验值 · {fact.field}：{fact.value}</div>)}
      <a href={card.path} target="_blank" rel="noopener noreferrer">核对来源</a>
    </Card>)}
  </Space>;
}
