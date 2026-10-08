import type { ConsumerImpact } from '@/services/consumption';
import {
  impactEvidenceGaps,
  impactEvidenceIncomplete,
  impactEvidenceWindowFacts,
} from './impact-evidence-coverage';
import { reviewVersionImpact, versionImpactReviewText } from './version-impact-review';

const impact: ConsumerImpact = {
  productKey: { productType: 'DATA_SERVICE', sourceIdentity: '9007199254740995' },
  subscriptionState: 'READY',
  usageState: 'READY',
  consumers: [],
  coverageNote: 'Only the bounded source audit window was examined',
  coverage: {
    requestedUsageLimit: 200,
    sourceRecordCount: 200,
    normalizedUsageCount: 120,
    sourceWindowLimitReached: true,
    normalizedUsageWindowLimitReached: false,
    normalizationGapCount: 0,
    sourceReadUnavailable: false,
  },
};

describe('precise evidence coverage and bounded-window impact', () => {
  it('does not misclassify a full readable source window as provider outage', () => {
    expect(impact.usageState).toBe('READY');
    expect(impactEvidenceIncomplete(impact)).toBe(true);
    expect(impactEvidenceGaps(impact)).toHaveLength(1);
    expect(impactEvidenceGaps(impact)[0]).toContain('已达到 200 条窗口上限');
    expect(impactEvidenceGaps(impact)[0]).not.toContain('读取失败');
    const review = reviewVersionImpact(impact, { identity: '9007199254740993' })!;
    expect(review.incomplete).toBe(true);
    expect(review.observedConsumerCount).toBe(0);
    const snapshot = versionImpactReviewText(
      'DATA_SERVICE:9007199254740995', '4', impact, review, '2026-10-08T18:00:00Z',
    );
    expect(snapshot).toContain('目标来源版本 ID: 9007199254740993');
    expect(snapshot).toContain('来源窗口达到上限：是');
    expect(snapshot).toContain('归一化窗口达到上限：否');
    expect(snapshot).toContain('PARTIAL / NEEDS_FOLLOW_UP');
  });

  it('separates two independent row budgets and normalization gaps', () => {
    const clipped: ConsumerImpact = {
      ...impact,
      coverage: {
        ...impact.coverage!,
        normalizedUsageCount: 200,
        normalizedUsageWindowLimitReached: true,
        normalizationGapCount: 3,
      },
      usageState: 'UNAVAILABLE',
    };
    const gaps = impactEvidenceGaps(clipped);
    expect(gaps).toHaveLength(4);
    expect(gaps.some((v) => v.includes('归一化有 3 条缺口'))).toBe(true);
    expect(gaps.some((v) => v.includes('来源审计查询已达到 200 条'))).toBe(true);
    expect(gaps.some((v) => v.includes('归一化成功使用证据已达到 200 条'))).toBe(true);
    expect(impactEvidenceWindowFacts(clipped)).toContain('归一化缺口条数：3');
  });

  it('exposes a failed source read without erasing the last returned normalized evidence count', () => {
    const failed: ConsumerImpact = {
      ...impact,
      usageState: 'UNAVAILABLE',
      coverage: {
        ...impact.coverage!,
        sourceRecordCount: 0,
        sourceWindowLimitReached: false,
        normalizedUsageCount: 5,
        sourceReadUnavailable: true,
      },
    };
    expect(impactEvidenceGaps(failed).some((s) => s.includes('来源审计读取失败'))).toBe(true);
    expect(impactEvidenceWindowFacts(failed)).toContain('本次归一化 Usage 返回条数：5');
    expect(impactEvidenceWindowFacts(failed)).toContain('来源审计读取失败：是');
  });

  it('keeps legacy payloads compatible and never implies historical completeness', () => {
    const legacy: ConsumerImpact = { ...impact, coverage: undefined };
    expect(impactEvidenceGaps(legacy)).toHaveLength(0);
    expect(impactEvidenceIncomplete(legacy)).toBe(false);
    expect(impactEvidenceWindowFacts(legacy)[0]).toContain('不得推断已覆盖全部历史');
  });
});
