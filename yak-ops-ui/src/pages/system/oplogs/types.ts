export interface OperationLogFilterValues {
  operateType?: string;
  operatePage?: string;
  operationMethods?: string;
  operator?: string;
  operatorIp?: string;
  target?: string;
  targetType?: string;
  detail?: string;
  startTime?: number;
  endTime?: number;
}

export type OperationLogSearchField =
  | 'operator'
  | 'target'
  | 'detail'
  | 'operatorIp';

export interface OperationLogPaginationState {
  current: number;
  pageSize: number;
  total: number;
}
