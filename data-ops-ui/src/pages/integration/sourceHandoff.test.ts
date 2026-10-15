import {
  buildIntegrationCreatePath,
  parseIntegrationSourceHandoff,
  stripIntegrationCreateHandoff,
} from '@/services/integration/sourceHandoff';

describe('source integration handoff', () => {
  it('round-trips a stable source identity into batch creation', () => {
    const path = buildIntegrationCreatePath('batch', {
      dataSourceId: '42',
      dbType: 'MYSQL',
    });

    expect(path).toBe(
      '/sync/batch-link-up?create=1&sourceDataSourceId=42&sourceDbType=MYSQL',
    );
    expect(parseIntegrationSourceHandoff(path.split('?')[1] || '')).toEqual({
      dataSourceId: '42',
      dbType: 'MYSQL',
    });
  });

  it('round-trips a stable source identity into realtime creation', () => {
    const path = buildIntegrationCreatePath('realtime', {
      dataSourceId: 'source/with spaces',
      dbType: 'MYSQL',
    });

    expect(path).toContain('/sync/realtime?create=1');
    expect(parseIntegrationSourceHandoff(`?${path.split('?')[1]}`)).toEqual({
      dataSourceId: 'source/with spaces',
      dbType: 'MYSQL',
    });
  });

  it('does not invent a handoff without a stable source id', () => {
    expect(parseIntegrationSourceHandoff('?create=1&sourceDbType=MYSQL')).toBeUndefined();
    expect(
      buildIntegrationCreatePath('batch', { dataSourceId: '   ', dbType: 'MYSQL' }),
    ).toBe('/sync/batch-link-up');
  });

  it('does not treat an ordinary source query as create intent', () => {
    expect(
      parseIntegrationSourceHandoff('?sourceDataSourceId=42&sourceDbType=MYSQL'),
    ).toBeUndefined();
  });

  it('strips only create handoff parameters', () => {
    expect(
      stripIntegrationCreateHandoff(
        '?create=1&sourceDataSourceId=42&sourceDbType=MYSQL&view=active',
      ),
    ).toBe('?view=active');
  });
  it('round-trips exact ODS published revision and target observation for manual sync', () => {
    const path = buildIntegrationCreatePath('batch', {
      dataSourceId: '10', dbType: 'POSTGRE_SQL',
      odsModelId: 50, odsVersionNo: 2, odsTargetObservationId: 99,
    });
    expect(parseIntegrationSourceHandoff(path.split('?')[1])).toEqual({
      dataSourceId: '10', dbType: 'POSTGRE_SQL',
      odsModelId: 50, odsVersionNo: 2, odsTargetObservationId: 99,
    });
    expect(stripIntegrationCreateHandoff('?view=active&' + path.split('?')[1]))
      .toBe('?view=active');
  });

  it('never manufactures an ODS link from incomplete or invalid client hints', () => {
    const path = buildIntegrationCreatePath('batch', {
      dataSourceId: '10', odsModelId: 50, odsVersionNo: 2,
    });
    expect(parseIntegrationSourceHandoff(path.split('?')[1])).toEqual({
      dataSourceId: '10',
    });
    expect(parseIntegrationSourceHandoff(
      '?create=1&sourceDataSourceId=10&odsModelId=-5&odsVersionNo=2&odsTargetObservationId=99',
    )).toEqual({ dataSourceId: '10' });
  });

});
