import HttpUtils from '@/utils/HttpUtils';
import { getModelingStructure, saveModelingStructure } from './api';

jest.mock('@/utils/HttpUtils', () => ({ __esModule: true, default: { getData: jest.fn(), putData: jest.fn() } }));
beforeEach(() => jest.clearAllMocks());
it('loads one coherent editor structure and its source fingerprint', async () => {
  (HttpUtils.getData as jest.Mock).mockResolvedValue({ structure: { modelId: 7, columns: [] }, definition: 'baseline' });
  expect(await getModelingStructure(7)).toEqual({ modelId: 7, columns: [], definition: 'baseline' });
  expect(HttpUtils.getData).toHaveBeenCalledWith('/api/v1/modeling/models/7/structure/edit-context');
});
it('sends the loaded baseline and returns the same-transaction receipt for subsequent local edits', async () => {
  const payload = { tableName: 'user', columns: [] };
  (HttpUtils.putData as jest.Mock).mockResolvedValue({ structure: { modelId: 7, tableName: 'user', columns: [] }, definition: 'saved-baseline' });
  expect(await saveModelingStructure(7, payload, 'loaded-baseline')).toEqual({ modelId: 7, tableName: 'user', columns: [], definition: 'saved-baseline' });
  expect(HttpUtils.putData).toHaveBeenCalledWith('/api/v1/modeling/models/7/structure/edit-context', payload, { headers: { 'If-Match': 'loaded-baseline' } });
});
it('preserves the existing manual API when no editor context was supplied', async () => {
  const payload = { columns: [] };
  await saveModelingStructure(7, payload);
  expect(HttpUtils.putData).toHaveBeenCalledWith('/api/v1/modeling/models/7/structure', payload);
});
