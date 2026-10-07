import HttpUtils from '@/utils/HttpUtils';
import { getModelingMappingContext, setModelingMapping } from './mapping';

jest.mock('@/utils/HttpUtils', () => ({ __esModule: true, default: { getData: jest.fn(), putData: jest.fn() } }));
beforeEach(() => jest.clearAllMocks());
it('loads a single owner-controlled column edit baseline', async () => {
  (HttpUtils.getData as jest.Mock).mockResolvedValue({ definition: 'baseline', mapping: { targetColumn: 'user_id' } });
  expect((await getModelingMappingContext(7, 'user_id')).definition).toBe('baseline');
  expect(HttpUtils.getData).toHaveBeenCalledWith('/api/v1/modeling/models/7/mappings/user_id/edit-context');
});
it('uses the original mapping rules with an explicit compare-and-save header', async () => {
  const payload = { sourceDatasourceId: 9, sourceDatabase: 'db', sourceTable: 'users', sourceColumn: 'buyer_id' };
  await setModelingMapping(7, 'user_id', payload, 'baseline');
  expect(HttpUtils.putData).toHaveBeenCalledWith('/api/v1/modeling/models/7/mappings/user_id/edit-context', payload, { headers: { 'If-Match': 'baseline' } });
});
