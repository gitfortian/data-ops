import React from 'react';
import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import GovernanceSuggestionPanel from './GovernanceSuggestionPanel';
import { agentChatApi, agentSessionApi, streamTurnEvents } from '@/services/agent';

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
async function complete() {
  await act(async () => {
    callbacks.onEvent({ type: 'TEXT_MESSAGE_CONTENT', phase: 'FINAL', delta: artifact });
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
