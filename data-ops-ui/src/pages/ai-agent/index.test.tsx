import React from 'react';
import { fireEvent, render, screen } from '@testing-library/react';
import AiAgentPage from './index';
import { streamTurnEvents } from '@/services/agent';
import { governanceEntryPath, governanceQuestions, governanceTaskTitle } from '@/services/agent/governance';
jest.mock('@/contexts/SecurityProjectContext', () => ({ useSecurityProject: () => ({ currentProject: { id: 1 } }) }));

jest.mock('@/hooks/usePermissionAccess', () => ({
  usePermissionAccess: () => ({ can: (code: string) => code === 'agent:chat:run' }),
}));
jest.mock('@/services/agent', () => ({
  agentSessionApi: { list: async () => [] },
  agentChatApi: {},
  streamTurnEvents: jest.fn(),
}));
it('prepares the exact consumer version question without starting a turn', async () => {
  const target = { purpose: 'CONSUMER_VERSION_IMPACT' as const, consumerVersionImpact: {
    productType: 'DATA_SERVICE' as const, productIdentity: '101', sourceVersionIdentity: '9001',
  } };
  window.history.replaceState({}, '', governanceEntryPath(target));
  try {
    render(<AiAgentPage />);
    expect(await screen.findByText(governanceTaskTitle(target))).toBeInTheDocument();
    fireEvent.click(screen.getByText('准备消费影响说明'));
    expect(screen.getByPlaceholderText(/输入/)).toHaveValue(governanceQuestions(target)[0]);
    expect(streamTurnEvents).not.toHaveBeenCalled();
    expect(screen.getByText('返回来源').closest('a')).toHaveAttribute('href', '/data-analysis/consumption/DATA_SERVICE%3A101?reviewVersion=9001');
  } finally { window.history.replaceState({}, '', '/'); }
});
it('rejects consumer context without its task purpose instead of falling back to ordinary chat', () => {
  window.history.replaceState({}, '', '/ai-agent?consumerProductType=DATASET&consumerProductIdentity=101&consumerVersionIdentity=9001');
  try {
    render(<AiAgentPage />);
    expect(screen.getByText('治理任务入口无效')).toBeInTheDocument();
    expect(screen.queryByPlaceholderText(/输入/)).not.toBeInTheDocument();
    expect(streamTurnEvents).not.toHaveBeenCalled();
  } finally { window.history.replaceState({}, '', '/'); }
});
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

it('shows both selected historical executions and fills comparison without inference', async () => {
  window.history.replaceState({}, '', '/ai-agent?qualityExecutionNo=after&qualityBaselineExecutionNo=before');
  try {
    render(<AiAgentPage />);
    expect(await screen.findByText('质量执行 before → after 历史比较')).toBeInTheDocument();
    fireEvent.click(screen.getByText('比较两次执行'));
    expect(screen.getByPlaceholderText(/输入/)).toHaveValue(governanceQuestions({ qualityExecutionNo: 'after', qualityBaselineExecutionNo: 'before' })[0]);
    expect(streamTurnEvents).not.toHaveBeenCalled();
    expect(screen.getByText('返回基准执行核对').closest('a')).toHaveAttribute('href', '/data-quality/execution/before');
    expect(screen.getByText('返回本次执行核对').closest('a')).toHaveAttribute('href', '/data-quality/execution/after');
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


it('prepares a fixed model structure review question and exact source backlink without inference', async () => {
  const target = { purpose: 'MODEL_STRUCTURE_REVIEW' as const, modelStructureReview: { modelId: '7', baselineVersionNo: 3, definition: 'a'.repeat(64) } };
  window.history.replaceState({}, '', `/ai-agent?purpose=MODEL_STRUCTURE_REVIEW&reviewModelId=7&reviewBaselineVersionNo=3&reviewDefinition=${'a'.repeat(64)}`);
  try {
    render(<AiAgentPage />);
    expect(await screen.findByText('模型 #7 发布 V3 → 已保存结构变更核对')).toBeInTheDocument();
    fireEvent.click(screen.getByText('准备结构变更说明'));
    expect(screen.getByPlaceholderText(/输入/)).toHaveValue(governanceQuestions(target)[0]);
    expect(streamTurnEvents).not.toHaveBeenCalled();
    expect(screen.getByText('返回来源').closest('a')).toHaveAttribute('href', '/modeling/models/7?tab=version&reviewVersion=3');
  } finally { window.history.replaceState({}, '', '/'); }
});
it('rejects incomplete structure review context instead of exposing ordinary chat', () => {
  window.history.replaceState({}, '', '/ai-agent?reviewModelId=7');
  try {
    render(<AiAgentPage />);
    expect(screen.queryByPlaceholderText(/输入/)).not.toBeInTheDocument();
    expect(streamTurnEvents).not.toHaveBeenCalled();
  } finally { window.history.replaceState({}, '', '/'); }
});
