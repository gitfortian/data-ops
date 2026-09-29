import {
  FilterOutlined,
  ReloadOutlined,
  SearchOutlined,
} from '@ant-design/icons';
import {
  DatePicker,
  Input,
  Popover,
  Select,
  Space,
} from 'antd';
import type { Dayjs } from 'dayjs';
import { useMemo, useState } from 'react';

import {
  YakButton,
  YakFilterSwitch,
  type YakFilterSwitchOption,
} from '@/components/ui';
import type { OperationLogOptions } from '@/services/security/operationLogs';

import {
  ALL_OPERATION_METHOD_FILTER,
  OPERATION_LOG_SEARCH_FIELDS,
  OPERATION_LOG_SEARCH_PLACEHOLDERS,
} from '../constants';
import type {
  OperationLogFilterValues,
  OperationLogSearchField,
} from '../types';

interface OperationLogFilterBarProps {
  options: OperationLogOptions;
  loading?: boolean;
  onSearch: (values: OperationLogFilterValues) => void;
  onRefresh: () => void;
}

interface AdvancedFilters {
  operateType?: string;
  operatePage?: string;
  targetType?: string;
  timeRange?: [Dayjs, Dayjs];
}

const clean = (value?: string): string | undefined => {
  const normalized = value?.trim();
  return normalized || undefined;
};

const selectOptions = (values: string[]) =>
  values.map((value) => ({ label: value, value }));

export default function OperationLogFilterBar({
  options,
  loading = false,
  onSearch,
  onRefresh,
}: OperationLogFilterBarProps) {
  const [searchField, setSearchField] =
    useState<OperationLogSearchField>('operator');
  const [keyword, setKeyword] = useState('');
  const [method, setMethod] = useState<string>();
  const [advanced, setAdvanced] =
    useState<AdvancedFilters>({});
  const [draftAdvanced, setDraftAdvanced] =
    useState<AdvancedFilters>({});
  const [advancedOpen, setAdvancedOpen] = useState(false);

  const methodFilterOptions = useMemo<
    YakFilterSwitchOption<string>[]
  >(
    () => [
      { value: ALL_OPERATION_METHOD_FILTER, label: '全部' },
      ...Array.from(new Set(options.operationMethods)).map(
        (value) => ({ value, label: value }),
      ),
    ],
    [options.operationMethods],
  );

  const createFilters = (
    nextKeyword = keyword,
    nextField = searchField,
    nextMethod = method,
    nextAdvanced = advanced,
  ): OperationLogFilterValues => {
    const values: OperationLogFilterValues = {
      operationMethods: nextMethod,
      operateType: nextAdvanced.operateType,
      operatePage: nextAdvanced.operatePage,
      targetType: nextAdvanced.targetType,
      startTime: nextAdvanced.timeRange?.[0]
        ?.startOf('second')
        .valueOf(),
      endTime: nextAdvanced.timeRange?.[1]
        ?.endOf('second')
        .valueOf(),
    };

    const normalizedKeyword = clean(nextKeyword);
    if (normalizedKeyword) values[nextField] = normalizedKeyword;
    return values;
  };

  const submit = () => onSearch(createFilters());

  const changeMethod = (value: string) => {
    const nextMethod =
      value === ALL_OPERATION_METHOD_FILTER ? undefined : value;
    setMethod(nextMethod);
    onSearch(
      createFilters(keyword, searchField, nextMethod, advanced),
    );
  };

  const changeSearchField = (field: OperationLogSearchField) => {
    setSearchField(field);
    setKeyword('');
    onSearch(createFilters('', field, method, advanced));
  };

  const applyAdvanced = () => {
    setAdvanced(draftAdvanced);
    setAdvancedOpen(false);
    onSearch(
      createFilters(
        keyword,
        searchField,
        method,
        draftAdvanced,
      ),
    );
  };

  const resetAdvanced = () => {
    const empty: AdvancedFilters = {};
    setAdvanced(empty);
    setDraftAdvanced(empty);
    setAdvancedOpen(false);
    onSearch(createFilters(keyword, searchField, method, empty));
  };

  const advancedCount = [
    advanced.operateType,
    advanced.operatePage,
    advanced.targetType,
    advanced.timeRange,
  ].filter(Boolean).length;

  const advancedContent = (
    <div className="w-[360px] max-w-[calc(100vw-48px)] space-y-4 p-1">
      <div>
        <div className="mb-1.5 text-sm text-slate-600">操作类型</div>
        <Select
          allowClear
          showSearch
          value={draftAdvanced.operateType}
          variant="filled"
          options={selectOptions(options.operateTypes)}
          placeholder="全部操作类型"
          className="w-full"
          onChange={(value) =>
            setDraftAdvanced((current) => ({
              ...current,
              operateType: value,
            }))
          }
        />
      </div>

      <div>
        <div className="mb-1.5 text-sm text-slate-600">操作页面</div>
        <Select
          allowClear
          showSearch
          value={draftAdvanced.operatePage}
          variant="filled"
          options={selectOptions(options.operatePages)}
          placeholder="全部操作页面"
          className="w-full"
          onChange={(value) =>
            setDraftAdvanced((current) => ({
              ...current,
              operatePage: value,
            }))
          }
        />
      </div>

      <div>
        <div className="mb-1.5 text-sm text-slate-600">目标类型</div>
        <Select
          allowClear
          showSearch
          value={draftAdvanced.targetType}
          variant="filled"
          options={selectOptions(options.targetTypes)}
          placeholder="全部目标类型"
          className="w-full"
          onChange={(value) =>
            setDraftAdvanced((current) => ({
              ...current,
              targetType: value,
            }))
          }
        />
      </div>

      <div>
        <div className="mb-1.5 text-sm text-slate-600">操作时间</div>
        <DatePicker.RangePicker
          showTime
          value={draftAdvanced.timeRange}
          variant="filled"
          className="w-full"
          onChange={(value) =>
            setDraftAdvanced((current) => ({
              ...current,
              timeRange:
                value?.[0] && value?.[1]
                  ? [value[0], value[1]]
                  : undefined,
            }))
          }
        />
      </div>

      <div className="flex justify-end gap-2 border-t border-slate-100 pt-3">
        <YakButton onClick={resetAdvanced}>重置</YakButton>
        <YakButton type="primary" onClick={applyAdvanced}>
          应用筛选
        </YakButton>
      </div>
    </div>
  );

  return (
    <div className="mb-4 flex min-w-0 flex-col gap-4 xl:flex-row xl:items-center xl:justify-between">
      <div className="min-w-0 overflow-x-auto pb-1 xl:pb-0">
        <YakFilterSwitch
          value={method ?? ALL_OPERATION_METHOD_FILTER}
          options={methodFilterOptions}
          onChange={changeMethod}
        />
      </div>

      <div className="flex shrink-0 flex-wrap items-center gap-2">
        <Space.Compact className="w-[330px] max-w-full">
          <Select<OperationLogSearchField>
            value={searchField}
            options={OPERATION_LOG_SEARCH_FIELDS}
            variant="filled"
            popupMatchSelectWidth={120}
            className="w-[104px] shrink-0"
            onChange={changeSearchField}
          />
          <Input
            allowClear
            value={keyword}
            variant="filled"
            placeholder={OPERATION_LOG_SEARCH_PLACEHOLDERS[searchField]}
            suffix={
              <SearchOutlined
                className="cursor-pointer text-slate-400 hover:text-slate-700"
                onClick={submit}
              />
            }
            className="min-w-0 flex-1"
            onChange={(event) => {
              const value = event.target.value;
              setKeyword(value);
              if (!value) {
                onSearch(
                  createFilters('', searchField, method, advanced),
                );
              }
            }}
            onPressEnter={submit}
          />
        </Space.Compact>

        <Popover
          trigger="click"
          placement="bottomRight"
          open={advancedOpen}
          content={advancedContent}
          onOpenChange={(open) => {
            setAdvancedOpen(open);
            if (open) setDraftAdvanced(advanced);
          }}
        >
          <YakButton icon={<FilterOutlined />}>
            高级筛选
            {advancedCount > 0 ? ` (${advancedCount})` : ''}
          </YakButton>
        </Popover>

        <YakButton
          icon={<ReloadOutlined />}
          loading={loading}
          onClick={onRefresh}
        >
          刷新
        </YakButton>
      </div>
    </div>
  );
}
