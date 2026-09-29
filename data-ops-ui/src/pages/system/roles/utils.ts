import type { Key } from 'react';

import type {
  RoleFilterValues,
  RoleSearchField,
} from './types';

export const cleanRoleText = (value?: string): string =>
  value?.trim() ?? '';

export const buildRoleFilters = (
  keyword: string,
  searchField: RoleSearchField,
): RoleFilterValues => {
  const value = cleanRoleText(keyword);
  if (!value) return {};

  if (searchField === 'id') {
    const id = Number(value);
    return Number.isInteger(id) && id > 0 ? { id } : {};
  }

  return { [searchField]: value } as RoleFilterValues;
};

export const checkedKeysToRolePermissionIds = (
  keys: Key[],
): number[] =>
  Array.from(
    new Set(
      keys
        .map((key) => Number(key))
        .filter(Number.isFinite),
    ),
  );
