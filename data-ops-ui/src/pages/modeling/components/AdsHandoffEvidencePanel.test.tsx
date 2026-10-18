import React from 'react';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import AdsHandoffEvidencePanel from './AdsHandoffEvidencePanel';
import { getAdsApplicationEvidence } from '@/services/modeling/adsApplication';

jest.mock('@/services/modeling/adsApplication', () => ({
  getAdsApplicationEvidence: jest.fn(),
}));

const snapshot = {
  modelId: 10,
  contractRevision: 3,
  contractSha256: 'a'.repeat(64),
  checkedAt: '2026-10-18T06:00:00Z',
  domains: [
    { domainCode: 'METRIC', status: 'VERIFIED', sourceStatus: 'VERIFIED_ACTIVE',
      sourceDomain: 'Metric', summary: '完整发布身份匹配。', evidenceId: '72',
      version: 'V4', digest: 'c'.repeat(64) },
    { domainCode: 'DATADEV_RUNTIME', status: 'NOT_CONNECTED', sourceDomain: 'DataDev / Runtime',
      summary: '尚无稳定关联。' },
    { domainCode: 'QUALITY', status: 'NOT_CONNECTED', sourceDomain: 'Quality',
      summary: '质量证据尚未接入。' },
    { domainCode: 'DATASET', status: 'NOT_CONNECTED', sourceDomain: 'Dataset',
      summary: 'Dataset 引用尚未接入。' },
    { domainCode: 'DATA_SERVICE', status: 'NOT_CONNECTED', sourceDomain: 'Data Service',
      summary: '服务引用尚未接入。' },
    { domainCode: 'CONSUMPTION_AUTHORIZATION', status: 'NOT_CONNECTED',
      sourceDomain: 'Consumption / Access Policy', summary: '授权尚未接入。' },
  ],
};

beforeEach(() => {
  jest.clearAllMocks();
  (getAdsApplicationEvidence as jest.Mock).mockResolvedValue(snapshot);
});

it('shows only verified Metric evidence and explicit disconnected states for downstream domains', async () => {
  render(<AdsHandoffEvidencePanel modelId={10} />);
  expect(await screen.findByText('交接证据（只读）')).toBeInTheDocument();
  expect(await screen.findByText('已核验')).toBeInTheDocument();
  expect(screen.getAllByText('尚未接入').length).toBeGreaterThan(0);
  expect(screen.getByText('完整发布身份匹配。')).toBeInTheDocument();
  expect(screen.getByText('尚无稳定关联。')).toBeInTheDocument();
  expect(screen.getByText('证据 ID：72')).toBeInTheDocument();
  expect(screen.queryByText(/运行成功/)).not.toBeInTheDocument();
});

it('does not turn an API failure into empty evidence or success', async () => {
  (getAdsApplicationEvidence as jest.Mock).mockRejectedValue(new Error('network down'));
  render(<AdsHandoffEvidencePanel modelId={10} />);
  expect(await screen.findByText('交接证据读取失败')).toBeInTheDocument();
  expect(screen.getByText(/未将请求失败转换为空记录或成功状态/)).toBeInTheDocument();
  expect(screen.queryByText('尚未接入')).not.toBeInTheDocument();
});

it('refreshes the evidence endpoint on user request', async () => {
  render(<AdsHandoffEvidencePanel modelId={10} />);
  await screen.findByText('已核验');
  fireEvent.click(screen.getByRole('button', { name: '刷新证据' }));
  await waitFor(() => expect(getAdsApplicationEvidence).toHaveBeenCalledTimes(2));
});
