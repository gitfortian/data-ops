import HttpUtils from '@/utils/HttpUtils';
import {
  getOfflineSyncEditDetail, saveOfflineSyncSingleGuide,
  saveOfflineSyncMultiGuide, saveOfflineSyncMultiGuideWithState,
  renderOfflineSyncMultiGuideConfig,
} from './api';

describe('Offline Sync editor definition data-only contract after legacy facade retirement', () => {
  afterEach(() => jest.restoreAllMocks());

  it('reads exact revision-aware Edit Detail with no transport envelope', async () => {
    const detail = { id: 42, mode: 'GUIDE_MULTI', state: { editorSyncState: 'DIRTY' } };
    const getData = jest.spyOn(HttpUtils, 'getData').mockResolvedValue(detail);
    await expect(getOfflineSyncEditDetail('42')).resolves.toEqual(detail);
    expect(getData).toHaveBeenCalledWith('/api/v1/job/batch-definition/42/edit-detail');
  });

  it('encodes task identity and retains the server Edit Detail shape', async () => {
    const getData = jest.spyOn(HttpUtils, 'getData').mockResolvedValue({ mode: 'GUIDE_SINGLE' });
    await expect(getOfflineSyncEditDetail('a b')).resolves.toEqual({ mode: 'GUIDE_SINGLE' });
    expect(getData).toHaveBeenCalledWith('/api/v1/job/batch-definition/a%20b/edit-detail');
  });

  it('saves single-table guide and returns only the canonical task ID', async () => {
    const postData = jest.spyOn(HttpUtils, 'postData').mockResolvedValue({ id: 17 });
    await expect(saveOfflineSyncSingleGuide({ basic: { mode: 'GUIDE_SINGLE' } }))
      .resolves.toBe(17);
    expect(postData).toHaveBeenCalledWith(
      '/api/v1/job/batch-definition/guide-single/saveOrUpdate',
      { basic: { mode: 'GUIDE_SINGLE' } },
    );
  });

  it('saves multi-table guide ID for the active editor', async () => {
    const postData = jest.spyOn(HttpUtils, 'postData').mockResolvedValue(33);
    await expect(saveOfflineSyncMultiGuide({ basic: { mode: 'GUIDE_MULTI' } }))
      .resolves.toBe(33);
    expect(postData).toHaveBeenCalledWith(
      '/api/v1/job/batch-definition/guide-multi/saveOrUpdate',
      { basic: { mode: 'GUIDE_MULTI' } },
    );
  });

  it('retains the entire server state, id and release version in Multi Guide legacy-shaped saves', async () => {
    const data = {
      id: 33, state: {
        editorSyncState: 'SYNCED', releaseState: 'OFFLINE',
        jobVersion: 4, contentVersion: 9,
      },
    };
    const postData = jest.spyOn(HttpUtils, 'postData').mockResolvedValue(data);
    const payload = { id: 33, workflow: { source: 1, target: 2 } };
    await expect(saveOfflineSyncMultiGuideWithState(payload)).resolves.toEqual(data);
    expect(postData).toHaveBeenCalledWith(
      '/api/v1/job/batch-definition/guide-multi/saveOrUpdate', payload);
  });

  it('does not invent state when the historical backend returns a scalar ID', async () => {
    const postData = jest.spyOn(HttpUtils, 'postData').mockResolvedValue(44);
    await expect(saveOfflineSyncMultiGuideWithState({ id: 44 })).resolves.toBe(44);
    expect(postData).toHaveBeenCalledWith(
      '/api/v1/job/batch-definition/guide-multi/saveOrUpdate', { id: 44 });
  });

  it('previews the multi-guide generated config as the returned string', async () => {
    const postData = jest.spyOn(HttpUtils, 'postData').mockResolvedValue('source { jdbc {} }');
    await expect(renderOfflineSyncMultiGuideConfig({ id: 5 })).resolves.toBe('source { jdbc {} }');
    expect(postData).toHaveBeenCalledWith(
      '/api/v1/job/batch-definition/guide-multi/build-config', { id: 5 });
  });

  it('propagates permission and concurrent edit failures without hiding them as successful saves', async () => {
    const getData = jest.spyOn(HttpUtils, 'getData').mockRejectedValue(
      new Error('403 project access denied'));
    await expect(getOfflineSyncEditDetail(19)).rejects.toThrow('403 project access denied');
    const postData = jest.spyOn(HttpUtils, 'postData').mockRejectedValue(
      new Error('409 stale editor state'));
    await expect(saveOfflineSyncSingleGuide({})).rejects.toThrow('409 stale editor state');
    await expect(saveOfflineSyncMultiGuideWithState({})).rejects.toThrow('409 stale editor state');
    await expect(renderOfflineSyncMultiGuideConfig({})).rejects.toThrow('409 stale editor state');
  });
});
