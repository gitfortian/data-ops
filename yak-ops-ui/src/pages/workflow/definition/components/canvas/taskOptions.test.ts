import type { TaskCatalogAsset } from '@/services/taskCatalog';
import {
  isWorkflowEligibleTaskCatalogAsset,
  taskCatalogOption,
} from './taskOptions';

const asset = (
  taskType: string,
  source = 'DATA_DEVELOPMENT',
): TaskCatalogAsset => ({
  id: '12',
  source,
  sourceRef: '10001',
  projectId: '7',
  name: '测试资产',
  taskType,
  status: 'ONLINE',
  currentRevision: {
    taskAssetId: '12',
    taskRevisionId: '101',
    revisionNo: 1,
  },
});

describe('workflow task catalog options', () => {
  test('allows executable data development tasks', () => {
    expect(isWorkflowEligibleTaskCatalogAsset(asset('SQL'))).toBe(true);
    expect(isWorkflowEligibleTaskCatalogAsset(asset('PYTHON'))).toBe(true);
    expect(isWorkflowEligibleTaskCatalogAsset(asset('SHELL'))).toBe(true);
    expect(isWorkflowEligibleTaskCatalogAsset(asset('HTTP'))).toBe(true);
  });

  test('rejects output resources from workflow orchestration', () => {
    expect(isWorkflowEligibleTaskCatalogAsset(asset('DATASET'))).toBe(false);
    expect(isWorkflowEligibleTaskCatalogAsset(asset('DATA_SERVICE'))).toBe(false);
    expect(() => taskCatalogOption(asset('DATASET'))).toThrow('不能进入工作流编排');
    expect(() => taskCatalogOption(asset('DATA_SERVICE'))).toThrow('不能进入工作流编排');
  });

  test('maps task asset sources to workflow task categories', () => {
    expect(taskCatalogOption(asset('SYNC', 'DATA_INTEGRATION')).typeLabel).toBe('数据同步');
    expect(taskCatalogOption(asset('SQL', 'DATA_DEVELOPMENT')).typeLabel).toBe('数据开发');
    expect(taskCatalogOption(asset('QUALITY_CHECK', 'DATA_QUALITY')).typeLabel).toBe('数据质量');
  });
});
