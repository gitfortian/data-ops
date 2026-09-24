import type { DevelopmentTaskDefinition } from '../types';
import { validateDevelopmentRunDefinition } from './runPreflight';

const sqlDefinition = (
  overrides: Partial<DevelopmentTaskDefinition> = {},
): DevelopmentTaskDefinition => ({
  taskType: 'SQL',
  schemaVersion: 1,
  content: 'select 1',
  configJson: '{"dataSourceId":"9"}',
  ...overrides,
});

describe('data-development run preflight', () => {
  it('accepts current valid SQL editor content without requiring a published revision', () => {
    expect(validateDevelopmentRunDefinition(sqlDefinition())).toBeUndefined();
  });

  it('rejects an empty SQL editor before submitting an execution', () => {
    expect(
      validateDevelopmentRunDefinition(sqlDefinition({ content: '   ' })),
    ).toEqual({
      code: 'CONTENT_REQUIRED',
      messageId: 'pages.dataDevelopment.execution.preflight.sqlContentRequired',
    });
  });

  it('rejects invalid config before submitting an execution', () => {
    expect(
      validateDevelopmentRunDefinition(sqlDefinition({ configJson: '{broken' })),
    ).toEqual({
      code: 'CONFIG_INVALID',
      messageId: 'pages.dataDevelopment.execution.preflight.configInvalid',
    });
  });

  it('requires a SQL datasource before submitting an execution', () => {
    expect(
      validateDevelopmentRunDefinition(sqlDefinition({ configJson: '{}' })),
    ).toEqual({
      code: 'SQL_DATASOURCE_REQUIRED',
      messageId: 'pages.dataDevelopment.execution.preflight.sqlDataSourceRequired',
    });
  });
});
