import type { ApiResponse } from '@/services/http/response';
import HttpUtils from '@/utils/HttpUtils';

export interface EnvVarEntry {
  key: string;
  value: string;
  /** 'app' = managed via settings page; 'system' = OS-level read-only */
  source: 'app' | 'system';
}

const ENV_VARS_API = '/api/v1/system/env-vars';

/** List all environment variables (app + system). */
export const listSystemEnvVars = (): Promise<ApiResponse<EnvVarEntry[]>> =>
  HttpUtils.get<EnvVarEntry[]>(ENV_VARS_API);

/** Batch-save application environment variables. */
export const saveSystemEnvVars = (
  vars: Record<string, string>,
): Promise<ApiResponse<void>> =>
  HttpUtils.put<void>(ENV_VARS_API, vars);

/** Delete a single application environment variable by key. */
export const deleteSystemEnvVar = (
  key: string,
): Promise<ApiResponse<void>> =>
  HttpUtils.delete<void>(`${ENV_VARS_API}/${encodeURIComponent(key)}`);
