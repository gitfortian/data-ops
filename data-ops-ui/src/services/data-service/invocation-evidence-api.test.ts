import HttpUtils from '@/utils/HttpUtils';
import { getDataServiceInvocationEvidence } from './api';

describe('Data Service persisted Invocation ID endpoint', () => {
  afterEach(() => { jest.restoreAllMocks(); });

  it('sends an unrounded SQL BIGINT ID to the owning service', async () => {
    const getData = jest.spyOn(HttpUtils, 'getData').mockResolvedValue({
      state: 'FOUND',
      record: {
        id: '9007199254740993', apiId: '7',
        sourceRevisionId: '9007199254740995',
        success: true, rowCount: 1, durationMs: 10,
      },
    });

    const found = await getDataServiceInvocationEvidence(7, '9007199254740993');

    expect(getData).toHaveBeenCalledWith(
      '/api/v1/data-service/7/logs/9007199254740993',
    );
    expect(found.record?.id).toBe('9007199254740993');
    expect(found.record?.sourceRevisionId).toBe('9007199254740995');
  });

  it('keeps NOT_FOUND distinct from a failed source lookup', async () => {
    const getData = jest.spyOn(HttpUtils, 'getData')
      .mockResolvedValueOnce({ state: 'NOT_FOUND', record: null })
      .mockRejectedValueOnce(new Error('forbidden'));

    await expect(getDataServiceInvocationEvidence(7, '99'))
      .resolves.toEqual({ state: 'NOT_FOUND', record: null });
    await expect(getDataServiceInvocationEvidence(7, '99'))
      .rejects.toThrow('forbidden');
  });
});
