import React from 'react';
import { act, fireEvent, render, screen } from '@testing-library/react';
import { AgentAvailabilityProvider, useAgentAvailability } from './AgentAvailabilityContext';
import { getApplicationFeatures } from '@/services/applicationFeatures';

let mockUserId: string | undefined = '1';
jest.mock('@umijs/max', () => ({
  useModel: () => ({ initialState: { currentUser: mockUserId ? { userid: mockUserId } : undefined } }),
}));
jest.mock('@/services/applicationFeatures', () => ({ getApplicationFeatures: jest.fn() }));

function Probe() {
  const value = useAgentAvailability();
  return <><p>{value.loading ? 'loading' : value.failed ? 'failed' : String(value.agentEnabled)}</p>
    <button onClick={value.refresh}>refresh</button></>;
}

beforeEach(() => { jest.clearAllMocks(); mockUserId = '1'; });

it('ignores an old identity response and does not probe anonymously', async () => {
  let oldResponse!: (value: { agentEnabled: boolean }) => void;
  jest.mocked(getApplicationFeatures).mockReturnValueOnce(new Promise(resolve => { oldResponse = resolve; }))
    .mockResolvedValue({ agentEnabled: false });
  const view = render(<AgentAvailabilityProvider><Probe /></AgentAvailabilityProvider>);
  expect(screen.getByText('loading')).toBeInTheDocument();
  mockUserId = '2';
  view.rerender(<AgentAvailabilityProvider><Probe /></AgentAvailabilityProvider>);
  expect(await screen.findByText('false')).toBeInTheDocument();
  await act(async () => oldResponse({ agentEnabled: true }));
  expect(screen.getByText('false')).toBeInTheDocument();
  mockUserId = undefined;
  view.rerender(<AgentAvailabilityProvider><Probe /></AgentAvailabilityProvider>);
  expect(screen.getByText('undefined')).toBeInTheDocument();
  fireEvent.click(screen.getByText('refresh'));
  expect(getApplicationFeatures).toHaveBeenCalledTimes(2);
});
