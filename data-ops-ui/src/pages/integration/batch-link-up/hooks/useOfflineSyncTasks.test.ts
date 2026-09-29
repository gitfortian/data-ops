import { act, renderHook, waitFor } from '@testing-library/react';

import { listOfflineSyncTasks } from '@/services/batch-link-up';

import { useOfflineSyncTasks } from './useOfflineSyncTasks';

let mockCurrentProjectId = 7;

jest.mock('@/contexts/SecurityProjectContext', () => ({
  useSecurityProject: () => ({
    currentProject: { id: mockCurrentProjectId, name: `Project ${mockCurrentProjectId}` },
  }),
}));

jest.mock('@/services/batch-link-up', () => ({
  listOfflineSyncTasks: jest.fn(),
}));

jest.mock('@umijs/max', () => ({
  history: { replace: jest.fn() },
}));

const deferred = <T,>() => {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((next) => {
    resolve = next;
  });
  return { promise, resolve };
};

const page = (id: number, jobName: string) =>
  ({
    bizData: [{ id, jobName }],
    pagination: { current: 1, pageSize: 10, total: 1 },
  }) as any;

describe('useOfflineSyncTasks Project Space isolation', () => {
  const mockListOfflineSyncTasks = jest.mocked(listOfflineSyncTasks);

  beforeEach(() => {
    mockCurrentProjectId = 7;
    mockListOfflineSyncTasks.mockReset();
    window.history.replaceState({}, '', '/batch-link-up');
  });

  it('drops stale rows and stale responses after the workspace changes', async () => {
    const projectA = deferred<any>();
    const projectB = deferred<any>();
    mockListOfflineSyncTasks
      .mockImplementationOnce(() => projectA.promise)
      .mockImplementationOnce(() => projectB.promise);

    const { result, rerender } = renderHook(() => useOfflineSyncTasks());
    await waitFor(() => expect(mockListOfflineSyncTasks).toHaveBeenCalledTimes(1));

    mockCurrentProjectId = 8;
    rerender();

    await waitFor(() => expect(mockListOfflineSyncTasks).toHaveBeenCalledTimes(2));
    expect(result.current.records).toEqual([]);

    await act(async () => {
      projectA.resolve(page(101, 'Project A task'));
      await projectA.promise;
    });
    expect(result.current.records).toEqual([]);

    await act(async () => {
      projectB.resolve(page(202, 'Project B task'));
      await projectB.promise;
    });
    await waitFor(() => expect(result.current.records).toEqual(page(202, 'Project B task').bizData));
  });
});
