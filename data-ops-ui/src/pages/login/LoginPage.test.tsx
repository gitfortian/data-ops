import { render, screen } from '@testing-library/react';
import LoginPage from './index';

jest.mock('@umijs/max', () => ({
  useIntl: () => ({
    formatMessage: ({ defaultMessage }: { defaultMessage?: string }) => defaultMessage,
  }),
}));
jest.mock('./LoginPanel', () => () => <div data-testid="login-panel" />);
jest.mock('./DataSculpture', () => () => <div data-testid="data-sculpture" />);

test('登录首页保留原品牌主标题，并展示新版产品定位副标题', () => {
  render(<LoginPage />);

  expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent('让数据，值得信任。');
  expect(screen.getByText('从数据接入到治理应用，让可信数据建设更简单。')).toBeInTheDocument();
  expect(screen.getByTestId('login-panel')).toBeInTheDocument();
});
