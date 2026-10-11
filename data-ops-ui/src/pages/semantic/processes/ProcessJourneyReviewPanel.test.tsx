import React from 'react';
import { fireEvent, render, screen } from '@testing-library/react';
import ProcessJourneyReviewPanel from './ProcessJourneyReviewPanel';
import type { ProcessJourneyReview } from './processJourneyReview';

const report: ProcessJourneyReview = {
  processId: 7, unknownCount: 1, attentionCount: 2,
  explanation: '该进度只读，不代表已经运行或消费',
  steps: [
    { key: 'grain', name: '业务过程与粒度', status: 'NEEDS_ATTENTION',
      summary: '尚未明确业务粒度', action: 'process' },
    { key: 'source', name: '业务过程来源', status: 'NEEDS_ATTENTION',
      evidence: [{ id: '42', label: 'order_detail', detail: 'DETAIL，无 MAIN', status: 'REVIEW' }],
      summary: '来源不可读，不等于空', action: 'process' },
    { key: 'standard', name: '过程标准字段', status: 'NOT_STARTED',
      summary: '未引用标准字段', action: 'process' },
    { key: 'logical', name: '逻辑业务设计', status: 'NOT_STARTED',
      summary: '尚未创建逻辑草稿', action: 'logical' },
    { key: 'physical', name: '物理模型设计', status: 'RECORDED',
      summary: '建模不等于部署', action: 'physical' },
  ],
};

it('shows evidence labels and routes to owner tabs/workspaces without creating tasks', () => {
  const openProcess = jest.fn();
  const openLogical = jest.fn();
  const openPhysical = jest.fn();
  const openEvidence = jest.fn();
  render(<ProcessJourneyReviewPanel report={report} loading={false}
    onReload={jest.fn()} onOpenProcess={openProcess}
    onOpenLogical={openLogical} onOpenPhysical={openPhysical} onOpenEvidence={openEvidence}
    onOpenLogicalEvidence={jest.fn()} onOpenPhysicalEvidence={jest.fn()} />);
  expect(screen.getByText(/有 1 项领域事实不可读取/)).toBeInTheDocument();
  expect(screen.getByText('来源不可读，不等于空')).toBeInTheDocument();
  const processButtons = screen.getAllByRole('button', { name: '查看过程配置' });
  fireEvent.click(processButtons[1]);
  expect(openProcess).toHaveBeenCalledWith('source');
  fireEvent.click(screen.getByRole('button', { name: '进入逻辑设计' }));
  fireEvent.click(screen.getByRole('button', { name: '查看物理模型' }));
  expect(openLogical).toHaveBeenCalledTimes(1);
  expect(openPhysical).toHaveBeenCalledTimes(1);
});

it('does not present missing context as successfully empty data', () => {
  render(<ProcessJourneyReviewPanel loading={false} onReload={jest.fn()}
    onOpenProcess={jest.fn()} onOpenLogical={jest.fn()} onOpenPhysical={jest.fn()} onOpenEvidence={jest.fn()}
    onOpenLogicalEvidence={jest.fn()} onOpenPhysicalEvidence={jest.fn()} />);
  expect(screen.getByText('暂无完整的建设上下文')).toBeInTheDocument();
  expect(screen.queryByText('已有当前记录')).not.toBeInTheDocument();
});

it('can locate an exact reviewed source row instead of redirecting generically', () => {
  const openEvidence = jest.fn();
  render(<ProcessJourneyReviewPanel report={report} loading={false}
    onReload={jest.fn()} onOpenProcess={jest.fn()} onOpenLogical={jest.fn()}
    onOpenPhysical={jest.fn()} onOpenEvidence={openEvidence}
    onOpenLogicalEvidence={jest.fn()} onOpenPhysicalEvidence={jest.fn()} />);
  fireEvent.click(screen.getByText('逐项查看（1）'));
  fireEvent.click(screen.getByRole('button', { name: '定位此对象' }));
  expect(openEvidence).toHaveBeenCalledWith('source', 42);
});


test('exact logical/physical owner evidence has per-record navigation, never a write action', () => {
  const openLogicalEvidence = jest.fn();
  const openPhysicalEvidence = jest.fn();
  const withOwners: ProcessJourneyReview = {
    ...report,
    steps: report.steps.map(step => step.key === 'logical'
      ? { ...step, status: 'RECORDED', evidence: [
        { id: '21', label: '订单逻辑草稿', detail: 'draft #21', status: 'OK', governanceTarget: 'logical' },
      ] }
      : step.key === 'physical' ? { ...step, evidence: [
        { id: '34', label: 'dwd_orders', detail: 'DWD · DRAFT', status: 'OK', governanceTarget: 'physical' },
      ] } : step),
  };
  render(<ProcessJourneyReviewPanel report={withOwners} loading={false}
    onReload={jest.fn()} onOpenProcess={jest.fn()} onOpenLogical={jest.fn()}
    onOpenPhysical={jest.fn()} onOpenEvidence={jest.fn()}
    onOpenLogicalEvidence={openLogicalEvidence} onOpenPhysicalEvidence={openPhysicalEvidence} />);
  const expand = screen.getAllByText('逐项查看（1）');
  expand.forEach(item => fireEvent.click(item));
  fireEvent.click(screen.getByRole('button', { name: '审阅此逻辑草稿' }));
  fireEvent.click(screen.getByRole('button', { name: '核验此物理模型字段' }));
  expect(openLogicalEvidence).toHaveBeenCalledWith(21);
  expect(openPhysicalEvidence).toHaveBeenCalledWith(34);
});

test('unavailable logical and physical owners do not expose invented evidence routes', () => {
  const withUnknown: ProcessJourneyReview = { ...report, steps: report.steps.map(step =>
    step.key === 'logical' || step.key === 'physical'
      ? { ...step, status: 'UNAVAILABLE', evidence: undefined } : step) };
  render(<ProcessJourneyReviewPanel report={withUnknown} loading={false}
    onReload={jest.fn()} onOpenProcess={jest.fn()} onOpenLogical={jest.fn()}
    onOpenPhysical={jest.fn()} onOpenEvidence={jest.fn()}
    onOpenLogicalEvidence={jest.fn()} onOpenPhysicalEvidence={jest.fn()} />);
  expect(screen.queryByRole('button', { name: '审阅此逻辑草稿' })).not.toBeInTheDocument();
  expect(screen.queryByRole('button', { name: '核验此物理模型字段' })).not.toBeInTheDocument();
});
