import { filterPermissionTree, getDirectChildren, retainMatchedAncestors } from './tree';
import type { PermissionVO } from '@/services/security/permissions';

const tree: PermissionVO[] = [{ id: 1, permissionName: '系统', permissionCode: 'system', childList: [{ id: 2, permissionName: '查看权限', permissionCode: 'system:permission:read' }] }];

test('permission search retains the ancestor chain using the backend childList contract', () => {
  expect(filterPermissionTree(tree, 'system:permission:read', 'all')).toEqual([{ ...tree[0], childList: [{ ...tree[0].childList![0], childList: [] }] }]);
  expect(getDirectChildren(tree[0])).toEqual(tree[0].childList);
});

test('department compatibility search supports mixed identifiers and ignores cycles', () => {
  interface Department { id: string | number; children: Department[] }
  const child: Department = { id: '2', children: [] };
  const parent: Department = { id: 1, children: [child] };
  expect(retainMatchedAncestors([parent], [child])).toEqual([{ ...parent, children: [{ ...child, children: [] }] }]);
  const cyclic: { id: number; children: any[] } = { id: 1, children: [] };
  cyclic.children.push(cyclic);
  expect(retainMatchedAncestors([cyclic], [cyclic])).toEqual([{ id: 1, children: [] }]);
});
