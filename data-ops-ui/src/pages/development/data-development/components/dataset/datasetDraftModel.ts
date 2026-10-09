import type {
  DevelopmentDatasetFieldDraft,
  DevelopmentDatasetNodeContext,
} from "@/services/data-development";

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
