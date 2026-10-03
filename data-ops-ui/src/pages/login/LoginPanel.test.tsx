import { fireEvent, render, screen } from '@testing-library/react';
import LoginPanel from './LoginPanel';
import { login } from '@/services/security/account';

jest.mock('@/services/security/account', () => ({ login: jest.fn() }));
jest.mock('@/utils/request', () => ({ resetAuthenticationFailure: jest.fn() }));
jest.mock('@/utils/notifyOnce', () => ({ notifyOnce: jest.fn() }));
jest.mock('@umijs/max', () => ({
  history: { replace: jest.fn() },
  useIntl: () => ({ formatMessage: ({ defaultMessage }: { defaultMessage?: string }) => defaultMessage }),
  useModel: () => ({ initialState: {}, setInitialState: jest.fn() }),
}));

test('登录请求失败后在表单附近保留错误，修改输入后清除', async () => {
  jest.mocked(login).mockRejectedValueOnce(new Error('密码错误'));
  render(<LoginPanel />);
  fireEvent.change(screen.getByLabelText('用户名'), { target: { value: 'test-user' } });
  fireEvent.change(screen.getByLabelText('密码'), { target: { value: 'test-password' } });
  fireEvent.click(screen.getByRole('button', { name: /登\s*录/ }));
  expect(await screen.findByRole('alert')).toHaveTextContent('登录失败');
  fireEvent.change(screen.getByLabelText('用户名'), { target: { value: 'corrected-user' } });
  expect(screen.queryByRole('alert')).not.toBeInTheDocument();
});

test('HTTP 错误体中的账号锁定原因留在表单附近，无需真实消耗失败次数', async () => {
  jest.mocked(login).mockRejectedValueOnce({ data: { code: 2001, message: '账号已锁定，请稍后重试' } });
  render(<LoginPanel />);
  fireEvent.change(screen.getByLabelText('用户名'), { target: { value: 'test-user' } });
  fireEvent.change(screen.getByLabelText('密码'), { target: { value: 'test-password' } });
  fireEvent.click(screen.getByRole('button', { name: /登\s*录/ }));
  expect(await screen.findByRole('alert')).toHaveTextContent('账号已锁定，请稍后重试');
});
