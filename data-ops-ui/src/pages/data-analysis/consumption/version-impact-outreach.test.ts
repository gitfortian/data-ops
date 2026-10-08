import type { ConsumerImpact, DataProductView, KnownConsumer } from '@/services/consumption';
import { reviewVersionImpact } from './version-impact-review';
import { consumerVersionOutreachDraft } from './version-impact-outreach';

const product: DataProductView = {
  productKey: { productType: 'DATA_SERVICE', sourceIdentity: '55' },
  sourceRef: { domain: 'DATA_SERVICE', identity: '55' },
  name: 'Sales API',
  projectId: 7,
  activeVersion: { identity: '9007199254740995', displayVersion: 'r8' },
  lifecycle: 'PUBLISHED',
  availability: 'AVAILABLE',
  access: { providerState: 'UNAVAILABLE' },
  sections: [],
  contractPayload: {},
};

const consumer = (
  id: string,
  activeSubscriptionCount: number,
  observedVersions: KnownConsumer['observedVersions'],
): KnownConsumer => ({
  consumerRef: {
    consumerType: 'DATA_SERVICE',
    sourceDomain: 'DATA_SERVICE_CONSUMER',
    sourceIdentity: id,
    displayHint: '应用 ' + id,
  },
  declaredModes: activeSubscriptionCount ? ['API_INVOKE'] : [],
  observedModes: observedVersions?.length ? ['API_INVOKE'] : [],
  activeSubscriptionCount,
  successfulUsageCount: observedVersions?.reduce((n, row) => n + row.successfulUsageCount, 0) || 0,
  observedVersions,
  providerEvidenceRefs: observedVersions?.flatMap((row) => row.providerEvidenceRefs) || [],
});

const impact: ConsumerImpact = {
  productKey: { productType: 'DATA_SERVICE', sourceIdentity: '55' },
  subscriptionState: 'READY',
  usageState: 'READY',
  coverageNote: 'Known, reconciled current 200-row evidence window only',
  consumers: [
    consumer('9007199254740999', 1, [
      {
        sourceVersion: { identity: '9007199254740993', displayVersion: 'r8' },
        successfulUsageCount: 3,
        lastObservedAt: '2026-10-08T14:10:00',
        providerEvidenceRefs: ['DATA_SERVICE_INVOCATION:invocation:9007199254741011'],
      },
      {
        sourceVersion: { identity: '9007199254740995', displayVersion: 'r8' },
        successfulUsageCount: 2,
        providerEvidenceRefs: ['DATA_SERVICE_INVOCATION:invocation:9007199254741012'],
      },
    ]),
    consumer('9007199254741000', 1, []),
    consumer('9007199254741002', 0, [
      {
        sourceVersion: { identity: '9007199254740995', displayVersion: 'r8' },
        successfulUsageCount: 1,
        providerEvidenceRefs: ['DATA_SERVICE_INVOCATION:invocation:9007199254741013'],
      },
    ]),
  ],
};

const review = reviewVersionImpact(impact, { identity: '9007199254740993', displayVersion: 'r8' })!;

describe('version-scoped manual Consumer outreach drafting', () => {
  it('preserves the exact BIGINT product, Consumer, revision and invocation evidence', () => {
    const note = consumerVersionOutreachDraft(
      product, impact, review, review.rows[0], '拟修改响应字段，与使用方共同确认兼容性', '2026-10-08T16:00:00Z',
    );
    expect(note).toContain('ProductKey：DATA_SERVICE:55');
    expect(note).toContain('Project ID：7');
    expect(note).toContain('待评估来源版本 ID：9007199254740993');
    expect(note).toContain('DATA_SERVICE:DATA_SERVICE_CONSUMER:9007199254740999');
    expect(note).toContain('DATA_SERVICE_INVOCATION:invocation:9007199254741011');
    expect(note).not.toContain('9007199254741012');
    expect(note).not.toContain('9007199254741013');
    expect(note).toContain('本次来源窗口内观察到该 Consumer 对指定版本的 3 次成功消费');
    expect(note).toContain('拟修改响应字段');
  });

  it('does not claim the declared Consumer was observed to use this exact version', () => {
    const note = consumerVersionOutreachDraft(
      product, impact, review, review.rows[1], '', '2026-10-08T16:00:00Z',
    );
    expect(note).toContain('Consumer 稳定身份：DATA_SERVICE:DATA_SERVICE_CONSUMER:9007199254741000');
    expect(note).toContain('已发现该 Consumer 的有效声明依赖');
    expect(note).toContain('未观察到它成功使用指定版本');
    expect(note).toContain('拟变更内容：尚未明确，请来源 Owner 补充');
    expect(note).not.toContain('DATA_SERVICE_INVOCATION:invocation:9007199254741011');
    expect(note).not.toContain('已发送通知');
  });

  it('keeps provider denial visible and does not convert missing data into no-impact assurance', () => {
    const missing: ConsumerImpact = {
      ...impact, subscriptionState: 'FORBIDDEN', usageState: 'UNAVAILABLE',
      consumers: [consumer('20', 1, [])],
      coverageNote: 'One provider denied and one unavailable',
    };
    const partial = reviewVersionImpact(missing, { identity: '9007199254740993' })!;
    expect(partial.incomplete).toBe(true);
    const text = consumerVersionOutreachDraft(
      product, missing, partial, partial.rows[0], '拟更新', '2026-10-08T16:00:00Z',
    );
    expect(text).toContain('PARTIAL / NEEDS_FOLLOW_UP');
    expect(text).toContain('Subscription Provider：FORBIDDEN');
    expect(text).toContain('Usage Provider：UNAVAILABLE');
    expect(text).toContain('不保证枚举全部历史/外部使用方');
    expect(text).toContain('未经发送');
  });

  it('does not infer the proposed target release, contact method, approval or a change date', () => {
    const note = consumerVersionOutreachDraft(
      product, impact, review, review.rows[0], '待确认', '2026-10-08T16:00:00Z',
    );
    expect(note).toContain('变更时间/替代版本：待来源 Owner 与消费者确认');
    expect(note).toContain('ConsumerRef 不提供可信联系方式');
    expect(note).toContain('不代表消费者已阅读/回复/同意');
    expect(note).toContain('不是发布授权、审批或持久审计');
  });

  it('remains scoped to a single source version even when display revision labels coincide', () => {
    const newer = reviewVersionImpact(impact, { identity: '9007199254740995', displayVersion: 'r8' })!;
    const other = consumerVersionOutreachDraft(
      product, impact, newer, newer.rows[2], '另一个版本更新', '2026-10-08T16:00:00Z',
    );
    expect(other).toContain('待评估来源版本 ID：9007199254740995');
    expect(other).toContain('DATA_SERVICE_INVOCATION:invocation:9007199254741013');
    expect(other).not.toContain('DATA_SERVICE_INVOCATION:invocation:9007199254741011');
  });
});
