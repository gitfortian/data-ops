import {
  physicalDataDevHandoffUrl, parsePhysicalDataDevHandoff,
  verifyPhysicalDataDevEvidence, isCurrentProjectSqlCandidate,
} from './physicalDataDevHandoff';
import type { PhysicalDataDevHandoffReadiness } from '@/services/modeling/api';

const report = {
  modelId: 50, layerCode: 'DWD',
  version: { modelId: 50, versionId: 99, versionNo: 3,
    frozenStructureSha256: 'a'.repeat(64) },
  inspectionSha256: 'b'.repeat(64),
} as PhysicalDataDevHandoffReadiness;
const frozen = { id: 99, modelId: 50, versionNo: 3, columnCount: 2,
  checksum: 'c'.repeat(64), publishTime: '2026-10-11T00:00:00Z' };
const eligible = {
  hint: { processId: 7, modelId: 50, modelVersionNo: 3 },
  owner: { id: 50, processId: 7, layerCode: 'DWD' },
  processId: 7,
  versions: [frozen],
  report,
};

describe('F-040 C5 explicit Modeling → DataDev evidence only', () => {
  it('builds and parses canonical, non-authorizing version route', () => {
    const url = physicalDataDevHandoffUrl(eligible.hint);
    expect(url).toBe('/data-development?source=modeling-handoff&processId=7&modelId=50&modelVersionNo=3');
    expect(parsePhysicalDataDevHandoff('?'+url.split('?')[1])).toEqual({
      kind: 'HINT', value: eligible.hint,
    });
    expect(parsePhysicalDataDevHandoff('?nodeId=abc')).toEqual({ kind: 'NONE' });
    expect(parsePhysicalDataDevHandoff('?source=dataset&datasetId=42')).toEqual({ kind: 'NONE' });
    expect(parsePhysicalDataDevHandoff('')).toEqual({ kind: 'NONE' });
  });

  it('rejects invalid, duplicate or foreign URL identity with no silent fallback', () => {
    for (const v of [0, -1, 1.5, Infinity, Number.MAX_SAFE_INTEGER + 1]) {
      expect(() => physicalDataDevHandoffUrl({ ...eligible.hint, modelId: v })).toThrow();
    }
    for (const query of [
      '?source=modeling-handoff&processId=7&modelVersionNo=3',
      '?source=modeling-handoff&processId=07&modelId=50&modelVersionNo=3',
      '?source=modeling-handoff&processId=7&modelId=50&modelVersionNo=3&modelId=51',
      '?source=modeling-handoff&processId=7&modelId=50&modelVersionNo=NaN',
      '?source=task-creation&processId=7&modelId=50&modelVersionNo=3',
    ]) {
      expect(parsePhysicalDataDevHandoff(query).kind).toBe('INVALID');
    }
  });

  it('only accepts owner-refreshed process, DWD/DIM model, exact frozen version and SHA evidence', () => {
    expect(verifyPhysicalDataDevEvidence(eligible)).toEqual({ valid: true });
    expect(verifyPhysicalDataDevEvidence({
      ...eligible, owner: { ...eligible.owner, layerCode: 'DIM' },
      report: { ...report, layerCode: 'DIM' },
    })).toEqual({ valid: true });
    const cases = [
      { ...eligible, processId: 8 },
      { ...eligible, owner: { ...eligible.owner, id: 51 } },
      { ...eligible, owner: { ...eligible.owner, processId: 8 } },
      { ...eligible, owner: { ...eligible.owner, layerCode: 'ODS' } },
      { ...eligible, versions: [] },
      { ...eligible, versions: [frozen, frozen] },
      { ...eligible, versions: [{ ...frozen, id: 101 }] },
      { ...eligible, versions: [{ ...frozen, modelId: 51 }] },
      { ...eligible, report: { ...report, version: { ...report.version, versionNo: 4 } } },
      { ...eligible, report: { ...report, version: { ...report.version, versionId: 0 } } },
      { ...eligible, report: { ...report, inspectionSha256: '' } },
      { ...eligible, report: { ...report, version: { ...report.version, frozenStructureSha256: 'x' } } },
    ];
    cases.forEach(input =>
      expect(verifyPhysicalDataDevEvidence(input).valid).toBe(false));
  });

  it('only offers manually chosen existing SQL nodes from current project', () => {
    expect(isCurrentProjectSqlCandidate({ id: 'sql-1', type: 'SQL', projectId: '3' }, 3)).toBe(true);
    expect(isCurrentProjectSqlCandidate({ id: 'sql-1', type: 'SQL' }, 3)).toBe(true);
    expect(isCurrentProjectSqlCandidate({ id: 'sql-1', type: 'PYTHON' }, 3)).toBe(false);
    expect(isCurrentProjectSqlCandidate({ id: 'sql-1', type: 'SQL', projectId: '4' }, 3)).toBe(false);
    expect(isCurrentProjectSqlCandidate({ id: 'sql-1', type: 'SQL' }, undefined)).toBe(false);
  });
});
