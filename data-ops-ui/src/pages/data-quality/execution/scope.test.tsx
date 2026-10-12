import React from 'react';
import { render, screen } from '@testing-library/react';
import QualityExecutionScope from './index';

let mockProjectId: number | undefined = 1;
let mockAllowed = true;
const mockMounts = jest.fn();
const mockUnmounts = jest.fn();
jest.mock('@/contexts/SecurityProjectContext', () => ({
  useSecurityProject: () => ({
    currentProject: mockProjectId === undefined ? undefined : { id: mockProjectId },
  }),
}));
jest.mock('@umijs/max', () => ({
  history: { push: jest.fn() },
  useIntl: () => ({ formatMessage: ({ id }: { id: string }) => id }),
}));
jest.mock('@/hooks/usePermissionAccess', () => ({
  usePermissionAccess: () => ({ can: () => mockAllowed, permissionCodes: mockAllowed ? ['quality:execution:read'] : [] }),
}));
jest.mock('./hooks/useQualityExecutionPage', () => ({
  useQualityExecutionPage: () => {
    require('react').useEffect(() => { mockMounts(); return () => mockUnmounts(); }, []);
    return { executionRecords: [], ruleRecords: [], loadError: '', draftFilters: {}, load: jest.fn() };
  },
}));
jest.mock('../table-config/hooks/useDataSourceTree', () => ({
  useDataSourceTree: () => ({ sourceNodes: [], loadSourceTree: jest.fn() }),
}));
jest.mock('../table-config/components/DataSourceTreePane', () => () => null);
jest.mock('./components/ExecutionRecordTable', () => () => null);
jest.mock('./components/ExecutionToolbar', () => () => null);
jest.mock('antd', () => ({
  ConfigProvider: ({ children }: { children: React.ReactNode }) => <>{children}</>,
  Alert: ({ message }: { message: string }) => <div>{message}</div>,
  Button: () => null, Pagination: () => null,
}));

beforeEach(() => { jest.clearAllMocks(); mockProjectId = 1; mockAllowed = true; });

test('project change discards the complete old execution scope; clearing it performs no reads', () => {
  const { rerender } = render(<QualityExecutionScope />);
  expect(mockMounts).toHaveBeenCalledTimes(1);
  mockProjectId = 2;
  rerender(<QualityExecutionScope />);
  expect(mockMounts).toHaveBeenCalledTimes(2);
  expect(mockUnmounts).toHaveBeenCalledTimes(1);
  mockProjectId = undefined;
  rerender(<QualityExecutionScope />);
  expect(mockUnmounts).toHaveBeenCalledTimes(2);
  expect(screen.getByText('请选择项目空间')).toBeInTheDocument();
  expect(mockMounts).toHaveBeenCalledTimes(2);
});

test('revoked execution permission removes the previous read scope', () => {
  const { rerender } = render(<QualityExecutionScope />);
  mockAllowed = false;
  rerender(<QualityExecutionScope />);
  expect(mockUnmounts).toHaveBeenCalledTimes(1);
  expect(screen.getByText('暂无执行读取权限')).toBeInTheDocument();
  expect(mockMounts).toHaveBeenCalledTimes(1);
});
