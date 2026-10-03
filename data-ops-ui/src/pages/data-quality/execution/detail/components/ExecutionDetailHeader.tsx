import { YakButton } from '@/components/ui';
import type {
  ExecutionWorkspaceListItem,
  ExecutionWorkspaceView,
} from '@/services/data-quality';
import { Select, Tooltip } from 'antd';
import {
  ArrowLeft,
  ArrowRight,
  Database,
  RefreshCw,
  ShieldCheck,
  Table2,
} from 'lucide-react';
import { useMemo } from 'react';

import { CheckResultTag, ExecutionStatusTag } from '../../../components/QualityStatus';
import {
  formatExecutionTime,
  qualityExecutionTriggerLabel,
} from '../utils';

interface ExecutionDetailHeaderProps {
  detail: ExecutionWorkspaceView;
  historyRecords: ExecutionWorkspaceListItem[];
  historyLoading: boolean;
  refreshing: boolean;
  backLabel?: string;
  onBack: () => void;
  onRefresh: () => void;
  onSelectExecution: (executionNo: string) => void;
}

export const ExecutionDetailHeader = ({
  detail,
  historyRecords,
  historyLoading,
  refreshing,
  backLabel = '返回运行记录',
  onBack,
  onRefresh,
  onSelectExecution,
}: ExecutionDetailHeaderProps) => {
  const historyOptions = useMemo(() => {
    const records = historyRecords.some(
      (record) => record.executionNo === detail.executionNo,
    )
      ? historyRecords
      : [detail, ...historyRecords];

    return records.map((record) => ({
      value: record.executionNo,
      label: `${formatExecutionTime(record.startedAt || record.queuedAt)} · ${record.monitorName}`,
    }));
  }, [detail, historyRecords]);

  return (
    <>
      <div className="mb-2 flex h-10 items-center">
        <YakButton
          type="text"
          icon={<ArrowLeft size={15} />}
          className="!h-9 !px-1 !text-[14px] !font-semibold !text-[#30343b]"
          onClick={onBack}
        >
          {backLabel}
        </YakButton>
      </div>

      <section className="overflow-hidden rounded-lg bg-white">
        <div className="grid gap-4 px-5 py-4 lg:px-6 xl:grid-cols-[48px_minmax(0,1fr)_330px] xl:items-center">
          <div className="flex h-12 w-12 items-center justify-center rounded-lg bg-[#f2f4f7] text-[#475467] max-xl:hidden">
            <ShieldCheck size={24} strokeWidth={1.5} />
          </div>

          <div className="min-w-0">
            <div className="flex min-w-0 flex-wrap items-center gap-2">
              <h1 className="m-0 max-w-[560px] truncate text-[18px] font-semibold leading-7 text-[#161823]">
                {detail.monitorName || '质量检测运行记录'}
              </h1>
              <ExecutionStatusTag value={detail.executionStatus} />
              <CheckResultTag value={detail.checkResult} />
            </div>

            <div className="mt-1 truncate text-[12px] leading-5 text-[#667085]">
              {detail.executionNo}
            </div>

            <div className="mt-4 flex flex-wrap items-center gap-x-5 gap-y-2 text-[12px] text-[#667085]">
              <span>{formatExecutionTime(detail.startedAt || detail.queuedAt)}</span>
              <span>{qualityExecutionTriggerLabel(detail.triggerType)}</span>
              <span>{detail.operator || 'system'}</span>
            </div>

            <div className="mt-3 flex min-w-0 flex-wrap items-center gap-2 text-[12px] text-[#667085]">
              <span className="flex max-w-[280px] items-center gap-1.5 truncate">
                <Database size={13} className="shrink-0 text-[#667085]" />
                <span className="truncate">{detail.dataSourceName || '数据源'}</span>
              </span>
              <ArrowRight size={13} className="shrink-0 text-[#c0c4cc]" />
              <span className="flex max-w-[420px] items-center gap-1.5 truncate">
                <Table2 size={13} className="shrink-0 text-[#667085]" />
                <span className="truncate">{detail.objectName || detail.tableName || '监控对象'}</span>
              </span>
            </div>
          </div>

          <div className="min-w-0 xl:justify-self-end">
            <div className="mb-2 text-[12px] leading-4 text-[#667085]">
              切换同一监控的运行记录
            </div>
            <div className="flex gap-2">
              <Select
                showSearch
                variant="filled"
                value={detail.executionNo}
                options={historyOptions}
                optionFilterProp="label"
                loading={historyLoading}
                className="min-w-0 flex-1 xl:w-[278px]"
                onChange={onSelectExecution}
                notFoundContent="暂无历史运行记录"
              />
              <Tooltip title="刷新运行详情">
                <YakButton
                  iconOnly
                  aria-label="刷新运行详情"
                  icon={<RefreshCw size={14} />}
                  loading={refreshing}
                  onClick={onRefresh}
                />
              </Tooltip>
            </div>
          </div>
        </div>

        {detail.errorMessage ? (
          <div className="mx-5 mb-5 rounded-md bg-[#fff5f5] px-4 py-3 text-[12px] leading-5 text-[#d92d20] lg:mx-6">
            <span className="font-medium">执行异常：</span>
            {detail.errorMessage}
          </div>
        ) : null}
      </section>
    </>
  );
};
