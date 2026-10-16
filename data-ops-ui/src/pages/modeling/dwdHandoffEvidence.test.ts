import { assessDwdMappingEvidence } from './dwdHandoffEvidence';
import type { ModelingVersionSummary } from '@/services/modeling/types';
import type { PhysicalDataDevHandoffReadiness } from '@/services/modeling/api';
import type { DwdPublishedMapping } from '@/services/modeling/dwdMapping';

const published: ModelingVersionSummary = {
  id: 650, modelId: 50, versionNo: 3, columnCount: 1,
  checksum: 'checksum', publishTime: '2026-10-16',
};
const readiness = {
  modelId: 50, version: { modelId: 50, versionId: 650, versionNo: 3 },
} as PhysicalDataDevHandoffReadiness;
const binding: DwdPublishedMapping = {
  modelVersionId: 650, versionNo: 3, state: 'DESIGN_PUBLISHED_NOT_EXECUTED',
  mapping: {
    mappingRevisionId: 901, mappingSetId: 88, revisionNo: 2,
    logicalModelId: 77, logicalVersionNo: 4,
    logicalSnapshotSha256: 'a'.repeat(64),
    physicalStructureSha256: 'b'.repeat(64),
    contentSha256: 'c'.repeat(64),
    rules: [{ targetColumn: 'order_id', logicalAttributeId: 11, stdFieldId: 21,
      kind: 'DIRECT' }],
  },
};
it('recognizes only the exact immutable DWD physical and mapping version', () => {
  expect(assessDwdMappingEvidence(50, 3, readiness, [published], binding).ready).toBe(true);
  expect(assessDwdMappingEvidence(51, 3, readiness, [published], binding).ready).toBe(false);
  expect(assessDwdMappingEvidence(50, 2, readiness, [published], binding).ready).toBe(false);
  expect(assessDwdMappingEvidence(50, 3, readiness, [published], {
    ...binding, modelVersionId: 99,
  }).ready).toBe(false);
});
it('fails closed for legacy, missing and corrupted mapping evidence', () => {
  expect(assessDwdMappingEvidence(50, 3, readiness, [published], undefined).ready).toBe(false);
  expect(assessDwdMappingEvidence(50, 3, readiness, [published], {
    modelVersionId: 650, versionNo: 3, state: 'LEGACY_UNBOUND', mapping: null,
  }).ready).toBe(false);
  expect(assessDwdMappingEvidence(50, 3, readiness, [published], {
    ...binding, mapping: { ...binding.mapping!, contentSha256: 'bad' },
  }).ready).toBe(false);
  expect(assessDwdMappingEvidence(50, 3, readiness, [published], {
    ...binding, mapping: { ...binding.mapping!, rules: [] },
  }).ready).toBe(false);
});
