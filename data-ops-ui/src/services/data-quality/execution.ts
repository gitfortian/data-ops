import HttpUtils from '@/utils/HttpUtils';

import {
  DATA_QUALITY_EXECUTION_API,
  DATA_QUALITY_EXECUTION_WORKSPACE_API,
} from './constants';
import type {
  CheckResult,
  ExecutionLogView,
  ExecutionStatus,
  ExecutionWorkspacePageView,
  ExecutionWorkspaceQuery,
  ExecutionWorkspaceView,
  RuleExecutionWorkspacePageView,
} from './types';

export interface QualityExecutionStatusView {
  executionNo: string;
  executionStatus: ExecutionStatus;
  checkResult: CheckResult;
  startedAt?: string;
  finishedAt?: string;
  durationMs?: number;
  errorMessage?: string;
}

const executionPath = (executionNo: string) =>
  `${DATA_QUALITY_EXECUTION_WORKSPACE_API}/${encodeURIComponent(executionNo)}`;

const executionStatusPath = (executionNo: string) =>
  `${DATA_QUALITY_EXECUTION_API}/${encodeURIComponent(executionNo)}/status`;

export const listQualityExecutionWorkspace = (
  query: ExecutionWorkspaceQuery,
): Promise<ExecutionWorkspacePageView> =>
  HttpUtils.postData<ExecutionWorkspacePageView>(
    `${DATA_QUALITY_EXECUTION_WORKSPACE_API}/page`,
    query,
  );

export const listQualityRuleExecutionWorkspace = (
  query: ExecutionWorkspaceQuery,
): Promise<RuleExecutionWorkspacePageView> =>
  HttpUtils.postData<RuleExecutionWorkspacePageView>(
    `${DATA_QUALITY_EXECUTION_WORKSPACE_API}/rule/page`,
    query,
  );

export const getQualityExecutionStatus = (
  executionNo: string,
): Promise<QualityExecutionStatusView> =>
  HttpUtils.getData<QualityExecutionStatusView>(executionStatusPath(executionNo));

export const getQualityExecutionWorkspace = (
  executionNo: string,
): Promise<ExecutionWorkspaceView> =>
  HttpUtils.getData<ExecutionWorkspaceView>(executionPath(executionNo));

export const getQualityExecutionLogs = (
  executionNo: string,
): Promise<ExecutionLogView> =>
  HttpUtils.getData<ExecutionLogView>(
    `${executionPath(executionNo)}/logs`,
  );
