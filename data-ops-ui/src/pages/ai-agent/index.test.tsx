import React from 'react';
import { fireEvent, render, screen } from '@testing-library/react';
import AiAgentPage from './index';
import { streamTurnEvents } from '@/services/agent';

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

it('carries an asset entry without automatically starting inference and clears it for a new conversation', async () => {
  window.history.replaceState({}, '', '/ai-agent?assetId=7');
  render(<AiAgentPage />);
  expect(await screen.findByText('资产 #7 治理解读')).toBeInTheDocument();
  fireEvent.click(screen.getByText('解释结果'));
  expect(screen.getByPlaceholderText(/输入/)).toHaveValue('解释这个资产的含义、负责人和治理状态，并引用证据。');
  expect(streamTurnEvents).not.toHaveBeenCalled();
  expect(screen.getByText('返回来源').closest('a')).toHaveAttribute('href', '/data-asset/detail/7');
  fireEvent.click(screen.getByText('新建会话'));
  expect(screen.queryByText('资产 #7 治理解读')).not.toBeInTheDocument();
  expect(screen.getByPlaceholderText(/输入/)).toHaveValue('');
  window.history.replaceState({}, '', '/');
});
