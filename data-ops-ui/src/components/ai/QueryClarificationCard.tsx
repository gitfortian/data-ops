import { useState } from 'react';
import { Alert, Button, Input, Space } from 'antd';
import { readClarificationQuestion } from '@/services/agent/clarification';

const labels = { FIELD: '核对查询字段', TIME: '核对时间范围与粒度', CALIBER: '核对统计口径' };

export default function QueryClarificationCard({ question, disabled, onAnswer }: {
  question: string; disabled: boolean; onAnswer: (answer: string) => void;
}) {
  const [answer, setAnswer] = useState('');
  let value;
  try { value = readClarificationQuestion(question); } catch {
    return <Alert type="error" message="待答问题暂无法核对，请刷新原会话或返回原页面核对。" />;
  }
  const source = value.queryContext;
  return <Alert type="warning" showIcon message={source ? labels[source.kind] : 'AI 需要补充信息'}
    description={<Space direction="vertical" style={{ width: '100%' }}>
      <span>{value.question}</span>
      {source && <>
        <span>数据集 #{source.datasetId} · 发现版本 v{source.versionNo}</span>
        {source.fields.map(field => <div key={field.fieldId}>
          <strong>{field.displayName || field.fieldId}</strong> · {field.fieldId} · {field.dataType} · {field.role}
          {field.description && <p>{field.description}</p>}
        </div>)}
        <span>以上为提问时发现的字段。回答后会重新核对版本和权限；补充信息不修改源定义，也不代表查询已获准。</span>
        {source.truncated && <span>字段发现范围已截断，所示字段不代表完整字段清单。</span>}
        {source.kind !== 'FIELD' && !!value.options.length && <span>下方为 AI 提供的待确认选项，请按实际业务核对，也可自行回答。</span>}
      </>}
      {!!value.options.length && <Space wrap>{value.options.map(option =>
        <Button key={option} size="small" disabled={disabled} onClick={() => onAnswer(option)}>{option}</Button>)}</Space>}
      <Input.TextArea aria-label="补充问数信息" disabled={disabled} value={answer}
        onChange={event => setAnswer(event.target.value)} maxLength={2000} placeholder="或输入你的回答..." />
      <Button disabled={disabled || !answer.trim() || answer.length > 2000} onClick={() => onAnswer(answer.trim())}>回答</Button>
    </Space>} style={{ marginTop: 12 }} />;
}
