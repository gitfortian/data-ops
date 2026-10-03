import { act, renderHook } from '@testing-library/react';
import { homeCockpitApi } from '@/services/home';
import { useHomeCockpit } from './useHomeCockpit';

let mockProjectId = 1;
jest.mock('@/contexts/SecurityProjectContext', () => ({
  useSecurityProject: () => ({ currentProject: { id: mockProjectId } }),
}));
jest.mock('@/services/home', () => ({ homeCockpitApi: { overview: jest.fn() } }));

test('project switch clears the prior view and rejects its late response', async () => {
  type Response = { data: { header: { dataSourceCount: number } } };
  let first!: (value: Response) => void;
  let second!: (value: Response) => void;
  (homeCockpitApi.overview as jest.Mock)
    .mockImplementationOnce(() => new Promise(resolve => { first = resolve; }))
    .mockImplementationOnce(() => new Promise(resolve => { second = resolve; }));
  const hook = renderHook(() => useHomeCockpit());
  mockProjectId = 2;
  hook.rerender();
  await act(async () => { first({ data: { header: { dataSourceCount: 99 } } }); });
  expect(hook.result.current).toEqual({ loading: true, failed: false });
  await act(async () => { second({ data: { header: { dataSourceCount: 0 } } }); });
  expect(hook.result.current.loading).toBe(false);
  expect(hook.result.current.data?.header.dataSourceCount).toBe(0);
  hook.unmount();
});
