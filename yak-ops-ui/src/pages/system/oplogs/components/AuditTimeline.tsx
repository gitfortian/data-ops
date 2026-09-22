import {
  CheckCircleOutlined,
  ClockCircleOutlined,
  CloseCircleOutlined,
} from '@ant-design/icons';
import { Collapse, Descriptions, Empty, Tag, Timeline, Typography } from 'antd';
import dayjs from 'dayjs';

import type { AuditTimelineEvent } from '@/services/audit';

interface AuditTimelineProps {
  events: AuditTimelineEvent[];
}

const statusMeta = (status?: string) => {
  if (status === 'FAILURE') {
    return {
      label: '失败',
      color: 'error' as const,
      icon: <CloseCircleOutlined className="text-red-500" />,
    };
  }
  if (status === 'SUCCESS') {
    return {
      label: '成功',
      color: 'success' as const,
      icon: <CheckCircleOutlined className="text-emerald-600" />,
    };
  }
  return {
    label: '记录',
    color: 'default' as const,
    icon: <ClockCircleOutlined className="text-slate-400" />,
  };
};

const formatJson = (value: unknown) => {
  try {
    return JSON.stringify(value ?? {}, null, 2);
  } catch {
    return '{}';
  }
};

export default function AuditTimeline({ events }: AuditTimelineProps) {
  if (!events.length) {
    return <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无审计事件" />;
  }

  return (
    <Timeline
      className="pt-2"
      items={events.map((event) => {
        const status = statusMeta(event.eventStatus);
        const technical = {
          eventType: event.eventType,
          eventCategory: event.eventCategory,
          eventStatus: event.eventStatus,
          actorId: event.actorId,
          resourceType: event.resourceType,
          resourceId: event.resourceId,
          traceId: event.traceId,
          spanId: event.spanId,
          parentEventId: event.parentEventId,
          reasonCode: event.reasonCode,
          rawMessage: event.message,
          payload: event.payload,
        };

        return {
          dot: status.icon,
          children: (
            <div className="pb-3">
              <div className="rounded-lg border border-slate-200 bg-white px-4 py-3">
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <div className="flex min-w-0 items-center gap-2">
                    <span className="font-medium text-slate-800">{event.title}</span>
                    <Tag bordered={false} color={status.color} className="m-0">
                      {status.label}
                    </Tag>
                    {event.eventCategory === 'AUTHORIZATION' ? (
                      <Tag bordered={false} className="m-0">
                        授权
                      </Tag>
                    ) : null}
                  </div>
                  <Typography.Text type="secondary" className="text-xs">
                    {dayjs(event.occurredAt).format('YYYY-MM-DD HH:mm:ss.SSS')}
                  </Typography.Text>
                </div>

                {event.description ? (
                  <div className="mt-2 text-sm leading-6 text-slate-600">
                    {event.description}
                  </div>
                ) : null}

                <Collapse
                  ghost
                  size="small"
                  className="mt-1 [&_.ant-collapse-header]:px-0"
                  items={[
                    {
                      key: 'technical',
                      label: <span className="text-xs text-slate-500">技术详情</span>,
                      children: (
                        <div className="rounded-md bg-slate-50 p-3">
                          <Descriptions size="small" column={1} colon={false}>
                            <Descriptions.Item label="Event Type">
                              {event.eventType}
                            </Descriptions.Item>
                            <Descriptions.Item label="Trace / Span">
                              {event.traceId || '-'} / {event.spanId || '-'}
                            </Descriptions.Item>
                            <Descriptions.Item label="Parent Event">
                              {event.parentEventId ?? '-'}
                            </Descriptions.Item>
                            <Descriptions.Item label="Resource">
                              {event.resourceType || '-'} / {event.resourceId || '-'}
                            </Descriptions.Item>
                            <Descriptions.Item label="Reason Code">
                              {event.reasonCode || '-'}
                            </Descriptions.Item>
                          </Descriptions>
                          <pre className="mb-0 mt-2 max-h-64 overflow-auto whitespace-pre-wrap break-all rounded bg-white p-3 text-xs text-slate-600">
                            {formatJson(technical)}
                          </pre>
                        </div>
                      ),
                    },
                  ]}
                />
              </div>
            </div>
          ),
        };
      })}
    />
  );
}
