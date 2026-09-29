jest.mock('@/utils/HttpUtils', () => ({
  __esModule: true,
  default: {
    get: jest.fn(),
  },
}));

import HttpUtils from '@/utils/HttpUtils';

import { fetchNotificationAlertChannels } from './alertChannels';

const mockedGet = HttpUtils.get as jest.MockedFunction<
  typeof HttpUtils.get
>;

describe('notification alert channels', () => {
  beforeEach(() => {
    mockedGet.mockReset();
  });

  it('keeps only persisted channels with stable positive ids', async () => {
    mockedGet.mockResolvedValue({
      code: 0,
      data: [
        {
          id: 7,
          type: 'DINGTALK',
          name: '钉钉告警',
          enabled: true,
          connStatus: 'CONNECTED',
        },
        {
          id: null,
          type: 'EMAIL',
          name: '邮件',
          enabled: false,
        },
        {
          id: -1,
          type: 'INVALID',
          name: 'Invalid',
          enabled: true,
        },
      ],
    } as any);

    await expect(fetchNotificationAlertChannels()).resolves.toEqual([
      {
        id: 7,
        type: 'DINGTALK',
        name: '钉钉告警',
        enabled: true,
        connStatus: 'CONNECTED',
      },
    ]);

    expect(mockedGet).toHaveBeenCalledWith('/api/v1/alert/channels');
  });
});
