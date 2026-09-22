import {
  PlusOutlined,
  ReloadOutlined,
  SearchOutlined,
} from '@ant-design/icons';
import { Input, Select, Tooltip } from 'antd';
import { useState } from 'react';

import { YakButton } from '@/components/ui';
import { SECURITY_PERMISSIONS } from '@/constants/securityPermissions';
import { usePermissionAccess } from '@/hooks/usePermissionAccess';

import {
  ROLE_SEARCH_FIELD_OPTIONS,
  ROLE_SEARCH_PLACEHOLDERS,
} from '../constants';
import type {
  RoleFilterValues,
  RoleSearchField,
} from '../types';
import { buildRoleFilters } from '../utils';

interface RoleFilterBarProps {
  total: number;
  loading?: boolean;
  onSearch: (values: RoleFilterValues) => void;
  onRefresh: () => void;
  onCreate: () => void;
}

export default function RoleFilterBar({
  total,
  loading = false,
  onSearch,
  onRefresh,
  onCreate,
}: RoleFilterBarProps) {
  const { can } = usePermissionAccess();
  const [searchField, setSearchField] =
    useState<RoleSearchField>('roleName');
  const [keyword, setKeyword] = useState('');

  const submit = () => {
    onSearch(buildRoleFilters(keyword, searchField));
  };

  return (
    <div className="mb-4 flex min-w-0 flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
      <div className="inline-flex h-8 w-fit items-center rounded-[8px] bg-[#f2f3f5] px-3.5 text-[13px] font-semibold text-[#242731]">
        全部角色
        <span className="ml-1.5 text-xs font-medium text-[#98a2b3]">
          {total}
        </span>
      </div>

      <div className="flex shrink-0 flex-wrap items-center gap-2">
        <div className="flex h-8 w-[360px] max-w-full overflow-hidden rounded-md bg-[#f2f2f4]">
          <Select<RoleSearchField>
            value={searchField}
            options={ROLE_SEARCH_FIELD_OPTIONS}
            variant="borderless"
            popupMatchSelectWidth={120}
            className="h-8 w-[104px] shrink-0 [&_.ant-select-selector]:!h-8 [&_.ant-select-selector]:!bg-transparent [&_.ant-select-selector]:!px-3 [&_.ant-select-selection-item]:!leading-[30px]"
            onChange={(nextField) => {
              setSearchField(nextField);
              setKeyword('');
              onSearch({});
            }}
          />
          <div className="my-2 w-px shrink-0 bg-slate-200" />
          <Input
            allowClear
            value={keyword}
            variant="borderless"
            inputMode={searchField === 'id' ? 'numeric' : 'text'}
            placeholder={ROLE_SEARCH_PLACEHOLDERS[searchField]}
            suffix={
              <SearchOutlined
                className="cursor-pointer text-slate-400 transition-colors hover:text-slate-700"
                onClick={submit}
              />
            }
            className="min-w-0 flex-1 !h-8 !bg-transparent !py-0 !shadow-none [&_.ant-input]:!bg-transparent"
            onChange={(event) => {
              const value = event.target.value;
              setKeyword(value);
              if (!value) onSearch({});
            }}
            onPressEnter={submit}
          />
        </div>

        <Tooltip title="刷新列表">
          <YakButton
            iconOnly
            icon={<ReloadOutlined />}
            loading={loading}
            onClick={onRefresh}
          />
        </Tooltip>

        {can(SECURITY_PERMISSIONS.role.create) && (
          <YakButton
            type="primary"
            icon={<PlusOutlined />}
            onClick={onCreate}
          >
            新增角色
          </YakButton>
        )}
      </div>
    </div>
  );
}
