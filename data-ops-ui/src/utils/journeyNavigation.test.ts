import {
  canOpenPhysicalGovernanceEvidence, chooseJourneyLogicalModel,
  exactJourneyLogicalDraft, latestJourneyFrozenVersion,
  logicalDraftsForProcess, parseJourneyProcessId,
  processToLogical, processToLogicalGovernance,
  processToPhysical, processToPhysicalGovernance, processToPhysicalHandoff, processToSemantic,
} from './journeyNavigation';

const drafts = [
  { id: 21, processId: 7, name: '订单' },
  { id: 22, processId: 8, name: '支付' },
  { id: 23, processId: 7, name: '订单扩展' },
];

describe('guided process/model URL context', () => {
  it('allows only safe, canonical positive process identities', () => {
    expect(parseJourneyProcessId('7')).toBe(7);
    for (const input of [null, '', '0', '-1', '+1', '1.0', '01',
      '1&foo=bar', 'NaN', '9007199254740993', ' 7', '7 ']) {
      expect(parseJourneyProcessId(input)).toBeUndefined();
    }
    expect(() => processToLogical(0)).toThrow();
    expect(() => processToPhysical(Number.MAX_SAFE_INTEGER + 1)).toThrow();
  });

  it('uses distinct, non-mutating existing professional routes', () => {
    expect(processToSemantic(7)).toBe('/semantic/processes/7/edit');
    expect(processToLogical(7)).toBe('/modeling/logical?processId=7');
    expect(processToPhysical(7)).toBe('/modeling?processId=7');
    expect(processToLogical(7)).not.toContain('approved');
  });

  it('builds canonical owner evidence URLs with both process and model identity', () => {
    expect(processToLogicalGovernance(7, 21))
      .toBe('/modeling/logical?processId=7&draftId=21&review=semantic');
    expect(processToPhysicalGovernance(7, 34))
      .toBe('/modeling/models/34?processId=7&review=standard-coverage');
    expect(processToPhysicalHandoff(7, 34))
      .toBe('/modeling/models/34?processId=7&review=datadev-handoff');
    for (const id of [0, -1, 1.4, Number.MAX_SAFE_INTEGER + 1]) {
      expect(() => processToLogicalGovernance(7, id)).toThrow();
      expect(() => processToPhysicalGovernance(id, 34)).toThrow();
      expect(() => processToPhysicalHandoff(7, id)).toThrow();
    }
  });

  it('rejects foreign, missing, and invalid evidence object IDs without a fallback', () => {
    expect(exactJourneyLogicalDraft(drafts, 7, 23)?.name).toBe('订单扩展');
    expect(exactJourneyLogicalDraft(drafts, 7, 22)).toBeUndefined();
    expect(exactJourneyLogicalDraft(drafts, 7, 99)).toBeUndefined();
    expect(exactJourneyLogicalDraft(drafts, 7, -1)).toBeUndefined();
    expect(latestJourneyFrozenVersion([{ versionNo: 2 }, { versionNo: 1 }])).toBe(2);
    expect(latestJourneyFrozenVersion([])).toBeUndefined();
    expect(latestJourneyFrozenVersion([{ versionNo: -1 }])).toBeUndefined();
  });

  it('opens physical evidence only for the same saved DWD/DIM and current process', () => {
    const base = { processId: 7, modelId: 34, owner: { id: 34, processId: 7, layerCode: 'DWD' },
      savedStructureReady: true, dirty: false, canRead: true };
    expect(canOpenPhysicalGovernanceEvidence(base)).toBe(true);
    expect(canOpenPhysicalGovernanceEvidence({ ...base, owner: { ...base.owner, layerCode: 'DIM' } })).toBe(true);
    expect(canOpenPhysicalGovernanceEvidence({ ...base, owner: { ...base.owner, processId: 8 } })).toBe(false);
    expect(canOpenPhysicalGovernanceEvidence({ ...base, owner: { ...base.owner, id: 99 } })).toBe(false);
    expect(canOpenPhysicalGovernanceEvidence({ ...base, owner: { ...base.owner, layerCode: 'ODS' } })).toBe(false);
    expect(canOpenPhysicalGovernanceEvidence({ ...base, owner: null })).toBe(false);
    expect(canOpenPhysicalGovernanceEvidence({ ...base, processId: undefined })).toBe(false);
    expect(canOpenPhysicalGovernanceEvidence({ ...base, dirty: true })).toBe(false);
    expect(canOpenPhysicalGovernanceEvidence({ ...base, savedStructureReady: false })).toBe(false);
    expect(canOpenPhysicalGovernanceEvidence({ ...base, canRead: false })).toBe(false);
  });

  it('never falls back to another process when the URL is scoped', () => {
    expect(logicalDraftsForProcess(drafts, 7).map(v => v.id)).toEqual([21, 23]);
    expect(chooseJourneyLogicalModel(drafts, 8)).toBe(22);
    expect(chooseJourneyLogicalModel(drafts, 9)).toBeUndefined();
    expect(chooseJourneyLogicalModel(drafts, 7, 22)).toBe(21);
    expect(chooseJourneyLogicalModel(drafts, 7, 23)).toBe(23);
    expect(chooseJourneyLogicalModel(drafts)).toBe(21);
  });
});
