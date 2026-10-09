import HttpUtils from '@/utils/HttpUtils';
import { listDevelopmentTaskExecutions } from './api';

describe('Data Development execution history modern endpoint contract', () => {
  afterEach(() => {
    jest.restoreAllMocks();
  });

  it('uses the unchanged GET executions route and returns the unwrapped page', async () => {
    const page = { records: [{ id: 'e-1' }], total: 1, pageNo: 2, pageSize: 20 };
    const getData = jest.spyOn(HttpUtils, 'getData').mockResolvedValue(page);

    await expect(listDevelopmentTaskExecutions({ pageNo: 2, pageSize: 20 })).resolves.toEqual(page);
    expect(getData).toHaveBeenCalledWith(
      '/api/v1/data-development/executions?pageNo=2&pageSize=20',
    );
  });

  it('preserves filtering, timestamp values, and query-string encoding', async () => {
    const getData = jest.spyOn(HttpUtils, 'getData').mockResolvedValue({
      records: [], total: 0, pageNo: 1, pageSize: 20,
    });
    await listDevelopmentTaskExecutions({
      pageNo: 1,
      pageSize: 20,
      keyword: 'sql task',
      status: 'RUNNING',
      taskType: 'SQL',
      triggerType: 'WORKFLOW',
      startTime: '2026-10-01 00:00:00',
      endTime: '2026-10-09 23:59:59',
    });

    expect(getData).toHaveBeenCalledWith(
      '/api/v1/data-development/executions?'
        + 'pageNo=1&pageSize=20&keyword=sql+task&status=RUNNING'
        + '&taskType=SQL&triggerType=WORKFLOW'
        + '&startTime=2026-10-01+00%3A00%3A00&endTime=2026-10-09+23%3A59%3A59',
    );
  });

  it('keeps valid empty pages and propagates failed responses to the workspace error UI', async () => {
    const getData = jest.spyOn(HttpUtils, 'getData')
      .mockResolvedValueOnce({ records: [], total: 0, pageNo: 1, pageSize: 20 })
      .mockRejectedValueOnce(new Error('403 forbidden'));

    await expect(listDevelopmentTaskExecutions({})).resolves.toEqual({
      records: [], total: 0, pageNo: 1, pageSize: 20,
    });
    await expect(listDevelopmentTaskExecutions({ pageNo: 2 })).rejects.toThrow('403 forbidden');
    expect(getData).toHaveBeenCalledTimes(2);
  });
});
