import HttpUtils from '@/utils/HttpUtils';
import {
  activateDevelopmentReleaseRevision,
  cancelDevelopmentTaskExecution,
  getDevelopmentEditorSettings,
  getDevelopmentRelease,
  listDevelopmentReleases,
  offlineDevelopmentRelease,
  onlineDevelopmentRelease,
  retryDevelopmentTaskExecution,
  saveDevelopmentEditorSettings,
  type YakEditorSettings,
} from './api';

describe('Data Development modern execution commands, release lifecycle and editor settings', () => {
  afterEach(() => jest.restoreAllMocks());

  it('POSTs cancel to the existing endpoint and returns the exact durable execution detail', async () => {
    const result = { id: 'run-1', status: 'CANCELLED', runtimeExecutionId: 'task-runtime-7' };
    const postData = jest.spyOn(HttpUtils, 'postData').mockResolvedValue(result);
    await expect(cancelDevelopmentTaskExecution('run-1')).resolves.toEqual(result);
    expect(postData).toHaveBeenCalledWith(
      '/api/v1/data-development/executions/run-1/cancel', {},
    );
  });

  it('POSTs retry and preserves only the durable new execution ID returned by the server', async () => {
    const submission = { id: 'run-2', nodeId: 'node-1', taskType: 'SQL', status: 'PENDING' };
    const postData = jest.spyOn(HttpUtils, 'postData').mockResolvedValue(submission);
    await expect(retryDevelopmentTaskExecution('run-1')).resolves.toEqual(submission);
    expect(postData).toHaveBeenCalledWith(
      '/api/v1/data-development/executions/run-1/retry', {},
    );
  });

  it('encodes execution identities for both state commands and propagates rejected writes', async () => {
    const postData = jest.spyOn(HttpUtils, 'postData')
      .mockRejectedValueOnce(new Error('403 execute denied'))
      .mockRejectedValueOnce(new Error('retry rejected by runtime'));
    await expect(cancelDevelopmentTaskExecution('run/1')).rejects.toThrow('403 execute denied');
    await expect(retryDevelopmentTaskExecution('run/1')).rejects.toThrow('retry rejected by runtime');
    expect(postData).toHaveBeenNthCalledWith(
      1, '/api/v1/data-development/executions/run%2F1/cancel', {},
    );
    expect(postData).toHaveBeenNthCalledWith(
      2, '/api/v1/data-development/executions/run%2F1/retry', {},
    );
  });

  it('GETs release list with the original query serialization and unwrapped counts', async () => {
    const page = { records: [], total: 0, onlineCount: 0, offlineCount: 0,
      disabledCount: 0, pageNo: 2, pageSize: 20 };
    const getData = jest.spyOn(HttpUtils, 'getData').mockResolvedValue(page);
    await expect(listDevelopmentReleases({
      pageNo: 2, pageSize: 20, status: 'OFFLINE', taskType: 'SQL',
      keyword: 'sql job',
    })).resolves.toEqual(page);
    expect(getData).toHaveBeenCalledWith(
      '/api/v1/data-development/releases?'
        + 'pageNo=2&pageSize=20&status=OFFLINE&taskType=SQL&keyword=sql+job',
    );
  });

  it('GETs the exact release detail by encoded asset identity', async () => {
    const detail = { release: { assetId: 'asset/a 1' }, revisions: [] };
    const getData = jest.spyOn(HttpUtils, 'getData').mockResolvedValue(detail);
    await expect(getDevelopmentRelease('asset/a 1')).resolves.toEqual(detail);
    expect(getData).toHaveBeenCalledWith(
      '/api/v1/data-development/releases/asset%2Fa%201',
    );
  });

  it.each([
    ['offline', offlineDevelopmentRelease, '/offline'],
    ['online', onlineDevelopmentRelease, '/online'],
  ] as const)('POSTs release %s without changing response status or route',
    async (_label, action, suffix) => {
      const result = { assetId: 'asset/9', currentRevisionNo: 4, status: 'ONLINE' };
      const postData = jest.spyOn(HttpUtils, 'postData').mockResolvedValue(result);
      await expect(action('asset/9')).resolves.toEqual(result);
      expect(postData).toHaveBeenCalledWith(
        '/api/v1/data-development/releases/asset%2F9' + suffix, {},
      );
      jest.restoreAllMocks();
    });

  it('POSTs explicit revision activation by exact number and returns the runtime release state', async () => {
    const result = { assetId: 'asset-2', currentRevisionNo: 3, status: 'ONLINE' };
    const postData = jest.spyOn(HttpUtils, 'postData').mockResolvedValue(result);
    await expect(activateDevelopmentReleaseRevision('asset-2', 3)).resolves.toEqual(result);
    expect(postData).toHaveBeenCalledWith(
      '/api/v1/data-development/releases/asset-2/activate/3', {},
    );
  });

  it('propagates release command failure rather than fabricating successful activation', async () => {
    const postData = jest.spyOn(HttpUtils, 'postData')
      .mockRejectedValue(new Error('draft revision not approved'));
    await expect(activateDevelopmentReleaseRevision('asset-2', 3))
      .rejects.toThrow('draft revision not approved');
  });

  it('retains editor settings GET with data-only response and stable API', async () => {
    const settings = { theme: 'Yak-Light', fontSize: 15 };
    const getData = jest.spyOn(HttpUtils, 'getData').mockResolvedValue(settings);
    await expect(getDevelopmentEditorSettings()).resolves.toEqual(settings);
    expect(getData).toHaveBeenCalledWith('/api/v1/data-development/editor-settings');
  });

  it('retains editor settings PUT and never sends a legacy response envelope', async () => {
    const settings = { theme: 'Yak-Light', fontSize: 16 } as YakEditorSettings;
    const putData = jest.spyOn(HttpUtils, 'putData').mockResolvedValue(settings);
    await expect(saveDevelopmentEditorSettings(settings)).resolves.toEqual(settings);
    expect(putData).toHaveBeenCalledWith('/api/v1/data-development/editor-settings', settings);
  });

  it('surfaces editor-setting write failures to callers unchanged', async () => {
    const putData = jest.spyOn(HttpUtils, 'putData').mockRejectedValue(new Error('403 forbidden'));
    await expect(saveDevelopmentEditorSettings({ fontSize: 14 } as YakEditorSettings))
      .rejects.toThrow('403 forbidden');
  });
});
