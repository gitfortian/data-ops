import { appRoutes } from '@/config/navigation';
import {
  canReadHomePulse, HOME_PULSE_ROUTES, pulseScopeKey, safeOwnerCount,
} from './homeProjectPulse';

describe('FE-A1 current-project owner pulse contract', () => {
  it('accepts only backend nonnegative safe integer counts, including zero', () => {
    expect(safeOwnerCount(0)).toBe(0);
    expect(safeOwnerCount(19)).toBe(19);
    for (const wrong of [null, undefined, '0', -1, 1.2, NaN, Infinity, Number.MAX_SAFE_INTEGER + 1]) {
      expect(safeOwnerCount(wrong)).toBeNull();
    }
  });

  it('ties result visibility to the exact project, identity and grants', () => {
    const first = pulseScopeKey(1, 'u1', ['modeling:read'], ['modeling']);
    expect(first).toBeTruthy();
    expect(first).toBe(pulseScopeKey(1, 'u1', ['modeling:read'], ['modeling']));
    expect(first).not.toBe(pulseScopeKey(2, 'u1', ['modeling:read'], ['modeling']));
    expect(first).not.toBe(pulseScopeKey(1, 'u2', ['modeling:read'], ['modeling']));
    expect(first).not.toBe(pulseScopeKey(1, 'u1', [], ['modeling']));
    expect(first).not.toBe(pulseScopeKey(1, 'u1', ['modeling:read'], []));
    expect(pulseScopeKey(undefined, 'u1', [], [])).toBeNull();
    expect(pulseScopeKey(1, undefined, [], [])).toBeNull();
  });

  it('preserves source route permissions and menu codes; no independent public bypass', () => {
    expect(Object.keys(HOME_PULSE_ROUTES)).toEqual(['sources','models','metrics','assets','quality']);
    const cases = [
      ['sources', 'resource:data-source:read'],
      ['models', 'modeling:read'],
      ['metrics', 'metric:read'],
      ['assets', 'data-asset:read'],
      ['quality', 'quality:execution:read'],
    ] as const;
    for (const [key, permission] of cases) {
      const route = appRoutes.find((candidate) => candidate.id === HOME_PULSE_ROUTES[key])!;
      expect(route).toBeTruthy();
      expect(canReadHomePulse(key, [], [])).toBe(false);
      expect(canReadHomePulse(key, [permission], [])).toBe(false);
      expect(canReadHomePulse(key, [permission], [route.menuCode!])).toBe(true);
      expect(canReadHomePulse(key, ['security:root'], [])).toBe(true);
    }
  });
});
