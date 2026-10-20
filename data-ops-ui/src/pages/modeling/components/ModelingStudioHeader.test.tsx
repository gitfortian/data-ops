import { fireEvent, render, screen } from '@testing-library/react';
import { Button } from 'antd';
import ModelingStudioHeader from './ModelingStudioHeader';
import ModelingStartGuide from './ModelingStartGuide';

it('uses the existing SiteLayout instead of adding a third navigation rail', () => {
  render(<ModelingStudioHeader area="catalog" title="模型总览" description="真实建模" />);
  expect(screen.getByRole('heading', { name: '模型总览' })).toBeInTheDocument();
  expect(screen.queryByRole('navigation', { name: /数仓建模工作区导航/ })).not.toBeInTheDocument();
  expect(screen.queryByText('模型目录')).not.toBeInTheDocument();
});

it('preserves contextual actions without creating secondary route buttons', () => {
  const onCreate = jest.fn();
  render(<ModelingStudioHeader area="physical" title="物理模型" description="模型设计"
    actions={<Button onClick={onCreate}>新建物理草稿</Button>} />);
  fireEvent.click(screen.getByRole('button', { name: '新建物理草稿' }));
  expect(onCreate).toHaveBeenCalledTimes(1);
  expect(screen.queryByRole('button', { name: /分层主线/ })).not.toBeInTheDocument();
});

it('retains authorized task entry and disables physical creation without scope', () => {
  const onCreate = jest.fn();
  const onSource = jest.fn();
  const onLogical = jest.fn();
  render(<ModelingStartGuide onBusiness={jest.fn()} onLogical={onLogical}
    onSource={onSource} onCreate={onCreate} canCreate={false} loading={false} />);
  expect(screen.getByRole('button', { name: '创建物理草稿' })).toBeDisabled();
  fireEvent.click(screen.getByRole('button', { name: /设计业务逻辑模型/ }));
  expect(onLogical).toHaveBeenCalledTimes(1);
  fireEvent.click(screen.getByRole('button', { name: '从来源表建 ODS' }));
  expect(onSource).toHaveBeenCalledTimes(1);
  expect(onCreate).not.toHaveBeenCalled();
});
