import type { ConsumerRef, ProductType } from '@/services/consumption';

export interface ConsumerSourceTarget {
  href: string;
  label: string;
  description: string;
  requiredPermission?: 'data-service:access';
}

/**
 * Only a canonical, app-owned Consumption Detail route may be used as a
 * source-detail return target. Not a general redirect, arbitrary pathname or
 * same-origin URL. The source version is an immutable decimal ID string.
 */
export const consumptionReviewReturnPath = (
  type: ProductType,
  sourceIdentity: string,
  versionIdentity?: string | null,
): string | null => {
  if (type !== 'DATASET' && type !== 'DATA_SERVICE') return null;
  if (!/^[1-9]\d{0,29}$/.test(sourceIdentity)) return null;
  if (versionIdentity && !/^[1-9]\d{0,29}$/.test(versionIdentity)) return null;
  const path = '/data-analysis/consumption/'
    + encodeURIComponent(type + ':' + sourceIdentity);
  if (!versionIdentity) return path;
  return path + '?' + new URLSearchParams({ reviewVersion: versionIdentity }).toString();
};

/**
 * Strict round trip: no external hosts, // or backslash paths, other query
 * parameters, fragment, decoded traversal, repeated params or data URLs.
 * Rebuild from the captured identities instead of trusting incoming text.
 */
export const parseConsumptionReviewReturnPath = (input: string | null): string | null => {
  if (!input || input.length > 240) return null;
  const matched = /^\/data-analysis\/consumption\/(DATASET|DATA_SERVICE)%3A([1-9]\d{0,29})(?:\?reviewVersion=([1-9]\d{0,29}))?$/.exec(input);
  if (!matched) return null;
  return consumptionReviewReturnPath(matched[1] as ProductType, matched[2], matched[3]);
};

/**
 * Source-object navigation only; NOT a human owner/contact lookup.
 *
 * Managed Data Service Consumers have number-typed IDs in the current client.
 * Dashboard source identities are kept as decimal strings by the Dashboard
 * domain, so > 2^53 Dashboard identities can be navigated losslessly.
 */
export const consumerSourceTarget = (
  ref: ConsumerRef,
  requestedReturnPath?: string | null,
): ConsumerSourceTarget | null => {
  const id = ref.sourceIdentity;
  if (!/^[1-9]\d{0,29}$/.test(id)) return null;
  const returnPath = parseConsumptionReviewReturnPath(requestedReturnPath || null);

  if (ref.consumerType === 'DATA_SERVICE' && ref.sourceDomain === 'DATA_SERVICE_CONSUMER') {
    if (parseManagedConsumerSourceId(id) === null) return null;
    const query = new URLSearchParams({ consumerId: id });
    if (returnPath) query.set('returnTo', returnPath);
    return {
      href: '/data-service/access?' + query.toString(),
      label: '核对调用方配置',
      description: '进入项目内 Data Service 调用方与授权配置；不代表已找到授权签字人',
      requiredPermission: 'data-service:access',
    };
  }

  if (ref.consumerType === 'DASHBOARD' && ref.sourceDomain === 'DASHBOARD') {
    const query = returnPath
      ? '?' + new URLSearchParams({ returnTo: returnPath }).toString()
      : '';
    return {
      href: '/dashboard/' + id + query,
      label: '核对仪表盘',
      description: '进入来源仪表盘查看；仪表盘存在不证明其负责人已确认变更',
    };
  }
  return null;
};

/** Numeric source management APIs must never round a BIGINT identity. */
export const parseManagedConsumerSourceId = (input: string | null): number | null => {
  if (!input || !/^[1-9]\d*$/.test(input)) return null;
  const value = Number(input);
  return Number.isSafeInteger(value) && String(value) === input ? value : null;
};
