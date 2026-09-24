import type { DevelopmentTaskDefinition } from '../types';

export type DevelopmentRunPreflightFailureCode =
  | 'CONTENT_REQUIRED'
  | 'CONFIG_INVALID'
  | 'SQL_DATASOURCE_REQUIRED';

export interface DevelopmentRunPreflightFailure {
  code: DevelopmentRunPreflightFailureCode;
  messageId: string;
}

const failure = (
  code: DevelopmentRunPreflightFailureCode,
  messageId: string,
): DevelopmentRunPreflightFailure => ({ code, messageId });

/**
 * Cheap editor-side checks that can be evaluated before a durable execution is submitted.
 * Runtime/plugin validation remains authoritative; this only prevents predictable UX failures.
 */
export const validateDevelopmentRunDefinition = (
  definition: DevelopmentTaskDefinition,
): DevelopmentRunPreflightFailure | undefined => {
  if (definition.taskType === 'SQL' && !definition.content?.trim()) {
    return failure(
      'CONTENT_REQUIRED',
      'pages.dataDevelopment.execution.preflight.sqlContentRequired',
    );
  }

  let config: Record<string, unknown>;
  try {
    const parsed = JSON.parse(definition.configJson || '{}');
    if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) {
      throw new Error('config must be an object');
    }
    config = parsed as Record<string, unknown>;
  } catch {
    return failure(
      'CONFIG_INVALID',
      'pages.dataDevelopment.execution.preflight.configInvalid',
    );
  }

  if (definition.taskType === 'SQL') {
    const dataSourceId = config.dataSourceId;
    if (dataSourceId === undefined || dataSourceId === null || !String(dataSourceId).trim()) {
      return failure(
        'SQL_DATASOURCE_REQUIRED',
        'pages.dataDevelopment.execution.preflight.sqlDataSourceRequired',
      );
    }
  }

  return undefined;
};
