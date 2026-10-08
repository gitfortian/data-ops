import React from 'react';
import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import BatchStandardAssistant from './BatchStandardAssistant';
import { agentChatApi, agentSessionApi, streamTurnEvents } from '@/services/agent';
import type { ColumnDraft } from '../editor/structureRules';
jest.mock('@/services/agent', () => ({ agentChatApi: { submit: jest.fn(), cancelTurn: jest.fn(), validateStandardMatch: jest.fn() }, agentSessionApi: { continuation: jest.fn(), history: jest.fn() }, streamTurnEvents: jest.fn() }));
const rows: ColumnDraft[] = [1, 2].map(key => ({ key, columnName: `user_${key}`, dataType: 'BIGINT', businessDescription: '用户编号', nullable: false }));
const targets = new Map<string, any>();
const receipt = (target: any) => ({ kind: 'STANDARD_MATCH', target, expectedDefinition: 'a'.repeat(64), skillVersion: 1, skillHash: 'b'.repeat(64), truncated: false,
  fieldDescription: '唯一用户编号', candidates: [{ standardId: 9, version: 2, code: 'uid', name: '用户编号', stdType: 'BIGINT', reason: '编号一致' }], questions: [] });
const props = { modelId: 7, definition: 'a'.repeat(64), rows, selectedKeys: [1, 2], disabled: false, onClose: jest.fn(), onApply: jest.fn() };
beforeEach(() => {
  jest.clearAllMocks(); targets.clear();
  let sequence = 0;
  Object.defineProperty(globalThis.crypto, 'randomUUID', { configurable: true, value: jest.fn().mockImplementation(() => `uuid-${sequence++}`) });
  (agentChatApi.submit as jest.Mock).mockImplementation(async request => { targets.set(request.sessionId, request.governanceTarget.standardMatch); return { turnId: `t-${request.sessionId}` }; });
  (agentChatApi.cancelTurn as jest.Mock).mockResolvedValue(true);
  (streamTurnEvents as jest.Mock).mockResolvedValue(undefined);
  (agentSessionApi.continuation as jest.Mock).mockImplementation(async sessionId => ({ sessionId, turnId: `t-${sessionId}`, status: 'COMPLETED', governanceTarget: { purpose: 'STANDARD_MATCH', standardMatch: targets.get(sessionId) } }));
  (agentSessionApi.history as jest.Mock).mockImplementation(async sessionId => [{ role: 'assistant', turnId: `t-${sessionId}`, content: '```yak-standard-match\n' + JSON.stringify(receipt(targets.get(sessionId))) + '\n```' }]);
  (agentChatApi.validateStandardMatch as jest.Mock).mockImplementation(async v => v);
});
it('runs independent selected turns and adopts only the chosen field after revalidation', async () => {
  render(<BatchStandardAssistant {...props} />); fireEvent.click(screen.getByText('生成所选字段候选'));
  await waitFor(() => expect(screen.getAllByText('带入此类型引用')).toHaveLength(2));
  expect(agentChatApi.submit).toHaveBeenCalledTimes(2);
  expect([...targets.values()].map(v => v.columnName)).toEqual(['user_1', 'user_2']);
  fireEvent.click(screen.getAllByText('同时带入说明：唯一用户编号')[0]);
  fireEvent.click(screen.getAllByText('带入此类型引用')[0]);
  await waitFor(() => expect(props.onApply).toHaveBeenCalledWith(1, JSON.stringify(rows[0]), { stdTypeId: 9, businessDescription: '唯一用户编号' }));
  expect(agentChatApi.validateStandardMatch).toHaveBeenCalledTimes(1);
});
it('pauses later items when submission outcome is unknown and never resubmits', async () => {
  (agentChatApi.submit as jest.Mock).mockRejectedValue(new Error('timeout'));
  render(<BatchStandardAssistant {...props} />); fireEvent.click(screen.getByText('生成所选字段候选'));
  await screen.findByText('提交或结果未确认，已停止后续项。请刷新核对原轮次。');
  expect(agentChatApi.submit).toHaveBeenCalledTimes(1); expect(screen.queryByText('生成所选字段候选')).not.toBeInTheDocument();
});
it('does not overwrite a changed field while validating a candidate', async () => {
  let resolve: (value: unknown) => void = () => {};
  (agentChatApi.validateStandardMatch as jest.Mock).mockImplementation(v => new Promise(r => { resolve = () => r(v); }));
  const view = render(<BatchStandardAssistant {...props} />); fireEvent.click(screen.getByText('生成所选字段候选'));
  fireEvent.click((await screen.findAllByText('带入此类型引用'))[0]);
  await waitFor(() => expect(agentChatApi.validateStandardMatch).toHaveBeenCalled());
  view.rerender(<BatchStandardAssistant {...props} rows={[{ ...rows[0], comment: '人工修改' }, rows[1]]} />);
  await act(async () => resolve(undefined)); expect(props.onApply).not.toHaveBeenCalled();
});
it('cancels an exact late receipt after closing and never starts later items', async () => {
  let resolve: (value: unknown) => void = () => {};
  (agentChatApi.submit as jest.Mock).mockImplementation(() => new Promise(r => { resolve = r; }));
  const view = render(<BatchStandardAssistant {...props} />); fireEvent.click(screen.getByText('生成所选字段候选'));
  view.unmount(); await act(async () => resolve({ turnId: 'late-exact' }));
  expect(agentChatApi.cancelTurn).toHaveBeenCalledWith('late-exact'); expect(agentChatApi.submit).toHaveBeenCalledTimes(1);
});
it('continues after a confirmed terminal failure, keeping the successful item reviewable', async () => {
  (agentSessionApi.continuation as jest.Mock).mockImplementation(async sessionId => ({ sessionId, turnId: `t-${sessionId}`, status: targets.get(sessionId).columnName === 'user_1' ? 'FAILED' : 'COMPLETED', governanceTarget: { purpose: 'STANDARD_MATCH', standardMatch: targets.get(sessionId) } }));
  render(<BatchStandardAssistant {...props} />); fireEvent.click(screen.getByText('生成所选字段候选'));
  await screen.findByText('带入此类型引用'); expect(agentChatApi.submit).toHaveBeenCalledTimes(2);
  expect(screen.getByText('本项未完成，保留原轮次结果。')).toBeInTheDocument();
});
