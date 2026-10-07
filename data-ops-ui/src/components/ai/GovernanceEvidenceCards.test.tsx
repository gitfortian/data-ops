import React from 'react';
import { fireEvent, render, screen } from '@testing-library/react';
import GovernanceEvidenceCards from './GovernanceEvidenceCards';

const statuses = ['OK', 'EMPTY', 'NOT_APPLICABLE', 'UNAVAILABLE', 'PERMISSION_DENIED'];
const cards = statuses.map((status, index) => ({ id: `E1234ABC${index}`, owner: 'QUALITY', reference: `run-${index}`,
  status, observedAt: '2026-10-07T10:00:00Z', sourceUpdatedAt: 'unknown', path: `/data-quality/execution/run-${index}` }));
const evidence = (value: unknown) => `\`\`\`yak-evidence\n${JSON.stringify(value)}\n\`\`\``;

it('explains all five states without converting source availability into business health', () => {
  render(<GovernanceEvidenceCards text={evidence(cards)} />);
  expect(screen.getByText(/本段回答提供 5 条证据/)).toBeInTheDocument();
  expect(screen.getByText(/不代表业务健康或合规/)).toBeInTheDocument();
  expect(screen.getByText(/不能据此推断无风险或无下游/)).toBeInTheDocument();
  expect(screen.getByText(/源域声明此项不适用/)).toBeInTheDocument();
  expect(screen.getByText(/不能视为空记录/)).toBeInTheDocument();
  expect(screen.getByText(/当前身份无权读取/)).toBeInTheDocument();
  expect(screen.getAllByRole('link', { name: '核对来源' })).toHaveLength(5);
  expect(screen.getAllByText(/源更新：未知/)).toHaveLength(5);
});

it('filters only this answer and resets when the answer changes', () => {
  const view = render(<GovernanceEvidenceCards text={evidence(cards)} />);
  fireEvent.click(screen.getByRole('radio', { name: '暂不可用（1）' }));
  expect(screen.getAllByRole('link', { name: '核对来源' })).toHaveLength(1);
  expect(screen.getByText('来源对象：run-3')).toBeInTheDocument();
  view.rerender(<GovernanceEvidenceCards text={evidence([cards[0]])} />);
  expect(screen.getByText('来源对象：run-0')).toBeInTheDocument();
  expect(screen.getByRole('radio', { name: '全部（1）' })).toBeChecked();
});

it('shows source references and server fact text safely without rendering injected markup', () => {
  const text = evidence([cards[0]]) + `\`\`\`yak-facts\n${JSON.stringify([
    { evidenceRef: cards[0].id, field: 'status', value: '<img src=x onerror=alert(1)>' },
  ])}\n\`\`\``;
  const { container } = render(<GovernanceEvidenceCards text={text} />);
  expect(screen.getByText(cards[0].id)).toBeInTheDocument();
  expect(screen.getByRole('link', { name: '核对来源' })).toHaveAttribute('href', cards[0].path);
  expect(screen.getByText(/核验值 · status/)).toHaveTextContent('<img src=x onerror=alert(1)>');
  expect(container.querySelector('img')).toBeNull();
});

it('reports invalid or ambiguous evidence without inventing a source and leaves ordinary text alone', () => {
  const view = render(<GovernanceEvidenceCards text={evidence([cards[0], cards[0]])} />);
  expect(screen.getByText(/没有可展示的有效证据/)).toBeInTheDocument();
  expect(screen.queryByRole('link')).not.toBeInTheDocument();
  view.rerender(<GovernanceEvidenceCards text="普通回答" />);
  expect(screen.queryByRole('alert')).not.toBeInTheDocument();
});
