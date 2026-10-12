import { act, renderHook, waitFor } from '@testing-library/react';
import {
  listQualityTableAssets, listQualityTableCandidates, registerQualityTables,
  type TableCandidateView,
} from '@/services/data-quality';
import { useTableAssets } from './useTableAssets';

jest.mock('@/services/data-quality', () => ({
  listQualityTableAssets: jest.fn(),
  listQualityTableCandidates: jest.fn(),
  registerQualityTables: jest.fn(),
  deleteQualityTableAsset: jest.fn(),
}));
jest.mock('@umijs/max', () => ({
  history: { push: jest.fn() },
  useIntl: () => ({ formatMessage: ({ id }: { id: string }) => id }),
}));
jest.mock('antd', () => ({ message: { error: jest.fn(), success: jest.fn(), warning: jest.fn() } }));
const candidate: TableCandidateView = {
  databaseName: 'trade', schemaName: '', tableName: 'orders', tableType: 'TABLE',
};
const options = { dataSourceId: 1, selectedDataSource: { id: 1, name: 'Source' } as never };
beforeEach(() => {
  jest.clearAllMocks();
  jest.mocked(listQualityTableAssets).mockResolvedValue({ records: [], total: 0 } as never);
});

test('registered table read failure is distinct from an empty successful result', async () => {
  jest.mocked(listQualityTableAssets).mockRejectedValueOnce(new Error('provider unavailable'));
  const { result } = renderHook(() => useTableAssets(options));
  await waitFor(() => expect(result.current.assetError).toBe('provider unavailable'));
  await act(async () => { await result.current.requestAssets(1, 1, 'orders'); });
  expect(result.current.assetError).toBe('');
  expect(listQualityTableAssets).toHaveBeenLastCalledWith(expect.objectContaining({ keyword: 'orders' }));
});

test('candidate failure cannot submit retained selections; retry preserves the requested scope', async () => {
  jest.mocked(listQualityTableCandidates)
    .mockResolvedValueOnce({ records: [candidate], total: 1 } as never)
    .mockRejectedValueOnce(new Error('cannot confirm candidates'))
    .mockResolvedValueOnce({ records: [candidate], total: 1 } as never);
  const { result } = renderHook(() => useTableAssets(options));
  act(() => result.current.openRegisterDrawer());
  await waitFor(() => expect(result.current.candidates).toHaveLength(1));
  act(() => {
    result.current.updateCandidateSelection(candidate, true);
    result.current.setCandidateCurrent(2);
  });
  await waitFor(() => expect(result.current.candidateError).toBe('cannot confirm candidates'));
  await act(async () => { await result.current.handleRegister(); });
  expect(registerQualityTables).not.toHaveBeenCalled();
  await act(async () => { await result.current.retryCandidates(); });
  expect(result.current.candidateError).toBe('');
  expect(result.current.selectedCandidateRecords).toEqual([candidate]);
  expect(listQualityTableCandidates).toHaveBeenLastCalledWith(expect.objectContaining({
    dataSourceId: 1, current: 2,
  }));
});

test('late registration after leaving the source does not reload that source', async () => {
  let resolve!: (value: unknown) => void;
  jest.mocked(listQualityTableCandidates).mockResolvedValue({ records: [candidate], total: 1 } as never);
  jest.mocked(registerQualityTables).mockImplementationOnce(() => new Promise(next => { resolve = next; }) as never);
  const { result, rerender } = renderHook(({ id }) => useTableAssets({
    dataSourceId: id, selectedDataSource: { id, name: 'Source' } as never,
  }), { initialProps: { id: 1 } });
  act(() => result.current.openRegisterDrawer());
  await waitFor(() => expect(result.current.candidates).toHaveLength(1));
  act(() => result.current.updateCandidateSelection(candidate, true));
  let pending!: Promise<void>;
  act(() => { pending = result.current.handleRegister(); });
  rerender({ id: 2 });
  act(() => result.current.resetForDataSource());
  jest.mocked(listQualityTableAssets).mockClear();
  await act(async () => { resolve({ registered: 1 }); await pending; });
  expect(listQualityTableAssets).not.toHaveBeenCalled();
});
