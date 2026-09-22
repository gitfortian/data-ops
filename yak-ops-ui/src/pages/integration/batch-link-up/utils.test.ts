import moment from 'moment';

import type { OfflineSyncSearchState } from './types';
import {
  buildOfflineSyncPageQuery,
  buildOfflineSyncQueryString,
  getOfflineSyncEditPath,
  parseOfflineSyncPaginationFromUrl,
  parseOfflineSyncSearchFromUrl,
} from './utils';

describe('batch-link-up page utilities', () => {
  it('parses filters and pagination from the URL', () => {
    const search = [
      '?jobName=orders',
      'status=RUNNING',
      'sourceType=MYSQL',
      'createTimeStart=2026-08-01%2000%3A00%3A00',
      'createTimeEnd=2026-08-02%2023%3A59%3A59',
      'current=3',
      'pageSize=20',
    ].join('&');

    const filters = parseOfflineSyncSearchFromUrl(search);
    const pagination = parseOfflineSyncPaginationFromUrl(search);

    expect(filters.jobName).toBe('orders');
    expect(filters.status).toBe('RUNNING');
    expect(filters.sourceType).toBe('MYSQL');
    expect(filters.createTime?.[0].format('YYYY-MM-DD HH:mm:ss')).toBe(
      '2026-08-01 00:00:00',
    );
    expect(pagination).toEqual({ current: 3, pageSize: 20, total: 0 });
  });

  it('builds the URL and service request from one search model', () => {
    const filters: OfflineSyncSearchState = {
      jobName: '  orders  ',
      sourceTable: 'orders',
      createTime: [
        moment('2026-08-01 00:00:00'),
        moment('2026-08-03 23:59:59'),
      ],
    };
    const pagination = { current: 2, pageSize: 50 };

    const queryString = buildOfflineSyncQueryString(filters, pagination);
    const request = buildOfflineSyncPageQuery(filters, pagination);

    expect(queryString).toContain('jobName=orders');
    expect(queryString).toContain('current=2');
    expect(request).toEqual({
      current: 2,
      pageSize: 50,
      jobName: 'orders',
      id: undefined,
      status: undefined,
      sourceType: undefined,
      sinkType: undefined,
      sourceTable: 'orders',
      sinkTable: undefined,
      createTimeStart: '2026-08-01 00:00:00',
      createTimeEnd: '2026-08-03 23:59:59',
    });
  });

  it('maps supported task modes to their editor route', () => {
    expect(
      getOfflineSyncEditPath({ id: '1001', mode: 'GUIDE_SINGLE' }),
    ).toBe('/sync/batch-link-up/1001/config/single?scene=edit');
    expect(
      getOfflineSyncEditPath({ id: '1002', mode: 'GUIDE_MULTI' }),
    ).toBe('/sync/batch-link-up/1002/config/multi?scene=edit');
    expect(getOfflineSyncEditPath({ id: '1003', mode: 'UNKNOWN' })).toBeUndefined();
  });
});
