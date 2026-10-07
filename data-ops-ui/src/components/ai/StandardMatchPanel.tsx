import { useEffect, useRef, useState } from 'react';
import { Alert, Button, Card, Input, Space } from 'antd';
import { agentChatApi, agentSessionApi, streamTurnEvents } from '@/services/agent';
import { readContinuation, sessionLocation } from '@/services/agent/continuation';
import { parseStandardMatch, type StandardMatchSuggestion } from '@/services/agent/standardMatch';
import type { StandardMatchTarget } from '@/services/agent/governance';

interface Props {
  target: StandardMatchTarget;
  definition: string;
  disabled: boolean;
  onApply: (id: number) => void;
}

/** Parent keys this panel by project, editor row and draft; no result crosses that lifetime. */
export default function StandardMatchPanel({ target, definition, disabled, onApply }: Props) {
  const [keyword, setKeyword] = useState('');
  const [busy, setBusy] = useState(false);
  const [running, setRunning] = useState(false);
  const [error, setError] = useState('');
  const [suggestion, setSuggestion] = useState<StandardMatchSuggestion | null>(null);
  const [sessionId, setSessionId] = useState('');
  const live = useRef(true);
  const blocked = useRef(false);
  const permitted = useRef(!disabled); permitted.current = !disabled;
  const turn = useRef('');
  const session = useRef('');
  const selected = useRef<StandardMatchTarget>();
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
      || JSON.stringify(view.governanceTarget?.standardMatch) !== JSON.stringify(selected.current)) throw new Error('任务范围无法核对');
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
    const value = answers.length === 1 ? parseStandardMatch(answers[0].content) : null;
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
    session.current = `ai-std-${Date.now()}-${Math.random().toString(36).slice(2)}`;
    setSessionId(session.current); turn.current = '';
    selected.current = { ...target, keyword: keyword.trim() };
    const controller = new AbortController(); abort.current = controller;
    try {
      const result = await agentChatApi.submit({ sessionId: session.current,
        governanceTarget: { purpose: 'STANDARD_MATCH', standardMatch: selected.current },
        message: '为绑定的未保存字段匹配类型标准；缺少业务依据时列出待确认项。' });
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
  const apply = async (id: number) => {
    if (!suggestion || busyRef.current || !permitted.current) return;
    const version = ++epoch.current; busyRef.current = true;
    setBusy(true);
    try {
      const checked = await agentChatApi.validateStandardMatch(suggestion);
      if (!live.current || epoch.current !== version || !permitted.current) return;
      if (checked.expectedDefinition !== definition || !checked.candidates.some(c => c.standardId === id)) throw new Error('候选已变化');
      setSuggestion(null); onApply(id);
    } catch (e) { if (live.current && epoch.current === version) { setSuggestion(null); setError(e instanceof Error ? e.message : '候选已失效，请重新生成。'); } }
    finally { if (live.current && epoch.current === version) { busyRef.current = false; setBusy(false); } }
  };
  return <Card size="small" title="AI 类型标准匹配">
    <Space direction="vertical" className="w-full">
      <Alert type="info" message="依据当前未保存字段草稿；带入后仍需在原编辑器人工保存。首期仅匹配类型标准。" />
      <p>{target.columnName} · {target.dataType} · {target.businessDescription || '尚未填写业务说明，可在字段中补充。'}</p>
      <Input value={keyword} onChange={e => setKeyword(e.target.value)} maxLength={64} disabled={busy} placeholder="标准检索词（可空，最多读取 20 项）" />
      <Space wrap>
        <Button onClick={() => void run()} loading={busy} disabled={disabled || !definition || blocked.current}>生成类型候选</Button>
        {running && <Button onClick={() => void stop()} disabled={disabled}>停止本轮</Button>}
        {sessionId && <Button onClick={() => void refresh()} disabled={busy}>刷新核对</Button>}
        {sessionId && <a href={sessionLocation(sessionId)} target="_blank" rel="noopener noreferrer">查看原会话</a>}
      </Space>
      {error && <Alert type="error" message={error} />}
      {suggestion && <p>Skill v{suggestion.skillVersion} · {suggestion.truncated ? '候选目录已截断，请缩小检索范围' : '候选来自当前授权目录'}</p>}
      {suggestion?.questions.map(q => <Alert key={q} type="warning" message={q} />)}
      {suggestion && !suggestion.candidates.length && <p>没有可带入的候选，请补充字段业务说明或调整检索词后重新生成。</p>}
      {suggestion?.candidates.map(c => <Card size="small" key={c.standardId}>
        <p>{c.name}（{c.code}） · v{c.version} · {c.stdType}</p><p>{c.reason}</p>
        <Button disabled={disabled || busy} onClick={() => void apply(c.standardId)}>带入类型引用</Button>
      </Card>)}
    </Space>
  </Card>;
}
