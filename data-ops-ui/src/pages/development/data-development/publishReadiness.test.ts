import {
  publishIssueLabel,
  publishReadinessSummary,
  type DevelopmentTaskPublishValidation,
} from './publishReadiness';

describe('data-development publish readiness', () => {
  it('keeps publish readiness bound to one exact draft revision', () => {
    const validation: DevelopmentTaskPublishValidation = {
      nodeId: '7',
      draftRevision: 12,
      valid: true,
      message: 'ready',
      issues: [],
    };

    expect(publishReadinessSummary(validation)).toEqual({
      draftRevision: 12,
      valid: true,
      issueLabels: [],
    });
  });

  it('renders field-aware validation issues without parsing server prose', () => {
    expect(
      publishIssueLabel({
        code: 'SQL_DATASOURCE_REQUIRED',
        field: 'config.dataSourceId',
        message: 'Data source is required',
      }),
    ).toBe('config.dataSourceId: Data source is required');

    expect(publishIssueLabel({ code: 'TASK_INVALID', message: '' })).toBe(
      'TASK_INVALID',
    );
  });
});
