import { YakEmpty } from '@/components/ui';
import type { ExecutionWorkspaceRuleView } from '@/services/data-quality';
import { Table, Tag, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useMemo } from 'react';

import { CheckResultTag } from '../../../components/QualityStatus';
import { dataQualityTableClassName } from '../../../components/tableStyle';
import {
  formatExecutionDuration,
  qualityRuleScopeLabel,
} from '../utils';

interface ExecutionRuleTableProps {
  records: ExecutionWorkspaceRuleView[];
  issueMode?: boolean;
}

export const ExecutionRuleTable = ({
  records,
  issueMode = false,
}: ExecutionRuleTableProps) => {
  const columns = useMemo<ColumnsType<ExecutionWorkspaceRuleView>>(
    () => [
      {
        title: '规则名称 / 模板',
        width: 250,
        render: (_, record) => (
          <div className="min-w-0 py-0.5">
            <div className="truncate font-medium text-[#30343b]">
              {record.ruleName}
            </div>
            <div className="mt-1 truncate text-[11px] text-[#98a2b3]">
              {record.templateCode}
            </div>
          </div>
        ),
      },
      {
        title: '关联范围',
        dataIndex: 'scope',
        width: 100,
        render: (value) => (
          <Tag className="!m-0 !border-0 !bg-[#fff0f3] !text-[11px] !text-[#fe2c55]">
            {qualityRuleScopeLabel(value)}
          </Tag>
        ),
      },
      {
        title: '质量维度',
        dataIndex: 'dimension',
        width: 110,
      },
      {
        title: '检查字段',
        dataIndex: 'columnName',
        width: 140,
        render: (value) => value || '整表',
      },
      {
        title: '检查结果',
        dataIndex: 'checkResult',
        width: 110,
        render: (value) => <CheckResultTag value={value} />,
      },
      {
        title: '实际值 / 期望值',
        width: 210,
        render: (_, record) => (
          <div className="space-y-1 text-[12px]">
            <div className="text-[#344054]">
              实际：{record.metricValue || '--'}
            </div>
            <div className="text-[#98a2b3]">
              期望：{record.expectedValue || '--'}
            </div>
          </div>
        ),
      },
      {
        title: '耗时',
        dataIndex: 'durationMs',
        width: 100,
        align: 'right',
        render: formatExecutionDuration,
      },
    ],
    [],
  );

  if (!records.length) {
    return (
      <YakEmpty
        compact
        title={issueMode ? '本次运行没有问题规则' : '暂无规则检测结果'}
        description={
          issueMode
            ? '本次运行的规则均未产生未通过或异常结果'
            : '当前运行记录没有返回规则检测明细'
        }
      />
    );
  }

  return (
    <Table<ExecutionWorkspaceRuleView>
      rowKey="id"
      size="small"
      pagination={false}
      scroll={{ x: 1040 }}
      className={dataQualityTableClassName(
        '[&_.ant-table-container]:!border [&_.ant-table-container]:!border-solid',
      )}
      dataSource={records}
      columns={columns}
      expandable={{
        expandedRowRender: (record) => (
          <div className="space-y-3 px-2 py-2">
            {(record.errorMessage || issueMode) && (
              <div className="rounded-md bg-[#fff5f5] px-3 py-2 text-[12px] leading-5 text-[#d92d20]">
                {record.errorMessage || '质量指标未满足预期阈值'}
              </div>
            )}
            <div>
              <div className="mb-2 text-[11px] font-medium text-[#8a8f98]">
                执行 SQL
              </div>
              <Typography.Paragraph
                copyable
                className="!mb-0 max-h-[360px] overflow-auto whitespace-pre-wrap break-words rounded-md !bg-[#181a1f] !p-4 font-mono !text-[12px] !leading-5 !text-[#d6d9df]"
              >
                {record.executedSql || '未生成执行 SQL'}
              </Typography.Paragraph>
            </div>
          </div>
        ),
      }}
    />
  );
};
