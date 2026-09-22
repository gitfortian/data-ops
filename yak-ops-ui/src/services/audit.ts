import type { ApiResponse } from '@/services/http/response';
import request from '@/utils/request';

const AUDIT_API = '/api/v1/audit';

export interface AuditFilterOption {
  value: string;
  label: string;
}

export interface AuditFilterOptions {
  actors: AuditFilterOption[];
  projects: AuditFilterOption[];
  operationTypes: AuditFilterOption[];
  resourceTypes: AuditFilterOption[];
  statuses: AuditFilterOption[];
  sources: AuditFilterOption[];
}

export interface AuditOperationQuery {
  page?: number;
  size?: number;
  keyword?: string;
  actor?: string;
  projectId?: number;
  operationType?: string;
  resourceType?: string;
  status?: string;
  source?: string;
  startTime?: string;
  endTime?: string;
}

export interface AuditOperationSummary {
  operationId: string;
  operationType: string;
  operationName: string;
  actorId?: string;
  actorName?: string;
  projectId?: number;
  projectName?: string;
  resourceType?: string;
  resourceId?: string;
  resourceName?: string;
  status: string;
  source: string;
  startedAt: string;
  finishedAt?: string;
  durationMillis?: number;
  rootTraceId?: string;
  errorCode?: string;
  summary?: string;
}

export interface AuditTimelineEvent {
  id: number;
  eventType: string;
  eventCategory: string;
  eventStatus: string;
  occurredAt: string;
  actorId?: string;
  resourceType?: string;
  resourceId?: string;
  traceId?: string;
  spanId?: string;
  parentEventId?: number;
  reasonCode?: string;
  message?: string;
  title: string;
  description?: string;
  payload: Record<string, unknown>;
}

export interface AuditOperationDetail {
  operation: AuditOperationSummary;
  metadata: Record<string, unknown>;
  events: AuditTimelineEvent[];
}

export interface AuditPagingData<T> {
  bizData: T[];
  pagination: {
    total: number;
    pages: number;
    pageNo: number;
    pageSize: number;
  };
}

const getData = async <T>(path: string): Promise<T> =>
  (await request<ApiResponse<T>>(path, { method: 'GET', protocol: 'yak-ops' })).data;

const postData = async <T>(path: string, data: unknown): Promise<T> =>
  (
    await request<ApiResponse<T>>(path, {
      method: 'POST',
      data,
      protocol: 'yak-ops',
    })
  ).data;

export const queryAuditOperations = (query: AuditOperationQuery) =>
  postData<AuditPagingData<AuditOperationSummary>>(`${AUDIT_API}/operations/page`, query);

export const getAuditOperation = (operationId: string) =>
  getData<AuditOperationDetail>(
    `${AUDIT_API}/operations/${encodeURIComponent(operationId)}`,
  );

export const getAuditFilterOptions = () =>
  getData<AuditFilterOptions>(`${AUDIT_API}/options`);
