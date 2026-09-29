import type { DataSourceRecord } from '@/services/data-source';
import type {
  TableAssetView,
  TableCandidateView,
} from '@/services/data-quality';

import {
  QUALITY_SOURCE_TREE_DEFAULT_WIDTH,
  QUALITY_SOURCE_TREE_MAX_WIDTH,
  QUALITY_SOURCE_TREE_MIN_WIDTH,
} from './constants';
import {
  buildQualityDataSourceNodes,
  getQualityMonitorCreatePath,
  getQualityMonitorDetailPath,
  groupQualityDataSourceNodes,
  normalizeQualityDataSourceType,
  parseQualitySourceTreeWidth,
  qualityTableCandidateKey,
} from './utils';

const tableAsset = (monitorId?: number): TableAssetView => ({
  id: 1,
  dataSourceId: 9,
  dataSourceName: 'warehouse',
  databaseName: 'analytics',
  schemaName: 'public',
  tableName: 'orders',
  monitorId,
  monitorCount: monitorId ? 1 : 0,
  ruleCount: monitorId ? 2 : 0,
  lastResult: 'NOT_RUN',
  registeredBy: 'admin',
  registeredAt: '2026-08-26 10:00:00',
});

describe('quality table registry helpers', () => {
  it('normalizes data source types', () => {
    expect(normalizeQualityDataSourceType(' mysql ')).toBe('MYSQL');
    expect(normalizeQualityDataSourceType()).toBe('OTHER');
  });

  it('builds source nodes and ignores invalid identifiers', () => {
    const records: DataSourceRecord[] = [
      {
        id: 2,
        name: '订单库',
        dbType: 'mysql',
        environmentName: '生产',
      },
      { id: '3', name: '报表库', dbType: 'DORIS' },
      { id: 'invalid', name: '异常数据源', dbType: 'MYSQL' },
    ];

    expect(buildQualityDataSourceNodes(records)).toEqual([
      {
        key: 'data-source:2',
        dataSourceId: 2,
        dataSourceName: '订单库',
        dataSourceType: 'MYSQL',
        environment: '生产',
      },
      {
        key: 'data-source:3',
        dataSourceId: 3,
        dataSourceName: '报表库',
        dataSourceType: 'DORIS',
        environment: undefined,
      },
    ]);
  });

  it('groups and sorts source nodes deterministically', () => {
    const nodes = buildQualityDataSourceNodes([
      { id: 3, name: 'B 库', dbType: 'MYSQL' },
      { id: 1, name: 'Doris 库', dbType: 'DORIS' },
      { id: 2, name: 'A 库', dbType: 'MYSQL' },
    ]);

    const groups = groupQualityDataSourceNodes(nodes);
    expect(groups.map((item) => item.dataSourceType)).toEqual([
      'DORIS',
      'MYSQL',
    ]);
    expect(groups[1].nodes.map((item) => item.dataSourceName)).toEqual([
      'A 库',
      'B 库',
    ]);
  });

  it('returns no groups when source nodes are missing', () => {
    expect(groupQualityDataSourceNodes()).toEqual([]);
  });

  it('restores and clamps the source tree width', () => {
    expect(parseQualitySourceTreeWidth()).toBe(
      QUALITY_SOURCE_TREE_DEFAULT_WIDTH,
    );
    expect(parseQualitySourceTreeWidth('10')).toBe(
      QUALITY_SOURCE_TREE_MIN_WIDTH,
    );
    expect(parseQualitySourceTreeWidth('9999')).toBe(
      QUALITY_SOURCE_TREE_MAX_WIDTH,
    );
    expect(parseQualitySourceTreeWidth('320')).toBe(320);
  });

  it('creates stable candidate keys across schemas', () => {
    const first: TableCandidateView = {
      databaseName: 'analytics',
      schemaName: 'public',
      tableName: 'orders',
    };
    const second: TableCandidateView = {
      databaseName: 'analytics',
      schemaName: 'archive',
      tableName: 'orders',
    };

    expect(qualityTableCandidateKey(first)).not.toBe(
      qualityTableCandidateKey(second),
    );
  });

  it('builds monitor navigation paths', () => {
    expect(getQualityMonitorDetailPath(tableAsset())).toBeUndefined();
    expect(getQualityMonitorDetailPath(tableAsset(18))).toBe(
      '/data-quality/monitor/18',
    );
    expect(getQualityMonitorCreatePath(tableAsset())).toBe(
      '/data-quality/monitor/create?dataSourceId=9&dataSourceName=warehouse&databaseName=analytics&schemaName=public&tableName=orders',
    );
  });
});
