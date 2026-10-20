import { fireEvent, render, screen } from '@testing-library/react';
import type { ModelPublicationReadiness } from '@/services/modeling/api';
import DwdPublicationReview from './DwdPublicationReview';

const readiness: ModelPublicationReadiness = {
  modelId: 701, layerCode: 'DWD', structureSha256: 'a'.repeat(64), standardsVerdict: 'VALID',
  semanticRevision: 3, standardEvidenceSha256: 'b'.repeat(64), authorizedForPublication: false,
  authorizationStatus: 'PUBLISH_BLOCKED', findings: ['当前发布条件尚未满足'],
  columns: [{ columnName: 'order_id', physicalType: 'BIGINT', standardFieldId: 81, status: 'VALID' }],
  mapping: { status: 'FROZEN_SAVED_DRAFT', mappingRevisionId: 20, mappingSetId: 21, logicalModelId: 10,
    logicalVersionNo: 2, logicalSnapshotSha256: 'c'.repeat(64), physicalStructureSha256: 'a'.repeat(64),
    mappingContentSha256: 'd'.repeat(64), finding: '映射版本与当前保存结构一致' },
};

it('keeps a publication refusal visible even when standards and mapping are valid', () => {
  const standards = jest.fn();
  const logical = jest.fn();
  render(<DwdPublicationReview readiness={readiness} onOpenStandards={standards} onOpenLogical={logical} />);
  expect(screen.getByText('当前设计尚未满足发布条件')).toBeInTheDocument();
  expect(screen.getByText('当前发布条件尚未满足')).toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: '查看标准字段' }));
  expect(standards).toHaveBeenCalledTimes(1);
  fireEvent.click(screen.getByRole('button', { name: '查看逻辑依据' }));
  expect(logical).toHaveBeenCalledTimes(1);
  expect(screen.queryByText('PUBLISH_BLOCKED')).not.toBeInTheDocument();
  fireEvent.click(screen.getByText('发布核验详情'));
  expect(screen.getByText(/PUBLISH_BLOCKED/)).toBeInTheDocument();
  expect(screen.getByText('order_id')).toBeInTheDocument();
});

it('does not add a warning when the owner authorizes the selected design', () => {
  render(<DwdPublicationReview readiness={{ ...readiness, authorizedForPublication: true, findings: [] }}
    onOpenStandards={jest.fn()} onOpenLogical={jest.fn()} />);
  expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  expect(screen.getByText('映射版本已保存')).toBeInTheDocument();
});

it('keeps unavailable publication evidence distinct from valid evidence', () => {
  render(<DwdPublicationReview readiness={null} onOpenStandards={jest.fn()} onOpenLogical={jest.fn()} />);
  expect(screen.getByRole('alert')).toHaveTextContent('发布条件暂不可读取');
  expect(screen.queryByText('映射版本已保存')).not.toBeInTheDocument();
});
