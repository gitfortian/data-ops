import type {
  UserPaginationState,
  UserSearchField,
} from './types';

export const DEFAULT_USER_PAGINATION: UserPaginationState = {
  current: 1,
  pageSize: 10,
  total: 0,
};

export const USER_SEARCH_FIELD_OPTIONS: Array<{
  label: string;
  value: UserSearchField;
}> = [
  { label: '用户名', value: 'userName' },
  { label: '真实姓名', value: 'realName' },
  { label: '用户 ID', value: 'id' },
];

export const USER_SEARCH_PLACEHOLDERS: Record<
  UserSearchField,
  string
> = {
  userName: '请输入用户名',
  realName: '请输入真实姓名',
  id: '请输入用户 ID',
};

export const ALL_ROLE_FILTER_VALUE = '__all__';

export const USER_NAME_PATTERN = /^[0-9a-zA-Z_]{4,50}$/;

export const PHONE_PATTERN =
  /^(13[0-9]|14[01456879]|15[0-35-9]|16[2567]|17[0-8]|18[0-9]|19[0-35-9])\d{8}$/;
