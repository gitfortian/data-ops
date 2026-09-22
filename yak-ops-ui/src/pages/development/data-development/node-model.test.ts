import {
  NODE_CATEGORY_BY_TYPE,
  getNodeCategory,
  isDevelopmentTaskNodeType,
} from './node-model';

describe('data-development standalone node model', () => {
  it('classifies processing and output node responsibilities', () => {
    expect(NODE_CATEGORY_BY_TYPE).toEqual({
      SQL: 'PROCESSING',
      SHELL: 'PROCESSING',
      HTTP: 'PROCESSING',
      PYTHON: 'PROCESSING',
      DATASET: 'OUTPUT',
      DATA_SERVICE: 'OUTPUT',
    });
    expect(getNodeCategory('SQL')).toBe('PROCESSING');
    expect(getNodeCategory('DATASET')).toBe('OUTPUT');
    expect(getNodeCategory('DATA_SERVICE')).toBe('OUTPUT');
  });

  it('keeps output nodes outside the executable task editor lifecycle', () => {
    expect(isDevelopmentTaskNodeType('SQL')).toBe(true);
    expect(isDevelopmentTaskNodeType('SHELL')).toBe(true);
    expect(isDevelopmentTaskNodeType('DATASET')).toBe(false);
    expect(isDevelopmentTaskNodeType('DATA_SERVICE')).toBe(false);
  });
});
