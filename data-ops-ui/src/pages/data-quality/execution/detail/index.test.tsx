import React from 'react';
import { fireEvent, render, screen } from '@testing-library/react';
import ExecutionDetailScope from './index';
import { history } from '@umijs/max';

let mockProjectId = 42;
let mockRouteNo = 'after';
let mockPermissions = new Set(['agent:chat:run', 'quality:execution:read', 'quality:monitor:read']);
let mockDetail: any;
let mockLoading = false;
let mockHistoryLoading = false;
const mockMounts = jest.fn(); const mockUnmounts = jest.fn();
jest.mock('@umijs/max', () => ({
  history: { push: jest.fn() }, useParams: () => ({ executionNo: mockRouteNo }), useLocation: () => ({ search: '' }),
}));
jest.mock('@/contexts/SecurityProjectContext', () => ({ useSecurityProject: () => ({ currentProject: { id: mockProjectId } }) }));
jest.mock('@/hooks/usePermissionAccess', () => ({ usePermissionAccess: () => ({ can: (permission: string) => mockPermissions.has(permission) }) }));
jest.mock('./hooks/useExecutionDetailPage', () => ({ useExecutionDetailPage: () => {
  require('react').useEffect(() => { mockMounts(); return () => mockUnmounts(); }, []);
  return { detail: mockDetail, logs: {}, historyRecords: [{ executionNo: 'before', executionStatus: 'SUCCESS', finishedAt: '2026-10-07' }],
    issueRules: [], loading: mockLoading, historyLoading: mockHistoryLoading, refreshing: false, refresh: jest.fn(), loadLogs: jest.fn() };
} }));
jest.mock('@/components/ui', () => ({
  YakEmpty: ({ title }: any) => <div>{title}</div>,
  YakButton: ({ children, className: _className, ...props }: any) => <button {...props}>{children}</button>,
  YakTab: ({ onChange }: any) => <button onClick={() => onChange('history')}>历史运行</button>,
}));
jest.mock('./components/ExecutionDetailHeader', () => ({ ExecutionDetailHeader: () => null }));
jest.mock('./components/ExecutionLogPanel', () => ({ ExecutionLogPanel: () => null }));
jest.mock('./components/ExecutionRuleTable', () => ({ ExecutionRuleTable: () => null }));
jest.mock('./components/ExecutionSectionCard', () => ({
  ExecutionSectionCard: ({ children }: any) => <div>{children}</div>, ExecutionMetricTile: () => null, ExecutionInfoItem: () => null,
}));
jest.mock('./components/ExecutionHistoryTable', () => ({ ExecutionHistoryTable: ({ onCompare }: any) =>
  onCompare ? <button onClick={() => onCompare('before')}>与本次比较</button> : null }));

beforeEach(() => {
  jest.clearAllMocks(); mockProjectId = 42; mockRouteNo = 'after'; mockLoading = false; mockHistoryLoading = false;
  mockPermissions = new Set(['agent:chat:run', 'quality:execution:read', 'quality:monitor:read']);
  mockDetail = { executionNo: 'after', executionStatus: 'SUCCESS', finishedAt: '2026-10-08', rules: [] };
});

test('entry only navigates after explicit history selection and freezes both IDs', () => {
  render(<ExecutionDetailScope />);
  expect(history.push).not.toHaveBeenCalled();
  fireEvent.click(screen.getByText('历史运行'));
  fireEvent.click(screen.getByText('与本次比较'));
  expect(history.push).toHaveBeenCalledWith('/ai-agent?qualityExecutionNo=after&qualityBaselineExecutionNo=before');
});

test.each(['agent:chat:run', 'quality:monitor:read', 'quality:execution:read', 'loading', 'historyLoading', 'running', 'identity', 'unfinished'])(
  'comparison is unavailable for %s', (mode) => {
    if (mockPermissions.has(mode)) mockPermissions.delete(mode);
    if (mode === 'loading') mockLoading = true;
    if (mode === 'historyLoading') mockHistoryLoading = true;
    if (mode === 'running') mockDetail.executionStatus = 'RUNNING';
    if (mode === 'identity') mockDetail.executionNo = 'old';
    if (mode === 'unfinished') mockDetail.finishedAt = undefined;
    render(<ExecutionDetailScope />);
    const tab = screen.queryByText('历史运行'); if (tab) fireEvent.click(tab);
    expect(screen.queryByText('与本次比较')).toBeNull();
    expect(history.push).not.toHaveBeenCalled();
  });

test('project, execution and revoked mockPermissions remount or remove the old read scope', () => {
  const { rerender } = render(<ExecutionDetailScope />);
  expect(mockMounts).toHaveBeenCalledTimes(1);
  mockProjectId = 43; rerender(<ExecutionDetailScope />);
  mockRouteNo = 'other'; rerender(<ExecutionDetailScope />);
  mockPermissions.delete('quality:monitor:read'); rerender(<ExecutionDetailScope />);
  expect(mockMounts).toHaveBeenCalledTimes(4); expect(mockUnmounts).toHaveBeenCalledTimes(3);
  mockPermissions.delete('quality:execution:read'); rerender(<ExecutionDetailScope />);
  expect(mockUnmounts).toHaveBeenCalledTimes(4);
});
