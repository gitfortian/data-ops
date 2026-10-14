import { appRoutes, canAccessNavigationRoute } from '@/config/navigation';

import { resolveHomeJourneyEntries } from './homeJourneyEntries';

describe('FE-A1 home journey entry contract', () => {
  it('always points to existing route metadata with unchanged access and menu contracts', () => {
    const permissionCodes = ['security:root'];
    const entries = resolveHomeJourneyEntries(permissionCodes, [], { agentEnabled: true });
    expect(entries.map((entry) => entry.key)).toEqual([
      'build', 'consume', 'recover', 'metric', 'security', 'semantic', 'mdm', 'agent',
    ]);
    for (const entry of entries) {
      const route = appRoutes.find((candidate) => candidate.path === entry.path);
      expect(route).toBeDefined();
      expect(canAccessNavigationRoute(route!, permissionCodes, [])).toBe(true);
      expect(entry.action).toContain(route!.title);
    }
  });

  it('never reveals data-source, Modeling or semantic actions without required permission', () => {
    const entries = resolveHomeJourneyEntries([], [], { agentEnabled: false });
    expect(entries.find((entry) => entry.key === 'build')).toBeUndefined();
    expect(entries.find((entry) => entry.key === 'metric')).toBeUndefined();
    expect(entries.find((entry) => entry.key === 'security')).toBeUndefined();
    expect(entries.find((entry) => entry.key === 'mdm')).toBeUndefined();
    expect(entries.find((entry) => entry.key === 'agent')).toBeUndefined();
    // A public Dataset management producer route must never substitute for governed consumption.
    expect(entries.find((entry) => entry.key === 'consume')).toBeUndefined();
    expect(entries.find((entry) => entry.key === 'recover')?.path).toBe('/workflow/instances');
  });

  it('honors original menu grants as well as resource read permissions', () => {
    const permissions = ['resource:data-source:read'];
    const withoutMenu = resolveHomeJourneyEntries(permissions, []);
    expect(withoutMenu.find((entry) => entry.key === 'build')).toBeUndefined();

    const source = appRoutes.find((route) => route.id === 'data-source')!;
    const withMenu = resolveHomeJourneyEntries(permissions, [source.menuCode!]);
    expect(withMenu.find((entry) => entry.key === 'build')?.path).toBe('/data-source');
    expect(withMenu.find((entry) => entry.key === 'metric')).toBeUndefined();
  });

  it('falls back only to another already-authorized professional destination', () => {
    const modeling = appRoutes.find((route) => route.id === 'modeling-workspace')!;
    const entries = resolveHomeJourneyEntries(['modeling:read'], [modeling.menuCode!]);
    expect(entries.find((entry) => entry.key === 'build')?.path).toBe('/modeling');
    expect(entries.find((entry) => entry.key === 'metric')).toBeUndefined();
  });

  it('does not expose Agent when provider is disabled, even with admin access', () => {
    const disabled = resolveHomeJourneyEntries(['security:root'], undefined, { agentEnabled: false });
    const enabled = resolveHomeJourneyEntries(['security:root'], undefined, { agentEnabled: true });
    expect(disabled.some((entry) => entry.key === 'agent')).toBe(false);
    expect(enabled.find((entry) => entry.key === 'agent')?.path).toBe('/ai-agent');
  });

  it('uses stable story identities instead of claiming completed task state', () => {
    const entries = resolveHomeJourneyEntries(['security:root'], undefined, { agentEnabled: true });
    expect(entries.filter((entry) => entry.primary).map((entry) => entry.story)).toEqual([
      'GM-J1', 'GM-J3', 'GM-J4',
    ]);
    expect(entries.every((entry) => !entry.path.includes(':') && entry.path.startsWith('/'))).toBe(true);
  });
});
