import { useEffect, useLayoutEffect, useRef, useState, type ReactNode } from 'react';
import { Alert, Button, Card, Input, Space } from 'antd';
import { agentChatApi, agentSessionApi, streamTurnEvents } from '@/services/agent';
import { readContinuation, sessionLocation } from '@/services/agent/continuation';
import { sameScenarioTarget, type GovernanceTarget } from '@/services/agent/governance';

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
  onActivityChange?: (blocked: boolean) => void;
  verifyResult?: (value: V) => Promise<void>;
}

/** Parents own project/form scope; the panel also invalidates its own target, definition and permission lifetime. */
export default function StructuredSuggestionPanel<T extends object, C, V extends ScenarioValue<T, C>>(props: Props<T, C, V>) {
  return <ScopedStructuredSuggestionPanel key={JSON.stringify([props.target, props.definition, props.disabled])} {...props} />;
}

function ScopedStructuredSuggestionPanel<T extends object, C, V extends ScenarioValue<T, C>>({
  target, definition, disabled, title, notice, summary, question, generateLabel, adoptLabel,
  bindTarget, selectTarget, parse, validate, candidateKey, renderCandidate, onApply, withKeyword, sourceLabel, onActivityChange, verifyResult,
}: Props<T, C, V>) {
  const [keyword, setKeyword] = useState('');
  const [busy, setBusy] = useState(false);
  const [running, setRunning] = useState(false);
  const [error, setError] = useState('');
  const [suggestion, setSuggestion] = useState<V | null>(null);
  const [sessionId, setSessionId] = useState('');
  const [stopping, setStopping] = useState(false);
  const stoppingRef = useRef(false);
  const [checking, setChecking] = useState(false);
  const checkingRef = useRef(false);
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
  const inputTarget = withKeyword ? withKeyword(target, keyword.trim()) : target;
  const currentTarget = useRef(inputTarget); currentTarget.current = inputTarget;

  useEffect(() => { onActivityChange?.(busy || running || blocked.current); }, [busy, running, error, sessionId, onActivityChange]);
  useEffect(() => () => { onActivityChange?.(false); }, [onActivityChange]);

  useLayoutEffect(() => {
    live.current = true;
    return () => {
      live.current = false; epoch.current += 1; abort.current?.abort();
      if (active.current && turn.current && !stoppingRef.current) void agentChatApi.cancelTurn(turn.current).catch(() => undefined);
    };
  }, []);

  const reconcile = async (version = epoch.current) => {
    checkingRef.current = true; setChecking(true);
    try {
      const view = readContinuation(await agentSessionApi.continuation(session.current), session.current);
      if (!live.current || epoch.current !== version) return;
      if (!view.turnId || (turn.current && turn.current !== view.turnId)
        || !sameScenarioTarget(selectTarget(view.governanceTarget), selected.current)) throw new Error('任务范围无法核对');
      turn.current = view.turnId;
      active.current = view.status === 'QUEUED' || view.status === 'RUNNING';
      setRunning(active.current);
      blocked.current = active.current;
      if (active.current) { setError('本轮仍在排队或推理，可停止或刷新核对。'); return; }
      if (view.blockingReason) throw new Error('任务暂不可继续');
      if (view.status !== 'COMPLETED') {
        const terminal = view.status === 'FAILED' || view.status === 'CANCELLED' || view.status === 'INTERRUPTED';
        blocked.current = !terminal;
        setError(view.status === 'FAILED' ? '本轮生成失败，请核对原会话后重新生成。'
          : view.status === 'CANCELLED' ? '本轮生成已停止，可重新生成。'
            : view.status === 'INTERRUPTED' ? '本轮生成已中断，请核对原会话后重新生成。'
              : view.status === 'WAITING_INPUT' ? '本轮有待答问题，请在原会话完成应答后刷新核对。'
                : '本轮状态尚未确认，请刷新核对或查看原会话。');
        return;
      }
      if (!sameScenarioTarget(currentTarget.current, selected.current)) {
        setError('检索条件已变化，旧候选已失效，请重新生成。');
        return;
      }
      const history = await agentSessionApi.history(session.current);
      if (!live.current || epoch.current !== version) return;
      const answers = history.filter(v => v.role === 'assistant' && v.turnId === view.turnId);
      const value = answers.length === 1 ? parse(answers[0].content) : null;
      if (!value || value.expectedDefinition !== definition || !sameScenarioTarget(value.target, selected.current)) {
        throw new Error('候选或模型定义无法核对，请重新加载原编辑器。');
      }
      if (verifyResult) {
        try { await verifyResult(value); }
        catch {
          if (live.current && epoch.current === version) {
            setSuggestion(null); blocked.current = false;
            setError('来源或发布证据暂无法核对，请重新准备版本与证据。');
          }
          return;
        }
        if (!live.current || epoch.current !== version || !permitted.current) return;
      }
      setSuggestion(value); setError(''); blocked.current = false;
    } finally {
      if (live.current && epoch.current === version) { checkingRef.current = false; setChecking(false); }
    }
  };
  const refresh = async () => {
    if (!permitted.current || busyRef.current) return;
    const version = ++epoch.current;
    busyRef.current = true; setBusy(true); setSuggestion(null);
    try { await reconcile(version); }
    catch { if (live.current && epoch.current === version) { blocked.current = true; setError('本轮结果暂无法核对，请刷新核对或查看原会话。'); } }
    finally { if (live.current && epoch.current === version) { busyRef.current = false; setBusy(false); } }
  };
  const run = async () => {
    if (!permitted.current || !definition || busyRef.current || blocked.current) return;
    const version = ++epoch.current;
    busyRef.current = true;
    setBusy(true); setError(''); setSuggestion(null); blocked.current = true;
    session.current = `ai-scenario-${Date.now()}-${Math.random().toString(36).slice(2)}`;
    setSessionId(session.current); turn.current = '';
    selected.current = currentTarget.current;
    const controller = new AbortController(); abort.current = controller;
    try {
      const result = await agentChatApi.submit({ sessionId: session.current,
        governanceTarget: bindTarget(selected.current), message: question });
      if (!result || typeof result.turnId !== 'string' || !result.turnId.trim()) throw new Error('提交回执缺失');
      if (!live.current || epoch.current !== version) { void agentChatApi.cancelTurn(result.turnId).catch(() => undefined); return; }
      turn.current = result.turnId; active.current = true; setRunning(true);
      await streamTurnEvents({ turnId: result.turnId, signal: controller.signal }, { onEvent() {}, onError() {}, onComplete() {} });
      if (live.current && epoch.current === version) await reconcile(version);
    } catch {
      if (live.current && epoch.current === version) { blocked.current = true; setError('提交或结果尚未确认，请刷新核对或查看原会话，避免重复生成。'); }
    } finally { if (live.current && epoch.current === version) { busyRef.current = false; setBusy(false); } }
  };
  const stop = async () => {
    if (!turn.current || !active.current || !permitted.current || stoppingRef.current || checkingRef.current) return;
    const version = ++epoch.current;
    const turnId = turn.current;
    stoppingRef.current = true; setStopping(true);
    busyRef.current = true; setBusy(true); blocked.current = true;
    abort.current?.abort(); setSuggestion(null);
    try {
      await agentChatApi.cancelTurn(turnId).catch(() => undefined);
      if (live.current && epoch.current === version) await reconcile(version);
    } catch {
      if (live.current && epoch.current === version) { blocked.current = true; setError('停止结果尚未确认，请刷新核对或查看原会话。'); }
    } finally {
      if (live.current && epoch.current === version) {
        stoppingRef.current = false; setStopping(false); busyRef.current = false; setBusy(false);
      }
    }
  };
  const apply = async (choice: C) => {
    if (!suggestion || busyRef.current || !permitted.current || !onApply
      || !sameScenarioTarget(currentTarget.current, selected.current)) return;
    const version = ++epoch.current; busyRef.current = true;
    setBusy(true);
    try {
      const checked = await validate(suggestion);
      if (!live.current || epoch.current !== version || !permitted.current) return;
      if (checked.expectedDefinition !== definition || !sameScenarioTarget(checked.target, selected.current)
        || !checked.candidates.some(c => candidateKey(c) === candidateKey(choice))) throw new Error('候选已变化');
      setSuggestion(null); onApply(checked.candidates.find(c => candidateKey(c) === candidateKey(choice))!);
    } catch { if (live.current && epoch.current === version) { setSuggestion(null); setError('候选或来源暂无法核对，请重新加载原编辑器后生成。'); } }
    finally { if (live.current && epoch.current === version) { busyRef.current = false; setBusy(false); } }
  };
  return <Card size="small" title={title}>
    <Space direction="vertical" className="w-full">
      <Alert type="info" message={notice} />
      <div>{summary}</div>
      {withKeyword && <Input value={keyword} onChange={e => {
        if (busyRef.current) return;
        setKeyword(e.target.value);
        currentTarget.current = withKeyword(target, e.target.value.trim());
        setSuggestion(null);
        if (selected.current && !sameScenarioTarget(currentTarget.current, selected.current)) {
          setError(blocked.current ? '检索条件已变化，旧候选已失效；请先停止或刷新核对本轮状态。' : '检索条件已变化，旧候选已失效，请重新生成。');
        }
      }} maxLength={64} disabled={busy || disabled} placeholder="字段/标准检索词（可空，读取有界目录）" />}
      <Space wrap>
        <Button onClick={() => void run()} loading={busy} disabled={disabled || busy || !definition || blocked.current}>{generateLabel}</Button>
        {running && <Button onClick={() => void stop()} disabled={disabled || stopping || checking} loading={stopping}>停止本轮</Button>}
        {sessionId && <Button onClick={() => void refresh()} disabled={busy || disabled}>刷新核对</Button>}
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
