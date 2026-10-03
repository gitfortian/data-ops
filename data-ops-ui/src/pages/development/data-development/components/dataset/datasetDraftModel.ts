import type {
  DevelopmentDatasetFieldDraft,
  DevelopmentDatasetNodeContext,
} from "../../dataset-service";

export const toFieldDrafts = (
  context?: DevelopmentDatasetNodeContext
): DevelopmentDatasetFieldDraft[] =>
  (context?.dataset?.fields || []).map((field) => ({
    fieldId: field.fieldId,
    physicalName: field.physicalName,
    displayName: field.displayName,
    dataType: field.dataType,
    nullable: field.nullable,
    description: field.description,
    defaultRole: field.defaultRole,
  }));
