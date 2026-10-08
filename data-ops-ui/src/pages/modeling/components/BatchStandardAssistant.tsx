import { useEffect, useRef, useState } from 'react';
import { Alert, Button, Card, Checkbox, Drawer, Input, Space } from 'antd';
import { agentChatApi, agentSessionApi, streamTurnEvents } from '@/services/agent';
import { readContinuation, sessionLocation } from '@/services/agent/continuation';
import { parseStandardMatch, type StandardMatchSuggestion } from '@/services/agent/standardMatch';
import type { StandardMatchTarget } from '@/services/agent/governance';
import type { ColumnDraft } from '../editor/structureRules';

interface Props {
  modelId: number; definition: string; rows: ColumnDraft[]; selectedKeys: number[]; disabled: boolean;
  onClose: () => void;
  onApply: (key: number, original: string, patch: Pick<ColumnDraft, 'stdTypeId' | 'businessDescription'>) => void;
}
interface Item {
  row: ColumnDraft; original: string; sessionId: string; target: StandardMatchTarget;
  status: string; turnId?: string; value?: StandardMatchSuggestion; error?: string; includeDescription?: boolean;
}

/** The batch is a bounded local selection. Every execution and adopted result has its own original turn. */
export default function BatchStandardAssistant({ modelId, definition, rows, selectedKeys, disabled, onClose, onApply }: Props) {
  const [items, setItems] = useState<Item[]>([]);
  const [running, setRunning] = useState(false);
  const [applying, setApplying] = useState(false);
  const [keyword, setKeyword] = useState('');
  const alive = useRef(true);
  const stopped = useRef(false);
  const lock = useRef(false);
  const abort = useRef<AbortController>();
  const activeTurn = useRef('');
  const current = useRef({ rows, disabled, definition }); current.current = { rows, disabled, definition };
  const selected = rows.filter(r => selectedKeys.includes(r.key));
  const validSelection = selected.length > 0 && selected.length <= 5 && selected.every(r =>
    /^[A-Za-z0-9_][A-Za-z0-9_$]{0,127}$/.test(r.columnName) && !!r.dataType.trim() && r.dataType.length <= 64
    && (r.businessDescription?.length ?? 0) <= 512);
  const update = (key: number, patch: Partial<Item>) => {
    if (alive.current) setItems(previous => previous.map(item => item.row.key === key ? { ...item, ...patch } : item));
  };
  useEffect(() => () => {
    alive.current = false; stopped.current = true; abort.current?.abort();
    if (activeTurn.current) void agentChatApi.cancelTurn(activeTurn.current).catch(() => undefined);
  }, []);
  useEffect(() => {
    if (disabled) { stopped.current = true; abort.current?.abort();
      if (activeTurn.current) void agentChatApi.cancelTurn(activeTurn.current).catch(() => undefined);
    }
  }, [disabled]);
  const unchanged = (item: Item) => !current.current.disabled && current.current.definition === definition
    && JSON.stringify(current.current.rows.find(r => r.key === item.row.key)) === item.original;

  const inspect = async (item: Item): Promise<boolean> => {
    const view = readContinuation(await agentSessionApi.continuation(item.sessionId), item.sessionId);
    if (!alive.current) return false;
    if (!view.turnId || (item.turnId && view.turnId !== item.turnId)
      || JSON.stringify(view.governanceTarget?.standardMatch) !== JSON.stringify(item.target)) throw new Error('原轮次或字段范围无法核对');
    if (['QUEUED', 'RUNNING', 'WAITING_INPUT'].includes(view.status ?? '')) {
      update(item.row.key, { turnId: view.turnId, status: '待核对', error: '原轮尚未完成，请查看原会话或刷新；本批不重复提交。' });
      return false;
    }
    if (view.status !== 'COMPLETED' || view.blockingReason) {
      update(item.row.key, { status: view.status ?? '待核对', error: view.blockingReason || '本项未完成，保留原轮次结果。' });
      return true;
    }
    const history = await agentSessionApi.history(item.sessionId);
    if (!alive.current) return false;
    const answers = history.filter(h => h.role === 'assistant' && h.turnId === view.turnId);
    const value = answers.length === 1 ? parseStandardMatch(answers[0].content) : null;
    if (!value || value.expectedDefinition !== definition || JSON.stringify(value.target) !== JSON.stringify(item.target)) {
      throw new Error('完成轮的唯一候选无法核对');
    }
    update(item.row.key, { status: '已生成', value, error: undefined });
    return true;
  };
  const start = async () => {
    if (lock.current || disabled || !validSelection || items.length) return;
    lock.current = true; stopped.current = false; setRunning(true);
    const batch: Item[] = selected.map(row => ({ row: { ...row }, original: JSON.stringify(row), status: '等待生成',
      sessionId: `ai-standard-${crypto.randomUUID()}`, target: { modelId, columnName: row.columnName,
        dataType: row.dataType, businessDescription: row.businessDescription ?? '', keyword: keyword.trim() } }));
    setItems(batch);
    try {
      for (const item of batch) {
        if (stopped.current || !alive.current || !unchanged(item)) break;
        update(item.row.key, { status: '提交中' });
        const controller = new AbortController(); abort.current = controller;
        try {
          const receipt = await agentChatApi.submit({ sessionId: item.sessionId,
            governanceTarget: { purpose: 'STANDARD_MATCH', standardMatch: item.target },
            message: '为本字段匹配类型标准并准备可选业务说明草稿；依据不足列出待确认问题。' });
          item.turnId = receipt.turnId;
          update(item.row.key, { turnId: receipt.turnId, status: '生成中' });
          if (!alive.current || stopped.current || !unchanged(item)) {
            await agentChatApi.cancelTurn(receipt.turnId).catch(() => undefined); break;
          }
          activeTurn.current = receipt.turnId;
          await streamTurnEvents({ turnId: receipt.turnId, signal: controller.signal }, { onEvent() {}, onError() {}, onComplete() {} });
          if (!await inspect(item)) break;
          activeTurn.current = '';
        } catch {
          update(item.row.key, { status: '待核对', error: '提交或结果未确认，已停止后续项。请刷新核对原轮次。' });
          break;
        }
      }
    } finally {
      if (alive.current) { lock.current = false; setRunning(false); }
    }
  };
  const stop = async () => {
    stopped.current = true; abort.current?.abort();
    const turnId = activeTurn.current;
    if (turnId) await agentChatApi.cancelTurn(turnId).catch(() => undefined);
    // The command reply is not terminal evidence. Each row remains refreshable.
  };
  const apply = async (item: Item, standardId?: number) => {
    if (lock.current || !item.value || !unchanged(item)) return;
    lock.current = true; setApplying(true);
    try {
      const checked = await agentChatApi.validateStandardMatch(item.value);
      if (!alive.current || !unchanged(item)) return;
      if (checked.expectedDefinition !== definition || JSON.stringify(checked.target) !== JSON.stringify(item.target)
        || (standardId != null && !checked.candidates.some(c => c.standardId === standardId))) throw new Error('候选范围已变化');
      const patch: Pick<ColumnDraft, 'stdTypeId' | 'businessDescription'> = {};
      if (standardId != null) patch.stdTypeId = standardId;
      if (item.includeDescription && checked.fieldDescription) patch.businessDescription = checked.fieldDescription;
      if (Object.keys(patch).length) {
        onApply(item.row.key, item.original, patch);
        update(item.row.key, { status: '已带入，待人工保存', value: undefined });
      }
    } catch {
      update(item.row.key, { status: '已失效', value: undefined, error: '候选或权限已变化，请关闭后重新选择生成。' });
    } finally { lock.current = false; if (alive.current) setApplying(false); }
  };
  return <Drawer open title="批量 AI 标准辅助" width={680} onClose={onClose} destroyOnClose>
    <Space direction="vertical" className="w-full">
      <Alert type="info" message="最多 5 个选定字段逐项生成。每项独立核对与带入，带入后仍需人工保存；字段说明属于 AI 草稿。" />
      {!validSelection && <Alert type="warning" message="请选择 1–5 个字段，并补全合法字段名、类型及长度不超过 512 字的业务说明。" />}
      {!items.length && <><p>已选字段：{selected.map(r => r.columnName).join('、')}</p>
        <Input value={keyword} onChange={e => setKeyword(e.target.value)} maxLength={64} placeholder="标准检索词（可空）" />
        <Button disabled={disabled || !validSelection || !definition} onClick={() => void start()}>生成所选字段候选</Button></>}
      {running && <Button onClick={() => void stop()}>停止本批后续生成及当前轮</Button>}
      {items.map(item => <Card key={item.row.key} size="small" title={`${item.row.columnName} · ${item.status}`}>
        <p>当前类型引用：{item.row.stdTypeId ?? '未绑定'} · 当前说明：{item.row.businessDescription || '未填写'}</p>
        {!unchanged(item) && <Alert type="warning" message="字段草稿或编辑范围已变化，本项不可带入。" />}
        {item.error && <Alert type="warning" message={item.error} />}
        <Space><a href={sessionLocation(item.sessionId)} target="_blank" rel="noopener noreferrer">核对原会话</a>
          <Button disabled={running || applying} onClick={() => void inspect(item).catch(() => update(item.row.key, { error: '读取失败，请重试核对。' }))}>刷新本项</Button></Space>
        {item.value?.questions.map(q => <Alert key={q} type="warning" message={q} />)}
        {item.value && <p>Skill v{item.value.skillVersion} · {item.value.truncated ? '标准目录已截断，请核对范围' : '授权类型标准目录'}</p>}
        {item.value?.fieldDescription && <Checkbox checked={item.includeDescription ?? false} disabled={running || applying}
          onChange={e => update(item.row.key, { includeDescription: e.target.checked })}>同时带入说明：{item.value.fieldDescription}</Checkbox>}
        {item.value?.candidates.map(c => <div key={c.standardId}><p>{c.name}（{c.code}）v{c.version} · {c.stdType}：{c.reason}</p>
          <Button disabled={running || applying || !unchanged(item)} onClick={() => void apply(item, c.standardId)}>带入此类型引用</Button></div>)}
        {item.value && !item.value.candidates.length && <p>没有匹配的类型标准。</p>}
        {item.value?.fieldDescription && <Button disabled={running || applying || !unchanged(item) || !item.includeDescription} onClick={() => void apply(item)}>仅带入说明</Button>}
      </Card>)}
    </Space>
  </Drawer>;
}
