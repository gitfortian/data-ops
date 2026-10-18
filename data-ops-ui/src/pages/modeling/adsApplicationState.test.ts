import {
  adsContractSignature, adsSourceDrift, changeAdsSourceMode,
  draftFromAdsSaved, newAdsApplicationDraft,
} from './adsApplicationState';
import type { AdsApplicationContractSaved } from '@/services/modeling/adsApplication';

const sample: AdsApplicationContractSaved = {
  modelId: 10,
  revision: 2,
  contractSha256: 'a'.repeat(64),
  contractJson: '{}',
  updatedBy: 'alice',
  updatedAt: '2026-10-18',
  contract: {
    appCode: 'order_overview',
    appName: '订单日报',
    owner: 'alice',
    purpose: '看板',
    consumerType: 'REPORT',
    sourceMode: 'DWS',
    upstream: {
      modelId: 20, versionId: 30, versionNo: 3,
      checksum: 'b'.repeat(64), layerCode: 'DWS',
    },
    outputFields: [
      { sourceColumn: 'order_day', outputColumn: 'order_day', standardFieldId: 22 },
    ],
    businessKeys: ['order_day'],
    filterExpression: '',
    sortExpression: 'order_day DESC',
    metricVersionId: 19,
    refreshTarget: 'DAILY',
    targetStructureFingerprint: 'physical-v1',
  },
};

test('starts with DWS as the default and never assumes an upstream pin', () => {
  const draft = newAdsApplicationDraft('app_code', '应用');
  expect(draft.sourceMode).toBe('DWS');
  expect(draft.upstreamVersionId).toBe(0);
  expect(adsSourceDrift(draft, undefined)).toBe('NOT_PINNED');
});

test('roundtrips the exact immutable source version, keys, and Metric reference', () => {
  const draft = draftFromAdsSaved(sample);
  expect(draft).toMatchObject({
    upstreamModelId: 20,
    upstreamVersionId: 30,
    upstreamVersionNo: 3,
    upstreamChecksum: 'b'.repeat(64),
    businessKeys: ['order_day'],
    metricVersionId: 19,
  });
  expect(adsSourceDrift(draft, 30)).toBe('CURRENT');
  expect(adsSourceDrift(draft, 31)).toBe('STALE');
  expect(adsSourceDrift(draft, null)).toBe('STALE');
  expect(adsSourceDrift(draft, undefined)).toBe('UNKNOWN');
});

test('changing source layer invalidates mapping, keys, filtering and metric', () => {
  const from = draftFromAdsSaved(sample);
  const to = changeAdsSourceMode(from, 'DWD');
  expect(to).toMatchObject({
    sourceMode: 'DWD',
    upstreamModelId: 0,
    upstreamVersionId: 0,
    upstreamChecksum: '',
    businessKeys: [],
    outputFields: [],
    filterExpression: '',
    metricVersionId: null,
  });
  expect(from.upstreamVersionId).toBe(30);
});

test('historic revision is copied into an independent draft', () => {
  const first = draftFromAdsSaved(sample);
  const second = draftFromAdsSaved(sample);
  expect(adsContractSignature(first)).toBe(adsContractSignature(second));
  second.outputFields[0].sourceColumn = 'other_day';
  expect(first.outputFields[0].sourceColumn).toBe('order_day');
  expect(adsContractSignature(first)).not.toBe(adsContractSignature(second));
});
