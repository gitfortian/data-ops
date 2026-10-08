import HttpUtils from '@/utils/HttpUtils';
import { resumeSubscription, suspendSubscription } from './api';

describe('Consumption subscription lifecycle endpoints', () => {
  afterEach(() => { jest.restoreAllMocks(); });

  it('suspends the exact existing subscription without creating another relationship', async () => {
    const post = jest.spyOn(HttpUtils, 'post').mockResolvedValue({
      code: 200, data: { id: 17, status: 'SUSPENDED' },
    } as any);

    const suspended = await suspendSubscription(17);

    expect(post).toHaveBeenCalledTimes(1);
    expect(post.mock.calls[0][0]).toBe('/api/v1/consumption/subscriptions/17/suspend');
    expect(suspended).toMatchObject({ id: 17, status: 'SUSPENDED' });
  });

  it('resumes a suspended relationship through its original stable ID', async () => {
    const post = jest.spyOn(HttpUtils, 'post').mockResolvedValue({
      code: 200, data: { id: 17, status: 'ACTIVE' },
    } as any);

    const resumed = await resumeSubscription(17);

    expect(post).toHaveBeenCalledTimes(1);
    expect(post.mock.calls[0][0]).toBe('/api/v1/consumption/subscriptions/17/resume');
    expect(resumed).toMatchObject({ id: 17, status: 'ACTIVE' });
  });

  it('does not report a successful resume if owning backend rejects the transition', async () => {
    const post = jest.spyOn(HttpUtils, 'post').mockResolvedValue({
      code: 403, message: 'No permission for consumer', data: null,
    } as any);

    await expect(resumeSubscription(17)).rejects.toThrow('No permission for consumer');
    expect(post).toHaveBeenCalledTimes(1);
  });
});
