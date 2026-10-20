import { parseLogicalId, parseLogicalJson } from './logicalIdentity';
import { getLogicalDraft, updateLogicalAttribute } from './logical';

const mockRequest = jest.fn();
jest.mock('@/utils/request', () => ({ __esModule: true, default: (...args: unknown[]) => mockRequest(...args) }));
jest.mock('@/utils/HttpUtils', () => ({ __esModule: true, default: { unwrap: (response: { data: unknown }) => response.data } }));

it('keeps neighboring Long identities distinct while leaving strings and normal JSON numbers intact', () => {
  const result = parseLogicalJson<{ ids: (string | number)[]; note: string; value: number; scientific: number }>(
    '{"ids":[2109169776643895297,2109169776643895298,7],"note":"escaped \\" id 2109169776643895297","value":12.5,"scientific":1e3}',
  );
  expect(result.ids).toEqual(['2109169776643895297', '2109169776643895298', 7]);
  expect(result.note).toBe('escaped " id 2109169776643895297');
  expect(result.value).toBe(12.5);
  expect(result.scientific).toBe(1000);
});

it('accepts exact positive Long strings and rejects already-rounded numbers and malformed coordinates', () => {
  expect(parseLogicalId('9223372036854775807')).toBe('9223372036854775807');
  expect(parseLogicalId('7')).toBe(7);
  for (const id of ['9223372036854775808', '01', '1e3', '-1', '', 2109169776643895300]) expect(parseLogicalId(id)).toBeUndefined();
  expect(() => getLogicalDraft(2109169776643895300)).toThrow('无效的逻辑对象身份');
  expect(() => getLogicalDraft('../other')).toThrow('无效的逻辑对象身份');
});

it('reads the original response text and carries exact object IDs through the existing update command', async () => {
  mockRequest.mockResolvedValue('{"code":0,"data":{"model":{"id":2109169776643895297},"entities":[]}}');
  const result = await getLogicalDraft('2109169776643895297');
  expect(result.model.id).toBe('2109169776643895297');
  expect(mockRequest).toHaveBeenLastCalledWith('/api/v1/modeling/logical-models/2109169776643895297',
    expect.objectContaining({ responseType: 'text', businessErrorMode: 'reject' }));
  await updateLogicalAttribute('2109169776643895297', '2109169776643895298', '2109169776643895299', { code: 'amount', name: '金额' }, 3);
  expect(mockRequest).toHaveBeenLastCalledWith(
    '/api/v1/modeling/logical-models/2109169776643895297/entities/2109169776643895298/attributes/2109169776643895299?expectedRevision=3',
    expect.objectContaining({ method: 'PUT', data: { code: 'amount', name: '金额' } }),
  );
});
