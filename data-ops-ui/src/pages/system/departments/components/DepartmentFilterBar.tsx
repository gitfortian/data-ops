import {
  ImportOutlined,
  PlusOutlined,
  ReloadOutlined,
  SearchOutlined,
} from '@ant-design/icons';
import { Input, Tooltip } from 'antd';
import { useMemo } from 'react';

import { PermissionGuard } from '@/components/security';
import {
  YakButton,
  YakFilterSwitch,
  type YakFilterSwitchOption,
} from '@/components/ui';
import { SECURITY_PERMISSIONS } from '@/constants/securityPermissions';

import type {
  DepartmentScope,
  DepartmentTreeStats,
} from '../types';

interface DepartmentFilterBarProps {
  scope: DepartmentScope;
  stats: DepartmentTreeStats;
  keyword: string;
  loading?: boolean;
  onScopeChange: (scope: DepartmentScope) => void;
  onKeywordChange: (keyword: string) => void;
  onRefresh: () => void;
  onImport: () => void;
  onCreate: () => void;
}

export default function DepartmentFilterBar({
  scope,
  stats,
  keyword,
  loading = false,
  onScopeChange,
  onKeywordChange,
  onRefresh,
  onImport,
  onCreate,
}: DepartmentFilterBarProps) {
  const scopeOptions = useMemo<
    YakFilterSwitchOption<DepartmentScope>[]
  >(
    () => [
      {
        value: 'all',
        label: (
          <span>
            全部
            <span className="ml-1 text-xs opacity-60">{stats.total}</span>
          </span>
        ),
      },
      {
        value: 'group',
        label: (
          <span>
            部门分组
            <span className="ml-1 text-xs opacity-60">{stats.groups}</span>
          </span>
        ),
      },
      {
        value: 'leaf',
        label: (
          <span>
            末级部门
            <span className="ml-1 text-xs opacity-60">{stats.leaves}</span>
          </span>
        ),
      },
    ],
    [stats.groups, stats.leaves, stats.total],
  );

  return (
    <div className="mb-4 flex shrink-0 flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
      <div className="min-w-0 overflow-x-auto pb-1 lg:pb-0">
        <YakFilterSwitch
          value={scope}
          options={scopeOptions}
          onChange={onScopeChange}
        />
      </div>

      <div className="flex shrink-0 flex-wrap items-center gap-2">
        <Input
          allowClear
          value={keyword}
          prefix={<SearchOutlined className="text-slate-400" />}
          placeholder="搜索部门名称、描述或 ID"
          className="w-[320px] max-w-full"
          onChange={(event) => onKeywordChange(event.target.value)}
        />

        <Tooltip title="刷新部门树">
          <YakButton
            iconOnly
            icon={<ReloadOutlined />}
            loading={loading}
            onClick={onRefresh}
          />
        </Tooltip>

        <PermissionGuard
          mode="one"
          permission={SECURITY_PERMISSIONS.department.import}
        >
          <YakButton
            icon={<ImportOutlined />}
            onClick={onImport}
          >
            导入
          </YakButton>
        </PermissionGuard>

        <PermissionGuard
          mode="one"
          permission={SECURITY_PERMISSIONS.department.create}
        >
          <YakButton
            type="primary"
            icon={<PlusOutlined />}
            onClick={onCreate}
          >
            新增部门
          </YakButton>
        </PermissionGuard>
      </div>
    </div>
  );
}
