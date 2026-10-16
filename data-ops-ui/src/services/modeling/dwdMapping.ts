import HttpUtils from '@/utils/HttpUtils';

/** R12 draft evidence, NOT an approved logical design or published physical model. */
export interface DwdStoredRule {
  targetColumn: string;
  logicalAttributeId: number;
  stdFieldId: number;
  kind: 'DIRECT' | 'RENAME';
}
export interface DwdMappingDraft {
  mappingSetId: number;
  physicalModelId: number;
  logicalModelId: number;
  logicalVersionNo: number;
  logicalSnapshotSha256: string;
  physicalStructureSha256: string;
  draftRevision: number;
  rules: DwdStoredRule[];
  state: 'DRAFT_SAVED_NOT_PUBLISHED';
}
export interface DwdMappingFrozen {
  mappingRevisionId: number;
  mappingSetId: number;
  revisionNo: number;
  contentSha256: string;
  draftRevision: number;
  state: 'FROZEN_DESIGN_ONLY';
}
export interface DwdMappingSave {
  logicalModelId: number;
  logicalVersionNo: number;
  logicalSnapshotSha256: string;
  physicalStructureSha256: string;
  expectedDraftRevision: number;
  rules: { physicalColumnId: number; logicalAttributeId: number;
    kind: 'DIRECT' | 'RENAME' }[];
}

const path = (physicalId: number) => `/api/v1/modeling/models/${physicalId}/logical-mapping`;

export const getDwdMappingDraft = (physicalId: number): Promise<DwdMappingDraft | null> =>
  HttpUtils.getData<DwdMappingDraft | null>(path(physicalId));

export const saveDwdMappingDraft = (physicalId: number, request: DwdMappingSave): Promise<DwdMappingDraft> =>
  HttpUtils.putData<DwdMappingDraft>(path(physicalId), request);

export const freezeDwdMappingDraft = (physicalId: number, expectedDraftRevision: number): Promise<DwdMappingFrozen> =>
  HttpUtils.postData<DwdMappingFrozen>(
    `${path(physicalId)}/revisions?expectedDraftRevision=${expectedDraftRevision}`, {},
  );

export const listDwdMappingRevisions = (physicalId: number): Promise<DwdMappingFrozen[]> =>
  HttpUtils.getData<DwdMappingFrozen[]>(`${path(physicalId)}/revisions`);

/** Design publication only; never implies DataDev, DDL or a running task. */
export interface DwdBoundPublication {
  modelVersionId: number;
  versionNo: number;
  mappingRevisionId: number;
  created: boolean;
  state: 'DESIGN_PUBLISHED_NOT_EXECUTED';
}
export interface DwdHistoricalRevision {
  mappingRevisionId: number;
  mappingSetId: number;
  revisionNo: number;
  logicalModelId: number;
  logicalVersionNo: number;
  logicalSnapshotSha256: string;
  physicalStructureSha256: string;
  contentSha256: string;
  rules: DwdStoredRule[];
}
export interface DwdPublishedMapping {
  modelVersionId: number;
  versionNo: number;
  state: 'DESIGN_PUBLISHED_NOT_EXECUTED' | 'LEGACY_UNBOUND';
  mapping: DwdHistoricalRevision | null;
}

export const publishDwdWithMapping = (
  physicalId: number, mappingRevisionId: number,
): Promise<DwdBoundPublication> =>
  HttpUtils.postData<DwdBoundPublication>(
    `${path(physicalId)}/publish-with-mapping?mappingRevisionId=${mappingRevisionId}`, {},
  );

export const submitDwdMappingPublishApproval = (
  physicalId: number, mappingRevisionId: number,
): Promise<unknown> =>
  HttpUtils.postData<unknown>(
    `${path(physicalId)}/publish-approval-with-mapping?mappingRevisionId=${mappingRevisionId}`, {},
  );

export const getPublishedDwdMapping = (
  physicalId: number, versionNo: number,
): Promise<DwdPublishedMapping> =>
  HttpUtils.getData<DwdPublishedMapping>(
    `${path(physicalId)}/published-versions/${versionNo}`,
  );

export const getFrozenDwdMappingDetail = (
  physicalId: number, mappingRevisionId: number,
): Promise<DwdHistoricalRevision> =>
  HttpUtils.getData<DwdHistoricalRevision>(
    `${path(physicalId)}/revisions/${mappingRevisionId}`,
  );
