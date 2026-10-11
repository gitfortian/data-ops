import {
  findExactSqlRelease, inspectExactDatasetConsumption, linkCurrentDatasetsToRelease,
} from './datasetConsumptionProof';
import type {
  DevelopmentReleaseDetail, DevelopmentReleasePage, DevelopmentReleaseSummary,
} from '@/services/data-development';
import type { DatasetManagementItem } from '@/services/dataset';
import type { ConsumerImpact, ProductLookupResult } from '@/services/consumption';

const release: DevelopmentReleaseSummary = {
  assetId: '601', nodeId: '12', taskName: '订单SQL', taskType: 'SQL',
  status: 'ONLINE', currentRevisionId: '701', currentRevisionNo: 3,
  latestRevisionNo: 3, hasNewerRevision: false, checksum: 'a'.repeat(64),
};
const page: DevelopmentReleasePage = {
  records: [release], total: 1, pageNo: 1, pageSize: 100,
  onlineCount: 1, offlineCount: 0, disabledCount: 0,
};
const revision = {
  id: '701', nodeId: '12', revisionNo: 3, checksum: 'x'.repeat(64),
  sourceDraftRevision: 5,
  definition: { taskType: 'SQL' as const, schemaVersion: 1, content: 'select 1', configJson: '{}' },
};
const detail: DevelopmentReleaseDetail = {
  release, currentRevision: revision, revisions: [
    { ...revision, id: '700', revisionNo: 2 },
    revision,
  ],
};
const dataset: DatasetManagementItem = {
  id: '88', name: '订单数据', description: '', status: 'ONLINE', fields: [],
  currentVersionId: '801',
  currentVersion: {
    id: '801', datasetId: '88', versionNo: 4, sourceType: 'QUERY_REVISION',
    sourceTaskAssetId: '601', sourceTaskRevisionId: '701', sourceTaskRevisionNo: 3,
  },
};
const lookup: ProductLookupResult = {
  state: 'FOUND', product: {
    productKey: { productType: 'DATASET', sourceIdentity: '88' },
    projectId: 4, sourceRef: {}, name: '订单数据', lifecycle: 'PUBLISHED',
    availability: 'AVAILABLE', access: { providerState: 'READY' }, sections: [],
    contractPayload: {},
    activeVersion: { identity: '801', displayVersion: 'V4' },
  },
};
const impact: ConsumerImpact = {
  productKey: { productType: 'DATASET', sourceIdentity: '88' },
  subscriptionState: 'READY', usageState: 'READY', coverageNote: '最近200条范围',
  consumers: [{
    consumerRef: { consumerType: 'USER', sourceDomain: 'IAM', sourceIdentity: 'u1' },
    declaredModes: [], observedModes: ['QUERY'], activeSubscriptionCount: 0,
    successfulUsageCount: 7, providerEvidenceRefs: [],
    observedVersions: [
      { sourceVersion: { identity: '800' }, successfulUsageCount: 5,
        providerEvidenceRefs: [] },
      { sourceVersion: { identity: '801' }, successfulUsageCount: 2,
        providerEvidenceRefs: ['DATASET_QUERY_PERFORMANCE:query:q1'] },
    ],
  }],
};

describe('F-040 C6 source-owner exact TaskAsset to governed consumption', () => {
  it('does not convert a bounded non-match into no published SQL release', () => {
    expect(findExactSqlRelease('12', page)).toMatchObject({ state: 'FOUND' });
    expect(findExactSqlRelease('12', { ...page, records: [
      { ...release, nodeId: '123' } ], total: 250 })).toEqual({
      state: 'NOT_FOUND_IN_WINDOW', examined: 1, total: 250, complete: false,
    });
    expect(findExactSqlRelease('12', { ...page, records: [release, release], total: 2 }))
      .toMatchObject({ state: 'INVALID' });
    expect(findExactSqlRelease('12', { ...page, pageNo: 2 }))
      .toMatchObject({ state: 'INVALID' });
  });
  it('links only exact TaskAsset AND historical revision ID+number to the current Dataset version', () => {
    const other = { ...dataset, id: '89', currentVersionId: '802',
      currentVersion: { ...dataset.currentVersion!, id: '802', datasetId: '89',
        sourceTaskRevisionId: '700', sourceTaskRevisionNo: 2 },
    };
    const wrong = { ...dataset, id: '90', currentVersionId: '803',
      currentVersion: { ...dataset.currentVersion!, id: '803', datasetId: '90',
        sourceTaskRevisionId: '701', sourceTaskRevisionNo: 2 },
    };
    const byName = { ...dataset, id: '91', currentVersionId: '804',
      currentVersion: { ...dataset.currentVersion!, id: '804', datasetId: '91',
        sourceTaskAssetId: '602' },
    };
    const res = linkCurrentDatasetsToRelease(release, detail, [dataset, other, wrong, byName]);
    expect(res.state).toBe('MATCHED');
    if (res.state === 'MATCHED') expect(res.datasets.map(d => d.id)).toEqual(['88','89']);
  });
  it('refuses owner drift, unknown release revision and duplicate rev identity', () => {
    expect(linkCurrentDatasetsToRelease(release, {
      ...detail, release: { ...release, assetId: '999' },
    }, [dataset]).state).toBe('INVALID_RELEASE');
    expect(linkCurrentDatasetsToRelease(release, {
      ...detail, revisions: [{ ...revision, nodeId: '99' }],
    }, [dataset]).state).toBe('INVALID_RELEASE');
    expect(linkCurrentDatasetsToRelease(release, {
      ...detail, revisions: [revision, revision],
    }, [dataset]).state).toBe('INVALID_RELEASE');
  });
  it('counts ONLY successful Usage receipts from the precise current Dataset version', () => {
    expect(inspectExactDatasetConsumption(dataset, 4, lookup, impact))
      .toMatchObject({ state: 'OBSERVED', successfulUsage: 2, activeVersionId: '801' });
    const notObserved = inspectExactDatasetConsumption(dataset, 4, lookup, {
      ...impact, consumers: [{ ...impact.consumers[0], observedVersions: [
        { sourceVersion: { identity: '800' }, successfulUsageCount: 50, providerEvidenceRefs: [] },
      ] },
    });
    expect(notObserved).toMatchObject({ state: 'NOT_OBSERVED', successfulUsage: 0 });
  });
  it('never allows wrong project, frozen Dataset version, product or access outage', () => {
    expect(inspectExactDatasetConsumption(dataset, 5, lookup, impact).state).toBe('INVALID_OWNER');
    expect(inspectExactDatasetConsumption(dataset, 4, {
      ...lookup, product: { ...lookup.product!, activeVersion: { identity: '800' } },
    }, impact).state).toBe('INVALID_OWNER');
    expect(inspectExactDatasetConsumption(dataset, 4, lookup, {
      ...impact, productKey: { productType: 'DATASET', sourceIdentity: '999' },
    }).state).toBe('INVALID_OWNER');
    expect(inspectExactDatasetConsumption(dataset, 4, lookup, {
      ...impact, usageState: 'UNAVAILABLE',
    }).state).toBe('UNAVAILABLE');
    expect(inspectExactDatasetConsumption(dataset, 4, lookup, {
      ...impact, coverage: {
        requestedUsageLimit: 200, sourceRecordCount: 3, normalizedUsageCount: 0,
        sourceWindowLimitReached: false, normalizedUsageWindowLimitReached: false,
        normalizationGapCount: 0, sourceReadUnavailable: true,
      },
    }).state).toBe('UNAVAILABLE');
  });
});
