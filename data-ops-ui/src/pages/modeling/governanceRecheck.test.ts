import {
  compareLogicalGovernance, comparePhysicalGovernance,
  logicalGovernanceBaseline, physicalGovernanceBaseline,
} from './governanceRecheck';
import type { LogicalVersionSemanticReview } from '@/services/modeling/logical';
import type { PhysicalStandardCoverageInspection } from '@/services/modeling/api';

const physical = {
  modelId: 20, evidenceSha256: 'a'.repeat(64), missingCount: 2,
  invalidCount: 1, reviewCount: 0,
} as PhysicalStandardCoverageInspection;
const logical = {
  logicalModelId: 30, versionNo: 2, frozenSnapshotSha256: 'f'.repeat(64),
  currentDesignSha256: 'a'.repeat(64), reviewStatus: 'NEEDS_REVIEW',
  attributes: [{ entityId: 3, attributeId: 4, stdFieldId: null,
    standardStatus: 'PENDING_STANDARD', explanation: '待确认' }],
  relations: [{ relationId: 5, cardinality: 'UNKNOWN',
    status: 'CARDINALITY_PENDING', explanation: '未知关系' }],
  blockers: ['尚有业务关系待确认'], changes: [],
} as LogicalVersionSemanticReview;

describe('F-040 governance recheck: evidence only, never a persistent approval', () => {
  it('distinguishes unchanged physical evidence from fewer, more, or equal-sized changed gaps', () => {
    const before = physicalGovernanceBaseline(physical);
    expect(comparePhysicalGovernance(before, physical).state).toBe('UNCHANGED');
    const fewer = comparePhysicalGovernance(before, {
      ...physical, evidenceSha256: 'b'.repeat(64), missingCount: 1,
    });
    expect(fewer.state).toBe('CHANGED');
    expect(fewer.explanation).toContain('2→1');
    expect(fewer.explanation).toContain('不自动判定修复成功');
    expect(comparePhysicalGovernance(before, { ...physical,
      evidenceSha256: 'c'.repeat(64), missingCount: 2 }).state).toBe('CHANGED');
    expect(comparePhysicalGovernance(before, { ...physical,
      evidenceSha256: 'd'.repeat(64), missingCount: 4 }).state).toBe('CHANGED');
  });

  it('rejects wrong physical owner or missing comparison fingerprints', () => {
    const before = physicalGovernanceBaseline(physical);
    expect(comparePhysicalGovernance(before, { ...physical, modelId: 21 }).state)
      .toBe('WRONG_OWNER');
    expect(comparePhysicalGovernance({ ...before, evidenceSha256: '' }, physical).state)
      .toBe('WRONG_OWNER');
  });

  it('compares frozen logical version against live design AND Semantic status', () => {
    const before = logicalGovernanceBaseline(logical);
    expect(compareLogicalGovernance(before, logical).state).toBe('UNCHANGED');
    const standardsUpdated = compareLogicalGovernance(before, {
      ...logical, attributes: [{ ...logical.attributes[0], standardStatus: 'ENABLED_NOW' }],
    });
    expect(standardsUpdated.state).toBe('CHANGED');
    expect(standardsUpdated.explanation).toContain('1→0');
    expect(standardsUpdated.explanation).toContain('冻结 V2');
    expect(compareLogicalGovernance(before, {
      ...logical, currentDesignSha256: 'd'.repeat(64),
    }).state).toBe('CHANGED');
    expect(compareLogicalGovernance(before, {
      ...logical, blockers: ['新增异常说明'],
    }).state).toBe('CHANGED');
  });

  it('rejects another logical model, version, or changed frozen snapshot', () => {
    const before = logicalGovernanceBaseline(logical);
    expect(compareLogicalGovernance(before, { ...logical, logicalModelId: 31 }).state)
      .toBe('WRONG_OWNER');
    expect(compareLogicalGovernance(before, { ...logical, versionNo: 3 }).state)
      .toBe('WRONG_OWNER');
    expect(compareLogicalGovernance(before, {
      ...logical, frozenSnapshotSha256: 'x'.repeat(64),
    }).state).toBe('WRONG_OWNER');
  });
});
