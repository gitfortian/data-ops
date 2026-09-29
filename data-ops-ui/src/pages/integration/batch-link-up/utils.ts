import type {
  OfflineJobDefinitionVO,
  OfflineSyncTaskPageQuery,
} from '@/services/batch-link-up';
import moment from 'moment';

import {
  createDefaultOfflineSyncTimeRange,
  OFFLINE_SYNC_DEFAULT_PAGINATION,
} from './constants';
import type {
  OfflineSyncPaginationState,
  OfflineSyncSearchState,
  OfflineSyncTimeRange,
} from './types';

const DATE_TIME_FORMAT = 'YYYY-MM-DD HH:mm:ss';

const positiveInteger = (value: string | null, fallback: number) => {
  const parsed = Number(value);
  return Number.isSafeInteger(parsed) && parsed > 0 ? parsed : fallback;
};

const validTimeRange = (
  search: OfflineSyncSearchState,
): OfflineSyncTimeRange | undefined => {
  const start = search.createTime?.[0];
  const end = search.createTime?.[1];
  if (!start || !end || !start.isValid() || !end.isValid()) return undefined;
  return [start, end];
};

export const parseOfflineSyncSearchFromUrl = (
  search: string,
): OfflineSyncSearchState => {
  const params = new URLSearchParams(search);
  const createTimeStart = params.get('createTimeStart');
  const createTimeEnd = params.get('createTimeEnd');
  const parsedStart = createTimeStart
    ? moment(createTimeStart, DATE_TIME_FORMAT)
    : undefined;
  const parsedEnd = createTimeEnd
    ? moment(createTimeEnd, DATE_TIME_FORMAT)
    : undefined;

  return {
    jobName: params.get('jobName') || undefined,
    id: params.get('id') || undefined,
    status: params.get('status') || undefined,
    sourceType: params.get('sourceType') || undefined,
    sinkType: params.get('sinkType') || undefined,
    sourceTable: params.get('sourceTable') || undefined,
    sinkTable: params.get('sinkTable') || undefined,
    createTime:
      parsedStart?.isValid() && parsedEnd?.isValid()
        ? [parsedStart, parsedEnd]
        : createDefaultOfflineSyncTimeRange(),
  };
};

export const parseOfflineSyncPaginationFromUrl = (
  search: string,
): OfflineSyncPaginationState => {
  const params = new URLSearchParams(search);
  return {
    current: positiveInteger(
      params.get('current'),
      OFFLINE_SYNC_DEFAULT_PAGINATION.current,
    ),
    pageSize: positiveInteger(
      params.get('pageSize'),
      OFFLINE_SYNC_DEFAULT_PAGINATION.pageSize,
    ),
    total: 0,
  };
};

export const buildOfflineSyncQueryString = (
  search: OfflineSyncSearchState,
  pagination: Pick<OfflineSyncPaginationState, 'current' | 'pageSize'>,
) => {
  const query = new URLSearchParams();
  const stringFields: Array<keyof Omit<OfflineSyncSearchState, 'createTime'>> = [
    'jobName',
    'id',
    'status',
    'sourceType',
    'sinkType',
    'sourceTable',
    'sinkTable',
  ];

  stringFields.forEach((field) => {
    const value = search[field]?.trim();
    if (value) query.set(field, value);
  });

  const timeRange = validTimeRange(search);
  if (timeRange) {
    query.set('createTimeStart', timeRange[0].format(DATE_TIME_FORMAT));
    query.set('createTimeEnd', timeRange[1].format(DATE_TIME_FORMAT));
  }

  query.set('current', String(pagination.current));
  query.set('pageSize', String(pagination.pageSize));
  return query.toString();
};

export const buildOfflineSyncPageQuery = (
  search: OfflineSyncSearchState,
  pagination: Pick<OfflineSyncPaginationState, 'current' | 'pageSize'>,
): OfflineSyncTaskPageQuery => {
  const timeRange = validTimeRange(search);
  return {
    current: pagination.current,
    pageSize: pagination.pageSize,
    jobName: search.jobName?.trim() || undefined,
    id: search.id?.trim() || undefined,
    status: search.status?.trim() || undefined,
    sourceType: search.sourceType?.trim() || undefined,
    sinkType: search.sinkType?.trim() || undefined,
    sourceTable: search.sourceTable?.trim() || undefined,
    sinkTable: search.sinkTable?.trim() || undefined,
    createTimeStart: timeRange?.[0].format(DATE_TIME_FORMAT),
    createTimeEnd: timeRange?.[1].format(DATE_TIME_FORMAT),
  };
};

export const getOfflineSyncEditPath = (
  record: OfflineJobDefinitionVO,
): string | undefined => {
  if (record.id === undefined || record.id === null || record.id === '') {
    return undefined;
  }

  const id = encodeURIComponent(String(record.id));
  if (record.mode === 'GUIDE_SINGLE') {
    return `/sync/batch-link-up/${id}/config/single?scene=edit`;
  }
  if (record.mode === 'GUIDE_MULTI') {
    return `/sync/batch-link-up/${id}/config/multi?scene=edit`;
  }
  if (record.mode === 'SCRIPT') {
    return `/sync/batch-link-up/${id}/config/script?scene=edit`;
  }
  return undefined;
};

export const copyTextToClipboard = async (value: string | number) => {
  const text = String(value);
  if (navigator.clipboard && window.isSecureContext) {
    await navigator.clipboard.writeText(text);
    return;
  }

  const textarea = document.createElement('textarea');
  textarea.value = text;
  textarea.style.position = 'fixed';
  textarea.style.opacity = '0';
  document.body.appendChild(textarea);
  textarea.focus();
  textarea.select();
  document.execCommand('copy');
  document.body.removeChild(textarea);
};
