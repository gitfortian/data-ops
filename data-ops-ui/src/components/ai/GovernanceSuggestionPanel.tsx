import { useEffect, useRef, useState } from 'react';
import { Alert, Button, Card, Input, Space } from 'antd';
import { agentChatApi, agentSessionApi, streamTurnEvents } from '@/services/agent';
import type { TurnSubmitPayload } from '@/services/agent';
import { parseSuggestion, visibleGovernanceText } from '@/services/agent/suggestions';
import type { GovernanceSuggestion } from '@/services/agent/suggestions';
import GovernanceEvidenceCards from './GovernanceEvidenceCards';
import type { SaveRulePayload } from '@/services/data-quality';
import { sameRuleConditions } from '@/services/data-quality/ruleComparison';
import { readContinuation, sessionLocation } from '@/services/agent/continuation';
import type { GovernanceTarget } from '@/services/agent/governance';
import QualityRuleComparison from './QualityRuleComparison';

interface Props {
  kind: GovernanceSuggestion['kind'];
  targetId: number;
  definition: string;
  disabled?: boolean;
  ruleLabel?: (templateId: number) => string;
  qualityRules?: SaveRulePayload[];
  onApply: (suggestion: GovernanceSuggestion, ruleIndex: number | undefined, isCurrent: () => boolean) => Promise<void>;
}

/** Uses the existing durable Agent turn, HITL and cancellation; adoption only edits the original form. */
export default function GovernanceSuggestionPanel({ kind, targetId, definition, disabled, ruleLabel, qualityRules = [], onApply }: Props) {
  const [constraints, setConstraints] = useState('');
  const [answer, setAnswer] = useState('');
  const [suggestion, setSuggestion] = useState<GovernanceSuggestion | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [applied, setApplied] = useState<number[]>([]);
  const [applying, setApplying] = useState<number | null>(null);
  const [pending, setPending] = useState<{ toolCallId: string; toolName: string; question: string } | null>(null);
  const [blocked, setBlocked] = useState(false);
  const [sessionId, setSessionId] = useState('');
  const session = useRef('');
  const controller = useRef<AbortController>();
  const generation = useRef(0);
  const applyingRequest = useRef<number | null>(null);
  const busyRequest = useRef(false);
  const confirmedTurn = useRef<string | null>(null);
  const cancelable = useRef(false);
  const disabledRef = useRef(Boolean(disabled));
  disabledRef.current = Boolean(disabled);
  const target: GovernanceTarget = kind === 'QUALITY_RULES'
    ? { qualityMonitorId: targetId, purpose: kind } : { assetId: targetId, purpose: kind };

  const reconcile = async (version: number, id: string | null) => {
    try {
      const view = readContinuation(await agentSessionApi.continuation(session.current), session.current);
      if (version !== generation.current) return;
      if (!view.turnId || (id && view.turnId !== id)
        || JSON.stringify(view.governanceTarget) !== JSON.stringify(target)) throw new Error('MISMATCH');
      confirmedTurn.current = view.turnId;
      cancelable.current = view.status === 'QUEUED' || view.status === 'RUNNING';
      setPending(null); setSuggestion(null); setAnswer('');
      if (cancelable.current) {
        setBlocked(true); setError('本轮仍在排队或推理，请刷新核对，或到原会话查看。'); return;
      }
      if (view.blockingReason) throw new Error('BLOCKED');
      if (view.status === 'WAITING_INPUT' && view.clarification) {
        const question = view.clarification.question.trim();
        setPending({ ...view.clarification, question: question.startsWith('{')
          ? JSON.parse(question).question : question });
        setBlocked(false); setError(''); return;
      }
      if (view.status === 'COMPLETED') {
        const history = await agentSessionApi.history(session.current);
        if (version !== generation.current) return;
        const answers = history.filter((entry) => entry.role === 'assistant' && entry.turnId === view.turnId);
        if (answers.length !== 1 || !answers[0].content?.trim()) throw new Error('HISTORY_UNAVAILABLE');
        const final = answers[0].content;
        const candidate = parseSuggestion(final);
        setAnswer(final);
        if (candidate && candidate.kind === kind && candidate.targetId === targetId
          && candidate.expectedDefinition === definition) {
          setSuggestion(candidate); setError('');
        } else setError(candidate ? '配置已改变，请重新加载原编辑器，再生成建议。' : '本轮没有可带入的候选，请核对回答与证据。');
      } else {
        const reasons: Record<string, string> = { FAILED: '本轮生成失败，请核对配置与源页面后重新生成。',
          CANCELLED: '本轮已停止，可重新整理业务约束。', INTERRUPTED: '本轮执行中断，请核对原会话后重新生成。' };
        setError(reasons[view.status ?? ''] ?? '无法确认本轮状态，请刷新核对。');
        if (!reasons[view.status ?? '']) throw new Error('UNKNOWN');
      }
      setBlocked(false);
    } catch {
      if (version !== generation.current) return;
      setSuggestion(null); setPending(null); setBlocked(true);
      setError('本轮状态或回答暂无法核对，已保留业务约束；请刷新核对或到原会话查看。');
    }
  };

  const refresh = async () => {
    if (busyRequest.current || !session.current) return;
    busyRequest.current = true; setBusy(true); setSuggestion(null); setBlocked(true);
    const version = ++generation.current;
    try { await reconcile(version, confirmedTurn.current); }
    finally { if (version === generation.current) { busyRequest.current = false; setBusy(false); } }
  };

  const cancel = async () => {
    if (applyingRequest.current !== null) {
      generation.current += 1; applyingRequest.current = null;
      setApplying(null); setSuggestion(null); return;
    }
    const id = confirmedTurn.current;
    if (!id) { setError('提交尚未确认，请等待回执后核对；尚未发送停止请求。'); return; }
    if (!cancelable.current || disabledRef.current) return;
    const version = ++generation.current;
    cancelable.current = false;
    controller.current?.abort();
    busyRequest.current = true; setBusy(true); setBlocked(true); setSuggestion(null);
    setError('正在核对本轮停止后的实际状态。');
    try {
      // Lost command acknowledgements still require the same read-only state check.
      await agentChatApi.cancelTurn(id).catch(() => undefined);
      if (version === generation.current) await reconcile(version, id);
    } finally { if (version === generation.current) { busyRequest.current = false; setBusy(false); } }
  };

  useEffect(() => {
    setAnswer(''); setSuggestion(null); setApplied([]); setPending(null); setError('');
    setBusy(false); setApplying(null); setBlocked(false); setSessionId(''); session.current = '';
    applyingRequest.current = null;
    busyRequest.current = false; confirmedTurn.current = null; cancelable.current = false;
    return () => {
      generation.current += 1;
      controller.current?.abort();
      if (confirmedTurn.current && cancelable.current) {
        void agentChatApi.cancelTurn(confirmedTurn.current).catch(() => undefined);
      }
    };
  }, [kind, targetId, definition]);

  const run = async (resume = false) => {
    if (disabled || busyRequest.current || blocked || applyingRequest.current !== null) return;
    busyRequest.current = true;
    const version = ++generation.current;
    controller.current?.abort();
    const abort = new AbortController(); controller.current = abort;
    setBusy(true); setBlocked(true); setError(''); setSuggestion(null); setApplied([]); setAnswer('');
    const originalTurn = resume ? confirmedTurn.current : null;
    if (!resume) {
      session.current = `ai-${Date.now()}-${Math.random().toString(36).slice(2)}`;
      setSessionId(session.current); confirmedTurn.current = null; cancelable.current = false;
    }
    const payload: TurnSubmitPayload = resume && pending
      ? { sessionId: session.current, toolResults: [{ toolCallId: pending.toolCallId,
          toolName: pending.toolName, output: constraints }] }
      : { sessionId: session.current, message: `${kind === 'QUALITY_RULES' ? '建议质量规则' : '建议台账描述'}。用户业务约束：${constraints || '未提供；缺少依据请澄清，不得编造。'}`,
          governanceTarget: target };
    setPending(null);
    try {
      const submitted = await agentChatApi.submit(payload);
      if (version !== generation.current) {
        if (submitted.turnId) void agentChatApi.cancelTurn(submitted.turnId).catch(() => undefined);
        return;
      }
      if (!submitted.turnId?.trim() || (originalTurn && submitted.turnId !== originalTurn)) throw new Error('UNCONFIRMED');
      confirmedTurn.current = submitted.turnId; cancelable.current = true;
      await streamTurnEvents({ turnId: submitted.turnId, signal: abort.signal }, {
        // Stream frames are delivery projections. Only persisted state/history expose candidates or pending.
        onEvent() {}, onComplete() {}, onError() {},
      });
      if (version === generation.current) await reconcile(version, submitted.turnId);
    } catch {
      if (version === generation.current) {
        if (confirmedTurn.current) await reconcile(version, confirmedTurn.current);
        else setError('提交尚未确认，已保留业务约束；请刷新核对或到原会话查看，避免重复生成。');
      }
    } finally { if (version === generation.current) { busyRequest.current = false; setBusy(false); } }
  };

  const apply = async (index: number) => {
    const duplicate = suggestion?.kind === 'QUALITY_RULES'
      && qualityRules.some((rule) => sameRuleConditions(rule, suggestion.rules[index]));
    if (disabled || busy || !suggestion || applyingRequest.current !== null || duplicate
      || (kind === 'ASSET_DESCRIPTION' && applied.includes(index))) return;
    const version = generation.current;
    applyingRequest.current = version;
    setApplying(index); setError('');
    try {
      await onApply(suggestion, kind === 'QUALITY_RULES' ? index : undefined,
        () => version === generation.current && !disabledRef.current);
      if (version === generation.current) setApplied((old) => [...old, index]);
    } catch (caught) {
      if (version === generation.current) setError(caught instanceof Error ? caught.message : '候选已失效，请重新生成');
    } finally {
      if (applyingRequest.current === version) applyingRequest.current = null;
      if (version === generation.current) setApplying(null);
    }
  };

  return <Card size="small" title={kind === 'QUALITY_RULES' ? 'AI 建议规则' : 'AI 建议台账描述'}>
    <Space direction="vertical" className="w-full">
      <Alert type="info" showIcon message="先核对业务条件与证据；带入仅修改表单，需在原页面人工保存。新规则默认不启用。" />
      {kind === 'QUALITY_RULES' && <p>候选依据已保存的监控定义；当前表单可能有未保存修改，保存前请核对过滤条件、阈值与调度。</p>}
      {pending && <Alert type="warning" message={pending.question} />}
      <Input.TextArea value={constraints} onChange={(event) => setConstraints(event.target.value)}
        placeholder="输入允许空值、唯一性、阈值/枚举或资产用途等业务条件" maxLength={4000} disabled={busy || applying !== null} />
      <Space>
        <Button loading={busy} disabled={disabled || blocked || !definition || applying !== null || (Boolean(pending) && !constraints.trim())}
          onClick={() => void run(Boolean(pending))}>{pending ? '补充并继续' : '生成建议'}</Button>
        {(busy || applying !== null || (blocked && cancelable.current)) && <Button disabled={applying === null && Boolean(disabled)} onClick={() => void cancel()}>停止</Button>}
        {sessionId && !busy && <Button disabled={applying !== null} onClick={() => void refresh()}>刷新核对</Button>}
        {sessionId && <a href={sessionLocation(sessionId)} target="_blank" rel="noopener noreferrer">到原会话查看或继续</a>}
      </Space>
      {error && <Alert type="error" showIcon message={error} />}
      {answer && <p>{visibleGovernanceText(answer)}</p>}
      <GovernanceEvidenceCards text={answer} />
      {suggestion?.kind === 'ASSET_DESCRIPTION' && <Card size="small">
        <p className="whitespace-pre-wrap">{suggestion.description}</p>
        <Button disabled={disabled || busy || applied.includes(0)} loading={applying === 0}
          onClick={() => void apply(0)}>{applied.includes(0) ? '已带入，尚未保存' : '带入描述'}</Button>
      </Card>}
      {suggestion?.kind === 'QUALITY_RULES' && suggestion.rules.map((rule, index) => {
        const duplicate = qualityRules.some((current) => sameRuleConditions(current, rule));
        return <Card key={index} size="small">
        <div>{ruleLabel?.(rule.templateId)}</div>
        <p>{rule.name} · {rule.columnName || '表级'}</p>
        <QualityRuleComparison candidate={rule} rules={qualityRules} />
        <Button disabled={disabled || busy || applying !== null || duplicate} loading={applying === index}
          onClick={() => void apply(index)}>{duplicate ? '表单已有相同条件' : '带入这条规则'}</Button>
        {duplicate && applied.includes(index) && <p>已带入表单，尚未保存；若已修改或删除，请核对上方规则。</p>}
      </Card>;
      })}
    </Space>
  </Card>;
}
