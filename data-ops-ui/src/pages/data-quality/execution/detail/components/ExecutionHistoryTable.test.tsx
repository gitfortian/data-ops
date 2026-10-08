import React from 'react';
import { fireEvent, render, screen } from '@testing-library/react';
import type { ExecutionWorkspaceListItem } from '@/services/data-quality';
import { ExecutionHistoryTable } from './ExecutionHistoryTable';

jest.mock('@/components/ui', () => ({
  YakButton: ({ children, type: _type, size: _size, ...props }: any) => <button {...props}>{children}</button>,
  YakEmpty: ({ title }: any) => <div>{title}</div>,
}));
jest.mock('antd', () => ({ Table: ({ dataSource, columns }: any) => <div>{dataSource.map((row: any) =>
  <div key={row.executionNo} data-testid={row.executionNo}>{columns.at(-1).render(null, row)}</div>)}</div> }));
const row = (executionNo: string, executionStatus = 'SUCCESS', finishedAt: string | undefined = '2026-10-08') => ({
  executionNo, executionStatus, finishedAt,
}) as ExecutionWorkspaceListItem;

test('only explicit other finished rows can navigate to comparison; never auto chooses a row', () => {
  const compare = jest.fn(); const open = jest.fn();
  render(<ExecutionHistoryTable records={[row('after'), row('before'), row('active', 'RUNNING'), row('unfinished', 'FAILED', '')]}
    currentExecutionNo="after" loading={false} onOpen={open} onCompare={compare} />);
  expect(compare).not.toHaveBeenCalled(); expect(open).not.toHaveBeenCalled();
  expect(screen.getAllByText('与本次比较')).toHaveLength(1);
  fireEvent.click(screen.getByText('与本次比较')); expect(compare).toHaveBeenCalledWith('before');
});
test('permission/current-state gate removes comparison; loading blocks stale rows', () => {
  const compare = jest.fn();
  const { rerender } = render(<ExecutionHistoryTable records={[row('before')]} currentExecutionNo="after" loading={false} onOpen={jest.fn()} />);
  expect(screen.queryByText('与本次比较')).toBeNull();
  rerender(<ExecutionHistoryTable records={[row('before')]} currentExecutionNo="after" loading onOpen={jest.fn()} onCompare={compare} />);
  fireEvent.click(screen.getByText('与本次比较')); expect(compare).not.toHaveBeenCalled();
});
