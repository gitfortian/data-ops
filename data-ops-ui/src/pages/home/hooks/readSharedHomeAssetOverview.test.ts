import { homeAssetOverviewApi } from '@/services/home';
import { readSharedHomeAssetOverview } from './readSharedHomeAssetOverview';

jest.mock('@/services/home', () => ({
  homeAssetOverviewApi: { overview: jest.fn() },
}));

describe('project/identity-scoped asset overview in-flight coalescing', () => {
  const overview = homeAssetOverviewApi.overview as jest.Mock;

  beforeEach(() => {
    overview.mockReset();
  });

  it('shares concurrent requests for the exact same grant scope only', async () => {
    const response = { code: 200, data: { dataset: { tableAssetCount: 3 } } };
    overview.mockImplementation(() => new Promise((resolve) => {
      queueMicrotask(() => resolve(response));
    }));
    const first = readSharedHomeAssetOverview('project-1/user-1/grants-a');
    const second = readSharedHomeAssetOverview('project-1/user-1/grants-a');
    const differentProject = readSharedHomeAssetOverview('project-2/user-1/grants-a');
    const differentGrant = readSharedHomeAssetOverview('project-1/user-1/grants-b');
    expect(first).toBe(second);
    expect(first).not.toBe(differentProject);
    expect(first).not.toBe(differentGrant);
    expect(overview).toHaveBeenCalledTimes(3);
    await Promise.all([first, second, differentProject, differentGrant]);
    const later = readSharedHomeAssetOverview('project-1/user-1/grants-a');
    expect(later).not.toBe(first);
    expect(overview).toHaveBeenCalledTimes(4);
    await later;
  });
});
