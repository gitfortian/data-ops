import { normalizeTableNames } from './useDataSourceTables';

describe('normalizeTableNames', () => {
  const relations = [
    { name: 'orders', type: 'TABLE' },
    { name: 'v_ods_project', type: 'VIEW' },
  ];

  it('keeps tables and views for source selection', () => {
    expect(normalizeTableNames(relations)).toEqual([
      'orders',
      'v_ods_project',
    ]);
  });

  it('filters views for sink selection', () => {
    expect(normalizeTableNames(relations, false)).toEqual([
      'orders',
    ]);
  });

  it('keeps legacy string catalog entries', () => {
    expect(
      normalizeTableNames(['orders', 'legacy_relation'], false),
    ).toEqual(['orders', 'legacy_relation']);
  });
});
