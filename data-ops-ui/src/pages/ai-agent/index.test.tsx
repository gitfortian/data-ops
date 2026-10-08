import React from 'react';
import { fireEvent, render, screen } from '@testing-library/react';
import AiAgentPage from './index';
import { streamTurnEvents } from '@/services/agent';
import { governanceQuestions } from '@/services/agent/governance';
jest.mock('@/contexts/SecurityProjectContext', () => ({ useSecurityProject: () => ({ currentProject: { id: 1 } }) }));

jest.mock('@/hooks/usePermissionAccess', () => ({
  usePermissionAccess: () => ({ can: (code: string) => code === 'agent:chat:run' }),
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

it('keeps historical troubleshooting on the selected execution and only fills the composer', async () => {
  window.history.replaceState({}, '', '/ai-agent?qualityExecutionNo=Q_20261007-1');
  try {
    render(<AiAgentPage />);
    expect(await screen.findByText('质量执行 Q_20261007-1 解读与排查')).toBeInTheDocument();
    expect(screen.queryByText('上个月各区域销售额是多少？')).not.toBeInTheDocument();
    fireEvent.click(screen.getByText('解读与排查'));
    expect(screen.getByPlaceholderText(/输入/)).toHaveValue(governanceQuestions({ qualityExecutionNo: 'Q_20261007-1' })[0]);
    expect(streamTurnEvents).not.toHaveBeenCalled();
    expect(screen.getByText('返回本次执行核对').closest('a')).toHaveAttribute('href', '/data-quality/execution/Q_20261007-1');
    fireEvent.click(screen.getByText('补充排查信息'));
    expect(screen.getByPlaceholderText(/输入/)).toHaveValue(governanceQuestions({ qualityExecutionNo: 'Q_20261007-1' })[0]);
    fireEvent.change(screen.getByPlaceholderText(/输入/), { target: { value: '' } });
    fireEvent.click(screen.getByText('补充排查信息'));
    expect(screen.getByPlaceholderText(/输入/)).toHaveValue(governanceQuestions({ qualityExecutionNo: 'Q_20261007-1' })[1]);
    fireEvent.click(screen.getByText('新建会话'));
    expect(screen.queryByText('质量执行 Q_20261007-1 解读与排查')).not.toBeInTheDocument();
    expect(screen.getByPlaceholderText(/输入/)).toHaveValue('');
  } finally { window.history.replaceState({}, '', '/'); }
});

it('does not present the current monitor candidate entry as an undefined execution', async () => {
  window.history.replaceState({}, '', '/ai-agent?qualityMonitorId=9');
  try {
    render(<AiAgentPage />);
    expect(await screen.findByText('质量监控 #9 规则建议')).toBeInTheDocument();
    expect(screen.getByText('返回来源').closest('a')).toHaveAttribute('href', '/data-quality/monitor/9');
    fireEvent.click(screen.getByText('生成规则候选'));
    expect(streamTurnEvents).not.toHaveBeenCalled();
  } finally { window.history.replaceState({}, '', '/'); }
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
