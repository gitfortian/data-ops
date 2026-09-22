import { Drawer, Descriptions, Empty, Spin, Tag, Typography, message } from 'antd';
import dayjs from 'dayjs';
import { useEffect, useMemo, useState } from 'react';

import YakTab from '@/components/YakTab';
import {
  getAuditOperation,
  type AuditOperationDetail,
  type AuditOperationSummary,
} from '@/services/audit';

import AuditTimeline from './AuditTimeline';

interface AuditOperationDetailDrawerProps {
  operationId?: string;
  open: boolean;
  onClose: () => void;
}

const statusMeta = (status?: string) => {
  if (status === 'FAILED') return { label: '失败', color: 'error' as const };
  if (status === 'SUCCEEDED') return { label: '成功', color: 'success' as const };
  if (status === 'RUNNING') return { label: '运行中', color: 'processing' as const };
  return { label: status || '-', color: 'default' as const };
};

const operationDisplayName = (operation: AuditOperationSummary) =>
  operation.operationType === 'AUTHORIZATION_CHECK'
    ? '权限检查'
    : operation.operationName || operation.operationType;

const formatDuration = (durationMillis?: number) => {
  if (durationMillis == null) return '-';
  if (durationMillis < 1000) return `${durationMillis} ms`;
  if (durationMillis < 60_000) return `${(durationMillis / 1000).toFixed(1)} s`;
  const minutes = Math.floor(durationMillis / 60_000);
  const seconds = Math.floor((durationMillis % 60_000) / 1000);
  return `${minutes}m ${seconds}s`;
};

const formatJson = (value: unknown) => {
  try {
    return JSON.stringify(value ?? {}, null, 2);
  } catch {
    return '{}';
  }
};

export default function AuditOperationDetailDrawer({
  operationId,
  open,
  onClose,
}: AuditOperationDetailDrawerProps) {
  const [detail, setDetail] = useState<AuditOperationDetail>();
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (!open || !operationId) return;

    let active = true;
    setLoading(true);
    setDetail(undefined);
    void getAuditOperation(operationId)
      .then((data) => {
        if (active) setDetail(data);
      })
      .catch(() => {
        if (active) void message.error('加载审计详情失败');
      })
      .finally(() => {
        if (active) setLoading(false);
      });

    return () => {
      active = false;
    };
  }, [open, operationId]);

  const tabs = useMemo(() => {
    if (!detail) return [];
    return [
      {
        key: 'timeline',
        label: '业务时间线',
        children: <AuditTimeline events={detail.events} />,
      },
      {
        key: 'snapshot',
        label: '原始快照',
        children: (
          <pre className="max-h-[56vh] overflow-auto whitespace-pre-wrap break-all rounded-lg bg-slate-50 p-4 text-xs leading-6 text-slate-600">
            {formatJson({ operation: detail.operation, metadata: detail.metadata })}
          </pre>
        ),
      },
    ];
  }, [detail]);

  const operation = detail?.operation;
  const status = statusMeta(operation?.status);

  return (
    <Drawer
      title="审计详情"
      width={840}
      open={open}
      onClose={onClose}
      destroyOnClose
    >
      <Spin spinning={loading}>
        {!loading && !detail ? (
          <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无审计详情" />
        ) : null}

        {detail && operation ? (
          <div>
            <div className="mb-4 rounded-lg border border-slate-200 bg-white p-4">
              <div className="mb-3 flex flex-wrap items-start justify-between gap-3">
                <div>
                  <div className="text-base font-semibold text-slate-800">
                    {operationDisplayName(operation)}
                  </div>
                  <Typography.Text copyable type="secondary" className="text-xs">
                    {operation.operationId}
                  </Typography.Text>
                </div>
                <Tag bordered={false} color={status.color} className="m-0">
                  {status.label}
                </Tag>
              </div>

              <Descriptions size="small" column={2} colon={false}>
                <Descriptions.Item label="操作人">
                  {operation.actorName || operation.actorId || '-'}
                </Descriptions.Item>
                <Descriptions.Item label="项目空间">
                  {operation.projectName || (operation.projectId ? `#${operation.projectId}` : '全局')}
                </Descriptions.Item>
                <Descriptions.Item label="资源">
                  {operation.resourceName || operation.resourceId || '-'}
                  {operation.resourceType ? ` · ${operation.resourceType}` : ''}
                </Descriptions.Item>
                <Descriptions.Item label="来源">{operation.source || '-'}</Descriptions.Item>
                <Descriptions.Item label="开始时间">
                  {operation.startedAt
                    ? dayjs(operation.startedAt).format('YYYY-MM-DD HH:mm:ss.SSS')
                    : '-'}
                </Descriptions.Item>
                <Descriptions.Item label="耗时">
                  {formatDuration(operation.durationMillis)}
                </Descriptions.Item>
                {operation.errorCode ? (
                  <Descriptions.Item label="错误码" span={2}>
                    {operation.errorCode}
                  </Descriptions.Item>
                ) : null}
                {operation.summary ? (
                  <Descriptions.Item label="摘要" span={2}>
                    {operation.summary}
                  </Descriptions.Item>
                ) : null}
              </Descriptions>
            </div>

            <YakTab defaultActiveKey="timeline" items={tabs} />
          </div>
        ) : null}
      </Spin>
    </Drawer>
  );
}
