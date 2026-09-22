import { YakButton, YakEmpty } from '@/components/ui';
import type { ExecutionLogLine, ExecutionLogView } from '@/services/data-quality';
import { Spin } from 'antd';
import { RefreshCw } from 'lucide-react';

import { formatExecutionTime } from '../utils';
import { ExecutionSectionCard } from './ExecutionSectionCard';

const logLevelClass: Record<ExecutionLogLine['level'], string> = {
  INFO: 'text-[#7fb3ff]',
  WARN: 'text-[#f7c56b]',
  ERROR: 'text-[#ff8585]',
};

interface ExecutionLogPanelProps {
  logs?: ExecutionLogView;
  loading: boolean;
  onRefresh: () => void;
}

export const ExecutionLogPanel = ({
  logs,
  loading,
  onRefresh,
}: ExecutionLogPanelProps) => (
  <ExecutionSectionCard
    title="原始日志"
    extra={
      <YakButton
        size="small"
        type="text"
        icon={<RefreshCw size={13} />}
        loading={loading}
        className="!text-[#667085]"
        onClick={onRefresh}
      >
        刷新
      </YakButton>
    }
  >
    <div className="px-5 pb-5 pt-1">
      <div className="mb-3 text-[12px] leading-5 text-[#8a8f98]">
        展示当前质量检测的执行过程，便于定位规则采集、比较和异常阶段。
      </div>

      <div className="overflow-hidden rounded-md bg-[#181a1f]">
        {loading && !logs ? (
          <div className="flex min-h-[420px] items-center justify-center">
            <Spin size="small" />
          </div>
        ) : logs?.lines.length ? (
          <div className="max-h-[680px] min-h-[420px] overflow-auto p-4 font-mono text-[12px] leading-5">
            {logs.lines.map((line, index) => (
              <div
                key={`${line.timestamp || 'log'}-${line.stage}-${index}`}
                className="flex gap-3 border-b border-white/[0.06] py-1.5 last:border-b-0"
              >
                <span className="w-[146px] shrink-0 text-[#7d8490]">
                  {formatExecutionTime(line.timestamp)}
                </span>
                <span
                  className={`w-12 shrink-0 font-semibold ${logLevelClass[line.level]}`}
                >
                  {line.level}
                </span>
                <span className="w-24 shrink-0 truncate text-[#9ca3af]">
                  [{line.stage}]
                </span>
                <span className="min-w-0 whitespace-pre-wrap break-all text-[#d6d9df]">
                  {line.message}
                </span>
              </div>
            ))}
          </div>
        ) : (
          <div className="flex min-h-[420px] items-center justify-center bg-[#181a1f]">
            <YakEmpty
              compact
              title="暂无原始日志"
              description="当前运行记录没有返回可展示的日志内容"
              className="[&_div]:text-[#8a9099]"
            />
          </div>
        )}
      </div>
    </div>
  </ExecutionSectionCard>
);
