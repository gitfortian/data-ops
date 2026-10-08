import React from 'react';
import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import MetricDefinitionPanel from './MetricDefinitionPanel';
import { getMetricDraftContext } from '@/services/agent/metricDraft';
let mockProjectId = 1;
jest.mock('@/contexts/SecurityProjectContext', () => ({ useSecurityProject: () => ({ currentProject: { id: mockProjectId } }) }));
jest.mock('@/services/agent/metricDraft', () => ({ ...jest.requireActual('@/services/agent/metricDraft'), getMetricDraftContext: jest.fn() }));
jest.mock('./StructuredSuggestionPanel', () => ({ __esModule: true, default: ({ target, onApply }: any) => <button onClick={() => onApply({ name: '金额', description: '每日金额', period: 'DAY', aggregation: 'SUM', field: 'amount', qualifiers: [], tokens: [] })}>候选来自模型 {target.modelId}</button> }));
const context = { definition: 'a'.repeat(64), fields: [{ name: 'amount', type: 'DECIMAL', description: '金额' }], upstream: [] };
const props = { metricType: 'ATOMIC' as const, modelId: 9, upstreamOptions: [], disabled: false, onApply: jest.fn() };
const enter = () => fireEvent.change(screen.getByPlaceholderText('说明业务目标、统计粒度、周期及限定条件；缺项会列为待确认问题。'), { target: { value: '每日金额合计' } });
beforeEach(() => { jest.clearAllMocks(); mockProjectId = 1; (getMetricDraftContext as jest.Mock).mockResolvedValue(context); });
it('prepares only explicit source and requirement, adopts a draft without persisting it', async () => {
  render(<MetricDefinitionPanel {...props} />); enter(); fireEvent.click(screen.getByText('核对所选依赖'));
  fireEvent.click(await screen.findByText('候选来自模型 9'));
  expect(getMetricDraftContext).toHaveBeenCalledWith({ metricId: null, version: null, metricType: 'ATOMIC', modelId: 9, upstreamIds: [], requirement: '每日金额合计' });
  expect(props.onApply).toHaveBeenCalledWith(expect.objectContaining({ field: 'amount', aggregation: 'SUM' }), '');
  fireEvent.change(screen.getByPlaceholderText('说明业务目标、统计粒度、周期及限定条件；缺项会列为待确认问题。'), { target: { value: '改为每周金额合计' } }); await waitFor(() => expect(screen.queryByText('候选来自模型 9')).not.toBeInTheDocument());
});
it('invalidates prepared context and ignores a late source when project changes', async () => {
  let resolve: (v: unknown) => void = () => {};
  (getMetricDraftContext as jest.Mock).mockImplementationOnce(() => new Promise(r => { resolve = r; }));
  const view = render(<MetricDefinitionPanel {...props} />); enter(); fireEvent.click(screen.getByText('核对所选依赖'));
  mockProjectId = 2; view.rerender(<MetricDefinitionPanel {...props} />);
  await act(async () => resolve(context)); expect(screen.queryByText('候选来自模型 9')).not.toBeInTheDocument();
});
