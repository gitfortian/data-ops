from pathlib import Path
p=Path('data-ops-ui/src/pages/system/permissions/tree.test.ts');p.write_text('''import { filterPermissionTree, getDirectChildren, retainMatchedAncestors } from './tree';
import type { PermissionVO } from '@/services/security/permissions';

const tree: PermissionVO[] = [{ id: 1, permissionName: '系统', permissionCode: 'system', childList: [{ id: 2, permissionName: '查看权限', permissionCode: 'system:permission:read' }] }];

test('permission search retains the ancestor chain using the backend childList contract', () => {
  expect(filterPermissionTree(tree, 'system:permission:read', 'all')).toEqual([{ ...tree[0], childList: [{ ...tree[0].childList![0], childList: [] }] }]);
  expect(getDirectChildren(tree[0])).toEqual(tree[0].childList);
});

test('department compatibility search supports mixed identifiers and ignores cycles', () => {
  const child = { id: '2', children: [] };
  const parent = { id: 1, children: [child] };
  expect(retainMatchedAncestors([parent], [child])).toEqual([{ ...parent, children: [{ ...child, children: [] }] }]);
  const cyclic: { id: number; children: any[] } = { id: 1, children: [] };
  cyclic.children.push(cyclic);
  expect(retainMatchedAncestors([cyclic], [cyclic])).toEqual([{ id: 1, children: [] }]);
});
''',encoding='utf-8')
p=Path('data-ops-ui/src/pages/integration/batch-link-up/detail/workerScheduling.test.ts');p.write_text('''import { buildSavePayload, normalizeEditDetail } from './model';

/** Link-Up owns worker dispatch; legacy UI placement fields must not become another scheduler truth. */
test.each(['AUTO', 'MANUAL'])('ignores legacy %s worker placement while preserving the job definition', mode => {
  const editor = normalizeEditDetail({
    id: '1001', basic: { jobName: '历史任务', jobDesc: '升级兼容', mode: 'GUIDE_SINGLE' },
    workflow: { nodes: [], edges: [] },
    worker: { mode, nodeId: 'legacy-worker', requiredLabels: { region: 'south' } },
  }, '1001');
  const payload = buildSavePayload(editor);
  expect(editor.basic.jobName).toBe('历史任务');
  expect(payload.basic.jobDesc).toBe('升级兼容');
  expect(editor).not.toHaveProperty('worker');
  expect(payload).not.toHaveProperty('worker');
});
''',encoding='utf-8')
