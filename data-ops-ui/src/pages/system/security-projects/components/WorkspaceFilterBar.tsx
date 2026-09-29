import {
  PlusOutlined,
  ReloadOutlined,
  SearchOutlined,
} from '@ant-design/icons';
import { Input, Select, Tooltip } from 'antd';
import { useState } from 'react';

import { YakButton } from '@/components/ui';
import type { SecurityProjectStatus } from '@/services/security/projects';

export interface WorkspaceFilters {
  projectCode?: string;
  projectName?: string;
  ownerName?: string;
  status?: SecurityProjectStatus;
}

type WorkspaceSearchField = 'projectName' | 'projectCode' | 'ownerName';

const SEARCH_FIELD_OPTIONS: Array<{
  label: string;
  value: WorkspaceSearchField;
}> = [
  { label: '工作空间名称', value: 'projectName' },
  { label: '工作空间编码', value: 'projectCode' },
  { label: '负责人', value: 'ownerName' },
];

const SEARCH_PLACEHOLDERS: Record<WorkspaceSearchField, string> = {
  projectName: '请输入工作空间名称',
  projectCode: '请输入工作空间编码',
  ownerName: '请输入负责人名称',
};

const buildFilters = (
  keyword: string,
  searchField: WorkspaceSearchField,
  status?: SecurityProjectStatus,
): WorkspaceFilters => {
  const filters: WorkspaceFilters = status ? { status } : {};
  const normalized = keyword.trim();
  if (normalized) filters[searchField] = normalized;
  return filters;
};

interface WorkspaceFilterBarProps {
  total: number;
  loading?: boolean;
  canManage: boolean;
  onSearch: (filters: WorkspaceFilters) => void;
  onRefresh: () => void;
  onCreate: () => void;
}

export default function WorkspaceFilterBar({
  total,
  loading = false,
  canManage,
  onSearch,
  onRefresh,
  onCreate,
}: WorkspaceFilterBarProps) {
  const [searchField, setSearchField] =
    useState<WorkspaceSearchField>('projectName');
  const [keyword, setKeyword] = useState('');
  const [status, setStatus] = useState<SecurityProjectStatus>();

  const submit = () => {
    onSearch(buildFilters(keyword, searchField, status));
  };

  return (
    <div className="mb-4 flex min-w-0 flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
      <div className="inline-flex h-8 w-fit items-center rounded-[8px] bg-[#f2f3f5] px-3.5 text-[13px] font-semibold text-[#242731]">
        全部工作空间
        <span className="ml-1.5 text-xs font-medium text-[#98a2b3]">
          {total}
        </span>
      </div>

      <div className="flex shrink-0 flex-wrap items-center gap-2">
        <Select<WorkspaceSearchField>
          value={searchField}
          options={SEARCH_FIELD_OPTIONS}
          variant="filled"
          popupMatchSelectWidth={140}
          className="w-[132px]"
          onChange={(nextField) => {
            setSearchField(nextField);
            setKeyword('');
            onSearch(status ? { status } : {});
          }}
        />

        <Input
          allowClear
          value={keyword}
          variant="filled"
          placeholder={SEARCH_PLACEHOLDERS[searchField]}
          suffix={
            <SearchOutlined
              className="cursor-pointer text-slate-400 transition-colors hover:text-slate-700"
              onClick={submit}
            />
          }
          className="w-[240px]"
          onChange={(event) => {
            const value = event.target.value;
            setKeyword(value);
            if (!value) onSearch(status ? { status } : {});
          }}
          onPressEnter={submit}
        />

        <Select<SecurityProjectStatus>
          allowClear
          value={status}
          variant="filled"
          placeholder="状态"
          className="w-[108px]"
          options={[
            { label: '启用', value: 'ENABLED' },
            { label: '停用', value: 'DISABLED' },
          ]}
          onChange={(nextStatus) => {
            setStatus(nextStatus);
            onSearch(buildFilters(keyword, searchField, nextStatus));
          }}
        />

        <Tooltip title="刷新列表">
          <YakButton
            iconOnly
            icon={<ReloadOutlined />}
            loading={loading}
            onClick={onRefresh}
          />
        </Tooltip>

        {canManage && (
          <YakButton
            type="primary"
            icon={<PlusOutlined />}
            onClick={onCreate}
          >
            新增工作空间
          </YakButton>
        )}
      </div>
    </div>
  );
}
