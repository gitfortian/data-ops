import type {
  RolePaginationState,
  RoleSearchField,
} from './types';

export const DEFAULT_ROLE_PAGINATION: RolePaginationState = {
  current: 1,
  pageSize: 10,
  total: 0,
};

export const ROLE_SEARCH_FIELD_OPTIONS: Array<{
  label: string;
  value: RoleSearchField;
}> = [
  { label: '角色名称', value: 'roleName' },
  { label: '角色编码', value: 'roleCode' },
  { label: '角色描述', value: 'description' },
  { label: '角色 ID', value: 'id' },
];

export const ROLE_SEARCH_PLACEHOLDERS: Record<
  RoleSearchField,
  string
> = {
  roleName: '请输入角色名称',
  roleCode: '请输入角色编码',
  description: '请输入角色描述',
  id: '请输入角色 ID',
};
