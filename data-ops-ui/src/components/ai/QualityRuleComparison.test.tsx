import React from 'react';
import { render, screen, within } from '@testing-library/react';
import QualityRuleComparison from './QualityRuleComparison';

const candidate = { templateId: 2, name: '候选', columnName: 'status', operator: 'EQ' as const,
  threshold: 0, enumValues: ['A', 'B'], enabled: false };

test('shows form provenance, candidate disabled and bounded rows with explicit truncation', () => {
  render(<QualityRuleComparison candidate={candidate} rules={Array.from({ length: 7 }, (_, index) => ({
    ...candidate, name: `规则${index}`, threshold: index, enabled: index === 0,
  }))} />);
  const table = screen.getByRole('table', { name: '规则对照：候选' });
  expect(within(table).getAllByRole('row')).toHaveLength(7);
  expect(screen.getByText('带入后停用')).toBeInTheDocument();
  expect(screen.getByText('规则0（相同条件）')).toBeInTheDocument();
  expect(screen.getByText('规则1（不同条件）')).toBeInTheDocument();
  expect(screen.getByText(/另有 2 条未在此显示/)).toBeInTheDocument();
  expect(screen.getByText(/当前表单，包含未保存修改/)).toBeInTheDocument();
});

test('does not present a different template or case as related evidence', () => {
  render(<QualityRuleComparison candidate={candidate} rules={[
    { ...candidate, name: '不同模板', templateId: 3 }, { ...candidate, name: '不同字段', columnName: 'STATUS' },
  ]} />);
  expect(screen.getByText(/当前表单没有同模板/)).toBeInTheDocument();
  expect(screen.queryByText('不同模板')).not.toBeInTheDocument();
});
