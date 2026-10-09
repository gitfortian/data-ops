import HttpUtils from '@/utils/HttpUtils';
import {
  getDevelopmentTaskRevision,
  listDevelopmentTaskRevisions,
} from './api';

describe('Data Development task revisions modern API contract', () => {
  afterEach(() => {
    jest.restoreAllMocks();
  });

  it('returns unwrapped revision lists from the unchanged GET revisions endpoint', async () => {
    const rows = [{ id: 'revision-1', revisionNo: 1 }];
    const getData = jest.spyOn(HttpUtils, 'getData').mockResolvedValue(rows);

    await expect(listDevelopmentTaskRevisions('node-42')).resolves.toEqual(rows);
    expect(getData).toHaveBeenCalledWith(
      '/api/v1/data-development/nodes/node-42/revisions',
    );
  });

  it('retains percent-encoded node identity in revision detail GET', async () => {
    const detail = { id: 'revision-9', revisionNo: 9 };
    const getData = jest.spyOn(HttpUtils, 'getData').mockResolvedValue(detail);

    await expect(getDevelopmentTaskRevision('project/task 1', 9)).resolves.toEqual(detail);
    expect(getData).toHaveBeenCalledWith(
      '/api/v1/data-development/nodes/project%2Ftask%201/revisions/9',
    );
  });

  it('preserves empty version lists and propagates failed reads', async () => {
    const getData = jest.spyOn(HttpUtils, 'getData')
      .mockResolvedValueOnce([])
      .mockRejectedValueOnce(new Error('403 forbidden'));

    await expect(listDevelopmentTaskRevisions('node-42')).resolves.toEqual([]);
    await expect(getDevelopmentTaskRevision('node-42', 2)).rejects.toThrow('403 forbidden');
    expect(getData).toHaveBeenCalledTimes(2);
  });
});
