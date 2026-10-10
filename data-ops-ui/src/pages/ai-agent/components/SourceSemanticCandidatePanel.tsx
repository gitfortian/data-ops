import React from 'react';
import { Alert, Button, Checkbox, Input, Select, Space, Tag, Typography } from 'antd';
import {
  sourceSemanticCandidates, type CandidatePreflight, type CandidateView,
  type SemanticCandidate,
} from '@/services/agent/sourceSemanticCandidates';

/**
 * F-039 candidate review is deliberately read-only with respect to Semantic.
 * Each selection is explicit, missing dependencies remain visible, and editing
 * invalidates the previous preflight. No "Save to Semantic" action exists here.
 */
const SourceSemanticCandidatePanel: React.FC<{ taskId: string }> = ({ taskId }) => {
  const [view, setView] = React.useState<CandidateView>();
  const [edit, setEdit] = React.useState<SemanticCandidate>();
  const [filter, setFilter] = React.useState('ALL');
  const [page, setPage] = React.useState(1);
  const [error, setError] = React.useState('');
  const [busy, setBusy] = React.useState(false);
  const [preflight, setPreflight] = React.useState<CandidatePreflight>();
  const [questionId, setQuestionId] = React.useState('business_domain');
  const [answer, setAnswer] = React.useState('');
  const [mergeTarget, setMergeTarget] = React.useState<string>();
  const [mergeConfirmed, setMergeConfirmed] = React.useState(false);

  React.useEffect(() => {
    let mounted = true;
    setView(undefined);
    sourceSemanticCandidates.read(taskId).then((response) => {
      if (mounted) setView(response);
    }).catch((e) => { if (mounted) setError(String((e as Error).message || e)); });
    return () => { mounted = false; };
  }, [taskId]);

  const mutate = async (operation: () => Promise<CandidateView>) => {
    setBusy(true);
    setError('');
    try {
      const next = await operation();
      setView(next);
      setPreflight(undefined);
      setEdit(undefined);
      setMergeTarget(undefined);
      setMergeConfirmed(false);
    } catch (e) {
      setError(String((e as Error).message || e));
    } finally {
      setBusy(false);
    }
  };

  if (!view) return <Alert type="info" showIcon
    message={error || '读取所有原分片的来源证据与 Semantic 目录，核验后才可生成候选。'} />;

  const { review } = view;
  const matches = new Map(view.matches.map((m) => [m.candidateId, m]));
  const category = (item: SemanticCandidate) => {
    const found = matches.get(item.id);
    if (found?.ambiguous) return 'CONFLICT';
    if (item.reuseId != null) return 'REUSE';
    if (!item.code || (item.kind === 'FIELD' && !item.typeId)
        || (item.kind === 'PROCESS' && !item.grain)) return 'INSUFFICIENT';
    return 'NEW';
  };
  const filtered = review.candidates.filter((item) => filter === 'ALL' || category(item) === filter);
  const pages = Math.max(1, Math.ceil(filtered.length / 10));
  const candidates = filtered.slice((page - 1) * 10, page * 10);
  const typeOptions = view.catalogEntries.filter((c) => c.kind === 'TYPE'
    && c.status === 'ENABLED' && c.id != null)
    .map((c) => ({ value: c.id!, label: `${c.name} (${c.code}) v${c.version}` }));
  const unitOptions = view.catalogEntries.filter((c) => c.kind === 'UNIT'
    && c.status === 'ENABLED' && c.id != null)
    .map((c) => ({ value: c.id!, label: `${c.name} (${c.code}) v${c.version}` }));
  const set = (values: Partial<SemanticCandidate>) => setEdit((old) => old ? { ...old, ...values } : old);
  const updateSelection = (candidateId: string, checked: boolean) => {
    const ids = checked
      ? Array.from(new Set([...review.selectedIds, candidateId]))
      : review.selectedIds.filter((id) => id !== candidateId);
    mutate(() => sourceSemanticCandidates.select(taskId, review.revision, ids));
  };

  return (
    <section aria-label="F-039 集中语义候选复核" style={{ marginTop: 16 }}>
      <Typography.Title level={5}>3/5 · 集中候选审阅与只读预检</Typography.Title>
      <Typography.Paragraph type="secondary">
        已核验来源的每列先保留独立候选；物理名称不等于业务同义，任何复用与归并须人工确认。
        候选未发布、未创建正式对象。当前修订 {review.revision}，
        已选择 {review.selectedIds.length}/{review.candidates.length} 项。
      </Typography.Paragraph>
      {error && <Alert type="warning" showIcon message={error} style={{ marginBottom: 10 }} />}
      <Space wrap style={{ marginBottom: 10 }}>
        <Select value={filter} style={{ width: 170 }} onChange={(v) => { setFilter(v); setPage(1); }}
          options={[
            { value: 'ALL', label: '全部候选' },
            { value: 'REUSE', label: '复用' },
            { value: 'NEW', label: '新增草稿' },
            { value: 'CONFLICT', label: '冲突' },
            { value: 'INSUFFICIENT', label: '证据不足' },
          ]} />
        <Button size="small" disabled={page <= 1} onClick={() => setPage((p) => p - 1)}>上一页</Button>
        <span>{page}/{pages}</span>
        <Button size="small" disabled={page >= pages} onClick={() => setPage((p) => p + 1)}>下一页</Button>
      </Space>
      {candidates.map((item) => (
        <div key={item.id} style={{ borderTop: '1px solid #e6eaf0', padding: '10px 0' }}>
          <Space wrap>
            <Checkbox checked={review.selectedIds.includes(item.id)} disabled={busy}
              onChange={(e) => updateSelection(item.id, e.target.checked)}>选择</Checkbox>
            <Tag>{item.kind}</Tag>
            <Tag color={category(item) === 'CONFLICT' ? 'orange' : undefined}>{category(item)}</Tag>
            <Typography.Text strong>{item.name}</Typography.Text>
            <Typography.Text type="secondary">{item.code || '待填编码'}</Typography.Text>
            <Button size="small" disabled={busy} onClick={() => setEdit(item)}>核对/编辑</Button>
          </Space>
          <div style={{ marginTop: 5 }}>
            <Typography.Text type="secondary">
              来源：{item.evidence.map((e) => `${e.tableAssetKey}/${e.column || '表'} [${e.sourceChunkId.slice(0, 8)}]`).join(' · ')}
            </Typography.Text>
          </div>
          {item.dependencies.length > 0 && <div>
            <Typography.Text type="secondary">
              依赖：{item.dependencies.map((d) => review.candidates.find((c) => c.id === d)?.name || d).join(' → ')}
            </Typography.Text>
          </div>}
          {matches.get(item.id)?.matches.length ? <div>
            <Typography.Text type="warning">
              目录匹配（不自动复用）：{matches.get(item.id)?.matches.map((m) =>
                `${m.name} · ${m.code} (ID ${m.id ?? '码集'}, v${m.version}, ${m.status})`).join('；')}
            </Typography.Text>
          </div> : null}
        </div>
      ))}
      {edit && <div style={{ border: '1px solid #d9d9d9', padding: 12, marginTop: 12 }}>
        <Typography.Text strong>修订 {edit.kind} · {edit.name}</Typography.Text>
        <Space direction="vertical" style={{ width: '100%', marginTop: 8 }}>
          <Input value={edit.name} maxLength={128} addonBefore="名称"
            onChange={(e) => set({ name: e.target.value })} />
          <Input value={edit.code ?? ''} maxLength={64} addonBefore="编码"
            onChange={(e) => set({ code: e.target.value || null })} />
          <Input.TextArea rows={2} maxLength={512} placeholder="业务定义/说明（需核对来源）"
            value={edit.description ?? ''} onChange={(e) => set({ description: e.target.value || null })} />
          {edit.kind === 'PROCESS' && <>
            <Input value={edit.grain ?? ''} maxLength={64} addonBefore="业务粒度"
              onChange={(e) => set({ grain: e.target.value || null })} />
            <Select value={edit.role} allowClear placeholder="过程类型" style={{ width: '100%' }}
              options={['FACT', 'DIMENSION'].map((s) => ({ value: s, label: s }))}
              onChange={(v) => set({ role: v || null })} />
          </>}
          {edit.kind === 'FIELD' && <>
            <Select value={edit.role} allowClear placeholder="字段角色" style={{ width: '100%' }}
              options={['PROCESS', 'DIMENSION', 'METRIC'].map((s) => ({ value: s, label: s }))}
              onChange={(v) => set({ role: v || null })} />
            <Select value={edit.typeId} showSearch optionFilterProp="label" allowClear
              placeholder="必须选择已启用 TYPE" style={{ width: '100%' }} options={typeOptions}
              onChange={(v) => set({ typeId: v ?? null })} />
            {edit.role === 'METRIC' && <Select value={edit.unitId} showSearch optionFilterProp="label"
              allowClear placeholder="度量必须选择已启用 UNIT" style={{ width: '100%' }}
              options={unitOptions} onChange={(v) => set({ unitId: v ?? null })} />}
          </>}
          <Space>
            <Select allowClear showSearch optionFilterProp="label" style={{ minWidth: 280 }}
              placeholder="选取已存在的精确匹配定义（可选）"
              value={edit.reuseId} options={(matches.get(edit.id)?.matches || [])
                .filter((m) => m.id != null).map((m) => ({
                  value: m.id!, label: `${m.name} · ${m.code} (v${m.version})`,
                }))}
              onChange={(value) => {
                const found = matches.get(edit.id)?.matches.find((m) => m.id === value);
                set({ reuseId: value ?? null, reuseVersion: found?.version ?? null,
                  code: found?.code ?? edit.code, name: found?.name ?? edit.name });
              }} />
          </Space>
          {edit.kind === 'FIELD' && <>
            <Typography.Text type="secondary">
              同名不等于同义；只有业务负责人确认两列含义相同才允许归并。
            </Typography.Text>
            <Select allowClear showSearch optionFilterProp="label" style={{ width: '100%' }}
              placeholder="选择另一条尚未选用的标准字段候选作人工归并"
              value={mergeTarget}
              options={review.candidates.filter((c) => c.kind === 'FIELD' && c.id !== edit.id
                && !review.selectedIds.includes(c.id) && !review.selectedIds.includes(edit.id))
                .map((c) => ({ value: c.id, label: `${c.name} (${c.evidence[0]?.tableAssetKey})` }))}
              onChange={(v) => { setMergeTarget(v); setMergeConfirmed(false); }} />
            <Checkbox checked={mergeConfirmed} onChange={(e) => setMergeConfirmed(e.target.checked)}>
              我已核对两个来源列确实表达同一业务概念；合并后重新审查编码、角色与标准引用
            </Checkbox>
            <Button disabled={!mergeTarget || !mergeConfirmed || busy} onClick={() =>
              mutate(() => sourceSemanticCandidates.merge(taskId, review.revision, edit.id, mergeTarget!))}>
              合并来源概念（清除旧选择）
            </Button>
            {edit.evidence.length > 1 && <Space wrap>
              {edit.evidence.map((e) => <Button key={`${e.tableAssetKey}/${e.column}`}
                disabled={busy || review.selectedIds.includes(edit.id)} size="small"
                onClick={() => mutate(() => sourceSemanticCandidates.split(taskId,
                  review.revision, edit.id, e.tableAssetKey, e.column!))}>
                拆出 {e.tableAssetKey}/{e.column}
              </Button>)}
            </Space>}
          </>}
          <Space>
            <Button type="primary" loading={busy} onClick={() =>
              mutate(() => sourceSemanticCandidates.edit(taskId, edit, review.revision))}>提交新修订</Button>
            <Button onClick={() => setEdit(undefined)}>取消编辑</Button>
          </Space>
        </Space>
      </div>}
      <div style={{ borderTop: '1px solid #ddd', paddingTop: 12, marginTop: 12 }}>
        <Typography.Text strong>集中业务答疑（修订记录，不自动覆盖字段事实）</Typography.Text>
        <Space.Compact style={{ width: '100%', marginTop: 8 }}>
          <Input value={questionId} style={{ maxWidth: 160 }} maxLength={100}
            onChange={(e) => setQuestionId(e.target.value)} placeholder="问题标识" />
          <Input value={answer} maxLength={512} onChange={(e) => setAnswer(e.target.value)}
            placeholder="确认的业务定义、粒度或歧义处理决定" />
          <Button disabled={!answer.trim() || busy} onClick={() => mutate(() =>
            sourceSemanticCandidates.answer(taskId, review.revision, questionId, answer))}>记录答案</Button>
        </Space.Compact>
        {Object.entries(review.answers).map(([key, value]) =>
          <div key={key} style={{ marginTop: 4 }}><Tag>{key}</Tag>{value}</div>)}
      </div>
      <Space style={{ marginTop: 12 }}>
        <Button type="primary" disabled={busy || !review.selectedIds.length}
          onClick={async () => {
            setBusy(true);setError('');
            try { setPreflight(await sourceSemanticCandidates.preflight(taskId, review.revision)); }
            catch (e) { setError(String((e as Error).message || e)); }
            finally { setBusy(false); }
          }}>依赖闭包及只读预检</Button>
        <Button disabled={busy} onClick={() => mutate(() => sourceSemanticCandidates.read(taskId))}>
          重新核验目录</Button>
      </Space>
      {preflight && <Alert style={{ marginTop: 10 }} showIcon
        type={preflight.ready ? 'success' : 'warning'}
        message={preflight.ready ? '只读预检通过（不是正式保存授权）' : '未满足预检条件'}
        description={<>
          <div>实际依赖闭包：{preflight.closure.length} 项，明确选择：{preflight.selected.length} 项</div>
          {preflight.blockers.map((blocker) => <div key={blocker}>{blocker}</div>)}
          {preflight.ready && <Typography.Text copyable={{ text: preflight.payloadDigest }}>
            Payload digest: {preflight.payloadDigest}
          </Typography.Text>}
        </>} />}
    </section>
  );
};
export default SourceSemanticCandidatePanel;
