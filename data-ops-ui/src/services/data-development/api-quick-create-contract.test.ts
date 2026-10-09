import HttpUtils from '@/utils/HttpUtils';
import { createDevelopmentNode } from './api';

describe('Quick Create Data Development node modern contract', () => {
  afterEach(() => jest.restoreAllMocks());

  it('POSTs a new development node to the unchanged endpoint and returns data-only identity', async () => {
    const payload = { name: 'SQL Orders', type: 'SQL' as const, projectId: 'project-4' };
    const created = { id: 'node-11', name: 'SQL Orders', type: 'SQL', configured: false };
    const postData = jest.spyOn(HttpUtils, 'postData').mockResolvedValue(created);
    await expect(createDevelopmentNode(payload)).resolves.toEqual(created);
    expect(postData).toHaveBeenCalledWith('/api/v1/data-development/nodes', payload);
  });

  it('preserves optional project identity instead of inventing a project', async () => {
    const payload = { name: 'Global Script', type: 'PYTHON' as const };
    const postData = jest.spyOn(HttpUtils, 'postData').mockResolvedValue({
      id: 'node-12', name: 'Global Script',
    });
    await createDevelopmentNode(payload);
    expect(postData).toHaveBeenCalledWith('/api/v1/data-development/nodes', payload);
  });

  it('propagates failed create operations so the page cannot navigate to an invented node', async () => {
    const postData = jest.spyOn(HttpUtils, 'postData')
      .mockRejectedValue(new Error('403 no development:create permission'));
    await expect(createDevelopmentNode({ name: 'Blocked SQL', type: 'SQL' }))
      .rejects.toThrow('403 no development:create permission');
  });
});
