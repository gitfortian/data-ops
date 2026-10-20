import { fireEvent, render, screen } from '@testing-library/react';
import ModelingStudioHeader from './ModelingStudioHeader';
import ModelingStartGuide from './ModelingStartGuide';

const mockPush = jest.fn();
let mockCanReadSemantic = true;
jest.mock('@umijs/max', () => ({ history: { push: (...args: unknown[]) => mockPush(...args) } }));
jest.mock('@/hooks/usePermissionAccess', () => ({
  __esModule: true,
  default: () => ({ can: (code: string) => code !== 'semantic:read' || mockCanReadSemantic }),
}));

beforeEach(() => {
  mockPush.mockReset();
  mockCanReadSemantic = true;
});

it('renders actual modeling workspaces and links to the logical editor', () => {
  render(<ModelingStudioHeader area="catalog" title="数仓模型设计中心"
    description="真实建模" />);
  expect(screen.getByRole('heading', { name: '数仓模型设计中心' })).toBeInTheDocument();
  expect(screen.getByRole('navigation', { name: '数仓建模工作区导航' })).toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: /逻辑建模/ }));
  expect(mockPush).toHaveBeenCalledWith('/modeling/logical');
});

it('hides Semantic entry when the user lacks Semantic read permission', () => {
  mockCanReadSemantic = false;
  render(<ModelingStudioHeader area="logical" title="业务逻辑设计" description="只读权限" />);
  expect(screen.queryByRole('button', { name: /业务过程/ })).not.toBeInTheDocument();
  expect(screen.getByRole('button', { name: /物理建模/ })).toBeInTheDocument();
});

it('delegates navigation to the caller so the unsaved-change guard can intercept it', () => {
  const onNavigate = jest.fn();
  render(<ModelingStudioHeader area="physical" title="模型详情" description="草稿"
    onNavigate={onNavigate} />);
  fireEvent.click(screen.getByRole('button', { name: /分层主线/ }));
  expect(onNavigate).toHaveBeenCalledWith('/modeling/mainline');
  expect(mockPush).not.toHaveBeenCalled();
});

it('keeps creation unavailable until project scope and authoring permission are ready', () => {
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
