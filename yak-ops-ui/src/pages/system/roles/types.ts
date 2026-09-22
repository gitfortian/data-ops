export interface RolePaginationState {
  current: number;
  pageSize: number;
  total: number;
}

export interface RoleFilterValues {
  id?: number;
  roleName?: string;
  roleCode?: string;
  description?: string;
}

export type RoleSearchField =
  | 'roleName'
  | 'roleCode'
  | 'description'
  | 'id';

export interface RoleFormValues {
  roleName: string;
  description?: string;
}
