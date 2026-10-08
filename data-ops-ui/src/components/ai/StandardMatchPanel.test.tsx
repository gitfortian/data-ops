import React from 'react';
import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import StandardMatchPanel from './StandardMatchPanel';
import { agentChatApi, agentSessionApi, streamTurnEvents } from '@/services/agent';

jest.mock('@/services/agent', () => ({ agentChatApi: { submit: jest.fn(), cancelTurn: jest.fn(), validateStandardMatch: jest.fn() },
  agentSessionApi: { continuation: jest.fn(), history: jest.fn() }, streamTurnEvents: jest.fn() }));
const submit = agentChatApi.submit as jest.Mock;
const stream = streamTurnEvents as jest.Mock;
const validate = agentChatApi.validateStandardMatch as jest.Mock;
const continuation = agentSessionApi.continuation as jest.Mock;
const history = agentSessionApi.history as jest.Mock;
const target = { modelId: 7, columnName: 'user_id', dataType: 'BIGINT', businessDescription: '用户编号', keyword: '' };
const props = { target, definition: 'a'.repeat(64), disabled: false };
const suggestion = { kind: 'STANDARD_MATCH', target, expectedDefinition: props.definition,
  skillVersion: 1, skillHash: 'b'.repeat(64), truncated: false,
  candidates: [{ standardId: 9, version: 2, code: 'user_id', name: '用户编号', stdType: 'BIGINT', reason: '业务编号' }], questions: [] };
let finish: () => void;
beforeEach(() => {
  jest.clearAllMocks();
  submit.mockResolvedValue({ turnId: 't1' });
  (agentChatApi.cancelTurn as jest.Mock).mockResolvedValue(true);
  continuation.mockImplementation(async sessionId => ({ sessionId, turnId: 't1', status: 'COMPLETED',
    governanceTarget: { purpose: 'STANDARD_MATCH', standardMatch: target } }));
  history.mockResolvedValue([{ role: 'assistant', turnId: 't1', content: `\`\`\`yak-standard-match\n${JSON.stringify(suggestion)}\n\`\`\`` }]);
  stream.mockImplementation(() => new Promise<void>(resolve => { finish = resolve; }));
  validate.mockResolvedValue(suggestion);
});
async function generate() {
  fireEvent.click(screen.getByRole('button', { name: '生成类型候选' }));
  await waitFor(() => expect(stream).toHaveBeenCalled());
}
async function complete() { await act(async () => { finish(); }); }
it('only exposes persisted completed results and revalidates before changing the draft', async () => {
  const apply = jest.fn(); render(<StandardMatchPanel {...props} onApply={apply} />);
  await generate(); expect(screen.queryByText('带入类型引用')).not.toBeInTheDocument();
  await complete(); fireEvent.click(screen.getByText('带入类型引用'));
  await waitFor(() => expect(apply).toHaveBeenCalledWith(9));
  expect(validate).toHaveBeenCalledWith(suggestion);
  expect(screen.queryByText('带入类型引用')).not.toBeInTheDocument();
});
it('discarded field lifetimes cancel the original turn and ignore late results', async () => {
  const apply = jest.fn(); const view = render(<StandardMatchPanel key="field1" {...props} onApply={apply} />);
  await generate(); view.rerender(<StandardMatchPanel key="field2" {...props} target={{ ...target, columnName: 'other' }} onApply={apply} />);
  await complete(); expect(agentChatApi.cancelTurn).toHaveBeenCalledWith('t1');
  expect(screen.queryByText('带入类型引用')).not.toBeInTheDocument(); expect(apply).not.toHaveBeenCalled();
});
it('does not adopt a stale validation after field switch or permission removal', async () => {
  let resolve: (v: typeof suggestion) => void = () => {};
  validate.mockImplementation(() => new Promise(r => { resolve = r; }));
  const apply = jest.fn(); const view = render(<StandardMatchPanel {...props} onApply={apply} />);
  await generate(); await complete(); fireEvent.click(screen.getByText('带入类型引用'));
  await waitFor(() => expect(validate).toHaveBeenCalled());
  view.rerender(<StandardMatchPanel {...props} disabled onApply={apply} />);
  await act(async () => resolve(suggestion)); expect(apply).not.toHaveBeenCalled();
});
it('stops the observed turn and rereads the real terminal status', async () => {
  render(<StandardMatchPanel {...props} onApply={jest.fn()} />); await generate();
  continuation.mockImplementation(async sessionId => ({ sessionId, turnId: 't1', status: 'CANCELLED', governanceTarget: { purpose: 'STANDARD_MATCH', standardMatch: target } }));
  // The submit acknowledgement schedules a render when status arrives; stream remains active.
  await act(async () => {});
  fireEvent.click(screen.getByText('停止本轮'));
  await waitFor(() => expect(continuation).toHaveBeenCalled());
  expect(agentChatApi.cancelTurn).toHaveBeenCalledWith('t1'); expect(screen.queryByText('带入类型引用')).not.toBeInTheDocument();
});
it('failed turns and unreadable history never expose candidates', async () => {
  continuation.mockImplementation(async sessionId => ({ sessionId, turnId: 't1', status: 'FAILED', governanceTarget: { purpose: 'STANDARD_MATCH', standardMatch: target } }));
  render(<StandardMatchPanel {...props} onApply={jest.fn()} />); await generate(); await complete();
  expect(history).not.toHaveBeenCalled(); expect(screen.queryByText('带入类型引用')).not.toBeInTheDocument();
});
