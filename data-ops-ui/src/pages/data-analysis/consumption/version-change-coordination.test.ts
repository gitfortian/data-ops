import type { ConsumerImpact, DataProductView, KnownConsumer } from '@/services/consumption';
import { reviewVersionImpact } from './version-impact-review';
import {
  buildVersionChangeCoordinationWorkpack,
  versionChangeCoordinationWorkpackText,
} from './version-change-coordination';

const product: DataProductView = {
  productKey: { productType: 'DATA_SERVICE', sourceIdentity: '9007199254740997' },
  sourceRef: { domain: 'DATA_SERVICE', identity: '9007199254740997' },
  name: 'Order API',
  projectId: 12,
  activeVersion: { identity: '9007199254740994', displayVersion: 'v5' },
  lifecycle: 'PUBLISHED',
  availability: 'AVAILABLE',
  access: { providerState: 'READY' },
  sections: [],
  contractPayload: {},
};

const consumer = (
  id: string,
  subscriptions: number,
  observedVersions: KnownConsumer['observedVersions'],
): KnownConsumer => ({
  consumerRef: {
    consumerType: 'JOB', sourceDomain: 'WORKFLOW',
    sourceIdentity: id, displayHint: 'Job ' + id,
  },
  declaredModes: subscriptions ? ['DOWNSTREAM'] : [],
  observedModes: observedVersions?.length ? ['API_INVOKE'] : [],
  activeSubscriptionCount: subscriptions,
  successfulUsageCount: observedVersions?.reduce((total, v) => total + v.successfulUsageCount, 0) || 0,
  providerEvidenceRefs: observedVersions?.flatMap((v) => v.providerEvidenceRefs) || [],
  observedVersions,
});

const impact: ConsumerImpact = {
  productKey: { productType: 'DATA_SERVICE', sourceIdentity: '9007199254740997' },
  subscriptionState: 'READY',
  usageState: 'READY',
  coverageNote: 'Only first 200 recent events, not historical completeness',
  consumers: [
    consumer('9007199254740998', 1, [
      {
        sourceVersion: { identity: '9007199254740993', displayVersion: 'v5' },
        successfulUsageCount: 4,
        lastObservedAt: '2026-10-08T17:00:00Z',
        providerEvidenceRefs: ['DATA_SERVICE_INVOCATION:invocation:9007199254741101'],
      },
      {
        sourceVersion: { identity: '9007199254740994', displayVersion: 'v5' },
        successfulUsageCount: 8,
        providerEvidenceRefs: ['DATA_SERVICE_INVOCATION:invocation:9007199254741102'],
      },
    ]),
    consumer('9007199254740999', 1, []),
    consumer('9007199254741000', 0, [
      {
        sourceVersion: { identity: '9007199254740994', displayVersion: 'v5' },
        successfulUsageCount: 2,
        providerEvidenceRefs: ['DATA_SERVICE_INVOCATION:invocation:9007199254741103'],
      },
    ]),
  ],
};

describe('human coordination workpack for source version changes', () => {
  it('turns selected-version observed use and unbound declarations into distinct human tasks', () => {
    const selected = reviewVersionImpact(impact, { identity: '9007199254740993', displayVersion: 'v5' })!;
    const workpack = buildVersionChangeCoordinationWorkpack(impact, selected);
    expect(workpack).toMatchObject({
      observedCount: 1, declaredOnlyCount: 1, evidenceGaps: [], incomplete: false,
    });
    expect(workpack.tasks.map((t) => t.consumerIdentity)).toEqual([
      'JOB:WORKFLOW:9007199254740998', 'JOB:WORKFLOW:9007199254740999',
    ]);
    expect(workpack.tasks[0].basis).toBe('OBSERVED_VERSION_USAGE');
    expect(workpack.tasks[0].successfulUsageCount).toBe(4);
    expect(workpack.tasks[1].basis).toBe('DECLARED_UNBOUND_DEPENDENCY');
    expect(workpack.tasks[1].successfulUsageCount).toBe(0);
  });

  it('exports a project and exact-source-version-scoped workpack with no evidence from other revisions', () => {
    const selected = reviewVersionImpact(impact, { identity: '9007199254740993', displayVersion: 'v5' })!;
    const text = versionChangeCoordinationWorkpackText(
      product, impact, selected, '拟增改字段，请核对兼容性', '2026-10-08T17:30:00Z',
    );
    expect(text).toContain('Project ID：12');
    expect(text).toContain('ProductKey：DATA_SERVICE:9007199254740997');
    expect(text).toContain('待评估来源版本 ID：9007199254740993');
    expect(text).toContain('JOB:WORKFLOW:9007199254740998');
    expect(text).toContain('DATA_SERVICE_INVOCATION:invocation:9007199254741101');
    expect(text).not.toContain('9007199254741102');
    expect(text).not.toContain('9007199254741103');
    expect(text).not.toContain('JOB:WORKFLOW:9007199254741000');
    expect(text).toContain('拟增改字段');
    expect(text).toContain('证据范围：WINDOW_ONLY / NOT_COMPLETE_HISTORY');
    expect(text).toContain('尚未送达任何 Consumer');
    expect(text).toContain('不持久化');
  });

  it('preserves forbidden and unavailable provider gaps, rather than inventing cleared impact', () => {
    const partial = {
      ...impact, subscriptionState: 'FORBIDDEN' as const, usageState: 'UNAVAILABLE' as const,
      coverageNote: 'Provider not readable', consumers: [consumer('17', 1, [])],
    };
    const selected = reviewVersionImpact(partial, { identity: '9007199254740993' })!;
    const workpack = buildVersionChangeCoordinationWorkpack(partial, selected);
    expect(workpack.incomplete).toBe(true);
    expect(workpack.evidenceGaps).toHaveLength(2);
    expect(workpack.tasks[0].basis).toBe('DECLARED_UNBOUND_DEPENDENCY');
    const text = versionChangeCoordinationWorkpackText(
      product, partial, selected, '', '2026-10-08T17:30:00Z',
    );
    expect(text).toContain('证据范围：PARTIAL / NEEDS_FOLLOW_UP');
    expect(text).toContain('Subscription 来源 FORBIDDEN');
    expect(text).toContain('Usage 来源 UNAVAILABLE');
    expect(text).toContain('拟变更内容：尚未明确');
  });

  it('does not claim no impacted consumers from an empty observed window', () => {
    const empty: ConsumerImpact = { ...impact, consumers: [] };
    const selected = reviewVersionImpact(empty, { identity: '9007199254740993' })!;
    const workpack = buildVersionChangeCoordinationWorkpack(empty, selected);
    expect(workpack.tasks).toHaveLength(0);
    expect(workpack.observedCount).toBe(0);
    const text = versionChangeCoordinationWorkpackText(
      product, empty, selected, '', '2026-10-08T17:30:00Z',
    );
    expect(text).toContain('不能据此推断无人受影响');
    expect(text).toContain('窗口之外的历史消费');
    expect(text).toContain('不是已联系/已完成的状态');
  });

  it('adds source management read-state to the human brief without claiming delivery or confirmation', () => {
    const selected = reviewVersionImpact(impact, { identity: '9007199254740993' })!;
    const text = versionChangeCoordinationWorkpackText(
      product, impact, selected, '核对字段兼容性', '2026-10-08T19:00:00Z',
      { state: 'FORBIDDEN', consumers: [] },
    );
    expect(text).toContain('当前来源配置（非消费/回复/批准）：来源配置未核实');
    expect(text).toContain('尚未送达任何 Consumer');
    expect(text).not.toContain('已正式批准');
  });

  it('supports Dataset identity without mixing it with Data Service consumer truth', () => {
    const dataset: DataProductView = {
      ...product, productKey: { productType: 'DATASET', sourceIdentity: '9007199254741801' },
      name: 'ODS Orders', activeVersion: { identity: '9007199254741802' },
    };
    const datasetImpact: ConsumerImpact = {
      ...impact, productKey: dataset.productKey, consumers: [consumer('42', 1, [])],
    };
    const selected = reviewVersionImpact(datasetImpact, { identity: '9007199254741802' })!;
    const text = versionChangeCoordinationWorkpackText(
      dataset, datasetImpact, selected, '更新字段', '2026-10-08T17:30:00Z',
    );
    expect(text).toContain('ProductKey：DATASET:9007199254741801');
    expect(text).toContain('待评估来源版本 ID：9007199254741802');
    expect(text).toContain('JOB:WORKFLOW:42');
    expect(text).not.toContain('9007199254741101');
  });
});
