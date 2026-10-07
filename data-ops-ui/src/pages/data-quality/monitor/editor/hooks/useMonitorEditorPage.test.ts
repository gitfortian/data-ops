import { act, renderHook, waitFor } from '@testing-library/react';
import { message } from 'antd';
import { history } from '@umijs/max';
import { getQualityEditorSnapshot, updateQualityMonitor, validateQualitySuggestions } from '@/services/data-quality';
import { useMonitorEditorPage } from './useMonitorEditorPage';
import { ruleDefaults } from '../model';
import type { SaveRulePayload, TemplateView } from '@/services/data-quality';

const mockValues = { name: '订单监控', dataSourceId: 1, dataSourceName: 'DB', tableName: 'orders', owner: 'owner', enabled: true };
const mockForm = { setFieldsValue: jest.fn(), validateFields: jest.fn() };
jest.mock('antd', () => ({
  Form: { useForm: () => [mockForm], useWatch: () => undefined },
  message: { error: jest.fn(), success: jest.fn(), warning: jest.fn() },
}));
jest.mock('@umijs/max', () => ({ history: { push: jest.fn() } }), { virtual: true });
jest.mock('@/services/data-source', () => ({
  listAllDataSources: jest.fn().mockResolvedValue({ bizData: [{ id: 1, name: 'DB' }] }),
  listDataSourceColumns: jest.fn().mockResolvedValue([]),
}));
jest.mock('@/services/data-quality', () => ({
  getQualityEditorSnapshot: jest.fn(), listQualityTemplates: jest.fn(),
  createQualityMonitor: jest.fn(), updateQualityMonitor: jest.fn(), validateQualitySuggestions: jest.fn(),
}));
jest.mock('../RuleEditor', () => ({ validateRules: jest.fn() }));

const template: TemplateView = { id: 2, name: '行数', code: 'rows', ruleType: 'TABLE_ROW_COUNT', scope: 'TABLE',
  dimension: 'COMPLETENESS', parameterSchema: '{}', builtin: true, enabled: true, ruleCount: 0, sortOrder: 0 };
const candidate: SaveRulePayload = { templateId: 2, name: '至少一行', operator: 'GT', threshold: 0, enabled: false };
const hash = 'a'.repeat(64);
const query = new URLSearchParams();
const options = { monitorId: '7', query };
const validate = validateQualitySuggestions as jest.Mock;
const update = updateQualityMonitor as jest.Mock;

function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (reason: Error) => void;
  const promise = new Promise<T>((yes, no) => { resolve = yes; reject = no; });
  return { promise, resolve, reject };
}

beforeEach(() => {
  jest.clearAllMocks();
  mockForm.validateFields.mockResolvedValue(mockValues);
  const api = jest.requireMock('@/services/data-quality');
  api.listQualityTemplates.mockResolvedValue({ records: [template] });
  (getQualityEditorSnapshot as jest.Mock).mockResolvedValue({ definition: hash,
    monitor: { ...mockValues, id: 7, rules: [] },
    settings: { runMode: 'MANUAL', ruleFailureAction: 'CONTINUE', notifyEnabled: false, notifyChannel: 'MESSAGE', alertLevel: 'WARNING' } });
  validate.mockResolvedValue([candidate]);
  update.mockResolvedValue({ id: 7 });
});

async function editor() {
  const view = renderHook((props) => useMonitorEditorPage(props), { initialProps: options });
  await waitFor(() => expect(view.result.current.loading).toBe(false));
  return view;
}

test('adoption appends a disabled rule only; explicit save retains source definition and settings', async () => {
  const { result } = await editor();
  const existing = { ...ruleDefaults(template), threshold: 10, name: '原规则', enabled: true };
  act(() => result.current.setRules([existing]));
  await act(async () => { await result.current.adoptRule(candidate, hash, () => true); });
  expect(result.current.rules).toEqual([existing, expect.objectContaining({ ...candidate, enabled: false })]);
  expect(update).not.toHaveBeenCalled();
  await act(async () => { await result.current.save(); });
  expect(update).toHaveBeenCalledWith('7', expect.objectContaining({ expectedDefinition: hash,
    settings: expect.objectContaining({ scheduleEnabled: false, ruleFailureAction: 'CONTINUE' }),
    rules: [expect.objectContaining({ name: '原规则', threshold: 10, enabled: true }), expect.objectContaining(candidate)] }));
  expect(history.push).toHaveBeenCalledWith('/data-quality/monitor/7');
});

test('duplicate adoption is rejected, but deleting the form entry permits re-adoption', async () => {
  const { result } = await editor();
  await act(async () => { await result.current.adoptRule(candidate, hash, () => true); });
  await expect(result.current.adoptRule({ ...candidate, name: '新名称' }, hash, () => true)).rejects.toThrow('相同条件');
  expect(validate).toHaveBeenCalledTimes(1);
  act(() => result.current.setRules([]));
  await act(async () => { await result.current.adoptRule(candidate, hash, () => true); });
  expect(result.current.rules).toHaveLength(1);
  expect(validate).toHaveBeenCalledTimes(2);
});

test('source validation prevents save and another adoption until it completes', async () => {
  const { result } = await editor();
  const pending = deferred<SaveRulePayload[]>(); validate.mockReturnValueOnce(pending.promise);
  let adoption!: Promise<void>;
  act(() => { adoption = result.current.adoptRule(candidate, hash, () => true); });
  expect(result.current.adopting).toBe(true);
  await act(async () => { await result.current.save(); });
  await expect(result.current.adoptRule(candidate, hash, () => true)).rejects.toThrow('正在校验或保存');
  expect(update).not.toHaveBeenCalled();
  expect(mockForm.validateFields).not.toHaveBeenCalled();
  await act(async () => { pending.resolve([candidate]); await adoption; });
  expect(result.current.adopting).toBe(false);
});

test('saving locks before async validation, freezes rules and rejects adoption/double save', async () => {
  const { result } = await editor();
  const existing = { ...ruleDefaults(template), name: '提交时规则', threshold: 5 };
  act(() => result.current.setRules([existing]));
  const pending = deferred<typeof mockValues>(); mockForm.validateFields.mockReturnValueOnce(pending.promise);
  let saving!: Promise<void>;
  act(() => { saving = result.current.save(); });
  expect(result.current.saving).toBe(true);
  await expect(result.current.adoptRule(candidate, hash, () => true)).rejects.toThrow('正在校验或保存');
  await act(async () => { await result.current.save(); result.current.setRules([]); });
  await act(async () => { pending.resolve(mockValues); await saving; });
  expect(update).toHaveBeenCalledTimes(1);
  expect(update.mock.calls[0][1].rules).toEqual([expect.objectContaining({ name: '提交时规则', threshold: 5 })]);
  expect(validate).not.toHaveBeenCalled();
});

test('late validation uses latest form without overwriting edits and rejects a newly added duplicate', async () => {
  const { result } = await editor();
  const pending = deferred<SaveRulePayload[]>(); validate.mockReturnValueOnce(pending.promise);
  let adoption!: Promise<void>;
  act(() => { adoption = result.current.adoptRule(candidate, hash, () => true); });
  const manual = { ...ruleDefaults(template), ...candidate, name: '人工添加', enabled: true };
  act(() => result.current.setRules([manual]));
  await act(async () => { pending.resolve([candidate]); await expect(adoption).rejects.toThrow('相同条件'); });
  expect(result.current.rules).toEqual([manual]);
});

test('late validation preserves different newly edited conditions', async () => {
  const { result } = await editor();
  const pending = deferred<SaveRulePayload[]>(); validate.mockReturnValueOnce(pending.promise);
  let adoption!: Promise<void>;
  act(() => { adoption = result.current.adoptRule(candidate, hash, () => true); });
  const manual = { ...ruleDefaults(template), threshold: 20, name: '人工修改' };
  act(() => result.current.setRules([manual]));
  await act(async () => { pending.resolve([candidate]); await adoption; });
  expect(result.current.rules).toEqual([manual, expect.objectContaining(candidate)]);
});

test.each(['denied', 'empty'])('source %s keeps form unchanged and releases the lock', async (mode) => {
  const { result } = await editor();
  if (mode === 'denied') validate.mockRejectedValueOnce(new Error('权限拒绝'));
  else validate.mockResolvedValueOnce([]);
  await act(async () => { await expect(result.current.adoptRule(candidate, hash, () => true)).rejects.toThrow(); });
  expect(result.current.rules).toEqual([]);
  expect(result.current.adopting).toBe(false);
  expect(update).not.toHaveBeenCalled();
});

test.each(['cancel', 'target'])('ignores validation after %s', async (mode) => {
  const { result, rerender } = await editor();
  const pending = deferred<SaveRulePayload[]>(); validate.mockReturnValueOnce(pending.promise);
  let current = true; let adoption!: Promise<void>;
  act(() => { adoption = result.current.adoptRule(candidate, hash, () => current); });
  if (mode === 'cancel') current = false;
  else {
    rerender({ ...options, monitorId: '8' });
    await waitFor(() => expect(result.current.loading).toBe(false));
  }
  await act(async () => { pending.resolve([candidate]); await adoption; });
  expect(result.current.rules).toEqual([]);
  expect(update).not.toHaveBeenCalled();
});

test('save conflict retains the unsaved form and never navigates or reports success', async () => {
  const { result } = await editor();
  await act(async () => { await result.current.adoptRule(candidate, hash, () => true); });
  update.mockRejectedValueOnce(new Error('定义已改变'));
  await act(async () => { await result.current.save(); });
  expect(result.current.rules).toHaveLength(1);
  expect(message.error).toHaveBeenCalledWith('定义已改变');
  expect(message.success).not.toHaveBeenCalled();
  expect(history.push).not.toHaveBeenCalled();
});
