import {
  buildSavePayload,
  normalizeEditDetail,
} from './model';

describe('offline sync notification policy', () => {
  it('maps legacy tasks without notification to the compatibility default', () => {
    const editor = normalizeEditDetail(
      {
        id: 10,
        basic: {
          jobName: '订单同步',
          jobDesc: '',
          mode: 'GUIDE_SINGLE',
        },
        source: {},
        sink: {},
        channel: {},
        schedule: {},
      },
      '10',
    );

    expect(editor.notification).toEqual({
      enabled: true,
      triggers: ['FINAL_FAILURE'],
      recipientType: 'PROJECT_OWNER',
      recipientUserIds: [],
      inAppEnabled: true,
      alertEnabled: false,
      alertChannelIds: [],
    });
  });

  it('serializes explicit recipients as stable unique numeric user ids', () => {
    const editor = normalizeEditDetail(
      {
        id: 10,
        basic: {
          jobName: '订单同步',
          jobDesc: '',
          mode: 'GUIDE_SINGLE',
        },
        source: {},
        sink: {},
        channel: {},
        schedule: {},
        notification: {
          enabled: true,
          recipientType: 'EXPLICIT_USERS',
          recipientUserIds: [11, '12', 11, -1, null],
          inAppEnabled: true,
        },
      },
      '10',
    );

    const payload = buildSavePayload(editor);

    expect(payload.notification).toEqual({
      enabled: true,
      triggers: ['FINAL_FAILURE'],
      recipientType: 'EXPLICIT_USERS',
      recipientUserIds: [11, 12],
      inAppEnabled: true,
      alertEnabled: false,
      alertChannelIds: [],
    });
  });

  it('normalizes and serializes stable alert channel ids', () => {
    const editor = normalizeEditDetail(
      {
        id: 10,
        basic: {
          jobName: '订单同步',
          jobDesc: '',
          mode: 'GUIDE_SINGLE',
        },
        source: {},
        sink: {},
        channel: {},
        schedule: {},
        notification: {
          alertEnabled: true,
          alertChannelIds: [7, '8', 7, -1, null],
        },
      },
      '10',
    );

    const payload = buildSavePayload(editor);

    expect(payload.notification.alertEnabled).toBe(true);
    expect(payload.notification.alertChannelIds).toEqual([7, 8]);
  });
});
