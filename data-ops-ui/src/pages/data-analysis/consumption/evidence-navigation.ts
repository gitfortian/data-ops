import type { ProductKeyRef } from '@/services/consumption';

/**
 * Source-owned audit refs: only known provider formats get an actionable link.
 * Unsupported refs remain visible as text; Consumption never fabricates a source URL.
 */
export const consumptionEvidenceTarget = (
  product: ProductKeyRef,
  reference: string,
): { href: string; description: string } | null => {
  if (!/^[1-9]\d*$/.test(product.sourceIdentity)) return null;

  if (product.productType === 'DATASET') {
    const match = /^DATASET_QUERY_PERFORMANCE:query:([A-Za-z0-9._-]{1,180})$/.exec(reference);
    if (!match) return null;
    return {
      href: `/dataset/${encodeURIComponent(product.sourceIdentity)}?tab=diagnostics&queryId=${encodeURIComponent(match[1])}`,
      description: '打开 Dataset Query 原始诊断',
    };
  }

  if (product.productType === 'DATA_SERVICE') {
    const match = /^DATA_SERVICE_INVOCATION:invocation:([1-9]\d*)$/.exec(reference);
    if (!match) return null;
    return {
      href: `/data-service/api/${encodeURIComponent(product.sourceIdentity)}?tab=logs&invocationId=${encodeURIComponent(match[1])}`,
      description: '打开 Data Service 精确历史调用记录',
    };
  }

  return null;
};
