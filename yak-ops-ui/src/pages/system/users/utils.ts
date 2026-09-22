import type {
  UserFilterValues,
  UserSearchField,
} from './types';

export const cleanUserText = (value?: string): string =>
  value?.trim() ?? '';

export const buildUserFilters = (
  keyword: string,
  searchField: UserSearchField,
  roleId?: number,
): UserFilterValues => {
  const normalizedKeyword = cleanUserText(keyword);
  const filters: UserFilterValues = { roleId };

  if (!normalizedKeyword) return filters;

  if (searchField === 'id') {
    const id = Number(normalizedKeyword);
    if (Number.isInteger(id) && id > 0) filters.id = id;
    return filters;
  }

  filters[searchField] = normalizedKeyword;
  return filters;
};
