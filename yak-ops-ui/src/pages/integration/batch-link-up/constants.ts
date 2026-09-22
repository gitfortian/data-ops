import type { ThemeConfig } from 'antd';
import moment from 'moment';

import type {
  OfflineSyncPaginationState,
  OfflineSyncTimeRange,
} from './types';

export const OFFLINE_SYNC_DEFAULT_PAGINATION: OfflineSyncPaginationState = {
  current: 1,
  pageSize: 10,
  total: 0,
};

export const OFFLINE_SYNC_PAGE_SIZE_OPTIONS = [10, 20, 50];

export const OFFLINE_SYNC_STATUS_TABS = [
  { label: '全部任务', value: 'ALL' },
  { label: '运行中', value: 'RUNNING' },
  { label: '已完成', value: 'COMPLETED' },
  { label: '失败', value: 'FAILED' },
] as const;

export const createDefaultOfflineSyncTimeRange = (): OfflineSyncTimeRange => [
  moment().subtract(4, 'days'),
  moment().add(1, 'days'),
];

export const OFFLINE_SYNC_PAGE_THEME: ThemeConfig = {
  token: {
    borderRadius: 10,
    colorBorder: '#f0f0f0',
    colorBgContainer: '#ffffff',
  },
  components: {
    Button: { borderRadius: 8 },
    Input: { activeShadow: 'none' },
    Select: { activeOutlineColor: 'transparent' },
  },
};

export const OFFLINE_SYNC_COMPACT_CONTENT_CLASS = [
  'text-[12px]',
  'leading-5',
  'text-[#667085]',
  '[&_ul]:!my-0',
  '[&_ol]:!my-0',
  '[&_li]:!my-0',
  '[&_li]:!leading-5',
  '[&_p]:!my-0',
].join(' ');
