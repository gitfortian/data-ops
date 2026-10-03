import React from 'react';
import { render, screen } from '@testing-library/react';
import AiAgentPage from './index';

jest.mock('@/hooks/usePermissionAccess', () => ({
  usePermissionAccess: () => ({ can: () => false }),
}));
jest.mock('@/services/agent', () => ({
  agentSessionApi: { list: async () => [] },
  agentChatApi: {},
  streamTurnEvents: jest.fn(),
}));
it('renders the assistant entry and composer with the installed Ant Design X components', async () => {
  render(<AiAgentPage />);
  expect(await screen.findByPlaceholderText(/输入/)).toBeInTheDocument();
});
