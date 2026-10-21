import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import type { ComponentProps } from 'react';
import ModelCreateWizardDrawer from './ModelCreateWizardDrawer';
import type { SemanticDomainNode, SemanticProcessRecord, SemanticLayerRecord } from '@/services/semantic/types';

jest.mock('./ModelCreationLogicalBasis', () => () => <div>只读业务逻辑依据</div>);
const tree: SemanticDomainNode[] = [{
  id: 1, code: 'trade', name: '交易域', sortOrder: 1, children: [
    { id: 2, code: 'orders', name: '订单子域', sortOrder: 1, children: [] },
  ],
}];
const processes: SemanticProcessRecord[] = [
  { id: 71, code: 'order_create', name: '下单', sortOrder: 1, domainId: 2, grain: '每笔订单', bizType: 'FACT' },
  { id: 80, code: 'bill', name: '开票', sortOrder: 2, domainId: 9, bizType: 'FACT' },
];
const layers = [
  { id: 11, code: 'ODS', name: '贴源层' },
  { id: 12, code: 'DWD', name: '明细层' },
] as SemanticLayerRecord[];
const setup = (extra: Partial<ComponentProps<typeof ModelCreateWizardDrawer>> = {}) => {
  const submit = jest.fn();
  const close = jest.fn();
  const view = render(<ModelCreateWizardDrawer open loading={false} layers={layers}
    domainTree={tree} processes={processes} initialProcessId={71}
    onClose={close} onSubmit={submit} {...extra} />);
  return { submit, close, ...view };
};

it('restores verified nested business process and its parent root domain on a scoped new-model route', async () => {
  const { submit } = setup();
  expect(await screen.findByText('下单 · 每笔订单')).toBeInTheDocument();
  expect(screen.getByLabelText('业务过程')).toBeDisabled();
  expect(screen.getByText('业务域')).toBeInTheDocument();
  expect(screen.getByText('创建物理模型草稿')).toBeInTheDocument();
  fireEvent.click(screen.getByText('创建物理草稿'));
  await waitFor(() => expect(submit).not.toHaveBeenCalled());
});

it('does not use a URL process ID as authorization if the Semantic owner catalog cannot resolve it', async () => {
  const { submit } = setup({ initialProcessId: 999 });
  expect(await screen.findByText('正在核验进入时的业务过程与所属业务域')).toBeInTheDocument();
  expect(screen.getByText('创建物理草稿').closest('button')).toBeDisabled();
  expect(submit).not.toHaveBeenCalled();
});

it('does not pretend that a partially populated Semantic domain tree is a valid new model scope', async () => {
  const { submit } = setup({ domainTree: [] });
  expect(await screen.findByText('正在核验进入时的业务过程与所属业务域')).toBeInTheDocument();
  expect(screen.getByText('创建物理草稿').closest('button')).toBeDisabled();
  expect(submit).not.toHaveBeenCalled();
});
