import React from 'react';
import { fireEvent, render, screen, waitFor, act } from '@testing-library/react';
import DatasetConsumptionProofPanel from './DatasetConsumptionProofPanel';
import { history } from '@umijs/max';
import { getDevelopmentRelease, listDevelopmentReleases } from '@/services/data-development';
import { listDatasetsForManagement } from '@/services/dataset';
import { getProduct, getConsumerImpact } from '@/services/consumption';

jest.mock('@umijs/max', () => ({ history: { push: jest.fn() } }));
jest.mock('@/services/data-development', () => ({
  getDevelopmentRelease: jest.fn(), listDevelopmentReleases: jest.fn(),
}));
jest.mock('@/services/dataset', () => ({ listDatasetsForManagement: jest.fn() }));
jest.mock('@/services/consumption', () => ({
  getProduct: jest.fn(), getConsumerImpact: jest.fn(),
  productKeyValue: (ref: { productType: string; sourceIdentity: string }) =>
    `${ref.productType}:${ref.sourceIdentity}`,
  consumptionProductPath: (type: string, id: string) =>
    `/data-analysis/consumption/${type}%3A${id}`,
}));
jest.mock('antd', () => {
  const actual = jest.requireActual('antd');
  return {
    ...actual,
    Select: ({ value, onChange, options, placeholder }: {
      value?: string; onChange: (value?: string) => void;
      options: { value: string; label: string }[]; placeholder: string;
    }) => <select aria-label={placeholder} value={value || ''}
      onChange={event => onChange(event.target.value || undefined)}>
      <option value="">未选择</option>
      {options.map(opt => <option key={opt.value} value={opt.value}>{opt.label}</option>)}
    </select>,
  };
});

const release = { assetId: '600', nodeId: '12', taskType: 'SQL',
  status: 'ONLINE', currentRevisionId: '700', currentRevisionNo: 2,
  latestRevisionNo: 2, taskName: '订单SQL', checksum: 'a'.repeat(64) };
const page = { records: [release], total: 1, pageNo: 1, pageSize: 100 };
const revision = { id: '700', nodeId: '12', revisionNo: 2,
  sourceDraftRevision: 1, checksum: 'b'.repeat(64),
  definition: { taskType: 'SQL', schemaVersion: 1, content: 'select 1', configJson: '{}' } };
const dataset = { id: '88', name: '订单', description: '', status: 'ONLINE',
  currentVersionId: '800', fields: [], currentVersion: {
    id: '800', datasetId: '88', versionNo: 1, sourceType: 'QUERY_REVISION',
    sourceTaskAssetId: '600', sourceTaskRevisionId: '700', sourceTaskRevisionNo: 2,
  } };
const product = { state: 'FOUND', product: {
  productKey: { productType: 'DATASET', sourceIdentity: '88' },
  projectId: 4, sourceRef: {}, name: '订单', lifecycle: 'PUBLISHED',
  availability: 'AVAILABLE', access: { providerState: 'READY' },
  sections: [], contractPayload: {}, activeVersion: { identity: '800' },
} };
const impact = { productKey: { productType: 'DATASET', sourceIdentity: '88' },
  usageState: 'READY', subscriptionState: 'READY', coverageNote: '最近200项',
  consumers: [{ consumerRef: { consumerType: 'USER', sourceDomain: 'IAM', sourceIdentity: 'a' },
    declaredModes: [], observedModes: ['QUERY'], activeSubscriptionCount: 0,
    successfulUsageCount: 3, providerEvidenceRefs: [],
    observedVersions: [{ sourceVersion: { identity: '800' },
      successfulUsageCount: 2, providerEvidenceRefs: [] }],
  }],
};
beforeEach(() => {
  jest.clearAllMocks();
  (listDevelopmentReleases as jest.Mock).mockResolvedValue(page);
  (getDevelopmentRelease as jest.Mock).mockResolvedValue({
    release, currentRevision: revision, revisions: [revision],
  });
  (listDatasetsForManagement as jest.Mock).mockResolvedValue([dataset]);
  (getProduct as jest.Mock).mockResolvedValue(product);
  (getConsumerImpact as jest.Mock).mockResolvedValue(impact);
});

it('does not read other domains before explicit C6 user action', () => {
  render(<DatasetConsumptionProofPanel nodeId="12" projectId={4} />);
  expect(listDevelopmentReleases).not.toHaveBeenCalled();
  expect(getConsumerImpact).not.toHaveBeenCalled();
});

it('checks real release and Dataset source IDs before reading exact consumption version', async () => {
  render(<DatasetConsumptionProofPanel nodeId="12" projectId={4} />);
  fireEvent.click(screen.getByRole('button', { name: '人工核验下游 Dataset' }));
  expect(await screen.findByText(/找到 1 个当前版本精确引用/)).toBeInTheDocument();
  expect(listDevelopmentReleases).toHaveBeenCalledWith({
    pageNo: 1, pageSize: 100, taskType: 'SQL',
  });
  expect(getDevelopmentRelease).toHaveBeenCalledWith('600');
  expect(getProduct).not.toHaveBeenCalled();
  fireEvent.change(screen.getByLabelText('选择精确来源匹配的 Dataset'),
    { target: { value: '88' } });
  expect(await screen.findByText('Consumption 观察到当前 Dataset 版本的成功消费回执')).toBeInTheDocument();
  expect(getProduct).toHaveBeenCalledWith('DATASET:88');
  expect(getConsumerImpact).toHaveBeenCalledWith('DATASET:88', '800');
  expect(screen.getByText(/成功消费观察计数 2/)).toBeInTheDocument();
  expect(screen.getByText(/不证明对应 Modeling 版本/)).toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: '进入 Consumption 原域核对' }));
  expect(history.push).toHaveBeenCalledWith('/data-analysis/consumption/DATASET%3A88');
});

it('refuses wrong revision identity and never loads Consumption without a verified Dataset', async () => {
  (getDevelopmentRelease as jest.Mock).mockResolvedValue({
    release, currentRevision: { ...revision, id: '999' }, revisions: [revision],
  });
  render(<DatasetConsumptionProofPanel nodeId="12" projectId={4} />);
  fireEvent.click(screen.getByRole('button', { name: '人工核验下游 Dataset' }));
  expect(await screen.findByText('下游来源证据无法核实')).toBeInTheDocument();
  expect(screen.queryByText(/找到 1 个当前版本精确引用/)).not.toBeInTheDocument();
  expect(getConsumerImpact).not.toHaveBeenCalled();
});

it('reports permissions failure as unavailable not as zero downstream products', async () => {
  (listDevelopmentReleases as jest.Mock).mockRejectedValue(new Error('当前项目无权限'));
  render(<DatasetConsumptionProofPanel nodeId="12" projectId={4} />);
  fireEvent.click(screen.getByRole('button', { name: '人工核验下游 Dataset' }));
  expect(await screen.findByText('当前项目无权限')).toBeInTheDocument();
  expect(screen.queryByText(/目录没有发现精确匹配/)).not.toBeInTheDocument();
});

it('discards late evidence from a previous project and SQL node', async () => {
  let finish: (value: unknown) => void = () => {};
  (listDevelopmentReleases as jest.Mock)
    .mockImplementationOnce(() => new Promise(resolve => { finish = resolve; }))
    .mockRejectedValueOnce(new Error('新项目发布中心不可访问'));
  const view = render(<DatasetConsumptionProofPanel nodeId="12" projectId={4} />);
  fireEvent.click(screen.getByRole('button', { name: '人工核验下游 Dataset' }));
  await waitFor(() => expect(listDevelopmentReleases).toHaveBeenCalledTimes(1));
  view.rerender(<DatasetConsumptionProofPanel nodeId="19" projectId={5} />);
  await waitFor(() => expect(listDevelopmentReleases).toHaveBeenCalledTimes(2));
  await act(async () => finish(page));
  expect(await screen.findByText('新项目发布中心不可访问')).toBeInTheDocument();
  expect(screen.queryByText(/找到 1 个当前版本精确引用/)).not.toBeInTheDocument();
});
