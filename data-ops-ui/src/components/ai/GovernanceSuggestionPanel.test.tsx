import React from 'react';
import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import GovernanceSuggestionPanel from './GovernanceSuggestionPanel';
import { agentChatApi, agentSessionApi, streamTurnEvents } from '@/services/agent';
import type { SaveRulePayload } from '@/services/data-quality';
import { Form } from 'antd';

jest.mock('@/services/agent', () => ({ agentChatApi: { submit: jest.fn(), cancelTurn: jest.fn() },
  agentSessionApi: { cancel: jest.fn(), continuation: jest.fn(), history: jest.fn() }, streamTurnEvents: jest.fn() }));

const hash = 'a'.repeat(64);
const artifact = `\`\`\`yak-suggestion\n${JSON.stringify({ kind: 'ASSET_DESCRIPTION', targetId: 7,
  expectedDefinition: hash, rules: [], description: '用途待确认的订单资产', evidenceRefs: ['E1234ABCD'] })}\n\`\`\``;
const submit = agentChatApi.submit as jest.Mock;
const stream = streamTurnEvents as jest.Mock;
const cancel = agentChatApi.cancelTurn as jest.Mock;
const continuation = agentSessionApi.continuation as jest.Mock;
const history = agentSessionApi.history as jest.Mock;
const props = { kind: 'ASSET_DESCRIPTION' as const, targetId: 7, definition: hash };
let callbacks: any;
let finish: () => void;

beforeEach(() => {
  jest.clearAllMocks();
  submit.mockResolvedValue({ turnId: 'turn-1' });
  cancel.mockResolvedValue(undefined);
  continuation.mockImplementation(async (sessionId) => ({ sessionId, turnId: 'turn-1', status: 'COMPLETED',
    governanceTarget: submit.mock.calls[0][0].governanceTarget }));
  history.mockResolvedValue([{ role: 'assistant', turnId: 'turn-1', content: artifact }]);
  stream.mockImplementation((_request, handlers) => {
    callbacks = handlers;
    return new Promise<void>((resolve) => { finish = resolve; });
  });
});

async function generate() {
  fireEvent.click(screen.getByRole('button', { name: '生成建议' }));
  await waitFor(() => expect(stream).toHaveBeenCalled());
}
async function complete(text = artifact) {
  history.mockResolvedValue([{ role: 'assistant', turnId: 'turn-1', content: text }]);
  await act(async () => {
    callbacks.onEvent({ type: 'TEXT_MESSAGE_CONTENT', phase: 'FINAL', delta: text });
    callbacks.onEvent({ type: 'RUN_FINISHED', outcome: { type: 'success' } });
    callbacks.onComplete(); finish();
  });
}

it('requires successful completion and explicit adoption, then prevents duplicate adoption', async () => {
  const apply = jest.fn().mockResolvedValue(undefined);
  render(<GovernanceSuggestionPanel {...props} onApply={apply} />);
  await generate();
  act(() => callbacks.onEvent({ type: 'TEXT_MESSAGE_CONTENT', phase: 'FINAL', delta: artifact }));
  expect(screen.queryByRole('button', { name: '带入描述' })).not.toBeInTheDocument();
  expect(apply).not.toHaveBeenCalled();
  await complete();
  fireEvent.click(screen.getByRole('button', { name: '带入描述' }));
  await waitFor(() => expect(apply).toHaveBeenCalledTimes(1));
  await waitFor(() => expect(screen.getByRole('button', { name: '已带入，尚未保存' })).toBeDisabled());
});

it('ignores a late final result after switching source and cancels only the old turn', async () => {
  const apply = jest.fn();
  const view = render(<GovernanceSuggestionPanel {...props} onApply={apply} />);
  await generate();
  view.rerender(<GovernanceSuggestionPanel {...props} targetId={8} onApply={apply} />);
  await complete();
  expect(cancel).toHaveBeenCalledWith('turn-1');
  expect(agentSessionApi.cancel).not.toHaveBeenCalled();
  expect(screen.queryByRole('button', { name: '带入描述' })).not.toBeInTheDocument();
  expect(apply).not.toHaveBeenCalled();
});

it('failed turns never expose an adoptable artifact', async () => {
  render(<GovernanceSuggestionPanel {...props} onApply={jest.fn()} />);
  await generate();
  continuation.mockImplementation(async (sessionId) => ({ sessionId, turnId: 'turn-1', status: 'FAILED',
    governanceTarget: props.kind === 'ASSET_DESCRIPTION' ? { assetId: 7, purpose: props.kind } : null }));
  act(() => callbacks.onEvent({ type: 'RUN_ERROR', message: '源域不可用' }));
  await complete();
  expect(screen.getByText(/本轮生成失败/)).toBeInTheDocument();
  expect(screen.queryByText('源域不可用')).not.toBeInTheDocument();
  expect(screen.queryByRole('button', { name: '带入描述' })).not.toBeInTheDocument();
});

it('resumes clarification through the same session with the original tool call id', async () => {
  render(<GovernanceSuggestionPanel {...props} onApply={jest.fn()} />);
  await generate();
  continuation.mockImplementation(async (sessionId) => ({ sessionId, turnId: 'turn-1', status: 'WAITING_INPUT',
    governanceTarget: { assetId: 7, purpose: 'ASSET_DESCRIPTION' },
    clarification: { toolCallId: 'clarify-1', toolName: 'request_clarification', question: '资产用途？' } }));
  await act(async () => {
    callbacks.onEvent({ type: 'CUSTOM', name: 'clarify_requested', value: {
      toolCallId: 'clarify-1', toolName: 'request_clarification', question: '资产用途？' } });
    callbacks.onEvent({ type: 'RUN_FINISHED', outcome: { type: 'interrupt' } });
    callbacks.onComplete(); finish();
  });
  fireEvent.change(screen.getByRole('textbox'), { target: { value: '订单核对' } });
  fireEvent.click(screen.getByRole('button', { name: '补充并继续' }));
  await waitFor(() => expect(submit).toHaveBeenCalledTimes(2));
  expect(submit.mock.calls[1][0]).toEqual({ sessionId: submit.mock.calls[0][0].sessionId,
    toolResults: [{ toolCallId: 'clarify-1', toolName: 'request_clarification', output: '订单核对' }] });
  await act(async () => { finish(); });
});

const qualityRule = { templateId: 2, name: '行数必须大于零', operator: 'GT' as const, threshold: 0, enabled: false as const };
const qualityArtifact = `\`\`\`yak-suggestion\n${JSON.stringify({ kind: 'QUALITY_RULES', targetId: 7,
  expectedDefinition: hash, rules: [qualityRule, { ...qualityRule, name: '行数下限', threshold: 20 }], evidenceRefs: ['E1234ABCD'] })}\n\`\`\``;

it('compares the current form and disables duplicate conditions while allowing a different threshold', async () => {
  const apply = jest.fn();
  render(<GovernanceSuggestionPanel kind="QUALITY_RULES" targetId={7} definition={hash} onApply={apply}
    qualityRules={[{ ...qualityRule, name: '已有启用规则', enabled: true }]} ruleLabel={() => '行数模板'} />);
  await generate(); await complete(qualityArtifact);
  expect(screen.getByText('已有启用规则（相同条件）')).toBeInTheDocument();
  expect(screen.getByText('已有启用规则（不同条件）')).toBeInTheDocument();
  expect(screen.getByRole('button', { name: '表单已有相同条件' })).toBeDisabled();
  fireEvent.click(screen.getByRole('button', { name: '带入这条规则' }));
  await waitFor(() => expect(apply).toHaveBeenCalledWith(expect.anything(), 1, expect.any(Function)));
});

it('tracks form entries across regeneration and allows re-adoption after deletion', async () => {
  function Editor() {
    const [rules, setRules] = React.useState<SaveRulePayload[]>([]);
    return <><button onClick={() => setRules([])}>删除带入规则</button>
      <GovernanceSuggestionPanel kind="QUALITY_RULES" targetId={7} definition={hash} qualityRules={rules}
        onApply={async (value, index) => { setRules((current) => [...current, value.rules[index!]]); }} /></>;
  }
  render(<Editor />);
  await generate(); await complete(qualityArtifact);
  fireEvent.click(screen.getAllByRole('button', { name: '带入这条规则' })[0]);
  await waitFor(() => expect(screen.getByRole('button', { name: '表单已有相同条件' })).toBeDisabled());
  await generate(); await complete(qualityArtifact);
  expect(screen.getByRole('button', { name: '表单已有相同条件' })).toBeDisabled();
  fireEvent.click(screen.getByRole('button', { name: '删除带入规则' }));
  expect(screen.getAllByRole('button', { name: '带入这条规则' })).toHaveLength(2);
  fireEvent.click(screen.getAllByRole('button', { name: '带入这条规则' })[0]);
  await waitFor(() => expect(screen.getByRole('button', { name: '表单已有相同条件' })).toBeDisabled());
});

it('blocks every candidate during validation and stop invalidates late adoption', async () => {
  let resolve!: () => void;
  const apply = jest.fn((_value, _index, isCurrent) => new Promise<void>((done) => {
    resolve = () => { expect(isCurrent()).toBe(false); done(); };
  }));
  const view = render(<Form><GovernanceSuggestionPanel kind="QUALITY_RULES" targetId={7} definition={hash} onApply={apply} /></Form>);
  await generate(); await complete(qualityArtifact);
  fireEvent.click(screen.getAllByRole('button', { name: '带入这条规则' })[0]);
  view.rerender(<Form disabled><GovernanceSuggestionPanel kind="QUALITY_RULES" targetId={7} definition={hash} onApply={apply} disabled /></Form>);
  expect(screen.getAllByRole('button', { name: '带入这条规则' }).every((button) => button.hasAttribute('disabled'))).toBe(true);
  expect(screen.getByRole('button', { name: /停\s*止/ })).toBeEnabled();
  fireEvent.click(screen.getByRole('button', { name: /停\s*止/ }));
  await act(async () => { resolve(); });
  expect(screen.queryByRole('button', { name: '带入这条规则' })).not.toBeInTheDocument();
  expect(apply).toHaveBeenCalledTimes(1);
});

it('permission removal blocks adoption after the candidate has been generated', async () => {
  const apply = jest.fn();
  const view = render(<GovernanceSuggestionPanel kind="QUALITY_RULES" targetId={7} definition={hash} onApply={apply} />);
  await generate(); await complete(qualityArtifact);
  view.rerender(<GovernanceSuggestionPanel kind="QUALITY_RULES" targetId={7} definition={hash} onApply={apply} disabled />);
  fireEvent.click(screen.getAllByRole('button', { name: '带入这条规则' })[0]);
  expect(apply).not.toHaveBeenCalled();
});

it('does not treat stream success as completion and refreshes persisted history explicitly', async () => {
  render(<GovernanceSuggestionPanel {...props} onApply={jest.fn()} />);
  await generate();
  continuation.mockImplementation(async (sessionId) => ({ sessionId, turnId: 'turn-1', status: 'RUNNING',
    governanceTarget: { assetId: 7, purpose: 'ASSET_DESCRIPTION' } }));
  await complete();
  expect(screen.queryByRole('button', { name: '带入描述' })).not.toBeInTheDocument();
  expect(screen.getByRole('button', { name: '生成建议' })).toBeDisabled();
  expect(history).not.toHaveBeenCalled();
  continuation.mockImplementation(async (sessionId) => ({ sessionId, turnId: 'turn-1', status: 'COMPLETED',
    governanceTarget: { assetId: 7, purpose: 'ASSET_DESCRIPTION' } }));
  await act(async () => { fireEvent.click(screen.getByRole('button', { name: '刷新核对' })); });
  expect(screen.getByRole('button', { name: '带入描述' })).toBeEnabled();
  expect(submit).toHaveBeenCalledTimes(1);
});

it.each(['missing', 'duplicate', 'other-turn'])('rejects %s history correlation without guessing', async (mode) => {
  render(<GovernanceSuggestionPanel {...props} onApply={jest.fn()} />);
  await generate();
  const entry = { role: 'assistant', turnId: 'turn-1', content: artifact };
  history.mockResolvedValue(mode === 'duplicate' ? [entry, entry] : [{ ...entry, turnId: mode === 'missing' ? null : 'other' }]);
  await act(async () => { callbacks.onComplete(); finish(); });
  expect(screen.queryByRole('button', { name: '带入描述' })).not.toBeInTheDocument();
  expect(screen.getByRole('button', { name: '生成建议' })).toBeDisabled();
});

it('retains constraints on transport/read failure and suppresses private errors', async () => {
  render(<GovernanceSuggestionPanel {...props} onApply={jest.fn()} />);
  fireEvent.change(screen.getByRole('textbox'), { target: { value: '人工业务背景' } });
  await generate();
  continuation.mockRejectedValue(new Error('private-source-secret'));
  await act(async () => { callbacks.onError('private-model-secret'); finish(); });
  expect(screen.getByRole('textbox')).toHaveValue('人工业务背景');
  expect(screen.getByRole('button', { name: '生成建议' })).toBeDisabled();
  expect(screen.getByRole('link', { name: '到原会话查看或继续' })).toHaveAttribute('href',
    `/ai-agent?sessionId=${encodeURIComponent(submit.mock.calls[0][0].sessionId)}`);
  expect(document.body.textContent).not.toMatch(/private-/);
});

it('stops the confirmed turn once and uses actual state even when the command acknowledgement is lost', async () => {
  render(<GovernanceSuggestionPanel {...props} onApply={jest.fn()} />);
  await generate();
  const stale = callbacks;
  cancel.mockRejectedValue(new Error('private-ack'));
  continuation.mockImplementation(async (sessionId) => ({ sessionId, turnId: 'turn-1', status: 'CANCELLED',
    governanceTarget: { assetId: 7, purpose: 'ASSET_DESCRIPTION' } }));
  await act(async () => {
    fireEvent.click(screen.getByRole('button', { name: /停\s*止/ }));
    fireEvent.click(screen.getByRole('button', { name: /停\s*止/ }));
  });
  expect(cancel).toHaveBeenCalledTimes(1);
  expect(cancel).toHaveBeenCalledWith('turn-1');
  expect(screen.getByText(/本轮已停止/)).toBeInTheDocument();
  await act(async () => { stale.onComplete(); finish(); });
  expect(screen.queryByRole('button', { name: '带入描述' })).not.toBeInTheDocument();
  expect(agentSessionApi.cancel).not.toHaveBeenCalled();
});

it('uses the actual pending state when clarification wins the stop race', async () => {
  render(<GovernanceSuggestionPanel {...props} onApply={jest.fn()} />);
  await generate();
  continuation.mockImplementation(async (sessionId) => ({ sessionId, turnId: 'turn-1', status: 'WAITING_INPUT',
    governanceTarget: { assetId: 7, purpose: 'ASSET_DESCRIPTION' },
    clarification: { toolCallId: 'original', toolName: 'request_clarification', question: '{"question":"用途待确认"}' } }));
  await act(async () => { fireEvent.click(screen.getByRole('button', { name: /停\s*止/ })); });
  expect(screen.getByText('用途待确认')).toBeInTheDocument();
  expect(screen.queryByText(/本轮已停止/)).not.toBeInTheDocument();
  expect(screen.getByRole('button', { name: '补充并继续' })).toBeDisabled();
});

it('prevents duplicate submission before acknowledgement and cleans only a late acknowledged turn after unmount', async () => {
  let acknowledge!: (value: { turnId: string }) => void;
  submit.mockImplementation(() => new Promise((resolve) => { acknowledge = resolve; }));
  const view = render(<GovernanceSuggestionPanel {...props} onApply={jest.fn()} />);
  const generateButton = screen.getByRole('button', { name: '生成建议' });
  act(() => { fireEvent.click(generateButton); fireEvent.click(generateButton); });
  fireEvent.click(screen.getByRole('button', { name: /停\s*止/ }));
  expect(submit).toHaveBeenCalledTimes(1);
  expect(cancel).not.toHaveBeenCalled();
  view.unmount();
  await act(async () => { acknowledge({ turnId: 'late-original' }); });
  expect(cancel).toHaveBeenCalledWith('late-original');
  expect(stream).not.toHaveBeenCalled();
  expect(agentSessionApi.cancel).not.toHaveBeenCalled();
});

it('ignores a late history read after the source changes', async () => {
  let resolveHistory!: (value: unknown) => void;
  history.mockImplementation(() => new Promise((resolve) => { resolveHistory = resolve; }));
  const view = render(<GovernanceSuggestionPanel {...props} onApply={jest.fn()} />);
  await generate();
  await act(async () => { finish(); });
  expect(history).toHaveBeenCalled();
  view.rerender(<GovernanceSuggestionPanel {...props} targetId={8} onApply={jest.fn()} />);
  await act(async () => { resolveHistory([{ role: 'assistant', turnId: 'turn-1', content: artifact }]); });
  expect(screen.queryByRole('button', { name: '带入描述' })).not.toBeInTheDocument();
});
