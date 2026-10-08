import React from 'react';
import { render, screen } from '@testing-library/react';
import ScenarioHistoryCard from './ScenarioHistoryCard';
import { readScenarioHistory } from '@/services/agent/scenarioHistory';
import { receiptText, scenario } from '../../../tests/fixtures/agent-scenarios';

it.each([
  ['STANDARD_MATCH', '类型标准匹配结果', '客户标识标准（customer_id）v2 · BIGINT'],
  ['MODEL_MAPPING', '来源字段映射结果', '来源字段 id · 类型 BIGINT · 不可空'],
  ['METRIC_EXPLANATION', '指标口径解释结果', 'AI 解读：按日统计客户'],
  ['METRIC_DRAFT', '指标定义草稿结果', '聚合：COUNT_DISTINCT(id)'],
] as const)('renders %s as read-only history with the original source link', (kind, title, content) => {
  const f = scenario(kind);
  render(<ScenarioHistoryCard review={readScenarioHistory(f.text, 's1', 't1', true, f.continuation)} />);
  expect(screen.getByText(title)).toBeInTheDocument();
  expect(screen.getByText(content)).toBeInTheDocument();
  expect(screen.getByText('待确认：请核对业务范围')).toBeInTheDocument();
  expect(screen.getByText(/尚未核验当前有效性/)).toBeInTheDocument();
  expect(screen.getAllByRole('link')).toHaveLength(1);
  expect(screen.queryByRole('button')).not.toBeInTheDocument();
  expect(screen.queryByText(/```yak/)).not.toBeInTheDocument();
});

it.each(['ATOMIC', 'DERIVED', 'COMPOSITE'] as const)('shows %s draft dependencies and keeps new drafts at the original list', type => {
  const f = scenario('METRIC_DRAFT', type);
  render(<ScenarioHistoryCard review={readScenarioHistory(f.text, 's1', 't1', true, f.continuation)} />);
  expect(screen.getByRole('link', { name: '返回原页面核对' })).toHaveAttribute('href', '/metric/manage');
  expect(screen.getByText(/未保存的表单不会自动恢复/)).toBeInTheDocument();
  if (type !== 'ATOMIC') expect(screen.getByText('当时依赖 客户基数（customers）v2')).toBeInTheDocument();
  if (type === 'COMPOSITE') expect(screen.getByText('组合：customers')).toBeInTheDocument();
});

it('labels historical snapshots with their exact version and raw facts', () => {
  const f = scenario('METRIC_EXPLANATION', 'ATOMIC', true);
  render(<ScenarioHistoryCard review={readScenarioHistory(f.text, 's1', 't1', true, f.continuation)} />);
  expect(screen.getByText(/历史快照 v3，仅供阅读/)).toBeInTheDocument();
  expect(screen.getByText('DAY')).toBeInTheDocument();
  expect(screen.getByRole('link')).toHaveAttribute('href', '/metric/manage/7');
});

it('keeps empty candidates, truncation and untrusted strings visible as plain text', () => {
  const f = scenario();
  if (f.value.kind !== 'STANDARD_MATCH') throw new Error('fixture');
  const text = receiptText({ ...f.value, candidates: [], truncated: true, questions: ['<img src=x onerror=alert(1)>'] });
  const { container } = render(<ScenarioHistoryCard review={readScenarioHistory(text, 's1', 't1', true, f.continuation)} />);
  expect(screen.getByText(/本轮没有候选/)).toBeInTheDocument();
  expect(screen.getByText(/生成时目录已截断/)).toBeInTheDocument();
  expect(screen.getByText('待确认：<img src=x onerror=alert(1)>')).toBeInTheDocument();
  expect(container.querySelector('img')).toBeNull();
});

it('offers no source link when a receipt cannot be verified', () => {
  const { rerender, container } = render(<ScenarioHistoryCard review={{ status: 'NONE' }} />);
  expect(container).toBeEmptyDOMElement();
  rerender(<ScenarioHistoryCard review={{ status: 'UNAVAILABLE' }} />);
  expect(screen.getByText('场景结果尚未核对')).toBeInTheDocument();
  expect(screen.queryByRole('link')).not.toBeInTheDocument();
});
