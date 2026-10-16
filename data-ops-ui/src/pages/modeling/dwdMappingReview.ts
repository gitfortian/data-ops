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

/** Explicit restoration of a persisted mapping after a user clicks "载入已保存映射".
 * Never silently assign same-named columns or trust stale version identity. */
export function restoreSavedDwdMapping(
  saved: DwdMappingDraft | undefined,
  preview: LogicalPhysicalPreview | undefined,
): { selections: Record<number, number>; kinds: Record<number, 'DIRECT' | 'RENAME'> } | null {
  if (!saved || !preview
      || saved.logicalModelId !== preview.logicalModelId
      || saved.logicalVersionNo !== preview.logicalVersionNo
      || saved.physicalModelId !== preview.physicalModelId
      || saved.logicalSnapshotSha256 !== preview.logicalSnapshotSha256
      || saved.physicalStructureSha256 !== preview.physicalStructureSha256) return null;
  const selections: Record<number, number> = {};
  const kinds: Record<number, 'DIRECT' | 'RENAME'> = {};
  for (const rule of saved.rules) {
    const candidates = preview.columns.filter(column =>
      column.physicalColumn === rule.targetColumn
      && column.physicalStdFieldId === rule.stdFieldId
      && column.physicalColumnId != null);
    if (candidates.length !== 1 || rule.logicalAttributeId <= 0
        || !['DIRECT', 'RENAME'].includes(rule.kind)) return null;
    const id = candidates[0].physicalColumnId!;
    if (id in selections) return null;
    selections[id] = rule.logicalAttributeId;
    kinds[id] = rule.kind;
  }
  if (Object.keys(selections).length !== preview.columns.filter(c => c.physicalColumnId != null).length) {
    return null;
  }
  return { selections, kinds };
}
