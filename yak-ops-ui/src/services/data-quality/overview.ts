import HttpUtils from '@/utils/HttpUtils';
import { DATA_QUALITY_OVERVIEW_API } from './constants';

export interface QualityOverviewQuery {
  startDate: string;
  endDate: string;
}

export interface QualityOverviewSummary {
  executionCount: number;
  activeMonitorCount: number;
  executedRuleCount: number;
  passedRuleCount: number;
  failedRuleCount: number;
  errorRuleCount: number;
  issueRuleCount: number;
  issueExecutionCount: number;
  affectedMonitorCount: number;
  affectedTableCount: number;
  affectedColumnCount: number;
  passRate?: number;
  issueRate?: number;
  averageDurationMs?: number;
  latestExecutionAt?: string;
}

export interface QualityOverviewDimension {
  dimension: string;
  total: number;
  issues: number;
  passRate?: number;
}

export interface QualityIssueContributor {
  dimension: string;
  issues: number;
  ratio?: number;
}

export interface QualityOverviewTrendPoint {
  date: string;
  executionCount: number;
  activeMonitorCount: number;
  executedRuleCount: number;
  passedRuleCount: number;
  failedRuleCount: number;
  errorRuleCount: number;
  issueExecutionCount: number;
  passRate?: number;
  issueRate?: number;
  averageDurationMs?: number;
}

export interface QualityOverviewView {
  rangeStart: string;
  rangeEnd: string;
  summary: QualityOverviewSummary;
  dimensions: QualityOverviewDimension[];
  issueContributors: QualityIssueContributor[];
  trend: QualityOverviewTrendPoint[];
}

export const getQualityOverview = (query: QualityOverviewQuery) => {
  const params = new URLSearchParams({
    startDate: query.startDate,
    endDate: query.endDate,
  });
  return HttpUtils.getData<QualityOverviewView>(
    `${DATA_QUALITY_OVERVIEW_API}?${params.toString()}`,
  );
};
