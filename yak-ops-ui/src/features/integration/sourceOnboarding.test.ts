import {
  bindOfflineDraftSource,
  buildBatchSourceOnboardingPath,
  findReadableSourceDataSource,
  parseBatchSourceOnboardingContext,
} from './sourceOnboarding';

const dataSources = [
  { id: 11, name: 'orders', dbType: 'MYSQL' },
  { id: 22, name: 'orders', dbType: 'POSTGRE_SQL' },
];

describe('Phase 6 datasource onboarding context', () => {
  it('uses stable datasource identity in the navigation handoff', () => {
    expect(buildBatchSourceOnboardingPath('source / 11')).toBe(
      '/sync/batch-link-up?create=1&sourceDataSourceId=source+%2F+11',
    );
  });

  it('keeps create intent separate from optional source context', () => {
    expect(
      parseBatchSourceOnboardingContext(
        '?create=1&sourceDataSourceId=22',
      ),
    ).toEqual({ createRequested: true, sourceDataSourceId: '22' });

    expect(parseBatchSourceOnboardingContext('?create=1')).toEqual({
      createRequested: true,
    });
  });

  it('resolves source by stable id rather than duplicate display name', () => {
    expect(findReadableSourceDataSource(dataSources, '22')).toMatchObject({
      id: 22,
      dbType: 'POSTGRE_SQL',
    });
    expect(findReadableSourceDataSource(dataSources, '404')).toBeUndefined();
  });

  it('binds only the validated source id into the owning offline draft', () => {
    const payload = {
      source: { dataSourceId: '', connectorId: 'jdbc' },
      sink: { dataSourceId: '' },
    };

    expect(bindOfflineDraftSource(payload, dataSources[0])).toEqual({
      source: { dataSourceId: '11', connectorId: 'jdbc' },
      sink: { dataSourceId: '' },
    });
    expect(payload.source.dataSourceId).toBe('');
  });
});
