import HttpUtils from '@/utils/HttpUtils';

export interface NotificationAlertChannel {
  id: number;
  type: string;
  name: string;
  enabled: boolean;
  connStatus: string;
}

interface AlertChannelResponse {
  id?: number | string | null;
  type?: string | null;
  name?: string | null;
  enabled?: boolean | null;
  connStatus?: string | null;
}

/**
 * 读取全局 Alert 渠道配置。
 *
 * 只有已经持久化的渠道才具有稳定 id，才能被任务通知策略引用。
 * 插件已注册但尚未保存配置的渠道不会作为任务通知选项返回。
 */
export async function fetchNotificationAlertChannels(): Promise<
  NotificationAlertChannel[]
> {
  const response = await HttpUtils.get<AlertChannelResponse[]>(
    '/api/v1/alert/channels',
  );
  const list = Array.isArray(response?.data) ? response.data : [];

  return list
    .map((item) => {
      const id = Number(item?.id);
      const type = String(item?.type || '').trim();
      const name = String(item?.name || type).trim();

      if (!Number.isSafeInteger(id) || id <= 0 || !type) {
        return null;
      }

      return {
        id,
        type,
        name: name || type,
        enabled: item?.enabled === true,
        connStatus: String(item?.connStatus || 'UNKNOWN'),
      } satisfies NotificationAlertChannel;
    })
    .filter(
      (
        item,
      ): item is NotificationAlertChannel => item !== null,
    );
}
