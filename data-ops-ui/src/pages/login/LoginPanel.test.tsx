import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import LoginPanel from './LoginPanel';
import { login } from '@/services/security/account';
import { resetAuthenticationFailure } from '@/utils/request';
import { history } from '@umijs/max';

const mockFetchUserInfo = jest.fn();
const mockSetInitialState = jest.fn();

jest.mock('@/services/security/account', () => ({ login: jest.fn() }));
jest.mock('@/utils/request', () => ({ resetAuthenticationFailure: jest.fn() }));
jest.mock('@/utils/notifyOnce', () => ({ notifyOnce: jest.fn() }));
jest.mock('@umijs/max', () => ({
  history: { replace: jest.fn() },
  useIntl: () => ({ formatMessage: ({ defaultMessage }: { defaultMessage?: string }) => defaultMessage }),
  useModel: () => ({ initialState: { fetchUserInfo: mockFetchUserInfo }, setInitialState: mockSetInitialState }),
}));

beforeEach(() => {
  jest.clearAllMocks();
  mockFetchUserInfo.mockReset();
  window.history.replaceState({}, '', '/login');
});

const submitCredentials = () => {
  fireEvent.change(screen.getByLabelText('用户名'), { target: { value: 'test-user' } });
  fireEvent.change(screen.getByLabelText('密码'), { target: { value: 'test-password' } });
  fireEvent.click(screen.getByRole('button', { name: /^登\s*录$/ }));
};

test('登录请求失败后在表单附近保留错误，修改输入后清除', async () => {
  jest.mocked(login).mockRejectedValueOnce(new Error('密码错误'));
  render(<LoginPanel />);
  fireEvent.change(screen.getByLabelText('用户名'), { target: { value: 'test-user' } });
  fireEvent.change(screen.getByLabelText('密码'), { target: { value: 'test-password' } });
  fireEvent.click(screen.getByRole('button', { name: /^登\s*录$/ }));
  expect(await screen.findByRole('alert')).toHaveTextContent('登录失败');
  fireEvent.change(screen.getByLabelText('用户名'), { target: { value: 'corrected-user' } });
  expect(screen.queryByRole('alert')).not.toBeInTheDocument();
});

test('HTTP 错误体中的账号锁定原因留在表单附近，无需真实消耗失败次数', async () => {
  jest.mocked(login).mockRejectedValueOnce({ data: { code: 2001, message: '账号已锁定，请稍后重试' } });
  render(<LoginPanel />);
  fireEvent.change(screen.getByLabelText('用户名'), { target: { value: 'test-user' } });
  fireEvent.change(screen.getByLabelText('密码'), { target: { value: 'test-password' } });
  fireEvent.click(screen.getByRole('button', { name: /^登\s*录$/ }));
  expect(await screen.findByRole('alert')).toHaveTextContent('账号已锁定，请稍后重试');
});

test('请求处理中禁用表单，重复提交只发送一次认证请求', async () => {
  let rejectLogin!: (error: Error) => void;
  jest.mocked(login).mockImplementationOnce(
    () =>
      new Promise((_, reject) => {
        rejectLogin = reject;
      }),
  );
  render(<LoginPanel />);
  submitCredentials();
  const submit = await screen.findByRole('button', { name: /正在登录/ });
  expect(submit).toBeDisabled();
  expect(screen.getByLabelText('用户名')).toBeDisabled();
  expect(screen.getByLabelText('密码')).toBeDisabled();
  fireEvent.click(submit);
  fireEvent.submit(submit.closest('form')!);
  await act(async () => {
    rejectLogin(new Error('test rejection'));
  });
  expect(await screen.findByRole('alert')).toBeInTheDocument();
  expect(login).toHaveBeenCalledTimes(1);
  expect(login).toHaveBeenCalledWith({ userName: 'test-user', pw: 'test-password' });
});

test('加载当前用户后才进入原本访问的安全路径', async () => {
  jest.mocked(login).mockResolvedValueOnce(undefined);
  const currentUser = { userName: 'test-user' };
  mockFetchUserInfo.mockResolvedValueOnce(currentUser);
  window.history.replaceState({}, '', '/login?returnTo=%2Fdata%2Fassets%3Fview%3Downed');
  render(<LoginPanel />);
  submitCredentials();
  await waitFor(() => expect(history.replace).toHaveBeenCalledWith('/data/assets?view=owned'));
  expect(resetAuthenticationFailure).toHaveBeenCalledTimes(1);
  expect(mockSetInitialState.mock.calls[0][0]({ previous: true })).toEqual({
    previous: true,
    currentUser,
    currentUserLoadError: false,
  });
});

test('认证后未能加载当前用户时保留表单提示并停止跳转', async () => {
  jest.mocked(login).mockResolvedValueOnce(undefined);
  mockFetchUserInfo.mockResolvedValueOnce(undefined);
  render(<LoginPanel />);
  submitCredentials();
  expect(await screen.findByRole('alert')).toHaveTextContent('未能加载当前用户信息');
  expect(history.replace).not.toHaveBeenCalled();
  expect(screen.getByRole('button', { name: /^登\s*录$/ })).toBeEnabled();
});
