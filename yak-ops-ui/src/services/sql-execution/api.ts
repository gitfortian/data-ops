import HttpUtils from '@/utils/HttpUtils';

import type {
  SqlExecutionAuditDetail,
  SqlExecutionAuditQuery,
  SqlExecutionAuditSummary,
  SqlExecutionPageResult,
} from './types';

const SQL_EXECUTION_API_PREFIX = '/api/v1/sql-executions';

export const pageSqlExecutions = (
  query: SqlExecutionAuditQuery,
): Promise<SqlExecutionPageResult> =>
  HttpUtils.postData<SqlExecutionPageResult>(
    `${SQL_EXECUTION_API_PREFIX}/page`,
    query,
  );

export const getSqlExecutionDetail = (
  executionId: string,
): Promise<SqlExecutionAuditDetail> =>
  HttpUtils.getData<SqlExecutionAuditDetail>(
    `${SQL_EXECUTION_API_PREFIX}/${encodeURIComponent(executionId)}`,
  );

export const getSqlExecutionSummary = (
  query: SqlExecutionAuditQuery,
): Promise<SqlExecutionAuditSummary> =>
  HttpUtils.postData<SqlExecutionAuditSummary>(
    `${SQL_EXECUTION_API_PREFIX}/summary`,
    query,
  );
