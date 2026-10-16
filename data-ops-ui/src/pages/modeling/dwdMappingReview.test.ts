import { matchesSavedDwdMapping, restoreSavedDwdMapping } from './dwdMappingReview';
import type { DwdMappingDraft } from '@/services/modeling/dwdMapping';
import type { LogicalPhysicalPreview } from '@/services/modeling/logical';

const saved: DwdMappingDraft = {
  mappingSetId: 200, physicalModelId: 50, logicalModelId: 77,
  logicalVersionNo: 2, logicalSnapshotSha256: 'a'.repeat(64),
  physicalStructureSha256: 'b'.repeat(64), draftRevision: 3,
  state: 'DRAFT_SAVED_NOT_PUBLISHED',
  rules: [{ targetColumn: 'order_id', logicalAttributeId: 10,
    stdFieldId: 11, kind: 'DIRECT' }],
};
const preview: LogicalPhysicalPreview = {
  physicalLayer: 'DWD', physicalStatus: 'DRAFT',
  logicalModelId: 77, logicalVersionNo: 2, physicalModelId: 50,
  logicalSnapshotSha256: 'a'.repeat(64), physicalStructureSha256: 'b'.repeat(64),
  blockers: [], readyForReview: true,
  columns: [{ physicalColumn: 'order_id', physicalColumnId: 999,
    logicalAttributeId: 10, logicalEntityId: 5,
    physicalStdFieldId: 11, logicalEntity: '订单',
    logicalAttribute: '订单标识', result: 'CANDIDATE_ONLY', reason: '' }],
};

it('allows frozen mapping only when server rules match exact editor IDs and types', () => {
  expect(matchesSavedDwdMapping(saved, preview, { 999: 10 }, { 999: 'DIRECT' })).toBe(true);
  expect(matchesSavedDwdMapping(saved, preview, { 999: 10 }, { 999: 'RENAME' })).toBe(false);
  expect(matchesSavedDwdMapping(saved, preview, { 999: 20 }, { 999: 'DIRECT' })).toBe(false);
  expect(matchesSavedDwdMapping(saved, preview, {}, {})).toBe(false);
});
it('rejects a stale physical structure or mismatched logical snapshot', () => {
  expect(matchesSavedDwdMapping(saved,
    { ...preview, physicalStructureSha256: 'c'.repeat(64) }, { 999: 10 },
    { 999: 'DIRECT' })).toBe(false);
  expect(matchesSavedDwdMapping(saved,
    { ...preview, logicalModelId: 80 }, { 999: 10 },
    { 999: 'DIRECT' })).toBe(false);
});
it('resolves unstable physical column row IDs via frozen column identity', () => {
  expect(matchesSavedDwdMapping(saved, {
    ...preview, columns: [{ ...preview.columns[0], physicalColumnId: 1234 }],
  }, { 1234: 10 }, { 1234: 'DIRECT' })).toBe(true);
});

it('restores persisted mapping only after matching exact model, version, column and standard', () => {
  expect(restoreSavedDwdMapping(saved, preview)).toEqual({
    selections: { 999: 10 }, kinds: { 999: 'DIRECT' },
  });
  expect(restoreSavedDwdMapping(saved, {
    ...preview, columns: [{ ...preview.columns[0], physicalStdFieldId: 12 }],
  })).toBeNull();
  expect(restoreSavedDwdMapping(saved, {
    ...preview, columns: [{ ...preview.columns[0], physicalColumn: 'other' }],
  })).toBeNull();
  expect(restoreSavedDwdMapping(saved, {
    ...preview, physicalStructureSha256: 'c'.repeat(64),
  })).toBeNull();
});
