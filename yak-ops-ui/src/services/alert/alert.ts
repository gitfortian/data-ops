import HttpUtils from '@/utils/HttpUtils';
import type { ApiResponse } from '@/utils/request';

const ALERT_API_PREFIX = '/api/v1/alert';

/** 已注册的告警渠道类型 */
export interface AlertChannelVO {
  type: string;
  name: string;
  description: string;
  version: string;
  enabled: boolean;
  connStatus: string;
  configJson: string | null;
}

/** 发送告警请求参数 */
export interface AlertSendDTO {
  channelType: string;
  configJson: string;
  title: string;
  content: string;
  level: string;
}

/** 发送告警结果 */
export interface AlertResult {
  success: boolean;
  errorMessage: string | null;
}

/** 保存告警渠道配置请求参数 */
export interface AlertChannelSaveDTO {
  channelType: string;
  configJson: string;
  enabled?: boolean;
}

/** 列出所有已注册的告警渠道 */
export const listAlertChannels = (): Promise<ApiResponse<AlertChannelVO[]>> =>
  HttpUtils.get<AlertChannelVO[]>(`${ALERT_API_PREFIX}/channels`);

/** 获取指定渠道的详细配置 */
export const getAlertChannel = (channelType: string): Promise<ApiResponse<AlertChannelVO>> =>
  HttpUtils.get<AlertChannelVO>(`${ALERT_API_PREFIX}/channels/${channelType}`);

/** 测试告警渠道连通性 */
export const testAlertConnection = (
  channelType: string,
  configJson?: string | null,
): Promise<ApiResponse<boolean>> =>
  HttpUtils.post<boolean>(`${ALERT_API_PREFIX}/test-connection`, {
    channelType,
    configJson: configJson || undefined,
  });

/** 保存告警渠道配置 */
export const saveAlertChannel = (dto: AlertChannelSaveDTO): Promise<ApiResponse<boolean>> =>
  HttpUtils.put<boolean>(`${ALERT_API_PREFIX}/channels`, dto as any);

/** 切换告警渠道启用状态 */
export const toggleAlertChannelEnabled = (
  channelType: string,
  enabled: boolean,
): Promise<ApiResponse<boolean>> =>
  HttpUtils.put<boolean>(`${ALERT_API_PREFIX}/channels/${channelType}/enabled?enabled=${enabled}`);

/** 发送告警消息 */
export const sendAlert = (dto: AlertSendDTO): Promise<ApiResponse<AlertResult>> =>
  HttpUtils.post<AlertResult>(`${ALERT_API_PREFIX}/send`, dto as any);
