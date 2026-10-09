import React from 'react';
import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import ModelStructureReviewPanel from './ModelStructureReviewPanel';
import { listModelingVersions } from '@/services/modeling/api';
import { prepareModelStructureReview, type ModelStructureReviewContext } from '@/services/modeling/structureReview';

let mockProjectId = 42;
let mockAllowed = true;
jest.mock('@/contexts/SecurityProjectContext', () => ({ useSecurityProject: () => ({ currentProject: { id: mockProjectId } }) }));
jest.mock('@/hooks/usePermissionAccess', () => ({ usePermissionAccess: () => ({ canAll: () => mockAllowed }) }));
jest.mock('@/services/modeling/api', () => ({ listModelingVersions: jest.fn() }));
jest.mock('@/services/modeling/structureReview', () => ({ ...jest.requireActual('@/services/modeling/structureReview'), prepareModelStructureReview: jest.fn() }));
jest.mock('antd', () => {
  const actual = jest.requireActual('antd');
  return { ...actual, Select: ({ options, value, onChange, ...props }: { options: Array<{ value: number; label: string }>; value?: number; onChange: (value: number) => void }) =>
    <select aria-label="结构比较基准版本" value={value ?? ''} onChange={(event) => onChange(Number(event.target.value))}><option value="">选择版本</option>{options.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}</select>,
  };
});
const result = (): ModelStructureReviewContext => ({ projectId: '42', modelId: '7', baselineVersionNo: 3, baselineVersionId: '11', definition: 'a'.repeat(64),
  baselineColumnCount: 1, savedColumnCount: 2, changes: [{ area: 'COLUMN', name: 'amount', before: 'INT', after: 'BIGINT' }],
  mappingChecks: [{ targetColumn: 'amount', mappingPresent: true, transformPresent: false, reasons: ['CHANGED_TARGET_REVIEW'] }], coverageGaps: ['NO_HISTORICAL_MAPPING_SNAPSHOT'] });
beforeEach(() => {
  jest.clearAllMocks(); mockProjectId = 42; mockAllowed = true;
  (listModelingVersions as jest.Mock).mockResolvedValue([{ versionNo: 3 }, { versionNo: 4 }]);
  (prepareModelStructureReview as jest.Mock).mockResolvedValue(result());
});
const panel = (modelId = '7', search = '?tab=version&reviewVersion=3') => <ModelStructureReviewPanel modelId={modelId} search={search} />;
async function prepare() {
  await waitFor(() => expect(screen.getByRole('button', { name: '准备结构比较' })).toBeEnabled());
  fireEvent.click(screen.getByRole('button', { name: '准备结构比较' }));
}

it('prepares only after clicking and opens a frozen task without sending inference', async () => {
  render(panel()); await screen.findByRole('option', { name: '发布版本 V3' });
  expect(prepareModelStructureReview).not.toHaveBeenCalled();
  await prepare();
  expect(await screen.findByRole('link', { name: 'AI 结构变更说明' })).toHaveAttribute('href', `/ai-agent?purpose=MODEL_STRUCTURE_REVIEW&reviewModelId=7&reviewBaselineVersionNo=3&reviewDefinition=${'a'.repeat(64)}`);
  expect(prepareModelStructureReview).toHaveBeenCalledWith('7', 3);
  expect(screen.getByRole('link', { name: '打开来源映射核对' })).toHaveAttribute('href', '/modeling/models/7/mapping');
  expect(screen.getByText(/没有历史映射快照/)).toBeInTheDocument();
});
it.each(['project', 'model', 'version', 'permission'])('ignores a late preparation after changing %s', async (kind) => {
  let resolve: (value: ModelStructureReviewContext) => void = () => {};
  (prepareModelStructureReview as jest.Mock).mockImplementation(() => new Promise<ModelStructureReviewContext>((done) => { resolve = done; }));
  const view = render(panel()); await prepare();
  if (kind === 'project') mockProjectId = 99;
  if (kind === 'permission') mockAllowed = false;
  view.rerender(panel(kind === 'model' ? '8' : '7', kind === 'version' ? '?reviewVersion=4' : '?tab=version&reviewVersion=3'));
  await act(async () => resolve(result()));
  expect(screen.queryByRole('link', { name: 'AI 结构变更说明' })).not.toBeInTheDocument();
});
it('clears a prepared result immediately when selecting a different baseline', async () => {
  render(panel()); await prepare(); await screen.findByRole('link', { name: 'AI 结构变更说明' });
  fireEvent.change(screen.getByLabelText('结构比较基准版本'), { target: { value: '4' } });
  expect(screen.queryByRole('link', { name: 'AI 结构变更说明' })).not.toBeInTheDocument();
  await prepare();
  expect(await screen.findByText(/结构比较不可用、已变化/)).toBeInTheDocument();
});
it('invalidates the existing entry immediately when preparing again and keeps it absent on failure', async () => {
  render(panel()); await prepare(); await screen.findByRole('link', { name: 'AI 结构变更说明' });
  (prepareModelStructureReview as jest.Mock).mockRejectedValue(new Error('unavailable'));
  await prepare(); expect(screen.queryByRole('link', { name: 'AI 结构变更说明' })).not.toBeInTheDocument();
  expect(await screen.findByText(/结构比较不可用、已变化/)).toBeInTheDocument();
});
it.each(['?reviewVersion=99', '?reviewVersion=03', '?reviewVersion=3&reviewVersion=4'])('never substitutes a missing or malformed exact baseline %s', async (search) => {
  render(panel('7', search)); await screen.findByRole('option', { name: '发布版本 V3' });
  expect(screen.getByRole('button', { name: '准备结构比较' })).toBeDisabled();
  expect(screen.getByText(/指定基准版本尚未确认或已失效/)).toBeInTheDocument(); expect(prepareModelStructureReview).not.toHaveBeenCalled();
});
it('hides stale version lists when a project changes and ignores their late arrival', async () => {
  let resolve: (value: { versionNo: number }[]) => void = () => {};
  (listModelingVersions as jest.Mock).mockImplementationOnce(() => new Promise((done) => { resolve = done; })).mockResolvedValue([]);
  const view = render(panel()); mockProjectId = 99; view.rerender(panel());
  await act(async () => resolve([{ versionNo: 3 }]));
  expect(screen.queryByRole('option', { name: '发布版本 V3' })).not.toBeInTheDocument();
  expect(screen.getByRole('button', { name: '准备结构比较' })).toBeDisabled();
});
it('rejects a response from the wrong project and does not claim no changes', async () => {
  (prepareModelStructureReview as jest.Mock).mockResolvedValue({ ...result(), projectId: '99' });
  render(panel()); await prepare(); expect(await screen.findByText(/结构比较不可用、已变化/)).toBeInTheDocument();
  expect(screen.queryByRole('link', { name: 'AI 结构变更说明' })).not.toBeInTheDocument();
});
it('keeps uncovered checks explicit when the supported comparison has no items', async () => {
  (prepareModelStructureReview as jest.Mock).mockResolvedValue({ ...result(), changes: [], mappingChecks: [] });
  render(panel()); await prepare(); expect(await screen.findByText(/白名单内未发现/)).toBeInTheDocument();
  expect(screen.queryByRole('link', { name: 'AI 结构变更说明' })).not.toBeInTheDocument();
});
it('does not read or expose the entry without both permissions', () => {
  mockAllowed = false; render(panel()); expect(listModelingVersions).not.toHaveBeenCalled(); expect(prepareModelStructureReview).not.toHaveBeenCalled();
  expect(screen.queryByText('模型结构变更与映射检查')).not.toBeInTheDocument();
});
