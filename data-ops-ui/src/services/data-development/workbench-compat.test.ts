import HttpUtils from '@/utils/HttpUtils';
import {
  previewDevelopmentSqlLineageRequest,
  publishDevelopmentTask,
  runDevelopmentTask,
} from './workbench-compat';

describe('Workbench-only raw envelope contracts', () => {
  afterEach(() => jest.restoreAllMocks());

  it('retains the original run POST body, route and full response envelope', async () => {
    const definition = {
      taskType: 'SQL', schemaVersion: 1, content: 'SELECT 1', configJson: '{}',
    } as never;
    const response = { code: 200, data: {
      id: 'execution-4', nodeId: 'node-1', status: 'PENDING',
    } };
    const post = jest.spyOn(HttpUtils, 'post').mockResolvedValue(response);
    await expect(runDevelopmentTask('node-1', definition)).resolves.toEqual(response);
    expect(post).toHaveBeenCalledWith('/api/v1/data-development/nodes/node-1/run', definition);
  });

  it('preserves immutable publish identity: exact requested Draft revision', async () => {
    const response = { code: 200, data: { id: 'revision-7', revisionNo: 7 } };
    const post = jest.spyOn(HttpUtils, 'post').mockResolvedValue(response);
    await expect(publishDevelopmentTask('node-1', 7)).resolves.toEqual(response);
    expect(post).toHaveBeenCalledWith('/api/v1/data-development/nodes/node-1/publish', {
      draftRevision: 7,
    });
  });

  it('preserves SQL preview body and raw envelope, without publishing or saving', async () => {
    const payload = {
      content: 'SELECT id FROM t', databaseName: 'analytics', schemaName: 'warehouse',
    } as never;
    const response = { code: 200, data: { status: 'PARTIAL', tables: [] } };
    const post = jest.spyOn(HttpUtils, 'post').mockResolvedValue(response);
    await expect(previewDevelopmentSqlLineageRequest('node-1', payload))
      .resolves.toEqual(response);
    expect(post).toHaveBeenCalledWith(
      '/api/v1/data-development/nodes/node-1/lineage/preview',
      payload,
    );
  });

  it('does not swallow raw business errors: the page coordinator owns fallback handling', async () => {
    const post = jest.spyOn(HttpUtils, 'post')
      .mockRejectedValueOnce(new Error('preflight rejected'))
      .mockRejectedValueOnce(new Error('publish conflict'));
    await expect(runDevelopmentTask('node-1', {} as never))
      .rejects.toThrow('preflight rejected');
    await expect(publishDevelopmentTask('node-1', 1))
      .rejects.toThrow('publish conflict');
    expect(post).toHaveBeenCalledTimes(2);
  });
});
