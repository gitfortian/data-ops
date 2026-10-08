import { useEffect, useRef, useState, type ReactNode } from 'react';
import { Alert, Button, Card, Input, Space } from 'antd';
import { agentChatApi, agentSessionApi, streamTurnEvents } from '@/services/agent';
import { readContinuation, sessionLocation } from '@/services/agent/continuation';
import type { GovernanceTarget } from '@/services/agent/governance';

interface ScenarioValue<T, C> {
  target: T; expectedDefinition: string; skillVersion: number; truncated: boolean; candidates: C[]; questions: string[];
}
interface Props<T extends object, C, V extends ScenarioValue<T, C>> {
  target: T; definition: string; disabled: boolean;
  title: string; notice: string; summary: ReactNode; question: string; generateLabel: string; adoptLabel: string;
  bindTarget: (target: T) => GovernanceTarget;
  selectTarget: (target: GovernanceTarget | null | undefined) => T | undefined;
  parse: (text: string) => V | null;
  validate: (value: V) => Promise<V>;
  candidateKey: (candidate: C) => string | number;
  renderCandidate: (candidate: C) => ReactNode;
  withKeyword?: (target: T, keyword: string) => T;
  sourceLabel?: string;
  onApply?: (candidate: C) => void;
}

/** Parent keys by project, object and full draft. Only persisted completed results can be adopted. */
export default function StructuredSuggestionPanel<T extends object, C, V extends ScenarioValue<T, C>>({
  target, definition, disabled, title, notice, summary, question, generateLabel, adoptLabel,
  bindTarget, selectTarget, parse, validate, candidateKey, renderCandidate, onApply, withKeyword, sourceLabel,
}: Props<T, C, V>) {
  const [keyword, setKeyword] = useState('');
  const [busy, setBusy] = useState(false);
  const [running, setRunning] = useState(false);
  const [error, setError] = useState('');
  const [suggestion, setSuggestion] = useState<V | null>(null);
  const [sessionId, setSessionId] = useState('');
  const live = useRef(true);
  const blocked = useRef(false);
  const permitted = useRef(!disabled); permitted.current = !disabled;
  const turn = useRef('');
  const session = useRef('');
  const selected = useRef<T>();
  const active = useRef(false);
  const epoch = useRef(0);
  const busyRef = useRef(false);
  const abort = useRef<AbortController>();

  useEffect(() => {
    live.current = true;
    return () => {
      live.current = false; epoch.current += 1; abort.current?.abort();
      if (active.current && turn.current) void agentChatApi.cancelTurn(turn.current).catch(() => undefined);
    };
  }, []);

  const reconcile = async (version = epoch.current) => {
    const view = readContinuation(await agentSessionApi.continuation(session.current), session.current);
    if (!live.current || epoch.current !== version) return;
    if (!view.turnId || (turn.current && turn.current !== view.turnId)
      || JSON.stringify(selectTarget(view.governanceTarget)) !== JSON.stringify(selected.current)) throw new Error('任务范围无法核对');
    turn.current = view.turnId;
    active.current = view.status === 'QUEUED' || view.status === 'RUNNING';
    setRunning(active.current);
    blocked.current = active.current;
    if (active.current) { setError('本轮仍在排队或推理，可停止或刷新核对。'); return; }
    if (view.blockingReason) throw new Error(view.blockingReason);
    if (view.status !== 'COMPLETED') { setError(`本轮状态：${view.status}；请核对原会话后重新生成。`); return; }
    const history = await agentSessionApi.history(session.current);
    if (!live.current || epoch.current !== version) return;
    const answers = history.filter(v => v.role === 'assistant' && v.turnId === view.turnId);
    const value = answers.length === 1 ? parse(answers[0].content) : null;
    if (!value || value.expectedDefinition !== definition || JSON.stringify(value.target) !== JSON.stringify(selected.current)) {
      throw new Error('候选或模型定义无法核对，请重新加载原编辑器。');
    }
    setSuggestion(value); setError(''); blocked.current = false;
  };
  const refresh = async () => {
    if (busyRef.current) return;
    const version = ++epoch.current;
    busyRef.current = true; setBusy(true); setSuggestion(null);
    try { await reconcile(version); }
    catch (e) { if (live.current && epoch.current === version) { blocked.current = true; setError(e instanceof Error ? e.message : '暂无法核对，请刷新。'); } }
    finally { if (live.current && epoch.current === version) { busyRef.current = false; setBusy(false); } }
  };
  const run = async () => {
    if (disabled || busyRef.current || blocked.current) return;
    const version = ++epoch.current;
    busyRef.current = true;
    setBusy(true); setError(''); setSuggestion(null); blocked.current = true;
    session.current = `ai-scenario-${Date.now()}-${Math.random().toString(36).slice(2)}`;
    setSessionId(session.current); turn.current = '';
    selected.current = withKeyword ? withKeyword(target, keyword.trim()) : target;
    const controller = new AbortController(); abort.current = controller;
    try {
      const result = await agentChatApi.submit({ sessionId: session.current,
        governanceTarget: bindTarget(selected.current), message: question });
      if (!live.current || epoch.current !== version) { void agentChatApi.cancelTurn(result.turnId).catch(() => undefined); return; }
      turn.current = result.turnId; active.current = true; setRunning(true);
      await streamTurnEvents({ turnId: result.turnId, signal: controller.signal }, { onEvent() {}, onError() {}, onComplete() {} });
      if (live.current && epoch.current === version) await reconcile(version);
    } catch (e) {
      if (live.current && epoch.current === version) { blocked.current = true; setError(e instanceof Error ? e.message : '提交或状态尚未确认，请刷新核对，避免重复生成。'); }
    } finally { if (live.current && epoch.current === version) { busyRef.current = false; setBusy(false); } }
  };
  const stop = async () => {
    if (!turn.current || !active.current || !permitted.current) return;
    epoch.current += 1; abort.current?.abort(); setSuggestion(null);
    await agentChatApi.cancelTurn(turn.current).catch(() => undefined);
    if (live.current) { busyRef.current = false; setBusy(false); await refresh(); }
  };
  const apply = async (choice: C) => {
    if (!suggestion || busyRef.current || !permitted.current || !onApply) return;
    const version = ++epoch.current; busyRef.current = true;
    setBusy(true);
    try {
      const checked = await validate(suggestion);
      if (!live.current || epoch.current !== version || !permitted.current) return;
      if (checked.expectedDefinition !== definition || JSON.stringify(checked.target) !== JSON.stringify(selected.current)
        || !checked.candidates.some(c => candidateKey(c) === candidateKey(choice))) throw new Error('候选已变化');
      setSuggestion(null); onApply(checked.candidates.find(c => candidateKey(c) === candidateKey(choice))!);
    } catch (e) { if (live.current && epoch.current === version) { setSuggestion(null); setError(e instanceof Error ? e.message : '候选已失效，请重新生成。'); } }
    finally { if (live.current && epoch.current === version) { busyRef.current = false; setBusy(false); } }
  };
  return <Card size="small" title={title}>
    <Space direction="vertical" className="w-full">
      <Alert type="info" message={notice} />
      <div>{summary}</div>
      {withKeyword && <Input value={keyword} onChange={e => setKeyword(e.target.value)} maxLength={64} disabled={busy} placeholder="字段/标准检索词（可空，读取有界目录）" />}
      <Space wrap>
        <Button onClick={() => void run()} loading={busy} disabled={disabled || !definition || blocked.current}>{generateLabel}</Button>
        {running && <Button onClick={() => void stop()} disabled={disabled}>停止本轮</Button>}
        {sessionId && <Button onClick={() => void refresh()} disabled={busy}>刷新核对</Button>}
        {sessionId && <a href={sessionLocation(sessionId)} target="_blank" rel="noopener noreferrer">查看原会话</a>}
      </Space>
      {error && <Alert type="error" message={error} />}
      {suggestion && <p>Skill v{suggestion.skillVersion} · {suggestion.truncated ? '候选目录已截断，请缩小检索范围' : (sourceLabel || '候选来自当前授权目录')}</p>}
      {suggestion?.questions.map(q => <Alert key={q} type="warning" message={q} />)}
      {suggestion && !suggestion.candidates.length && <p>没有可带入的候选，请补充业务说明或核对来源后重新生成。</p>}
      {suggestion?.candidates.map(c => <Card size="small" key={candidateKey(c)}>
        {renderCandidate(c)}
        {onApply && <Button disabled={disabled || busy} onClick={() => void apply(c)}>{adoptLabel}</Button>}
      </Card>)}
    </Space>
  </Card>;
}
