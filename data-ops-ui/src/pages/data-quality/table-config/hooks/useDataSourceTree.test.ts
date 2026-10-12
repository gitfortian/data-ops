import { act, renderHook } from '@testing-library/react';
import { message } from 'antd';
import { listAllDataSources } from '@/services/data-source';
import { useDataSourceTree } from './useDataSourceTree';

jest.mock('@/services/data-source', () => ({ listAllDataSources: jest.fn() }));
jest.mock('@umijs/max', () => ({
  useIntl: () => ({ formatMessage: ({ id }: { id: string }) => id }),
}));
jest.mock('antd', () => ({ message: { error: jest.fn() } }));
beforeEach(() => jest.clearAllMocks());

test('a source read failure remains distinct from a confirmed empty source list', async () => {
  jest.mocked(listAllDataSources)
    .mockRejectedValueOnce(new Error('source forbidden'))
    .mockResolvedValueOnce({ bizData: [] } as never);
  const { result } = renderHook(() => useDataSourceTree());
  await act(async () => { await result.current.loadSourceTree(); });
  expect(result.current.sourceError).toBe('source forbidden');
  await act(async () => { await result.current.loadSourceTree(); });
  expect(result.current.sourceError).toBe('');
  expect(result.current.sourceNodes).toEqual([]);
});

test('late failed source reads cannot notify a departed project', async () => {
  let reject!: (reason: Error) => void;
  jest.mocked(listAllDataSources).mockImplementationOnce(
    () => new Promise((_, next) => { reject = next; }) as never,
  );
  const { result, unmount } = renderHook(() => useDataSourceTree());
  let pending!: ReturnType<typeof result.current.loadSourceTree>;
  act(() => { pending = result.current.loadSourceTree(); });
  unmount();
  await act(async () => { reject(new Error('departed project')); await pending; });
  expect(message.error).not.toHaveBeenCalled();
});
