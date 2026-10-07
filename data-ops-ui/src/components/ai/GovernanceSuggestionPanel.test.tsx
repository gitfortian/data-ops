import React from 'react';
import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import GovernanceSuggestionPanel from './GovernanceSuggestionPanel';
import { agentChatApi, agentSessionApi, streamTurnEvents } from '@/services/agent';
import type { SaveRulePayload } from '@/services/data-quality';
import { Form } from 'antd';

jest.mock('@/services/agent', () => ({ agentChatApi: { submit: jest.fn() },
  agentSessionApi: { cancel: jest.fn() }, streamTurnEvents: jest.fn() }));

const hash = 'a'.repeat(64);
const artifact = `\`\`\`yak-suggestion\n${JSON.stringify({ kind: 'ASSET_DESCRIPTION', targetId: 7,
  expectedDefinition: hash, rules: [], description: '用途待确认的订单资产', evidenceRefs: ['E1234ABCD'] })}\n\`\`\``;
const submit = agentChatApi.submit as jest.Mock;
const stream = streamTurnEvents as jest.Mock;
const cancel = agentSessionApi.cancel as jest.Mock;
const props = { kind: 'ASSET_DESCRIPTION' as const, targetId: 7, definition: hash };
let callbacks: any;
let finish: () => void;

beforeEach(() => {
  jest.clearAllMocks();
  submit.mockResolvedValue({ turnId: 'turn-1' });
  cancel.mockResolvedValue(undefined);
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

it('ignores a late final result after switching source and cancels the old session', async () => {
  const apply = jest.fn();
  const view = render(<GovernanceSuggestionPanel {...props} onApply={apply} />);
  await generate();
  view.rerender(<GovernanceSuggestionPanel {...props} targetId={8} onApply={apply} />);
  await complete();
  expect(cancel).toHaveBeenCalled();
  expect(screen.queryByRole('button', { name: '带入描述' })).not.toBeInTheDocument();
  expect(apply).not.toHaveBeenCalled();
});

it('failed turns never expose an adoptable artifact', async () => {
  render(<GovernanceSuggestionPanel {...props} onApply={jest.fn()} />);
  await generate();
  act(() => callbacks.onEvent({ type: 'RUN_ERROR', message: '源域不可用' }));
  await complete();
  expect(screen.getByText('源域不可用')).toBeInTheDocument();
  expect(screen.queryByRole('button', { name: '带入描述' })).not.toBeInTheDocument();
});

it('resumes clarification through the same session with the original tool call id', async () => {
  render(<GovernanceSuggestionPanel {...props} onApply={jest.fn()} />);
  await generate();
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
