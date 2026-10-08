import type { ConsumerImpact, KnownConsumer, SourceVersionRef } from '@/services/consumption';
import { impactEvidenceIncomplete, impactEvidenceWindowFacts } from './impact-evidence-coverage';

export interface VersionImpactRow {
  consumer: KnownConsumer;
  successfulUsageCount: number;
  lastObservedAt?: string | null;
  evidenceRefs: string[];
  declaredOnly: boolean;
}

export interface VersionImpactReview {
  version: SourceVersionRef;
  rows: VersionImpactRow[];
  observedConsumerCount: number;
  declaredOnlyConsumerCount: number;
  observedSuccessCount: number;
  incomplete: boolean;
}

/** Only source-owned identities; a currently active version is not necessarily an executed version. */
export const reviewableVersions = (
  impact: ConsumerImpact | null,
  active?: SourceVersionRef | null,
): SourceVersionRef[] => {
  const byIdentity = new Map<string, SourceVersionRef>();
  if (active?.identity) byIdentity.set(active.identity, active);
  impact?.consumers.forEach((consumer) => {
    consumer.observedVersions?.forEach(({ sourceVersion }) => {
      if (!sourceVersion.identity) return;
      const previous = byIdentity.get(sourceVersion.identity);
      if (!previous?.displayVersion && sourceVersion.displayVersion) {
        byIdentity.set(sourceVersion.identity, sourceVersion);
      } else if (!previous) {
        byIdentity.set(sourceVersion.identity, sourceVersion);
      }
    });
  });
  return [...byIdentity.values()];
};

/**
 * An explicit deep-linked immutable version must match current source evidence.
 * Never silently fall back to the active version when a returned review link is
 * stale or the requested source version has left the readable audit window.
 */
export const selectReviewableVersion = (
  versions: readonly SourceVersionRef[],
  requestedIdentity?: string | null,
): SourceVersionRef | undefined =>
  requestedIdentity ? versions.find((version) => version.identity === requestedIdentity) : versions[0];

/** Declared dependency is not a version-bound execution fact. */
export const reviewVersionImpact = (
  impact: ConsumerImpact | null,
  version: SourceVersionRef | undefined,
): VersionImpactReview | null => {
  if (!impact || !version?.identity) return null;
  const rows: VersionImpactRow[] = [];
  for (const consumer of impact.consumers) {
    const observed = consumer.observedVersions?.find(
      (item) => item.sourceVersion.identity === version.identity,
    );
    if (observed) {
      rows.push({
        consumer,
        successfulUsageCount: observed.successfulUsageCount,
        lastObservedAt: observed.lastObservedAt,
        evidenceRefs: observed.providerEvidenceRefs || [],
        declaredOnly: false,
      });
    } else if (consumer.activeSubscriptionCount > 0) {
      rows.push({
        consumer,
        successfulUsageCount: 0,
        evidenceRefs: [],
        declaredOnly: true,
      });
    }
  }
  return {
    version,
    rows,
    observedConsumerCount: rows.filter((row) => !row.declaredOnly).length,
    declaredOnlyConsumerCount: rows.filter((row) => row.declaredOnly).length,
    observedSuccessCount: rows.reduce((sum, row) => sum + row.successfulUsageCount, 0),
    incomplete: impactEvidenceIncomplete(impact),
  };
};

/** Client-side review snapshot only: never call it approval or durable source audit. */
export const versionImpactReviewText = (
  productKey: string,
  projectId: string,
  impact: ConsumerImpact,
  review: VersionImpactReview,
  reviewedAt: string,
): string => [
  '数据产品版本变更前消费者影响核对（手动快照，非发布批准）',
  'Project: ' + projectId,
  'Product: ' + productKey,
  '目标来源版本 ID: ' + review.version.identity,
  '版本显示: ' + (review.version.displayVersion || '未提供'),
  '核对时间: ' + reviewedAt,
  'Subscription Evidence: ' + impact.subscriptionState,
  'Usage Evidence: ' + impact.usageState,
  '已观察到该版本的 Consumer: ' + review.observedConsumerCount,
  '仅声明依赖、未观察到该版本的 Consumer: ' + review.declaredOnlyConsumerCount,
  '该版本已观察成功使用次数（本次窗口）: ' + review.observedSuccessCount,
  '覆盖状态: ' + (review.incomplete ? 'PARTIAL / NEEDS_FOLLOW_UP' : 'WINDOW_ONLY / NOT_COMPLETE_HISTORY'),
  '覆盖说明: ' + impact.coverageNote,
  ...impactEvidenceWindowFacts(impact),
  ...review.rows.map((row) => [
    '- ' + row.consumer.consumerRef.consumerType + ':' + row.consumer.consumerRef.sourceDomain + ':' + row.consumer.consumerRef.sourceIdentity,
    '  ' + (row.declaredOnly ? '声明依赖，未观察到该版本消费' : '该版本成功消费 ' + row.successfulUsageCount + ' 次'),
    '  最近证据: ' + (row.lastObservedAt || '未提供'),
    '  来源证据: ' + (row.evidenceRefs.join(', ') || '无'),
  ].join('\n')),
  '声明依赖不是版本绑定；实际使用只涵盖当前来源同步窗口，不能由未观察到推出不受影响。',
  '此记录由页面复制，不持久化、不代表已审批、也不改变来源域发布行为。',
].join('\n');
