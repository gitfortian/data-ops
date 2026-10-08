import { resolveConsumptionDiscoveryView } from './presentation';

describe('Consumption discovery empty and failure truth', () => {
  const ready = { DATASET: 'READY', DATA_SERVICE: 'READY' } as const;

  it('shows an empty catalogue only when all applicable providers confirmed READY', () => {
    expect(resolveConsumptionDiscoveryView(false, '', 0, ready)).toBe('EMPTY');
    expect(resolveConsumptionDiscoveryView(false, '', 0, { DATASET: 'READY' })).toBe('INCOMPLETE');
    expect(resolveConsumptionDiscoveryView(false, '', 0, { DATASET: 'READY', DATA_SERVICE: 'UNAVAILABLE' })).toBe('INCOMPLETE');
    expect(resolveConsumptionDiscoveryView(false, '', 0, { DATASET: 'READY', DATA_SERVICE: 'FORBIDDEN' })).toBe('INCOMPLETE');
  });

  it('respects a type filter, but never treats an absent selected provider as empty', () => {
    expect(resolveConsumptionDiscoveryView(false, '', 0, { DATASET: 'READY' }, 'DATASET')).toBe('EMPTY');
    expect(resolveConsumptionDiscoveryView(false, '', 0, { DATASET: 'READY' }, 'DATA_SERVICE')).toBe('INCOMPLETE');
  });

  it('preserves partial visible products while distinguishing transport errors and pending requests', () => {
    expect(resolveConsumptionDiscoveryView(false, '', 2, { DATASET: 'READY', DATA_SERVICE: 'UNAVAILABLE' })).toBe('RESULTS');
    expect(resolveConsumptionDiscoveryView(false, 'timeout', 0, ready)).toBe('ERROR');
    expect(resolveConsumptionDiscoveryView(true, '', 0, ready)).toBe('LOADING');
  });
});
