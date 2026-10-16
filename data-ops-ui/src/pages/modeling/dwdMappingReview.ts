import type { DwdMappingDraft } from '@/services/modeling/dwdMapping';
import type { LogicalPhysicalPreview } from '@/services/modeling/logical';

/** Persisted server draft must match every current editor rule before freeze. */
export function matchesSavedDwdMapping(
  saved: DwdMappingDraft | undefined,
  preview: LogicalPhysicalPreview | undefined,
  selections: Record<number, number>,
  kinds: Record<number, 'DIRECT' | 'RENAME'>,
): boolean {
  if (!saved || !preview
      || saved.logicalModelId !== preview.logicalModelId
      || saved.logicalVersionNo !== preview.logicalVersionNo
      || saved.physicalModelId !== preview.physicalModelId
      || saved.logicalSnapshotSha256 !== preview.logicalSnapshotSha256
      || saved.physicalStructureSha256 !== preview.physicalStructureSha256
      || saved.rules.length !== Object.keys(selections).length) {
    return false;
  }
  return saved.rules.every(rule => {
    const columns = preview.columns.filter(column =>
      column.physicalColumn === rule.targetColumn
      && column.physicalStdFieldId === rule.stdFieldId
      && column.physicalColumnId != null);
    return columns.length === 1
      && selections[columns[0].physicalColumnId!] === rule.logicalAttributeId
      && kinds[columns[0].physicalColumnId!] === rule.kind;
  });
}
