import HttpUtils from '@/utils/HttpUtils';
import {
  getAdsApplicationContract,
  getAdsApplicationContractHistory,
  saveAdsApplicationContract,
  validateAdsApplicationContract,
  type AdsApplicationContractRequest,
  type AdsApplicationContractSaveRequest,
} from './adsApplication';

jest.mock('@/utils/HttpUtils', () => ({
  __esModule: true,
  default: {
    getData: jest.fn(),
    postData: jest.fn(),
    putData: jest.fn(),
  },
}));

const request: AdsApplicationContractRequest = {
  appCode: 'order_overview',
  appName: '订单经营日报',
  owner: '订单运营',
  purpose: '经营看板',
  consumerType: 'REPORT',
  sourceMode: 'DWS',
  upstreamModelId: 20,
  upstreamVersionId: 30,
  upstreamVersionNo: 3,
  upstreamChecksum: 'a'.repeat(64),
  outputFields: [{ sourceColumn: 'order_day', outputColumn: 'order_day', sensitivity: 'LOW' }],
  businessKeys: ['order_day'],
  filterExpression: '',
  sortExpression: '',
  refreshTarget: 'DAILY',
};

const saveRequest: AdsApplicationContractSaveRequest = {
  ...request,
  expectedRevision: 0,
  expectedTargetStructureFingerprint: 'physical-v1',
};

describe('ADS application contract API adapter', () => {
  beforeEach(() => jest.clearAllMocks());

  it('reads current contract and immutable history from the model-scoped routes', async () => {
    (HttpUtils.getData as jest.Mock).mockResolvedValueOnce(null).mockResolvedValueOnce([]);
    await expect(getAdsApplicationContract(10)).resolves.toBeNull();
    await expect(getAdsApplicationContractHistory(10)).resolves.toEqual([]);
    expect(HttpUtils.getData).toHaveBeenNthCalledWith(
      1, '/api/v1/modeling/models/10/ads-application',
    );
    expect(HttpUtils.getData).toHaveBeenNthCalledWith(
      2, '/api/v1/modeling/models/10/ads-application/history',
    );
  });

  it('sends the same explicit upstream pin to server validation and save', async () => {
    (HttpUtils.postData as jest.Mock).mockResolvedValue({ valid: true, blockers: [], warnings: [] });
    (HttpUtils.putData as jest.Mock).mockResolvedValue({ revision: 1, contract });
    await validateAdsApplicationContract(10, request);
    await saveAdsApplicationContract(10, saveRequest);
    expect(HttpUtils.postData).toHaveBeenCalledWith(
      '/api/v1/modeling/models/10/ads-application/validate', request,
    );
    expect(HttpUtils.putData).toHaveBeenCalledWith(
      '/api/v1/modeling/models/10/ads-application', saveRequest,
    );
    expect(request).toMatchObject({
      sourceMode: 'DWS',
      upstreamModelId: 20,
      upstreamVersionId: 30,
      upstreamVersionNo: 3,
      upstreamChecksum: 'a'.repeat(64),
    });
  });
});

const contract = {
  appCode: 'order_overview',
  appName: '订单经营日报',
  owner: '订单运营',
  purpose: '经营看板',
  consumerType: 'REPORT',
  sourceMode: 'DWS',
  upstream: { modelId: 20, versionId: 30, versionNo: 3, checksum: 'a'.repeat(64), layerCode: 'DWS' },
  outputFields: request.outputFields,
  businessKeys: ['order_day'],
  filterExpression: '',
  sortExpression: '',
  refreshTarget: 'DAILY',
  targetStructureFingerprint: 'physical-v1',
};
