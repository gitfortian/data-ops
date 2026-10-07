import React from 'react';
import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { message } from 'antd';
import { agentReportApi } from '@/services/agent';
import ReportsTab from './ReportsTab';

let mockRead = true;
let mockDelete = true;
jest.mock('@/hooks/usePermissionAccess', () => ({ usePermissionAccess: () => ({
  can: (code: string) => code === 'agent:report:read' ? mockRead : mockDelete,
}) }));
jest.mock('@/services/agent', () => ({ agentReportApi: { page: jest.fn(), detail: jest.fn(), remove: jest.fn() } }));
jest.mock('./MarkdownContent', () => ({ __esModule: true, default: ({ md }: { md: string }) => <div>{md}</div> }));
const page = agentReportApi.page as jest.Mock;
const detail = agentReportApi.detail as jest.Mock;
const remove = agentReportApi.remove as jest.Mock;
const reports = [{ id: 1, sessionId: 's1', title: '列表标题一' }, { id: 2, sessionId: 's2', title: '列表标题二' }];
const result = (rows = reports) => ({ bizData: rows, pagination: { total: rows.length } });
const fetched = (id: number) => ({ ...reports[id - 1], title: `详情标题${id}`, content: `原报告正文${id}` });
const row = (title: string) => within(screen.getByText(title).closest('tr')!);
const click = jest.fn();

beforeEach(() => {
  jest.clearAllMocks(); mockRead = true; mockDelete = true;
  page.mockReset(); detail.mockReset(); remove.mockReset(); click.mockReset();
  page.mockResolvedValue(result()); detail.mockImplementation(async (id) => fetched(id)); remove.mockResolvedValue(true);
  URL.createObjectURL = jest.fn(() => 'blob:report');
  URL.revokeObjectURL = jest.fn();
  jest.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (this: HTMLAnchorElement) {
    click({ download: this.download, href: this.href });
  });
  jest.spyOn(message, 'success').mockImplementation(() => undefined as never);
  jest.spyOn(message, 'error').mockImplementation(() => undefined as never);
  jest.spyOn(window, 'confirm').mockReturnValue(true);
});
afterEach(() => { jest.restoreAllMocks(); });
async function show() { await act(async () => { render(<ReportsTab />); }); }

it('exports the fresh detail title and content once and releases the download URL', async () => {
  let resolve!: (value: unknown) => void;
  detail.mockImplementation(() => new Promise((done) => { resolve = done; }));
  await show();
  const button = row('列表标题一').getByRole('button', { name: '导出 Markdown' });
  act(() => { fireEvent.click(button); fireEvent.click(button); });
  expect(detail).toHaveBeenCalledTimes(1);
  await act(async () => { resolve(fetched(1)); });
  expect(click).toHaveBeenCalledWith(expect.objectContaining({ download: '详情标题1.md' }));
  expect(URL.createObjectURL).toHaveBeenCalledTimes(1);
  const blob = (URL.createObjectURL as jest.Mock).mock.calls[0][0];
  expect(blob.type).toBe('text/markdown;charset=utf-8');
  expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:report');
});

it('does not export a mismatched detail and permits retry after a fixed error', async () => {
  await show(); detail.mockResolvedValueOnce(fetched(2));
  await act(async () => { fireEvent.click(row('列表标题一').getByRole('button', { name: '导出 HTML' })); });
  expect(URL.createObjectURL).not.toHaveBeenCalled();
  expect(message.error).toHaveBeenCalledWith('报告导出失败，请核对权限或重试。');
  await act(async () => { fireEvent.click(row('列表标题一').getByRole('button', { name: '导出 HTML' })); });
  expect(click).toHaveBeenCalledWith(expect.objectContaining({ download: '详情标题1.html' }));
});

it.each(['cancel', 'unmount', 'permission'])('ignores late export after %s', async (mode) => {
  let resolve!: (value: unknown) => void;
  detail.mockImplementation(() => new Promise((done) => { resolve = done; }));
  let view!: ReturnType<typeof render>;
  await act(async () => { view = render(<ReportsTab />); });
  fireEvent.click(row('列表标题一').getByRole('button', { name: '导出 HTML' }));
  if (mode === 'cancel') fireEvent.click(screen.getByRole('button', { name: '取消导出' }));
  else if (mode === 'unmount') view.unmount();
  else { mockRead = false; view.rerender(<ReportsTab />); }
  await act(async () => { resolve(fetched(1)); });
  expect(URL.createObjectURL).not.toHaveBeenCalled(); expect(click).not.toHaveBeenCalled();
  expect(message.success).not.toHaveBeenCalled();
});

it('releases URL even if browser download fails and shows a fixed error', async () => {
  await show(); click.mockImplementationOnce(() => { throw new Error('private-browser-error'); });
  await act(async () => { fireEvent.click(row('列表标题一').getByRole('button', { name: '导出 HTML' })); });
  expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:report');
  expect(message.error).toHaveBeenCalledWith('报告导出失败，请核对权限或重试。');
});

it('a cancelled download cannot clear or download a later export request', async () => {
  const waiting: Array<(value: unknown) => void> = [];
  detail.mockImplementation(() => new Promise((done) => { waiting.push(done); }));
  await show();
  fireEvent.click(row('列表标题一').getByRole('button', { name: '导出 HTML' }));
  fireEvent.click(screen.getByRole('button', { name: '取消导出' }));
  fireEvent.click(row('列表标题二').getByRole('button', { name: '导出 Markdown' }));
  await act(async () => { waiting[0](fetched(1)); });
  expect(URL.createObjectURL).not.toHaveBeenCalled();
  expect(row('列表标题一').getByRole('button', { name: '导出 HTML' })).toBeDisabled();
  await act(async () => { waiting[1](fetched(2)); });
  expect(click).toHaveBeenCalledTimes(1);
  expect(click).toHaveBeenCalledWith(expect.objectContaining({ download: '详情标题2.md' }));
});

it('isolates detail selection and closing from late responses', async () => {
  const waiting = new Map<number, (value: unknown) => void>();
  detail.mockImplementation((id) => new Promise((done) => { waiting.set(id, done); }));
  await show();
  fireEvent.click(row('列表标题一').getByRole('button', { name: /查\s*看/ }));
  await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'Close' })); });
  fireEvent.click(row('列表标题二').getByRole('button', { name: /查\s*看/ }));
  await act(async () => { waiting.get(2)!(fetched(2)); });
  await act(async () => { waiting.get(1)!(fetched(1)); });
  expect(screen.getByText('原报告正文2')).toBeInTheDocument();
  expect(screen.queryByText('原报告正文1')).not.toBeInTheDocument();
  await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'Close' })); });
  fireEvent.click(row('列表标题一').getByRole('button', { name: /查\s*看/ }));
  await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'Close' })); });
  await act(async () => { waiting.get(1)!(fetched(1)); });
  expect(screen.queryByText('原报告正文1')).not.toBeInTheDocument();
});

it('keeps a failed detail open for retry without leaking the previous body or exception', async () => {
  await show();
  await act(async () => { fireEvent.click(row('列表标题一').getByRole('button', { name: /查\s*看/ })); });
  await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'Close' })); });
  detail.mockRejectedValueOnce(new Error('private-server-error'));
  await act(async () => { fireEvent.click(row('列表标题二').getByRole('button', { name: /查\s*看/ })); });
  expect(screen.getByText('报告加载失败，请核对权限或重试。')).toBeInTheDocument();
  expect(screen.queryByText('原报告正文1')).not.toBeInTheDocument();
  expect(document.body.textContent).not.toContain('private-server-error');
  await act(async () => { fireEvent.click(screen.getByRole('button', { name: '重试报告' })); });
  expect(screen.getByText('原报告正文2')).toBeInTheDocument();
});

it('ignores a late error from a closed detail while the next report is visible', async () => {
  let rejectOld!: (error: Error) => void;
  detail.mockImplementationOnce(() => new Promise((_done, reject) => { rejectOld = reject; }));
  await show();
  fireEvent.click(row('列表标题一').getByRole('button', { name: /查\s*看/ }));
  await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'Close' })); });
  await act(async () => { fireEvent.click(row('列表标题二').getByRole('button', { name: /查\s*看/ })); });
  await act(async () => { rejectOld(new Error('private-old-error')); });
  expect(screen.getByText('原报告正文2')).toBeInTheDocument();
  expect(screen.queryByText(/报告加载失败/)).not.toBeInTheDocument();
});

it('clears displayed report content when read permission is removed', async () => {
  let view!: ReturnType<typeof render>;
  await act(async () => { view = render(<ReportsTab />); });
  await act(async () => { fireEvent.click(row('列表标题一').getByRole('button', { name: /查\s*看/ })); });
  expect(screen.getByText('原报告正文1')).toBeInTheDocument();
  mockRead = false; view.rerender(<ReportsTab />);
  expect(screen.queryByText('原报告正文1')).not.toBeInTheDocument();
  expect(screen.getByText('当前没有分析报告读取权限。')).toBeInTheDocument();
});

it('shows only the latest list query and provides a retry after read failure', async () => {
  let resolveOld!: (value: unknown) => void;
  page.mockImplementationOnce(() => new Promise((done) => { resolveOld = done; }));
  render(<ReportsTab />);
  const input = screen.getByPlaceholderText('按标题关键词搜索');
  fireEvent.change(input, { target: { value: '新查询' } });
  await act(async () => { fireEvent.keyDown(input, { key: 'Enter', code: 'Enter', keyCode: 13 }); fireEvent.keyUp(input, { key: 'Enter' }); });
  await act(async () => { resolveOld(result([{ ...reports[0], title: '旧查询结果' }])); });
  expect(screen.queryByText('旧查询结果')).not.toBeInTheDocument();
  page.mockRejectedValueOnce(new Error('private-list-error'));
  fireEvent.change(input, { target: { value: '不可用' } });
  await act(async () => { fireEvent.keyDown(input, { key: 'Enter', code: 'Enter', keyCode: 13 }); fireEvent.keyUp(input, { key: 'Enter' }); });
  expect(screen.getByText('报告列表加载失败，请重试。')).toBeInTheDocument();
  expect(screen.queryByText('列表标题一')).not.toBeInTheDocument();
  await act(async () => { fireEvent.click(screen.getByRole('button', { name: '重试列表' })); });
  expect(screen.getByText('列表标题一')).toBeInTheDocument();
});

it('does not read without report permission and disables delete without its own permission', async () => {
  mockRead = false;
  const view = render(<ReportsTab />);
  expect(page).not.toHaveBeenCalled();
  expect(screen.getByText('当前没有分析报告读取权限。')).toBeInTheDocument();
  mockRead = true; mockDelete = false;
  await act(async () => { view.rerender(<ReportsTab />); });
  expect(row('列表标题一').getByRole('button', { name: /删\s*除/ })).toBeDisabled();
});

it('deletion requires confirmation and refreshes the current search after a late response', async () => {
  await show(); (window.confirm as jest.Mock).mockReturnValueOnce(false);
  fireEvent.click(row('列表标题一').getByRole('button', { name: /删\s*除/ }));
  expect(remove).not.toHaveBeenCalled();
  let resolve!: () => void;
  remove.mockImplementation(() => new Promise<void>((done) => { resolve = done; }));
  fireEvent.click(row('列表标题一').getByRole('button', { name: /删\s*除/ }));
  const input = screen.getByPlaceholderText('按标题关键词搜索');
  fireEvent.change(input, { target: { value: '当前搜索' } });
  await act(async () => { fireEvent.keyDown(input, { key: 'Enter', code: 'Enter', keyCode: 13 }); fireEvent.keyUp(input, { key: 'Enter' }); });
  await act(async () => { resolve(); });
  expect(page).toHaveBeenLastCalledWith({ pageNo: 1, pageSize: 10, keyword: '当前搜索' });
  expect(remove).toHaveBeenCalledTimes(1);
});
