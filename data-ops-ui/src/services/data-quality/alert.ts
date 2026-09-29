import HttpUtils from '@/utils/HttpUtils';
import { DATA_QUALITY_MONITOR_API } from './constants';

/** 与后端 QualityMonitorVO.AlertEvent 对齐;non_null 序列化下可空字段以 undefined 呈现。 */
export interface QualityAlertEvent {
  id: number;
  monitorId?: number | null;
  monitorName?: string | null;
  executionNo?: string | null;
  checkResult?: string | null;
  alertLevel?: string | null;
  notifyChannel?: string | null;
  /** 原样透传字符串(RECORDED/PENDING/FAILED),前端不扩枚举。 */
  deliveryStatus?: string | null;
  alertMessage?: string | null;
  createdAt?: string | null;
}

export interface QualityAlertOverview {
  last24hCount: number;
  recent: QualityAlertEvent[];
}

export const getQualityAlertOverview = (
  limit = 5,
): Promise<QualityAlertOverview> =>
  HttpUtils.getData<QualityAlertOverview>(
    `${DATA_QUALITY_MONITOR_API}/alert-overview?limit=${encodeURIComponent(String(limit))}`,
  );
