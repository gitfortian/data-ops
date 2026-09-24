import type { AssetSection, AssetSectionContract } from './types';

const supportsAssetReturn = (target: string): boolean =>
  /^\/data-asset\/catalog(?:\?|$)/.test(target)
  || target.startsWith('/data-analysis/lineage?')
  || /^\/modeling\/models\/\d+(?:\?|$)/.test(target)
  || /^\/data-security\/classification(?:\?|$)/.test(target)
  || /^\/data-lifecycle\/monitor(?:\?|$)/.test(target)
  || /^\/metric\/manage\/\d+(?:\?|$)/.test(target)
  || /^\/data-quality\/(?:table-config|monitor\/\d+|execution(?:\/[^?]+)?)(?:\?|$)/.test(target);

/** Convert the Section API envelope without dropping its owner or source evidence. */
export const toAssetSectionView = (
  contract: AssetSectionContract,
  returnAssetId?: number,
): AssetSection<Record<string, unknown>> => {
  const values = contract.summary?.values ?? {};
  const data = Object.prototype.hasOwnProperty.call(values, 'data') ? values.data : values;
  const actions = contract.actions ?? [];

  return {
    status: contract.status,
    note: contract.reason,
    data: data as Record<string, unknown>,
    actions: actions.map((action) => ({
      ...action,
      target: returnAssetId
        && supportsAssetReturn(action.target)
        && !/[?&]returnAssetId=\d+(?:&|$)/.test(action.target)
        ? `${action.target}${action.target.includes('?') ? '&' : '?'}returnAssetId=${returnAssetId}`
        : action.target,
    })),
    ownerDomain: contract.ownerDomain,
    updatedAt: contract.updatedAt,
    evidence: contract.evidence ?? [],
    provenance: contract.provenance,
    capability: contract.capability,
  };
};

export const unavailableAssetSection = (): AssetSection => ({
  status: 'UNAVAILABLE',
  note: '该分区暂不可用，请稍后重试',
});
