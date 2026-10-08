import React from 'react';
import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import ModelMappingPanel from './ModelMappingPanel';
import { agentChatApi, agentSessionApi, streamTurnEvents } from '@/services/agent';

jest.mock('@/services/agent', () => ({ agentChatApi: { submit: jest.fn(), cancelTurn: jest.fn(), validateModelMapping: jest.fn() },
  agentSessionApi: { continuation: jest.fn(), history: jest.fn() }, streamTurnEvents: jest.fn() }));
const submit = agentChatApi.submit as jest.Mock;
const stream = streamTurnEvents as jest.Mock;
const validate = agentChatApi.validateModelMapping as jest.Mock;
const continuation = agentSessionApi.continuation as jest.Mock;
const history = agentSessionApi.history as jest.Mock;
const target = { modelId: 7, columnName: 'user_id', datasourceId: 9, database: 'db', table: 'users', businessDescription: '用户编号', keyword: '' };
const props = { target, definition: 'a'.repeat(64), disabled: false };
const suggestion = { kind: 'MODEL_MAPPING', target, expectedDefinition: props.definition,
  sourceDefinition: 'c'.repeat(64), targetType: 'BIGINT', skillVersion: 1, skillHash: 'b'.repeat(64), truncated: false,
  candidates: [{ sourceColumn: 'buyer_id', type: 'BIGINT', nullable: false, reason: '业务编号' }], questions: [] };
let finish: () => void;
beforeEach(() => {
  jest.clearAllMocks();
  submit.mockResolvedValue({ turnId: 't1' });
  (agentChatApi.cancelTurn as jest.Mock).mockResolvedValue(true);
  continuation.mockImplementation(async sessionId => ({ sessionId, turnId: 't1', status: 'COMPLETED',
    governanceTarget: { purpose: 'MODEL_MAPPING', modelMapping: target } }));
  history.mockResolvedValue([{ role: 'assistant', turnId: 't1', content: `\`\`\`yak-model-mapping\n${JSON.stringify(suggestion)}\n\`\`\`` }]);
  stream.mockImplementation(() => new Promise<void>(resolve => { finish = resolve; }));
  validate.mockResolvedValue(suggestion);
});
async function generate() {
  fireEvent.click(screen.getByRole('button', { name: '生成来源候选' }));
  await waitFor(() => expect(stream).toHaveBeenCalled());
}
async function complete() { await act(async () => { finish(); }); }
it('only exposes persisted completed results and revalidates before changing the draft', async () => {
  const apply = jest.fn(); render(<ModelMappingPanel {...props} onApply={apply} />);
  await generate(); expect(screen.queryByText('带入来源字段')).not.toBeInTheDocument();
  await complete(); fireEvent.click(screen.getByText('带入来源字段'));
  await waitFor(() => expect(apply).toHaveBeenCalledWith('buyer_id'));
  expect(validate).toHaveBeenCalledWith(suggestion);
  expect(screen.queryByText('带入来源字段')).not.toBeInTheDocument();
});
it('discarded field lifetimes cancel the original turn and ignore late results', async () => {
  const apply = jest.fn(); const view = render(<ModelMappingPanel key="field1" {...props} onApply={apply} />);
  await generate(); view.rerender(<ModelMappingPanel key="field2" {...props} target={{ ...target, columnName: 'other' }} onApply={apply} />);
  await complete(); expect(agentChatApi.cancelTurn).toHaveBeenCalledWith('t1');
  expect(screen.queryByText('带入来源字段')).not.toBeInTheDocument(); expect(apply).not.toHaveBeenCalled();
});
it('does not adopt a stale validation after field switch or permission removal', async () => {
  let resolve: (v: typeof suggestion) => void = () => {};
  validate.mockImplementation(() => new Promise(r => { resolve = r; }));
  const apply = jest.fn(); const view = render(<ModelMappingPanel {...props} onApply={apply} />);
  await generate(); await complete(); fireEvent.click(screen.getByText('带入来源字段'));
  await waitFor(() => expect(validate).toHaveBeenCalled());
  view.rerender(<ModelMappingPanel {...props} disabled onApply={apply} />);
  await act(async () => resolve(suggestion)); expect(apply).not.toHaveBeenCalled();
});
it('stops the observed turn and rereads the real terminal status', async () => {
  render(<ModelMappingPanel {...props} onApply={jest.fn()} />); await generate();
  continuation.mockImplementation(async sessionId => ({ sessionId, turnId: 't1', status: 'CANCELLED', governanceTarget: { purpose: 'MODEL_MAPPING', modelMapping: target } }));
  // The submit acknowledgement schedules a render when status arrives; stream remains active.
  await act(async () => {});
  fireEvent.click(screen.getByText('停止本轮'));
  await waitFor(() => expect(continuation).toHaveBeenCalled());
  expect(agentChatApi.cancelTurn).toHaveBeenCalledWith('t1'); expect(screen.queryByText('带入来源字段')).not.toBeInTheDocument();
});
it('failed turns and unreadable history never expose candidates', async () => {
  continuation.mockImplementation(async sessionId => ({ sessionId, turnId: 't1', status: 'FAILED', governanceTarget: { purpose: 'MODEL_MAPPING', modelMapping: target } }));
  render(<ModelMappingPanel {...props} onApply={jest.fn()} />); await generate(); await complete();
  expect(history).not.toHaveBeenCalled(); expect(screen.queryByText('带入来源字段')).not.toBeInTheDocument();
});

it('invalidates an old source choice when search changes and never restores it from old history', async () => {
  const apply = jest.fn(); render(<ModelMappingPanel {...props} onApply={apply} />);
  await generate(); await complete();
  expect(screen.getByText('带入来源字段')).toBeInTheDocument();
  fireEvent.change(screen.getByPlaceholderText('字段/标准检索词（可空，读取有界目录）'), { target: { value: 'operator' } });
  expect(screen.queryByText('带入来源字段')).not.toBeInTheDocument();
  fireEvent.click(screen.getByText('刷新核对'));
  await screen.findByText('检索条件已变化，旧候选已失效，请重新生成。');
  await waitFor(() => expect(screen.getByText('生成来源候选').closest('button')).not.toBeDisabled());
  expect(screen.queryByText('带入来源字段')).not.toBeInTheDocument();
  expect(apply).not.toHaveBeenCalled(); expect(validate).not.toHaveBeenCalled();
  fireEvent.click(screen.getByText('生成来源候选'));
  await waitFor(() => expect(submit).toHaveBeenCalledTimes(2));
  expect(submit.mock.calls[1][0].governanceTarget.modelMapping.keyword).toBe('operator');
  await complete();
});
