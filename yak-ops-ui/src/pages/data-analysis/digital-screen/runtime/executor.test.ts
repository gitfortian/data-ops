import type { ScreenComponent, ScreenTemplate } from '@/components/screen-engine';
import type { DatasetQueryResult, PublishedDataset } from '@/services/dataset';
import type { DigitalScreenBindings } from '@/services/digital-screen';
import { ScreenRuntimeExecutor } from './executor';
import { planScreenRuntimeQueries } from './planner';

const component = (type: ScreenComponent['type'], id: string) => ({
  id,
  type,
  x: 0,
  y: 0,
  width: 100,
  height: 100,
}) as ScreenComponent;

const dataset: PublishedDataset = {
  id: '12',
  name: '销售 Dataset',
  description: '',
  status: 'ONLINE',
  sourceTaskId: '1',
  sourceTaskName: 'task',
  currentVersionNo: 3,
  updatedAt: '2026-08-27T00:00:00Z',
  fields: [
    {
      key: 'region',
      label: '区域',
      physicalName: 'region',
      dataType: 'string',
      role: 'dimension',
      nullable: false,
    },
    {
      key: 'amount',
      label: '金额',
      physicalName: 'amount',
      dataType: 'number',
      role: 'metric',
      nullable: false,
    },
  ],
};

const queryResult: DatasetQueryResult = {
  datasetId: '12',
  datasetVersionId: '30',
  datasetVersionNo: 3,
  bindings: [
    {
      key: 'region',
      fieldId: 'region',
      displayName: '区域',
      dataType: 'STRING',
      aggregation: null,
    },
    {
      key: 'amount_SUM',
      fieldId: 'amount',
      displayName: '金额',
      dataType: 'NUMBER',
      aggregation: 'SUM',
    },
  ],
  columns: [],
  rows: [['华东', 10]],
  returnedRows: 1,
  truncated: false,
  elapsedMillis: 8,
};

const binding = {
  datasetId: '12',
  dimensions: ['region'],
  metrics: [{ field: 'amount', aggregation: 'SUM' as const }],
};

const runtimePlan = () => {
  const template = {
    components: [component('line', 'line'), component('bar', 'bar')],
  } as ScreenTemplate;
  const bindings: DigitalScreenBindings = { line: binding, bar: binding };
  return planScreenRuntimeQueries(template, bindings, [dataset]);
};

describe('ScreenRuntimeExecutor', () => {
  it('deduplicates identical raw Dataset queries and reuses the cached result', async () => {
    const query = jest.fn(async () => queryResult);
    const executor = new ScreenRuntimeExecutor(query);
    const candidates = runtimePlan();

    const first = await executor.execute(candidates, {
      cacheTtlMs: 10_000,
      maxConcurrency: 4,
    });

    expect(query).toHaveBeenCalledTimes(1);
    expect(first.stats).toEqual({
      candidateCount: 2,
      uniqueQueryCount: 1,
      deduplicatedCount: 1,
      networkQueryCount: 1,
      cacheHitQueryCount: 0,
    });
    expect(first.data.line).toEqual(first.data.bar);

    const second = await executor.execute(candidates, {
      cacheTtlMs: 10_000,
      maxConcurrency: 4,
    });

    expect(query).toHaveBeenCalledTimes(1);
    expect(second.stats.networkQueryCount).toBe(0);
    expect(second.stats.cacheHitQueryCount).toBe(1);
  });

  it('isolates one grouped query failure to the components using that query', async () => {
    const executor = new ScreenRuntimeExecutor(async () => {
      throw new Error('query failed');
    });

    const result = await executor.execute(runtimePlan(), {
      cacheTtlMs: 10_000,
      maxConcurrency: 4,
    });

    expect(result.errors).toEqual({ line: 'query failed', bar: 'query failed' });
    expect(result.data).toEqual({});
  });
});
