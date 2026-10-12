import { act, renderHook, waitFor } from '@testing-library/react';
import { message } from 'antd';
import { listQualityRuleExecutionWorkspace } from '@/services/data-quality';
import { useQualityExecutionPage } from './useQualityExecutionPage';

jest.mock('@/services/data-quality', () => ({
  listQualityExecutionWorkspace: jest.fn(),
  listQualityRuleExecutionWorkspace: jest.fn(),
}));
jest.mock('@umijs/max', () => ({
  useIntl: () => ({ formatMessage: ({ id }: { id: string }) => id }),
}));
jest.mock('antd', () => ({ message: { error: jest.fn() } }));

const page = (executionNo: string) => ({
  records: [{ executionNo, executionStatus: 'SUCCESS' }],
  current: 1, pageSize: 20, total: 1,
});
beforeEach(() => jest.clearAllMocks());

test('switching datasource rejects a late old response', async () => {
  let resolveOld!: (value: unknown) => void;
  jest.mocked(listQualityRuleExecutionWorkspace)
    .mockImplementationOnce(() => new Promise(resolve => { resolveOld = resolve; }) as never)
    .mockResolvedValueOnce(page('new-source') as never);
  const { result, rerender } = renderHook(({ id }) => useQualityExecutionPage(id), {
    initialProps: { id: 1 },
  });
  rerender({ id: 2 });
  await waitFor(() => expect(result.current.ruleRecords[0]?.executionNo).toBe('new-source'));
  await act(async () => { resolveOld(page('old-source')); });
  expect(result.current.ruleRecords[0]?.executionNo).toBe('new-source');
});

test('failed history remains unavailable until an explicit retry succeeds', async () => {
  jest.mocked(listQualityRuleExecutionWorkspace)
    .mockRejectedValueOnce(new Error('permission denied'))
    .mockResolvedValueOnce(page('recovered') as never);
  const { result } = renderHook(() => useQualityExecutionPage(1));
  await waitFor(() => expect(result.current.loadError).toBe('permission denied'));
  expect(result.current.ruleRecords).toEqual([]);
  await act(async () => { await result.current.load(1); });
  expect(result.current.loadError).toBe('');
  expect(result.current.ruleRecords[0]?.executionNo).toBe('recovered');
});

test('a departed page does not show late errors', async () => {
  let rejectOld!: (reason: Error) => void;
  jest.mocked(listQualityRuleExecutionWorkspace).mockImplementationOnce(
    () => new Promise((_, reject) => { rejectOld = reject; }) as never,
  );
  const { unmount } = renderHook(() => useQualityExecutionPage(1));
  unmount();
  await act(async () => { rejectOld(new Error('old project')); });
  expect(message.error).not.toHaveBeenCalled();
});

test('a failed page transition retries that page rather than the previous successful page', async () => {
  jest.mocked(listQualityRuleExecutionWorkspace)
    .mockResolvedValueOnce(page('first') as never)
    .mockRejectedValueOnce(new Error('page unavailable'))
    .mockResolvedValueOnce({ ...page('second'), current: 2 } as never);
  const { result } = renderHook(() => useQualityExecutionPage(1));
  await waitFor(() => expect(result.current.ruleRecords[0]?.executionNo).toBe('first'));
  await act(async () => { await result.current.load(2); });
  expect(result.current.current).toBe(2);
  expect(result.current.loadError).toBe('page unavailable');
  await act(async () => { await result.current.load(result.current.current); });
  expect(listQualityRuleExecutionWorkspace).toHaveBeenLastCalledWith(expect.objectContaining({ current: 2 }));
  expect(result.current.ruleRecords[0]?.executionNo).toBe('second');
});
