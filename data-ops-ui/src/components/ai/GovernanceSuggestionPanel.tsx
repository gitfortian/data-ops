import { useEffect, useRef, useState } from 'react';
import { Alert, Button, Card, Input, Space } from 'antd';
import { agentChatApi, agentSessionApi, streamTurnEvents } from '@/services/agent';
import type { TurnSubmitPayload } from '@/services/agent';
import { parseSuggestion, visibleGovernanceText } from '@/services/agent/suggestions';
import type { GovernanceSuggestion } from '@/services/agent/suggestions';
import GovernanceEvidenceCards from './GovernanceEvidenceCards';

interface Props {
  kind: GovernanceSuggestion['kind'];
  targetId: number;
  definition: string;
  disabled?: boolean;
  ruleLabel?: (templateId: number) => string;
  onApply: (suggestion: GovernanceSuggestion, ruleIndex: number | undefined, isCurrent: () => boolean) => Promise<void>;
}

/** Uses the existing durable Agent turn, HITL and cancellation; adoption only edits the original form. */
export default function GovernanceSuggestionPanel({ kind, targetId, definition, disabled, ruleLabel, onApply }: Props) {
  const [constraints, setConstraints] = useState('');
  const [answer, setAnswer] = useState('');
  const [suggestion, setSuggestion] = useState<GovernanceSuggestion | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [applied, setApplied] = useState<number[]>([]);
  const [applying, setApplying] = useState<number | null>(null);
  const [pending, setPending] = useState<{ toolCallId: string; toolName: string; question: string } | null>(null);
  const session = useRef('');
  const controller = useRef<AbortController>();
  const generation = useRef(0);

  const cancel = () => {
    generation.current += 1;
    controller.current?.abort();
    if (session.current) void agentSessionApi.cancel(session.current).catch(() => undefined);
    setBusy(false); setPending(null); setSuggestion(null);
  };

  useEffect(() => {
    setAnswer(''); setSuggestion(null); setApplied([]); setPending(null); setError('');
    setBusy(false); setApplying(null); session.current = '';
    return () => {
      generation.current += 1;
      controller.current?.abort();
      if (session.current) void agentSessionApi.cancel(session.current).catch(() => undefined);
    };
  }, [kind, targetId, definition]);

  const run = async (resume = false) => {
    const version = ++generation.current;
    controller.current?.abort();
    const abort = new AbortController(); controller.current = abort;
    setBusy(true); setError(''); setSuggestion(null); setApplied([]); setAnswer('');
    if (!resume) session.current = `ai-${Date.now()}-${Math.random().toString(36).slice(2)}`;
    const payload: TurnSubmitPayload = resume && pending
      ? { sessionId: session.current, toolResults: [{ toolCallId: pending.toolCallId,
          toolName: pending.toolName, output: constraints }] }
      : { sessionId: session.current, message: `${kind === 'QUALITY_RULES' ? '建议质量规则' : '建议台账描述'}。用户业务约束：${constraints || '未提供；缺少依据请澄清，不得编造。'}`,
          governanceTarget: kind === 'QUALITY_RULES'
            ? { qualityMonitorId: targetId, purpose: kind } : { assetId: targetId, purpose: kind } };
    setPending(null);
    let final = ''; let failed = false; let completed = false;
    try {
      const submitted = await agentChatApi.submit(payload);
      if (version !== generation.current) {
        void agentSessionApi.cancel(payload.sessionId).catch(() => undefined); return;
      }
      await streamTurnEvents({ turnId: submitted.turnId, signal: abort.signal }, {
        onEvent(event) {
          if (version !== generation.current) return;
          if (event.type === 'TEXT_MESSAGE_CONTENT' && event.phase === 'FINAL') final = event.delta || '';
          if (event.type === 'RUN_ERROR') { failed = true; setError(event.message || event.errorMessage || '生成失败'); }
          if (event.type === 'RUN_FINISHED') completed = event.outcome?.type !== 'interrupt';
          if (event.type === 'CUSTOM' && event.name === 'clarify_requested') {
            const value = event.value as { toolCallId?: string; toolName?: string; question?: string };
            if (value?.toolCallId && value.toolName) {
              setPending({ toolCallId: value.toolCallId, toolName: value.toolName, question: value.question || '请补充业务约束' });
              setConstraints('');
            }
          }
        },
        onComplete() {
          if (version !== generation.current || failed || !completed) return;
          setAnswer(final);
          const candidate = parseSuggestion(final);
          if (candidate && candidate.kind === kind && candidate.targetId === targetId
            && candidate.expectedDefinition === definition) setSuggestion(candidate);
          else if (candidate) setError('配置已改变，请重新加载原编辑器，再生成建议。');
        },
        onError(message) { failed = true; if (version === generation.current) setError(message); },
      });
    } catch (caught) {
      if (version === generation.current) setError(caught instanceof Error ? caught.message : '生成失败');
    } finally { if (version === generation.current) setBusy(false); }
  };

  const apply = async (index: number) => {
    if (!suggestion || applying !== null || applied.includes(index)) return;
    const version = generation.current;
    setApplying(index); setError('');
    try {
      await onApply(suggestion, kind === 'QUALITY_RULES' ? index : undefined, () => version === generation.current);
      if (version === generation.current) setApplied((old) => [...old, index]);
    } catch (caught) {
      if (version === generation.current) setError(caught instanceof Error ? caught.message : '候选已失效，请重新生成');
    } finally { if (version === generation.current) setApplying(null); }
  };

  return <Card size="small" title={kind === 'QUALITY_RULES' ? 'AI 建议规则' : 'AI 建议台账描述'}>
    <Space direction="vertical" className="w-full">
      <Alert type="info" showIcon message="先核对业务条件与证据；带入仅修改表单，需在原页面人工保存。新规则默认不启用。" />
      {pending && <Alert type="warning" message={pending.question} />}
      <Input.TextArea value={constraints} onChange={(event) => setConstraints(event.target.value)}
        placeholder="输入允许空值、唯一性、阈值/枚举或资产用途等业务条件" maxLength={4000} disabled={busy || applying !== null} />
      <Space>
        <Button loading={busy} disabled={disabled || !definition || applying !== null || (Boolean(pending) && !constraints.trim())}
          onClick={() => void run(Boolean(pending))}>{pending ? '补充并继续' : '生成建议'}</Button>
        {(busy || pending) && <Button onClick={cancel}>停止</Button>}
      </Space>
      {error && <Alert type="error" showIcon message={error} />}
      {answer && <p>{visibleGovernanceText(answer)}</p>}
      <GovernanceEvidenceCards text={answer} />
      {suggestion?.kind === 'ASSET_DESCRIPTION' && <Card size="small">
        <p className="whitespace-pre-wrap">{suggestion.description}</p>
        <Button disabled={disabled || busy || applied.includes(0)} loading={applying === 0}
          onClick={() => void apply(0)}>{applied.includes(0) ? '已带入，尚未保存' : '带入描述'}</Button>
      </Card>}
      {suggestion?.kind === 'QUALITY_RULES' && suggestion.rules.map((rule, index) => <Card key={index} size="small">
        <div>{ruleLabel?.(rule.templateId)}</div>
        <p>{rule.name} · {rule.columnName || '表级'} · {rule.operator} {rule.threshold}
          {rule.thresholdEnd != null ? `～${rule.thresholdEnd}` : ''}{rule.enumValues?.length ? ` · ${rule.enumValues.join('、')}` : ''}</p>
        <Button disabled={disabled || busy || applied.includes(index)} loading={applying === index}
          onClick={() => void apply(index)}>{applied.includes(index) ? '已带入，尚未保存' : '带入这条规则'}</Button>
      </Card>)}
    </Space>
  </Card>;
}
