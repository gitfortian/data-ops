import HttpUtils from '@/utils/HttpUtils';
import { getApplicationFeatures } from './applicationFeatures';

jest.mock('@/utils/HttpUtils', () => ({ __esModule: true, default: { getData: jest.fn() } }));

it('reads deployment metadata through the ordinary API instead of probing a disabled Agent endpoint', async () => {
  jest.mocked(HttpUtils.getData).mockResolvedValue({ agentEnabled: false });
  await expect(getApplicationFeatures()).resolves.toEqual({ agentEnabled: false });
  expect(HttpUtils.getData).toHaveBeenCalledWith('/api/v1/system/features', { skipErrorHandler: true });
});

it.each([null, {}, { agentEnabled: 'false' }])('treats malformed discovery as unavailable, never as an enabled or disabled module: %p', async value => {
  jest.mocked(HttpUtils.getData).mockResolvedValue(value);
  await expect(getApplicationFeatures()).rejects.toThrow('部署功能状态无法确认');
});
