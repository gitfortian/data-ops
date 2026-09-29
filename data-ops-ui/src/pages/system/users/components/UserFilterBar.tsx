import {
  PlusOutlined,
  ReloadOutlined,
  SearchOutlined,
} from '@ant-design/icons';
import { Input, Select, Tooltip } from 'antd';
import { useMemo, useState } from 'react';

import { PermissionGuard } from '@/components/security';
import {
  YakButton,
  YakFilterSwitch,
  type YakFilterSwitchOption,
} from '@/components/ui';
import { SECURITY_PERMISSIONS } from '@/constants/securityPermissions';

import {
  ALL_ROLE_FILTER_VALUE,
  USER_SEARCH_FIELD_OPTIONS,
  USER_SEARCH_PLACEHOLDERS,
} from '../constants';
import type {
  RoleOption,
  UserFilterValues,
  UserSearchField,
} from '../types';
import { buildUserFilters } from '../utils';

interface UserFilterBarProps {
  roleOptions: RoleOption[];
  onSearch: (values: UserFilterValues) => void;
  onRefresh: () => void;
  onCreate: () => void;
}

export default function UserFilterBar({
  roleOptions,
  onSearch,
  onRefresh,
  onCreate,
}: UserFilterBarProps) {
  const [activeRoleId, setActiveRoleId] = useState<number>();
  const [searchField, setSearchField] =
    useState<UserSearchField>('userName');
  const [keyword, setKeyword] = useState('');

  const roleFilterOptions = useMemo<
    YakFilterSwitchOption<string>[]
  >(
    () => [
      { value: ALL_ROLE_FILTER_VALUE, label: '全部' },
      ...roleOptions.map((role) => ({
        value: String(role.value),
        label: role.label,
      })),
    ],
    [roleOptions],
  );

  const activeRoleValue =
    activeRoleId === undefined
      ? ALL_ROLE_FILTER_VALUE
      : String(activeRoleId);

  const submit = () => {
    onSearch(buildUserFilters(keyword, searchField, activeRoleId));
  };

  const changeRole = (value: string) => {
    const roleId =
      value === ALL_ROLE_FILTER_VALUE ? undefined : Number(value);
    const nextRoleId = Number.isFinite(roleId) ? roleId : undefined;

    setActiveRoleId(nextRoleId);
    onSearch(buildUserFilters(keyword, searchField, nextRoleId));
  };

  const changeSearchField = (field: UserSearchField) => {
    setSearchField(field);
    setKeyword('');
    onSearch(buildUserFilters('', field, activeRoleId));
  };

  return (
    <div className="mb-4 flex min-w-0 flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
      <div className="min-w-0 overflow-x-auto pb-1 lg:pb-0">
        <YakFilterSwitch
          value={activeRoleValue}
          options={roleFilterOptions}
          onChange={changeRole}
        />
      </div>

      <div className="flex shrink-0 flex-wrap items-center gap-2">
        <div className="flex h-8 w-[330px] max-w-full overflow-hidden rounded-md bg-[#f2f2f4]">
          <Select<UserSearchField>
            value={searchField}
            options={USER_SEARCH_FIELD_OPTIONS}
            variant="borderless"
            popupMatchSelectWidth={120}
            className={[
              'h-8 w-[100px] shrink-0',
              '[&_.ant-select-selector]:!h-8',
              '[&_.ant-select-selector]:!bg-transparent',
              '[&_.ant-select-selector]:!px-3',
              '[&_.ant-select-selection-item]:!leading-[30px]',
            ].join(' ')}
            onChange={changeSearchField}
          />

          <div className="my-2 w-px shrink-0 bg-slate-200" />

          <Input
            allowClear
            value={keyword}
            variant="borderless"
            inputMode={searchField === 'id' ? 'numeric' : 'text'}
            placeholder={USER_SEARCH_PLACEHOLDERS[searchField]}
            suffix={
              <SearchOutlined
                className="cursor-pointer text-slate-400 transition-colors hover:text-slate-700"
                onClick={submit}
              />
            }
            className="min-w-0 flex-1 !h-8 !bg-transparent !py-0 !shadow-none [&_.ant-input]:!bg-transparent"
            onChange={(event) => {
              const nextKeyword = event.target.value;
              setKeyword(nextKeyword);
              if (!nextKeyword) {
                onSearch(
                  buildUserFilters('', searchField, activeRoleId),
                );
              }
            }}
            onPressEnter={submit}
          />
        </div>

        <Tooltip title="刷新列表">
          <YakButton
            iconOnly
            icon={<ReloadOutlined />}
            onClick={onRefresh}
          />
        </Tooltip>

        <PermissionGuard
          mode="one"
          permission={SECURITY_PERMISSIONS.user.create}
        >
          <YakButton
            type="primary"
            icon={<PlusOutlined />}
            onClick={onCreate}
          >
            新增用户
          </YakButton>
        </PermissionGuard>
      </div>
    </div>
  );
}
