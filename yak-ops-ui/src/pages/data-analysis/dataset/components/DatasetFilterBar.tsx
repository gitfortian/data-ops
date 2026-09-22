import {
  CloudUploadOutlined,
  ReloadOutlined,
  SearchOutlined,
} from '@ant-design/icons';
import { Input, Select, Tooltip } from 'antd';
import { useState } from 'react';

import { YakButton } from '@/components/ui';
import type {
  DatasetSourceType,
  DatasetStatus,
} from '@/services/dataset';

export interface DatasetListFilters {
  keyword: string;
  status: 'ALL' | DatasetStatus;
  sourceType: 'ALL' | DatasetSourceType;
}

const SOURCE_TYPE_OPTIONS: Array<{
  label: string;
  value: 'ALL' | DatasetSourceType;
}> = [
  { value: 'ALL', label: '全部来源' },
  { value: 'QUERY_REVISION', label: 'SQL 任务' },
  { value: 'SQL_QUERY', label: 'Standalone SQL' },
  { value: 'TABLE', label: '数据表' },
  { value: 'VIEW', label: '视图' },
];

interface DatasetFilterBarProps {
  total: number;
  loading?: boolean;
  onSearch: (filters: DatasetListFilters) => void;
  onRefresh: () => void;
  onOpenReleaseCenter: () => void;
}

export default function DatasetFilterBar({
  total,
  loading = false,
  onSearch,
  onRefresh,
  onOpenReleaseCenter,
}: DatasetFilterBarProps) {
  const [keyword, setKeyword] = useState('');
  const [status, setStatus] = useState<'ALL' | DatasetStatus>('ALL');
  const [sourceType, setSourceType] =
    useState<'ALL' | DatasetSourceType>('ALL');

  const submit = (
    nextKeyword = keyword,
    nextStatus = status,
    nextSourceType = sourceType,
  ) => {
    onSearch({
      keyword: nextKeyword.trim(),
      status: nextStatus,
      sourceType: nextSourceType,
    });
  };

  return (
    <div className="mb-4 flex min-w-0 flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
      <div className="inline-flex h-8 w-fit items-center rounded-[8px] bg-[#f2f3f5] px-3.5 text-[13px] font-semibold text-[#242731]">
        全部数据集
        <span className="ml-1.5 text-xs font-medium text-[#98a2b3]">
          {total}
        </span>
      </div>

      <div className="flex shrink-0 flex-wrap items-center gap-2">
        <Input
          allowClear
          value={keyword}
          variant="filled"
          placeholder="搜索 Dataset、字段、来源"
          suffix={
            <SearchOutlined
              className="cursor-pointer text-slate-400 transition-colors hover:text-slate-700"
              onClick={() => submit()}
            />
          }
          className="w-[260px]"
          onChange={(event) => {
            const value = event.target.value;
            setKeyword(value);
            if (!value) submit('', status, sourceType);
          }}
          onPressEnter={() => submit()}
        />

        <Select<'ALL' | DatasetStatus>
          value={status}
          variant="filled"
          className="w-[116px]"
          options={[
            { value: 'ALL', label: '全部状态' },
            { value: 'ONLINE', label: '已上线' },
            { value: 'OFFLINE', label: '已下线' },
          ]}
          onChange={(nextStatus) => {
            setStatus(nextStatus);
            submit(keyword, nextStatus, sourceType);
          }}
        />

        <Select<'ALL' | DatasetSourceType>
          value={sourceType}
          variant="filled"
          className="w-[156px]"
          options={SOURCE_TYPE_OPTIONS}
          onChange={(nextSourceType) => {
            setSourceType(nextSourceType);
            submit(keyword, status, nextSourceType);
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

        <YakButton
          type="primary"
          icon={<CloudUploadOutlined />}
          onClick={onOpenReleaseCenter}
        >
          发布中心
        </YakButton>
      </div>
    </div>
  );
}
