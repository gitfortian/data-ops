import type { ConsumerRef } from '@/services/consumption';

export interface ConsumerSourceTarget {
  href: string;
  label: string;
  description: string;
  requiredPermission?: 'data-service:access';
}

/**
 * Source-object navigation only. It does not resolve a human owner or grant
 * permission to represent a Consumer in a change approval.
 *
 * Existing Data Service Consumer/Dashboard UI use numeric ids, so never route
 * unsafe integer identities through JS Number coercion.
 */
export const consumerSourceTarget = (ref: ConsumerRef): ConsumerSourceTarget | null => {
  const id = ref.sourceIdentity;
  if (!/^[1-9]\d*$/.test(id)) return null;
  const numericId = Number(id);
  if (!Number.isSafeInteger(numericId) || String(numericId) !== id) return null;

  if (ref.consumerType === 'DATA_SERVICE' && ref.sourceDomain === 'DATA_SERVICE_CONSUMER') {
    return {
      href: '/data-service/access?consumerId=' + encodeURIComponent(id),
      label: '核对调用方配置',
      description: '进入项目内 Data Service 调用方与授权配置；不代表已找到授权签字人',
      requiredPermission: 'data-service:access',
    };
  }

  if (ref.consumerType === 'DASHBOARD' && ref.sourceDomain === 'DASHBOARD') {
    return {
      href: '/dashboard/' + encodeURIComponent(id),
      label: '核对仪表盘',
      description: '进入来源仪表盘查看；仪表盘存在不证明其负责人已确认变更',
    };
  }

  return null;
};

/** A deep link only targets an exact, representable numeric consumer id. */
export const parseManagedConsumerSourceId = (input: string | null): number | null => {
  if (!input || !/^[1-9]\d*$/.test(input)) return null;
  const value = Number(input);
  return Number.isSafeInteger(value) && String(value) === input ? value : null;
};
