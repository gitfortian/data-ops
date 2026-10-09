import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import type { DataServiceConsumer } from '@/services/data-service';
import { listDataServiceConsumerKeys, getDataServiceConsumerIpAccess } from '@/services/data-service';
import ConsumerKeyPanel from './ConsumerKeyPanel';
import ConsumerIpAccessPanel from './ConsumerIpAccessPanel';

jest.mock('@/services/data-service', () => ({
  listDataServiceConsumerKeys: jest.fn(),
  getDataServiceConsumerIpAccess: jest.fn(),
  createDataServiceConsumerKey: jest.fn(),
  updateDataServiceConsumerKey: jest.fn(),
  setDataServiceConsumerKeyEnabled: jest.fn(),
  rotateDataServiceConsumerKey: jest.fn(),
  deleteDataServiceConsumerKey: jest.fn(),
  createDataServiceConsumerIpAccessRule: jest.fn(),
  updateDataServiceConsumerIpAccessRule: jest.fn(),
  setDataServiceConsumerIpAccessMode: jest.fn(),
  deleteDataServiceConsumerIpAccessRule: jest.fn(),
}));
jest.mock('@/components/ui', () => {
  const R = require('react') as typeof import('react');
  return {
    YakButton: ({ children, onClick, disabled, icon }: any) =>
      R.createElement('button', { onClick, disabled }, icon, children),
    YakEmpty: ({ title }: any) => R.createElement('div', null, title),
    YakTab: ({ items }: any) => R.createElement('div', null, items.map((x: any) =>
      R.createElement('span', { key: x.key }, x.label))),
  };
});
jest.mock('antd', () => {
  const R = require('react') as typeof import('react');
  const element = R.createElement;
  const Holder = ({ children }: any) => element('div', null, children);
  const Form = Object.assign(Holder, {
    useForm: () => [{ resetFields: jest.fn(), setFieldsValue: jest.fn(), submit: jest.fn() }],
    Item: Holder,
  });
  const Modal = Object.assign(
    ({ open, children, footer }: any) => open ? element('div', null, children, footer) : null,
    { confirm: jest.fn() },
  );
  return {
    Alert: ({ message, description, action }: any) =>
      element('div', { role: 'alert' }, message, description, action),
    Form, Modal, Input: Holder, InputNumber: Holder,
    DatePicker: Holder, Switch: Holder,
    Spin: () => element('div', { role: 'status' }, '正在读取'),
    message: { error: jest.fn(), success: jest.fn(), warning: jest.fn() },
  };
});

const consumer = (id: number): DataServiceConsumer => ({
  id, name: 'consumer-' + id, enabled: true, accessScope: 'ALL',
  apiIds: [], apiCount: 0, keyCount: 0, activeKeyCount: 0,
  ipAccessMode: 'NONE', ipRuleCount: 0, defaultRateLimitPerMinute: 60,
});
const key = (id: number, name: string) => ({
  id, name, keyPrefix: 'safe', enabled: true,
  rateLimitPerMinute: 60, expiresAt: null,
});
const defer = <T,>() => {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>(done => { resolve = done; });
  return { resolve, promise };
};

describe('Consumer Key and IP policy read truth', () => {
  const keys = jest.mocked(listDataServiceConsumerKeys);
  const policies = jest.mocked(getDataServiceConsumerIpAccess);
  beforeEach(() => {
    keys.mockReset().mockResolvedValue([key(1, 'current-key')]);
    policies.mockReset().mockResolvedValue({
      mode: 'DENYLIST', rules: [{
        id: 2, consumerId: 7, ruleType: 'DENYLIST',
        networkCidr: '203.0.113.8', enabled: true, expiresAt: null,
      }],
    });
  });

  it('does not render an absent-Key empty state or actions when the Key source denies access', async () => {
    keys.mockRejectedValueOnce({ response: { status: 403 } })
      .mockResolvedValueOnce([key(3, 'recovered-key')]);
    render(<ConsumerKeyPanel consumer={consumer(7)} onChanged={jest.fn()} />);
    await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent('无权读取当前调用方 API Key'));
    expect(screen.queryByText('暂无 API Key')).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '创建 Key' })).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '重试' }));
    await screen.findByText('recovered-key');
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('clears an old key on failed refresh and will not show previous action controls', async () => {
    keys.mockResolvedValueOnce([key(1, 'previous-key')])
      .mockRejectedValueOnce({ response: { status: 500 } })
      .mockResolvedValueOnce([key(2, 'new-key')]);
    render(<ConsumerKeyPanel consumer={consumer(7)} onChanged={jest.fn()} />);
    await screen.findByText('previous-key');
    const refresh = screen.getAllByRole('button')
      .find(button => button.querySelector('.lucide-refresh-cw'));
    expect(refresh).toBeDefined();
    fireEvent.click(refresh!);
    await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent('API Key 清单读取失败'));
    expect(screen.queryByText('previous-key')).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '重试' }));
    await screen.findByText('new-key');
  });

  it('rejects previous-consumer Key responses after a different Consumer selection', async () => {
    const old = defer<ReturnType<typeof key>[]>();
    keys.mockReset().mockReturnValueOnce(old.promise).mockResolvedValueOnce([key(2, 'new-consumer-key')]);
    const view = render(<ConsumerKeyPanel consumer={consumer(7)} onChanged={jest.fn()} />);
    view.rerender(<ConsumerKeyPanel consumer={consumer(8)} onChanged={jest.fn()} />);
    await screen.findByText('new-consumer-key');
    await act(async () => { old.resolve([key(1, 'old-consumer-key')]); await old.promise; });
    expect(screen.queryByText('old-consumer-key')).not.toBeInTheDocument();
  });

  it('does not represent a failed IP policy as NONE or empty blacklist', async () => {
    policies.mockRejectedValueOnce({ response: { status: 403 } })
      .mockResolvedValueOnce({ mode: 'DENYLIST', rules: [] });
    render(<ConsumerIpAccessPanel consumer={consumer(7)} onChanged={jest.fn()} />);
    await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent('无权读取来源策略'));
    expect(screen.queryByText('不限制')).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '添加规则' })).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '重试' }));
    await screen.findByText('不限制');
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('ignores a delayed old Consumer IP policy after choosing another Consumer', async () => {
    const old = defer<Awaited<ReturnType<typeof getDataServiceConsumerIpAccess>>>();
    policies.mockReset().mockReturnValueOnce(old.promise).mockResolvedValueOnce({
      mode: 'DENYLIST', rules: [{
        id: 3, consumerId: 8, ruleType: 'DENYLIST',
        networkCidr: '192.0.2.10', enabled: true, expiresAt: null,
      }],
    });
    const view = render(<ConsumerIpAccessPanel consumer={consumer(7)} onChanged={jest.fn()} />);
    view.rerender(<ConsumerIpAccessPanel consumer={consumer(8)} onChanged={jest.fn()} />);
    await screen.findByText('192.0.2.10');
    await act(async () => { old.resolve({ mode: 'ALLOWLIST', rules: [{
      id: 9, consumerId: 7, ruleType: 'ALLOWLIST',
      networkCidr: '10.0.0.0/8', enabled: true, expiresAt: null,
    }] }); await old.promise; });
    expect(screen.queryByText('10.0.0.0/8')).not.toBeInTheDocument();
    expect(screen.getByText('192.0.2.10')).toBeInTheDocument();
  });
});
