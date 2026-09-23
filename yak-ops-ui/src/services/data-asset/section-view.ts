import type { AssetSection, AssetSectionContract } from './types';

/** Convert the Section API envelope without dropping its owner or source evidence. */
export const toAssetSectionView = (contract: AssetSectionContract): AssetSection<Record<string, unknown>> => {
  const values = contract.summary?.values ?? {};
  const data = Object.prototype.hasOwnProperty.call(values, 'data') ? values.data : values;

  return {
    status: contract.status,
    note: contract.reason,
    data: data as Record<string, unknown>,
    actions: contract.actions ?? [],
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
