import { request } from '@umijs/max';

const PREFIX = '/api/v1/compute-environments';

export interface ComputeEnvironmentSshConfig {
  executable?: string;
  host?: string;
  port?: number;
  user?: string;
  identityFile?: string;
  knownHostsFile?: string;
  strictHostKeyChecking?: boolean;
  connectTimeoutSeconds?: number;
  remoteRestAddress?: string;
  remoteRestPort?: number;
}

export interface ComputeEnvironmentRuntimeConfig {
  restUrl: string;
  flinkHome: string;
  flinkCdcHome: string;
  javaHome?: string;
  flinkVersion: string;
  flinkCdcVersion: string;
  ssh?: ComputeEnvironmentSshConfig;
}

export type ComputeEnvironmentCheckStatus = 'PASS' | 'WARN' | 'FAIL';
export type ComputeEnvironmentHealthStatus = 'HEALTHY' | 'WARNING' | 'FAILED';

export interface ComputeEnvironmentDiagnosisCheck {
  key: string;
  label: string;
  status: ComputeEnvironmentCheckStatus;
  message: string;
}

export interface ComputeEnvironmentDiagnosis {
  environmentId?: number;
  environmentName: string;
  status: ComputeEnvironmentHealthStatus;
  ready: boolean;
  summary: string;
  detectedFlinkVersion?: string;
  detectedFlinkCdcVersion?: string;
  detectedJavaVersion?: string;
  checkedAt: string;
  checks: ComputeEnvironmentDiagnosisCheck[];
}

export interface ComputeEnvironment {
  id: number;
  name: string;
  engineType: 'FLINK_CDC';
  deploymentMode: 'REMOTE';
  submitterType: 'LOCAL' | 'SSH';
  config: ComputeEnvironmentRuntimeConfig;
  enabled: boolean;
  defaultEnvironment: boolean;
  version: number;
  createTime?: string;
  updateTime?: string;
  lastCheckStatus?: ComputeEnvironmentHealthStatus;
  lastCheckMessage?: string;
  lastCheckTime?: string;
}

export interface ComputeEnvironmentPayload {
  name: string;
  submitterType: 'LOCAL' | 'SSH';
  config: ComputeEnvironmentRuntimeConfig;
  enabled: boolean;
  makeDefault: boolean;
}

interface ApiResponse<T> {
  code: number;
  data: T;
  msg?: string;
  message?: string;
}

export const computeEnvironmentApi = {
  list: () => request<ApiResponse<ComputeEnvironment[]>>(PREFIX),
  detail: (id: number) => request<ApiResponse<ComputeEnvironment>>(`${PREFIX}/${id}`),
  create: (payload: ComputeEnvironmentPayload) =>
    request<ApiResponse<number>>(PREFIX, { method: 'POST', data: payload }),
  update: (id: number, payload: ComputeEnvironmentPayload) =>
    request<ApiResponse<boolean>>(`${PREFIX}/${id}`, { method: 'PUT', data: payload }),
  diagnosePreview: (payload: ComputeEnvironmentPayload) =>
    request<ApiResponse<ComputeEnvironmentDiagnosis>>(`${PREFIX}/diagnose`, {
      method: 'POST',
      data: payload,
    }),
  diagnose: (id: number) =>
    request<ApiResponse<ComputeEnvironmentDiagnosis>>(`${PREFIX}/${id}/diagnose`, {
      method: 'POST',
    }),
  setEnabled: (id: number, enabled: boolean) =>
    request<ApiResponse<boolean>>(`${PREFIX}/${id}/enabled`, {
      method: 'PUT',
      data: { enabled },
    }),
  setDefault: (id: number) =>
    request<ApiResponse<boolean>>(`${PREFIX}/${id}/default`, { method: 'POST' }),
  remove: (id: number) =>
    request<ApiResponse<boolean>>(`${PREFIX}/${id}`, { method: 'DELETE' }),
};
