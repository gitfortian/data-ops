import type { ConsumerImpact, KnownConsumer } from '@/services/consumption';
import { reviewableVersions, selectReviewableVersion, reviewVersionImpact, versionImpactReviewText } from './version-impact-review';

const consumer = (
  id: string,
  activeSubscriptionCount: number,
  observedVersions: KnownConsumer['observedVersions'],
): KnownConsumer => ({
  consumerRef: {
    consumerType: 'DATA_SERVICE', sourceDomain: 'DATA_SERVICE_CONSUMER',
    sourceIdentity: id, displayHint: 'Consumer ' + id,
  },
  declaredModes: activeSubscriptionCount ? ['API_INVOKE'] : [],
  observedModes: observedVersions?.length ? ['API_INVOKE'] : [],
  activeSubscriptionCount,
  successfulUsageCount: observedVersions?.reduce((n, v) => n + v.successfulUsageCount, 0) || 0,
  providerEvidenceRefs: observedVersions?.flatMap((v) => v.providerEvidenceRefs) || [],
  observedVersions,
});

const impact: ConsumerImpact = {
  productKey: { productType: 'DATA_SERVICE', sourceIdentity: '55' },
  subscriptionState: 'READY',
  usageState: 'READY',
  coverageNote: 'Only known evidence in current source window',
  consumers: [
    consumer('1', 1, [
      {
        sourceVersion: { identity: '9007199254740993', displayVersion: 'r8' },
        successfulUsageCount: 2, lastObservedAt: '2026-10-08T12:00:00',
        providerEvidenceRefs: ['DATA_SERVICE_INVOCATION:invocation:9007199254740999'],
      },
      {
        sourceVersion: { identity: '9007199254740995', displayVersion: 'r8' },
        successfulUsageCount: 1,
        providerEvidenceRefs: ['DATA_SERVICE_INVOCATION:invocation:9007199254741001'],
      },
    ]),
    consumer('2', 1, []),
    consumer('3', 0, [{
      sourceVersion: { identity: '9007199254740995', displayVersion: 'r8' },
      successfulUsageCount: 9,
      providerEvidenceRefs: ['DATA_SERVICE_INVOCATION:invocation:9'],
    }]),
  ],
};

describe('pre-change known Consumer version review', () => {
  it('lists exact source-owned versions with the active revision first and no number coercion', () => {
    const versions = reviewableVersions(impact, {
      identity: '9007199254740995', displayVersion: 'r9',
    });
    expect(versions.map((v) => v.identity)).toEqual([
      '9007199254740995', '9007199254740993',
    ]);
    expect(versions[0].displayVersion).toBe('r9');
  });

  it('separates version-observed consumers from unbound declared dependencies', () => {
    const target = reviewableVersions(impact).find((v) => v.identity === '9007199254740993');
    const review = reviewVersionImpact(impact, target)!;
    expect(review.observedConsumerCount).toBe(1);
    expect(review.declaredOnlyConsumerCount).toBe(1);
    expect(review.observedSuccessCount).toBe(2);
    expect(review.incomplete).toBe(false);
    expect(review.rows.map((row) => row.consumer.consumerRef.sourceIdentity)).toEqual(['1', '2']);
    expect(review.rows[0].evidenceRefs).toEqual([
      'DATA_SERVICE_INVOCATION:invocation:9007199254740999',
    ]);
    expect(review.rows[1]).toMatchObject({ declaredOnly: true, evidenceRefs: [] });
  });

  it('matches immutable identities instead of the same human-readable revision label', () => {
    const review = reviewVersionImpact(impact, {
      identity: '9007199254740995', displayVersion: 'r8',
    })!;
    expect(review.observedConsumerCount).toBe(2);
    expect(review.observedSuccessCount).toBe(10);
    expect(review.rows[0].evidenceRefs).toEqual([
      'DATA_SERVICE_INVOCATION:invocation:9007199254741001',
    ]);
  });

  it('does not turn provider failure or no successful usage into a negative impact assertion', () => {
    const partial: ConsumerImpact = {
      ...impact, subscriptionState: 'UNAVAILABLE', usageState: 'FORBIDDEN', consumers: [],
    };
    const review = reviewVersionImpact(partial, { identity: '9007199254740993' })!;
    expect(review).toMatchObject({
      incomplete: true, observedConsumerCount: 0,
      declaredOnlyConsumerCount: 0, observedSuccessCount: 0,
    });
    const text = versionImpactReviewText('DATA_SERVICE:55', '7', partial, review, '2026-10-08T15:00:00Z');
    expect(text).toContain('PARTIAL / NEEDS_FOLLOW_UP');
    expect(text).toContain('非发布批准');
    expect(text).toContain('不能由未观察到推出不受影响');
    expect(reviewVersionImpact(null, { identity: '9007199254740993' })).toBeNull();
  });

  it('copies exact invocation and revision identity as an evidence-limited non-durable snapshot', () => {
    const review = reviewVersionImpact(impact, { identity: '9007199254740993' })!;
    const text = versionImpactReviewText('DATA_SERVICE:55', '7', impact, review, '2026-10-08T15:00:00Z');
    expect(text).toContain('Project: 7');
    expect(text).toContain('目标来源版本 ID: 9007199254740993');
    expect(text).toContain('DATA_SERVICE_INVOCATION:invocation:9007199254740999');
    expect(text).not.toContain('DATA_SERVICE_INVOCATION:invocation:9007199254741001');
    expect(text).toContain('WINDOW_ONLY / NOT_COMPLETE_HISTORY');
    expect(text).toContain('不持久化');
  });
  it('restores only the exact URL-selected revision, never silently selects a different version', () => {
    const versions = reviewableVersions(impact, { identity: '9007199254740995', displayVersion: 'r8' });
    expect(selectReviewableVersion(versions, '9007199254740993')?.identity).toBe('9007199254740993');
    expect(selectReviewableVersion(versions, '9007199254740995')?.identity).toBe('9007199254740995');
    expect(selectReviewableVersion(versions, null)?.identity).toBe('9007199254740995');
    expect(selectReviewableVersion(versions, '9007199254740997')).toBeUndefined();
    expect(selectReviewableVersion(versions, 'r8')).toBeUndefined();
  });

});
