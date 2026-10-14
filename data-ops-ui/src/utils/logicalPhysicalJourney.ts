/**
 * FE-A3: navigational coordinates ONLY. Neither a persisted logical/physical
 * mapping nor proof of Project access, design approval or a DataDev handoff.
 */
export interface LogicalPhysicalCoordinate {
  processId: number;
  logicalId: number;
  logicalVersionNo: number;
  physicalId: number;
}
const positive = (n: number) => Number.isSafeInteger(n) && n > 0 && n <= 2147483647;
export const validLogicalPhysicalCoordinate = (v: LogicalPhysicalCoordinate): boolean =>
  positive(v.processId) && positive(v.logicalId)
  && positive(v.logicalVersionNo) && positive(v.physicalId);

export function physicalEvidencePath(v: LogicalPhysicalCoordinate): string | null {
  if (!validLogicalPhysicalCoordinate(v)) return null;
  const q = new URLSearchParams({
    tab: 'logical', processId: String(v.processId),
    logicalId: String(v.logicalId), logicalVersion: String(v.logicalVersionNo),
  });
  return `/modeling/models/${v.physicalId}?${q.toString()}`;
}

export function parseLogicalPhysicalHint(q: URLSearchParams, physicalId: number): LogicalPhysicalCoordinate | null {
  if (['logicalId','logicalVersion','processId'].some(k => q.getAll(k).length !== 1)) return null;
  const raw = ['processId','logicalId','logicalVersion'].map(k => q.get(k) || '');
  if (raw.some(s => !/^[1-9]\d*$/.test(s))) return null;
  const [processId, logicalId, logicalVersionNo] = raw.map(Number);
  const v = { processId, logicalId, logicalVersionNo, physicalId };
  return validLogicalPhysicalCoordinate(v) ? v : null;
}

export function logicalDraftNavigationPath(processId: number, logicalId: number): string | null {
  if (!positive(processId) || !positive(logicalId)) return null;
  return `/modeling/logical?processId=${processId}&draftId=${logicalId}&review=physical`;
}
