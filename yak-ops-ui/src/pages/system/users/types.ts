export interface RoleOption {
  value: number;
  label: string;
}

export interface UserFilterValues {
  id?: number;
  userName?: string;
  realName?: string;
  roleId?: number;
}

export type UserSearchField = 'userName' | 'realName' | 'id';

export interface UserPaginationState {
  current: number;
  pageSize: number;
  total: number;
}
