import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { message } from 'antd';
import React from 'react';
import { agentConfigApi } from '@/services/agent';
import ConfigPanel from './ConfigPanel';

jest.mock('@/services/agent', () => ({ agentConfigApi: { list: jest.fn(), update: jest.fn() } }));
const api = agentConfigApi as unknown as Record<string, jest.Mock>;

beforeEach(() => {
  jest.spyOn(message, 'success').mockImplementation(() => undefined as never);
  jest.spyOn(message, 'error').mockImplementation(() => undefined as never);
  api.list.mockResolvedValue([
    { key: 'memory', kind: 'bool', description: 'memory', dbValue: null, effectiveValue: 'false', valueSource: 'STARTUP', updateMode: 'HOT' },
    { key: 'timeout', kind: 'int', description: 'timeout', dbValue: '20', effectiveValue: '20', valueSource: 'DYNAMIC', updateMode: 'HOT' },
    { key: 'reserved', kind: 'int', description: 'reserved', dbValue: '40', effectiveValue: null, valueSource: 'NOT_CONNECTED', updateMode: 'NOT_CONNECTED' },
  ]);
  api.update.mockResolvedValue(true);
});
afterEach(() => { jest.restoreAllMocks(); jest.clearAllMocks(); });

it('uses the server startup value and disables unwired settings', async () => {
  render(<ConfigPanel />);
  await screen.findAllByText('memory');
  expect(screen.getByRole('switch').getAttribute('aria-checked')).toBe('false');
  const row = screen.getAllByText('reserved')[0].closest('tr')!;
  expect(within(row).getByText('尚未接入')).toBeTruthy();
  expect(within(row).getByRole('button', { name: /保\s*存/ })).toHaveProperty('disabled', true);
});

it('saving an untouched startup setting preserves the absence of a DB override', async () => {
  render(<ConfigPanel />);
  await screen.findAllByText('memory');
  const row = screen.getAllByText('memory')[0].closest('tr')!;
  fireEvent.click(within(row).getByRole('button', { name: /保\s*存/ }));
  await waitFor(() => expect(api.update).toHaveBeenCalledWith('memory', ''));
});

it('clearing an integer removes its override rather than resubmitting the old value', async () => {
  render(<ConfigPanel />);
  const input = await screen.findByRole('spinbutton');
  fireEvent.change(input, { target: { value: '' } });
  const row = screen.getAllByText('timeout')[0].closest('tr')!;
  fireEvent.click(within(row).getByRole('button', { name: /保\s*存/ }));
  await waitFor(() => expect(api.update).toHaveBeenCalledWith('timeout', ''));
});
