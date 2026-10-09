import { act, renderHook, waitFor } from '@testing-library/react';
import { useDataServiceMarketplace } from './useDataServiceMarketplace';
import {
  listDataServices, listDataServiceDataSources, listRecentDataServiceLogs,
  type DataServiceApi, type DataServiceCallLog,
} from '@/services/data-service';

let mockCanObserve = true;
jest.mock('@umijs/max', () => ({
  useAccess: () => ({ hasPermission: (code: string) => code === 'data-service:observe' && mockCanObserve }),
  useIntl: () => ({ formatMessage: ({ id }: { id: string }) => id }),
}));
jest.mock('antd', () => ({
  message: { error: jest.fn(), success: jest.fn(), warning: jest.fn() },
}));
jest.mock('@/services/data-service', () => ({
  listDataServices: jest.fn(),
  listDataServiceDataSources: jest.fn(),
  listRecentDataServiceLogs: jest.fn(),
  setDataServiceEnabled: jest.fn(), deleteDataService: jest.fn(),
}));
const api = (id: number): DataServiceApi => ({
  id, name: 'service-' + id, path: '/api/' + id,
  runtimePath: '/runtime/' + id, dataSourceId: 8, sql: 'select 1',
  parameterNames: [], maxRows: 100, timeoutSeconds: 30,
  enabled: true, authMode: 'API_KEY',
});
const call = (id: number): DataServiceCallLog => ({
  id, apiId: 7, serviceName: 'service-7',
  servicePath: '/api/7', callerType: 'CONSOLE',
  success: true, durationMs: 10, rowCount: 1,
});
const deferred = <T,>() => {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>(yes => { resolve = yes; });
  return { promise, resolve };
};

describe('Data Service marketplace independent source projections', () => {
  const list = jest.mocked(listDataServices);
  const sources = jest.mocked(listDataServiceDataSources);
  const logs = jest.mocked(listRecentDataServiceLogs);
  beforeEach(() => {
    mockCanObserve = true;
    list.mockReset().mockResolvedValue([api(7)]);
    sources.mockReset().mockResolvedValue([{ value: '8', label: 'Main DB' }]);
    logs.mockReset().mockResolvedValue([call(900)]);
  });

  it('keeps the API list usable when optional source name lookup fails', async () => {
    sources.mockRejectedValue(new Error('source unavailable'));
    const { result } = renderHook(useDataServiceMarketplace);
    await waitFor(() => expect(result.current.loading).toBe(false));
    expect(result.current.services.map(s => s.id)).toEqual([7]);
    expect(result.current.catalogIssue).toBeNull();
    expect(result.current.sourceUnavailable).toBe(true);
    expect(result.current.dataSourceName(8)).toBe('#8');
    expect(result.current.totalCalls).toBe(1);
    expect(result.current.callStatsUnavailable).toBe(false);
  });

  it('shows unknown counts instead of 0 when recent call projection is unavailable', async () => {
    logs.mockRejectedValue(new Error('observation unavailable'));
    const { result } = renderHook(useDataServiceMarketplace);
    await waitFor(() => expect(result.current.loading).toBe(false));
    expect(result.current.services).toHaveLength(1);
    expect(result.current.callStatsUnavailable).toBe(true);
    expect(result.current.totalCalls).toBeUndefined();
    expect(result.current.hotServices).toEqual([]);
    expect(result.current.recommendedServices).toHaveLength(1);
  });

  it('distinguishes forbidden catalog reads from empty and clears old write targets', async () => {
    list.mockResolvedValueOnce([api(7)])
      .mockRejectedValueOnce({ response: { status: 403 } })
      .mockResolvedValueOnce([api(9)]);
    const { result } = renderHook(useDataServiceMarketplace);
    await waitFor(() => expect(result.current.services).toHaveLength(1));
    act(() => result.current.openDetail(api(7)));
    expect(result.current.detailTarget?.id).toBe(7);
    await act(async () => { await result.current.loadMarketplace(); });
    expect(result.current.catalogIssue).toBe('FORBIDDEN');
    expect(result.current.services).toEqual([]);
    expect(result.current.detailTarget).toBeUndefined();
    expect(result.current.totalCalls).toBeUndefined();
    await act(async () => { await result.current.loadMarketplace(); });
    expect(result.current.catalogIssue).toBeNull();
    expect(result.current.services.map(s => s.id)).toEqual([9]);
  });

  it('discards older primary API results after newer refresh succeeds', async () => {
    const old = deferred<DataServiceApi[]>();
    list.mockReset().mockReturnValueOnce(old.promise).mockResolvedValueOnce([api(22)]);
    const { result } = renderHook(useDataServiceMarketplace);
    await act(async () => { await result.current.loadMarketplace(); });
    expect(result.current.services.map(s => s.id)).toEqual([22]);
    await act(async () => { old.resolve([api(11)]); await old.promise; });
    expect(result.current.services.map(s => s.id)).toEqual([22]);
  });

  it('does not query the observation endpoint for an actor without observe permission', async () => {
    mockCanObserve = false;
    const { result } = renderHook(useDataServiceMarketplace);
    await waitFor(() => expect(result.current.loading).toBe(false));
    expect(logs).not.toHaveBeenCalled();
    expect(result.current.totalCalls).toBeUndefined();
    expect(result.current.callStatsUnavailable).toBe(false);
    expect(result.current.services).toHaveLength(1);
  });

  it('invalidates pending previous-Project reads after unmount', async () => {
    const old = deferred<DataServiceApi[]>();
    list.mockReset().mockReturnValueOnce(old.promise);
    const { unmount } = renderHook(useDataServiceMarketplace);
    unmount();
    await act(async () => { old.resolve([api(12)]); await old.promise; });
    expect(list).toHaveBeenCalledTimes(1);
  });
});
