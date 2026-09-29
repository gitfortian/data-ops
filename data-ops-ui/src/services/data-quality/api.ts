import HttpUtils from '@/utils/HttpUtils';

import {
  DATA_QUALITY_EXECUTION_API,
  DATA_QUALITY_MONITOR_API,
  DATA_QUALITY_TABLE_ASSET_API,
  DATA_QUALITY_TEMPLATE_API,
} from './constants';
import type {
  CopyCustomTemplatePayload,
  ExecutionPageView,
  ExecutionView,
  MonitorPageView,
  MonitorReportView,
  MonitorSettingsView,
  MonitorView,
  MonitorWorkspaceView,
  OperationLogPageView,
  QualityExecutionPageQuery,
  QualityMonitorPageQuery,
  QualityResourceId,
  QualityTableAssetPageQuery,
  QualityTableCandidateQuery,
  QualityTableSummaryQuery,
  QualityTemplateListQuery,
  RegisterTablesPayload,
  RegisterTablesView,
  RunView,
  SaveCustomTemplatePayload,
  SaveMonitorPayload,
  SaveTemplateFolderPayload,
  TableAssetPageView,
  TableCandidatePageView,
  TableMonitorSummary,
  TemplateFolderView,
  TemplateListView,
  TemplateView,
} from './types';

const queryString = (params: object) => {
  const search = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && String(value).length > 0) {
      search.set(key, String(value));
    }
  });
  const value = search.toString();
  return value ? `?${value}` : '';
};

const pathId = (id: QualityResourceId) => encodeURIComponent(String(id));

export const listQualityTemplates = (
  query: QualityTemplateListQuery = {},
): Promise<TemplateListView> =>
  HttpUtils.getData<TemplateListView>(
    `${DATA_QUALITY_TEMPLATE_API}${queryString(query)}`,
  );

export const listCustomQualityTemplates = (
  query: QualityTemplateListQuery = {},
): Promise<TemplateListView> =>
  HttpUtils.getData<TemplateListView>(
    `${DATA_QUALITY_TEMPLATE_API}/custom${queryString(query)}`,
  );

export const getQualityTemplate = (
  id: QualityResourceId,
): Promise<TemplateView> =>
  HttpUtils.getData<TemplateView>(
    `${DATA_QUALITY_TEMPLATE_API}/${pathId(id)}`,
  );

export const listQualityTemplateFolders = (): Promise<TemplateFolderView[]> =>
  HttpUtils.getData<TemplateFolderView[]>(
    `${DATA_QUALITY_TEMPLATE_API}/folder`,
  );

export const createQualityTemplateFolder = (
  payload: SaveTemplateFolderPayload,
): Promise<TemplateFolderView> =>
  HttpUtils.postData<TemplateFolderView>(
    `${DATA_QUALITY_TEMPLATE_API}/folder`,
    payload,
  );

export const updateQualityTemplateFolder = (
  id: QualityResourceId,
  payload: SaveTemplateFolderPayload,
): Promise<TemplateFolderView> =>
  HttpUtils.putData<TemplateFolderView>(
    `${DATA_QUALITY_TEMPLATE_API}/folder/${pathId(id)}`,
    payload,
  );

export const deleteQualityTemplateFolder = async (
  id: QualityResourceId,
): Promise<void> => {
  await HttpUtils.deleteData<boolean>(
    `${DATA_QUALITY_TEMPLATE_API}/folder/${pathId(id)}`,
  );
};

export const createCustomQualityTemplate = (
  payload: SaveCustomTemplatePayload,
): Promise<TemplateView> =>
  HttpUtils.postData<TemplateView>(
    `${DATA_QUALITY_TEMPLATE_API}/custom`,
    payload,
  );

export const updateCustomQualityTemplate = (
  id: QualityResourceId,
  payload: SaveCustomTemplatePayload,
): Promise<TemplateView> =>
  HttpUtils.putData<TemplateView>(
    `${DATA_QUALITY_TEMPLATE_API}/custom/${pathId(id)}`,
    payload,
  );

export const copyCustomQualityTemplate = (
  id: QualityResourceId,
  payload: CopyCustomTemplatePayload,
): Promise<TemplateView> =>
  HttpUtils.postData<TemplateView>(
    `${DATA_QUALITY_TEMPLATE_API}/custom/${pathId(id)}/copy`,
    payload,
  );

export const deleteCustomQualityTemplate = async (
  id: QualityResourceId,
): Promise<void> => {
  await HttpUtils.deleteData<boolean>(
    `${DATA_QUALITY_TEMPLATE_API}/custom/${pathId(id)}`,
  );
};

export const listQualityTableAssets = (
  query: QualityTableAssetPageQuery,
): Promise<TableAssetPageView> =>
  HttpUtils.postData<TableAssetPageView>(
    `${DATA_QUALITY_TABLE_ASSET_API}/page`,
    query,
  );

export const listQualityTableCandidates = (
  query: QualityTableCandidateQuery,
): Promise<TableCandidatePageView> =>
  HttpUtils.getData<TableCandidatePageView>(
    `${DATA_QUALITY_TABLE_ASSET_API}/candidates${queryString(query)}`,
  );

export const registerQualityTables = (
  payload: RegisterTablesPayload,
): Promise<RegisterTablesView> =>
  HttpUtils.postData<RegisterTablesView>(
    `${DATA_QUALITY_TABLE_ASSET_API}/register`,
    payload,
  );

export const deleteQualityTableAsset = async (
  id: QualityResourceId,
): Promise<void> => {
  await HttpUtils.deleteData<boolean>(
    `${DATA_QUALITY_TABLE_ASSET_API}/${pathId(id)}`,
  );
};

export const listQualityMonitors = (
  query: QualityMonitorPageQuery,
): Promise<MonitorPageView> =>
  HttpUtils.postData<MonitorPageView>(
    `${DATA_QUALITY_MONITOR_API}/page`,
    query,
  );

export const listQualityTableMonitorSummaries = (
  query: QualityTableSummaryQuery,
): Promise<TableMonitorSummary[]> =>
  HttpUtils.getData<TableMonitorSummary[]>(
    `${DATA_QUALITY_MONITOR_API}/table-summary${queryString(query)}`,
  );

export const getQualityMonitor = (
  id: QualityResourceId,
): Promise<MonitorView> =>
  HttpUtils.getData<MonitorView>(
    `${DATA_QUALITY_MONITOR_API}/${pathId(id)}`,
  );

export const getQualityMonitorSettings = (
  id: QualityResourceId,
): Promise<MonitorSettingsView> =>
  HttpUtils.getData<MonitorSettingsView>(
    `${DATA_QUALITY_MONITOR_API}/${pathId(id)}/settings`,
  );

export const createQualityMonitor = (
  payload: SaveMonitorPayload,
): Promise<MonitorView> =>
  HttpUtils.postData<MonitorView>(DATA_QUALITY_MONITOR_API, payload);

export const updateQualityMonitor = (
  id: QualityResourceId,
  payload: SaveMonitorPayload,
): Promise<MonitorView> =>
  HttpUtils.putData<MonitorView>(
    `${DATA_QUALITY_MONITOR_API}/${pathId(id)}`,
    payload,
  );

export const deleteQualityMonitor = async (
  id: QualityResourceId,
): Promise<void> => {
  await HttpUtils.deleteData<boolean>(
    `${DATA_QUALITY_MONITOR_API}/${pathId(id)}`,
  );
};

export const runQualityMonitor = (
  id: QualityResourceId,
): Promise<RunView> =>
  HttpUtils.postData<RunView>(
    `${DATA_QUALITY_MONITOR_API}/${pathId(id)}/run`,
    {},
  );

export const getQualityMonitorWorkspace = (
  id: QualityResourceId,
): Promise<MonitorWorkspaceView> =>
  HttpUtils.getData<MonitorWorkspaceView>(
    `${DATA_QUALITY_MONITOR_API}/${pathId(id)}/workspace`,
  );

export const getQualityMonitorReport = (
  id: QualityResourceId,
  date?: string,
): Promise<MonitorReportView> =>
  HttpUtils.getData<MonitorReportView>(
    `${DATA_QUALITY_MONITOR_API}/${pathId(id)}/report${queryString({ date })}`,
  );

export const listQualityMonitorOperationLogs = (
  id: QualityResourceId,
  current = 1,
  pageSize = 20,
): Promise<OperationLogPageView> =>
  HttpUtils.getData<OperationLogPageView>(
    `${DATA_QUALITY_MONITOR_API}/${pathId(id)}/operation-log${queryString({
      current,
      pageSize,
    })}`,
  );

export const listQualityExecutions = (
  query: QualityExecutionPageQuery,
): Promise<ExecutionPageView> =>
  HttpUtils.postData<ExecutionPageView>(
    `${DATA_QUALITY_EXECUTION_API}/page`,
    query,
  );

export const getQualityExecution = (
  executionNo: string,
): Promise<ExecutionView> =>
  HttpUtils.getData<ExecutionView>(
    `${DATA_QUALITY_EXECUTION_API}/${encodeURIComponent(executionNo)}`,
  );
