import type { DepartmentVO } from '@/services/security/departments';
import type {
  SecurityProjectCreateInput,
  SecurityProjectDetail,
  SecurityProjectInput,
  SecurityProjectUser,
} from '@/services/security/projects';

export type WorkspaceAssignmentMode = 'owner' | 'member';

export interface WorkspaceDepartmentTreeNode {
  value: number;
  title: string;
  children?: WorkspaceDepartmentTreeNode[];
}

const safeChildren = (department?: DepartmentVO): DepartmentVO[] =>
  Array.isArray(department?.childList) ? department.childList : [];

const departmentForest = (root?: DepartmentVO): DepartmentVO[] => {
  if (!root) return [];
  const virtualRoot = Number(root.id) === 0 && !root.deptName;
  return virtualRoot ? safeChildren(root) : [root];
};

const departmentLabel = (department: DepartmentVO): string =>
  department.deptName?.trim() || `部门 ${department.id}`;

const toDepartmentNodes = (
  departments: DepartmentVO[],
  visited = new Set<string>(),
): WorkspaceDepartmentTreeNode[] =>
  departments.flatMap((department) => {
    const key = String(department.id);
    if (visited.has(key)) return [];

    const nextVisited = new Set(visited);
    nextVisited.add(key);
    const children = toDepartmentNodes(
      safeChildren(department),
      nextVisited,
    );

    return [
      {
        value: Number(department.id),
        title: departmentLabel(department),
        ...(children.length > 0 ? { children } : {}),
      },
    ];
  });

export const toWorkspaceDepartmentTreeData = (
  root?: DepartmentVO,
): WorkspaceDepartmentTreeNode[] =>
  toDepartmentNodes(departmentForest(root));

export const normalizeWorkspaceInput = (
  values: SecurityProjectInput,
): SecurityProjectInput => ({
  projectName: values.projectName.trim(),
  description: values.description?.trim() ?? '',
  deptId: Number(values.deptId),
});

export const buildWorkspaceCreateInput = (
  values: SecurityProjectInput,
  creatorUserId?: number,
): SecurityProjectCreateInput => ({
  ...normalizeWorkspaceInput(values),
  ...(creatorUserId && creatorUserId > 0
    ? { ownerIdList: [creatorUserId] }
    : {}),
});

export const filterWorkspaceAssignmentCandidates = (
  mode: WorkspaceAssignmentMode,
  detail: SecurityProjectDetail,
  candidates: SecurityProjectUser[],
): SecurityProjectUser[] => {
  const ownerIds = new Set(detail.owners.map((user) => user.id));
  const memberIds = new Set(detail.members.map((user) => user.id));

  return candidates.filter((user) =>
    mode === 'owner'
      ? !memberIds.has(user.id) || ownerIds.has(user.id)
      : !ownerIds.has(user.id) || memberIds.has(user.id),
  );
};
