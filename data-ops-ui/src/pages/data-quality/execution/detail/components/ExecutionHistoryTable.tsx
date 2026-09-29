import { YakButton, YakEmpty } from '@/components/ui';
import type { ExecutionWorkspaceListItem } from '@/services/data-quality';
import { Table } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useMemo } from 'react';

import {
  CheckResultTag,
  ExecutionStatusTag,
} from '../../../components/QualityStatus';
import { dataQualityTableClassName } from '../../../components/tableStyle';
import {
  formatExecutionDuration,
  formatExecutionTime,
  qualityExecutionIssueCount,
} from '../utils';

interface ExecutionHistoryTableProps {
  records: ExecutionWorkspaceListItem[];
  loading: boolean;
  currentExecutionNo: string;
  onOpen: (executionNo: string) => void;
}

export const ExecutionHistoryTable = ({
  records,
  loading,
  currentExecutionNo,
  onOpen,
}: ExecutionHistoryTableProps) => {
  const columns = useMemo<ColumnsType<ExecutionWorkspaceListItem>>(
    () => [
      {
        title: '运行时间',
        width: 190,
        render: (_, record) => (
          <div>
            <div className="text-[12px] font-medium text-[#344054]">
              {formatExecutionTime(record.startedAt || record.queuedAt)}
            </div>
            <div className="mt-1 max-w-[240px] truncate text-[11px] text-[#98a2b3]">
              {record.executionNo}
            </div>
          </div>
        ),
      },
      {
        title: '执行状态',
        dataIndex: 'executionStatus',
        width: 110,
        render: (value) => <ExecutionStatusTag value={value} />,
      },
      {
        title: '质量结果',
        dataIndex: 'checkResult',
        width: 110,
        render: (value) => <CheckResultTag value={value} />,
      },
      {
        title: '规则概况',
        minWidth: 260,
        render: (_, record) => (
          <div className="flex flex-wrap gap-x-3 gap-y-1 text-[12px]">
            <span className="text-[#344054]">通过 {record.passedRules}</span>
            <span className="text-[#b54708]">未通过 {record.failedRules}</span>
            <span className="text-[#d92d20]">异常 {record.errorRules}</span>
          </div>
        ),
      },
      {
        title: '问题数量',
        width: 100,
        align: 'right',
        render: (_, record) => qualityExecutionIssueCount(record),
      },
      {
        title: '耗时',
        dataIndex: 'durationMs',
        width: 110,
        align: 'right',
        render: formatExecutionDuration,
      },
      {
        title: '操作',
        width: 80,
        fixed: 'right',
        render: (_, record) => (
          <YakButton
            type="text"
            size="small"
            disabled={record.executionNo === currentExecutionNo}
            onClick={() => onOpen(record.executionNo)}
          >
            查看
          </YakButton>
        ),
      },
    ],
    [currentExecutionNo, onOpen],
  );

  if (!loading && !records.length) {
    return (
      <YakEmpty
        compact
        title="暂无历史运行记录"
        description="当前监控还没有更多可查看的历史执行记录"
      />
    );
  }

  return (
    <Table<ExecutionWorkspaceListItem>
      rowKey="executionNo"
      size="small"
      loading={loading}
      pagination={false}
      scroll={{ x: 980 }}
      className={dataQualityTableClassName(
        '[&_.ant-table-container]:!border [&_.ant-table-container]:!border-solid',
      )}
      dataSource={records}
      columns={columns}
      rowClassName={(record) =>
        record.executionNo === currentExecutionNo ? 'bg-[#fff8fa]' : ''
      }
    />
  );
};
