import { securityGetData, securityPostData } from './client';

const MESSAGE_API = '/api/v1/message';

export const MESSAGE_COUNT_CHANGED_EVENT = 'yak-message-count-changed';

export type MessageStatus = 'UNREAD' | 'READ';
export type MessageLevel = 'INFO' | 'SUCCESS' | 'WARNING' | 'ERROR';
export type MessageScope = 'SYSTEM' | 'PROJECT';
export type MessageTimestamp = string | number;

export interface SecurityMessage {
  id: number | string;
  title: string;
  summary?: string;
  type?: string;
  level?: MessageLevel;
  scope?: MessageScope;
  projectId?: number | string | null;
  sourceType?: string;
  sourceId?: string;
  actionPath?: string;
  status: MessageStatus;
  readTag?: boolean;
  readTime?: number | null;
  createTime?: MessageTimestamp;
  oplogId?: number | string | null;
  operationLogId?: number | string | null;
}

export interface MessageDetail extends SecurityMessage {
  content?: string;
}

export interface MessagePage {
  records: SecurityMessage[];
  total: number;
}

export interface MessageQuery {
  pageNum: number;
  pageSize: number;
  status?: MessageStatus;
  type?: string;
  projectId?: number | string;
  /** Unix epoch milliseconds; aligned with yak-framework message-center contract. */
  startTime?: number;
  /** Unix epoch milliseconds; aligned with yak-framework message-center contract. */
  endTime?: number;
}

export const buildMessageQuery = (values: MessageQuery) => {
  const params = new URLSearchParams();
  Object.entries(values as Record<string, unknown>).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') {
      params.set(key, String(value));
    }
  });
  return params.toString();
};

/** Message actions are limited to internal application routes. */
export const safeMessageActionPath = (value?: string) => {
  if (!value || !value.startsWith('/') || value.startsWith('//')) {
    return undefined;
  }
  return value;
};

/** Notify mounted message counters that read state has changed. */
export const notifyMessageCountChanged = () => {
  if (typeof window === 'undefined') return;
  window.dispatchEvent(new Event(MESSAGE_COUNT_CHANGED_EVENT));
};

export const pageMessages = (params: MessageQuery) =>
  securityGetData<MessagePage>(`${MESSAGE_API}/page?${buildMessageQuery(params)}`);

export const getUnreadMessageCount = () =>
  securityGetData<number>(`${MESSAGE_API}/unread-count`);

export const markMessageRead = (id: number | string) =>
  securityPostData<void>(`${MESSAGE_API}/mark-read`, { id });

export const batchReadMessages = (ids: Array<number | string>) =>
  securityPostData<void>(`${MESSAGE_API}/batch-read`, { ids });

export const getMessageDetail = (id: number | string) =>
  securityGetData<MessageDetail>(
    `${MESSAGE_API}/detail?id=${encodeURIComponent(id)}`,
  );
