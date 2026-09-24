import type {
  DevelopmentTaskPublishValidation,
  DevelopmentTaskValidationIssue,
} from '@/services/data-development';

export type { DevelopmentTaskPublishValidation } from '@/services/data-development';

export const publishIssueLabel = (issue: DevelopmentTaskValidationIssue) => {
  const field = issue.field?.trim();
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
