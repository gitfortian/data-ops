import { EyeOutlined } from '@ant-design/icons';
import type { TableColumnsType } from 'antd';
import { Tag, Typography } from 'antd';
import { useMemo } from 'react';

import { YakButton } from '@/components/ui';
import type { OperationLog } from '@/services/security/operationLogs';

import { formatSystemDateTime } from '../../utils';

interface UseOperationLogColumnsOptions {
  onDetail: (log: OperationLog) => void;
}

export function useOperationLogColumns({
  onDetail,
}: UseOperationLogColumnsOptions): TableColumnsType<OperationLog> {
  return useMemo<TableColumnsType<OperationLog>>(
    () => [
      {
        title: '日志 ID',
        dataIndex: 'id',
        key: 'id',
        width: 110,
        render: (value: number) => (
          <Typography.Text copyable={{ text: String(value) }}>
            {value}
          </Typography.Text>
        ),
      },
      {
        title: '操作',
        dataIndex: 'operateType',
        key: 'operateType',
        width: 190,
        render: (_, row) => (
          <div className="min-w-0">
            <div className="truncate font-medium text-slate-700">
              {row.operateType || '-'}
            </div>
            {row.operationMethods && (
              <div className="mt-1">
                <Tag className="!mr-0">{row.operationMethods}</Tag>
              </div>
            )}
          </div>
        ),
      },
      {
        title: '操作人',
        dataIndex: 'operator',
        key: 'operator',
        width: 180,
        render: (_, row) => (
          <div className="min-w-0">
            <div className="truncate text-slate-700">
              {row.operator || '-'}
            </div>
            <div className="mt-1 truncate font-mono text-xs text-slate-400">
              {row.operatorIp || '-'}
            </div>
          </div>
        ),
      },
      {
        title: '操作页面',
        dataIndex: 'operatePage',
        key: 'operatePage',
        width: 180,
        ellipsis: true,
        render: (value?: string) => value || '-',
      },
      {
        title: '操作对象',
        dataIndex: 'target',
        key: 'target',
        width: 260,
        render: (_, row) => (
          <div className="min-w-0">
            <Typography.Text
              ellipsis={{ tooltip: row.target }}
              copyable={row.target ? { text: row.target } : false}
              className="max-w-full"
            >
              {row.target || '-'}
            </Typography.Text>
            <div className="mt-1 text-xs text-slate-400">
              {row.targetType || '未分类'}
            </div>
          </div>
        ),
      },
      {
        title: '操作时间',
        dataIndex: 'createTime',
        key: 'createTime',
        width: 175,
        render: (value?: string) => formatSystemDateTime(value),
      },
      {
        title: '操作',
        key: 'action',
        width: 90,
        fixed: 'right',
        render: (_, row) => (
          <YakButton
            type="text"
            size="small"
            icon={<EyeOutlined />}
            onClick={() => onDetail(row)}
          >
            详情
          </YakButton>
        ),
      },
    ],
    [onDetail],
  );
}
