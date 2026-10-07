import React from 'react';
import { Alert, Button, Collapse, Input, Select, Space, Typography } from 'antd';
import { governanceQuestions, type GovernanceTarget } from '@/services/agent/governance';
import { prepareGovernanceQuestion } from '../question-preparation';

export default function QuestionPreparationPanel({ target, disabled, hasInput, onFill }: {
  target: GovernanceTarget; disabled: boolean; hasInput: boolean; onFill: (question: string) => void;
}) {
  const [templateIndex, setTemplateIndex] = React.useState(0);
  const [background, setBackground] = React.useState('');
  const [focus, setFocus] = React.useState('');
  const [outcome, setOutcome] = React.useState('');
  let preview = '';
  let error = '';
  try { preview = prepareGovernanceQuestion(target, { templateIndex, background, focus, outcome }); }
  catch (failure) { error = (failure as Error).message; }

  return <Collapse size="small" style={{ marginBottom: 12 }} items={[{
    key: 'prepare', label: '准备治理问题', children: <Space direction="vertical" style={{ width: '100%', maxHeight: 280, overflowY: 'auto' }}>
      <Typography.Text type="secondary">仅围绕当前所选对象组织问题。补充内容是用户背景，发送后仍需源证据核对。</Typography.Text>
      <label htmlFor="governance-question-template">希望先做什么</label>
      <Select id="governance-question-template" aria-label="希望先做什么" value={templateIndex} disabled={disabled}
        style={{ width: '100%' }} onChange={setTemplateIndex}
        options={governanceQuestions(target).map((question, value) => ({ value, label: question }))} />
      <label htmlFor="governance-question-background">已知背景（可选，最多 2000 字符）</label>
      <Input.TextArea id="governance-question-background" value={background} disabled={disabled}
        onChange={(event) => setBackground(event.target.value)} autoSize={{ minRows: 2, maxRows: 4 }} />
      <label htmlFor="governance-question-focus">希望核对（可选，最多 1000 字符）</label>
      <Input.TextArea id="governance-question-focus" value={focus} disabled={disabled}
        onChange={(event) => setFocus(event.target.value)} autoSize={{ minRows: 1, maxRows: 3 }} />
      <label htmlFor="governance-question-outcome">期望结果（可选，最多 1000 字符）</label>
      <Input.TextArea id="governance-question-outcome" value={outcome} disabled={disabled}
        onChange={(event) => setOutcome(event.target.value)} autoSize={{ minRows: 1, maxRows: 3 }} />
      <Typography.Text strong>发送前预览</Typography.Text>
      {error ? <Alert type="warning" message={error} />
        : <Typography.Paragraph style={{ whiteSpace: 'pre-wrap' }}>{preview}</Typography.Paragraph>}
      {hasInput && <Typography.Text type="secondary">输入框已有未发送内容，先完成编辑或清空后再填写。</Typography.Text>}
      <Button disabled={disabled || hasInput || !!error} onClick={() => { if (!disabled && !hasInput && !error) onFill(preview); }}>
        填入准备的问题
      </Button>
    </Space>,
  }]} />;
}
