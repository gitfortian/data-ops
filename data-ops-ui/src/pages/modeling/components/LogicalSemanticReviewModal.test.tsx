import { fireEvent, render, screen, within } from '@testing-library/react';
import type { LogicalVersionSemanticReview } from '@/services/modeling/logical';
import LogicalSemanticReviewModal from './LogicalSemanticReviewModal';

const report: LogicalVersionSemanticReview = {
  logicalModelId: 10, versionNo: 3, frozenStatus: 'DRAFT', draftStatus: 'DRAFT',
  frozenSnapshotSha256: 'a'.repeat(64), frozenDesignSha256: 'b'.repeat(64), currentDesignSha256: 'b'.repeat(64),
  entityCount: 2, attributeCount: 1, relationCount: 1, designComparison: 'MATCHES_CURRENT',
  reviewStatus: 'NEEDS_REVIEW', changes: [], blockers: ['订单金额尚未关联标准字段'], limitations: ['设计快照不可修改'],
  attributes: [{ entityId: 11, entityName: '订单', attributeId: 102, attributeName: '订单金额',
    standardStatus: 'PENDING_STANDARD', explanation: '请选择金额标准字段' }],
  relations: [{ relationId: 51, sourceEntity: '订单', targetEntity: '明细', cardinality: 'UNKNOWN',
    status: 'CARDINALITY_PENDING', explanation: '请确认订单与明细的关系基数' }],
};

it('keeps actual findings visible and locates the exact current attribute for repair', async () => {
  const repair = jest.fn();
  render(<LogicalSemanticReviewModal review={report} onClose={jest.fn()} onReload={jest.fn()} onRepair={repair} />);
  expect(await screen.findByText('订单金额尚未关联标准字段')).toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: '定位当前属性' }));
  expect(repair).toHaveBeenCalledWith({ kind: 'attribute', entityId: 11, attributeId: 102 });
  expect(screen.queryByText(report.frozenSnapshotSha256)).not.toBeInTheDocument();
  fireEvent.click(screen.getByText('核验详情'));
  expect(await screen.findByText(report.frozenSnapshotSha256)).toBeInTheDocument();
});

it('switches to relationship review without changing the declared cardinality', async () => {
  const repair = jest.fn();
  render(<LogicalSemanticReviewModal review={report} onClose={jest.fn()} onReload={jest.fn()} onRepair={repair} />);
  fireEvent.click(screen.getByRole('tab', { name: '实体关系 · 待确认 1' }));
  const tab = within(screen.getByRole('tabpanel', { name: '实体关系 · 待确认 1' }));
  expect(tab.getByText('基数待确认')).toBeInTheDocument();
  fireEvent.click(tab.getByRole('button', { name: '定位当前关系' }));
  expect(repair).toHaveBeenCalledWith({ kind: 'relation', relationId: 51 });
});

it('rechecks the displayed version and closes through the owner callbacks', async () => {
  const reload = jest.fn();
  const close = jest.fn();
  render(<LogicalSemanticReviewModal review={{ ...report, blockers: [], attributes: [], relations: [], reviewStatus: 'REVIEWABLE_ONLY' }}
    onClose={close} onReload={reload} onRepair={jest.fn()} />);
  expect(await screen.findByText('该版本与当前草稿的实体、属性和关系一致')).toBeInTheDocument();
  expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: '重新核对' }));
  expect(reload).toHaveBeenCalledWith(3);
  fireEvent.click(screen.getByRole('button', { name: /^关\s*闭$/ }));
  expect(close).toHaveBeenCalled();
});

it('allows reading the report without offering unauthorized repairs', () => {
  const repair = jest.fn();
  render(<LogicalSemanticReviewModal review={report} canEdit={false}
    onClose={jest.fn()} onReload={jest.fn()} onRepair={repair} />);
  expect(screen.getByRole('button', { name: '定位当前属性' })).toBeDisabled();
  expect(repair).not.toHaveBeenCalled();
  expect(screen.getByRole('button', { name: '重新核对' })).toBeEnabled();
});
