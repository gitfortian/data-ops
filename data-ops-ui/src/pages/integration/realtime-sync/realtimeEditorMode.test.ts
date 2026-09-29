import { wizardCompatibility } from './realtimeEditorMode';
import type { CdcPipelineSpec } from './types';

const spec = (matchMode: 'EXACT' | 'REGEX'): CdcPipelineSpec => ({
  sourceDataSourceRef: 1,
  sinkDataSourceRef: 2,
  tables: [
    {
      sourceTable: matchMode === 'EXACT' ? 'orders' : 'orders_.*',
      sinkTable: 'orders',
      matchMode,
      keyColumns: ['id'],
    },
  ],
  startupMode: 'initial',
  schemaEvolution: 'EVOLVE',
  parallelism: 1,
  checkpointIntervalMs: 60_000,
  restart: { strategy: 'fixed-delay', attempts: 3, delayMs: 10_000 },
  sink: {
    maxRetries: 3,
    batchSize: 1_000,
    flushIntervalMs: 2_000,
    maxBatchBytes: 16_777_216,
    statementCacheSize: 128,
    strictReplaySafety: true,
  },
});

describe('wizardCompatibility', () => {
  it('accepts specs fully representable by the wizard', () => {
    expect(wizardCompatibility(spec('EXACT'))).toEqual({ supported: true });
  });

  it('rejects regex routes instead of silently losing them', () => {
    const result = wizardCompatibility(spec('REGEX'));
    expect(result.supported).toBe(false);
    expect(result.reason).toContain('REGEX');
    expect(result.reason).toContain('orders_.*');
  });
});
