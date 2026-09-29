import type { PaginationInfo } from '@/services/data-source/types';

/** 一次 SQL 执行(聚合)的观测视图,字段对齐 SqlExecutionAuditVO。 */
export interface SqlExecutionAuditRecord {
  executionId: string;
  dataSourceId?: string;
  caller?: string;
  callerReference?: string;
  operatorName?: string;
  transactionMode?: string;
  status?: string;
  statementCount: number;
  succeededStatementCount: number;
  returnedRows: number;
  affectedRows: number;
  startedAt?: string;
  finishedAt?: string;
  durationMs: number;
  errorMessage?: string;
}

/** 语句级明细,字段对齐 SqlStatementExecutionAuditVO。 */
export interface SqlStatementAuditRecord {
  statementId: string;
  statementIndex: number;
  statementType?: string;
  sqlFingerprint?: string;
  sqlPreview?: string;
  status?: string;
  resultType?: string;
  returnedRows: number;
  affectedRows: number;
  truncated: boolean;
  startedAt?: string;
  finishedAt?: string;
  durationMs: number;
  errorMessage?: string;
}

export interface SqlExecutionAuditDetail {
  execution: SqlExecutionAuditRecord;
  statements: SqlStatementAuditRecord[];
}

export interface SqlExecutionAuditSummary {
  total: number;
  succeeded: number;
  failed: number;
  cancelled: number;
  timedOut: number;
  successRate: number;
  avgDurationMs: number;
  maxDurationMs: number;
  p95DurationMs: number;
  returnedRows: number;
  affectedRows: number;
  statementTypes: { statementType: string; count: number }[];
}

/** 过滤条件对齐 SqlExecutionAuditQueryDTO(时间用 ISO-LOCALDATE-TIME 字符串)。 */
export interface SqlExecutionAuditQuery {
  pageNo?: number;
  pageSize?: number;
  executionId?: string;
  dataSourceId?: string;
  caller?: string;
  operatorName?: string;
  status?: string;
  transactionMode?: string;
  statementType?: string;
  sqlFingerprint?: string;
  minDurationMs?: number;
  startedFrom?: string;
  startedTo?: string;
}

export interface SqlExecutionPageResult {
  bizData: SqlExecutionAuditRecord[];
  pagination?: PaginationInfo;
}
