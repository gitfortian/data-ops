import { act, renderHook, waitFor } from '@testing-library/react';
import { getQualityExecutionWorkspace, getQualityExecutionLogs, listQualityExecutionWorkspace } from '@/services/data-quality';
import { useExecutionDetailPage } from './useExecutionDetailPage';

jest.mock('@/services/data-quality', () => ({
  getQualityExecutionWorkspace: jest.fn(), getQualityExecutionLogs: jest.fn().mockResolvedValue({}),
  listQualityExecutionWorkspace: jest.fn().mockResolvedValue({ records: [] }),
}));
jest.mock('antd', () => ({ message: { error: jest.fn() } }));
const detail = (executionNo: string) => ({ executionNo, monitorId: 7, executionStatus: 'SUCCESS', rules: [] });
beforeEach(() => jest.clearAllMocks());

test('newer refresh wins and a failed detail cannot keep comparison history', async () => {
  jest.mocked(getQualityExecutionWorkspace).mockResolvedValue(detail('after') as never);
  const { result } = renderHook(() => useExecutionDetailPage('after'));
  await waitFor(() => expect(result.current.detail?.executionNo).toBe('after'));
  let resolveOld!: (value: unknown) => void;
  jest.mocked(getQualityExecutionWorkspace).mockImplementationOnce(() => new Promise((resolve) => { resolveOld = resolve; }) as never);
  let pending!: Promise<void>;
  act(() => { pending = result.current.refresh(); });
  jest.mocked(getQualityExecutionWorkspace).mockRejectedValueOnce(new Error('cannot read'));
  await act(async () => { await result.current.refresh(); });
  expect(result.current.detail).toBeUndefined(); expect(result.current.historyRecords).toEqual([]);
  await act(async () => { resolveOld(detail('after')); await pending; });
  expect(result.current.detail).toBeUndefined();
});

test('unmounted project/execution scope cannot apply late reads or fetch its history', async () => {
  let resolveOld!: (value: unknown) => void;
  jest.mocked(getQualityExecutionWorkspace).mockImplementationOnce(() => new Promise((resolve) => { resolveOld = resolve; }) as never);
  const old = renderHook(() => useExecutionDetailPage('before'));
  old.unmount();
  jest.mocked(getQualityExecutionWorkspace).mockResolvedValue(detail('after') as never);
  const next = renderHook(() => useExecutionDetailPage('after'));
  await waitFor(() => expect(next.result.current.detail?.executionNo).toBe('after'));
  jest.mocked(listQualityExecutionWorkspace).mockClear();
  await act(async () => { resolveOld(detail('before')); });
  expect(next.result.current.detail?.executionNo).toBe('after');
  expect(listQualityExecutionWorkspace).not.toHaveBeenCalled();
  expect(getQualityExecutionLogs).toHaveBeenCalledWith('after');
});

test('mismatched server identity never becomes current detail', async () => {
  jest.mocked(getQualityExecutionWorkspace).mockResolvedValue(detail('forged') as never);
  const { result } = renderHook(() => useExecutionDetailPage('after'));
  await waitFor(() => expect(result.current.loading).toBe(false));
  expect(result.current.detail).toBeUndefined();
  expect(listQualityExecutionWorkspace).not.toHaveBeenCalled();
});
