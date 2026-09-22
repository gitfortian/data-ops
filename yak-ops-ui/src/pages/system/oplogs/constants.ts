import type { OperationLogOptions } from '@/services/security/operationLogs';

import type {
  OperationLogPaginationState,
  OperationLogSearchField,
} from './types';

export const DEFAULT_OPERATION_LOG_PAGINATION: OperationLogPaginationState = {
  current: 1,
  pageSize: 10,
  total: 0,
};

export const EMPTY_OPERATION_LOG_OPTIONS: OperationLogOptions = {
  operateTypes: [],
  operatePages: [],
  operationMethods: [],
  targetTypes: [],
};

export const ALL_OPERATION_METHOD_FILTER = '__all__';

export const OPERATION_LOG_SEARCH_FIELDS: Array<{
  label: string;
  value: OperationLogSearchField;
}> = [
  { label: '操作人', value: 'operator' },
  { label: '操作目标', value: 'target' },
  { label: '操作详情', value: 'detail' },
  { label: 'IP 地址', value: 'operatorIp' },
];

export const OPERATION_LOG_SEARCH_PLACEHOLDERS: Record<
  OperationLogSearchField,
  string
> = {
  operator: '请输入操作人',
  target: '请输入操作目标',
  detail: '请输入详情关键字',
  operatorIp: '请输入 IP 地址',
};
