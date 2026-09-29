import type { DepartmentVO } from '@/services/security/departments';

import {
  filterDepartmentTree,
  getDepartmentForest,
  getDepartmentTreeStats,
} from './tree';

const root: DepartmentVO = {
  id: 0,
  childList: [
    {
      id: 1,
      deptName: '研发中心',
      leaf: false,
      childList: [
        {
          id: 2,
          deptName: '前端研发部',
          description: 'Web',
          leaf: true,
        },
      ],
    },
  ],
};

test('hides the backend virtual root from the management tree', () => {
  expect(getDepartmentForest(root).map((item) => item.id)).toEqual([1]);
});

test('keeps ancestors when a child matches the keyword', () => {
  const result = filterDepartmentTree(
    getDepartmentForest(root),
    '前端',
    'all',
  );

  expect(result).toHaveLength(1);
  expect(result[0].id).toBe(1);
  expect(result[0].childList?.[0]?.id).toBe(2);
});

test('calculates group and leaf counts from the visible tree', () => {
  expect(getDepartmentTreeStats(getDepartmentForest(root))).toEqual({
    total: 2,
    groups: 1,
    leaves: 1,
  });
});
