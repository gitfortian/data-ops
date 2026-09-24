export interface DevelopmentPublishValidationIssue {
  code?: string | null;
  field?: string | null;
  path?: string | null;
  message?: string | null;
}

export interface DevelopmentTaskPublishValidation {
  nodeId: string;
  draftRevision: number;
  valid: boolean;
  message?: string | null;
  issues: DevelopmentPublishValidationIssue[];
}

export const publishIssueLabel = (issue: DevelopmentPublishValidationIssue) => {
  const field = issue.field || issue.path;
  const message = issue.message?.trim();
  if (field && message) return `${field}: ${message}`;
  if (message) return message;
  if (issue.code) return issue.code;
  return 'Unknown validation issue';
};

export const publishReadinessSummary = (
  validation: DevelopmentTaskPublishValidation,
) => ({
  draftRevision: validation.draftRevision,
  valid: validation.valid,
  issueLabels: (validation.issues || []).map(publishIssueLabel),
});
