import type { DevelopmentId } from './types';

export interface DevelopmentTaskValidationIssue {
  code: string;
  field?: string | null;
  message: string;
}

export interface DevelopmentTaskPublishValidation {
  nodeId: DevelopmentId;
  draftRevision: number;
  valid: boolean;
  message?: string | null;
  issues: DevelopmentTaskValidationIssue[];
}
