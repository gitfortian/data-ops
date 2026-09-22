import { Tag } from 'antd';
import { useEffect, useState } from 'react';

import { findByBiz } from '@/services/approval/api';
import type { InstanceStatus, StepStatus } from '@/services/approval/types';

const STATUS_META: Record<InstanceStatus, { label: string; color: string }> = {
  PENDING: { label: '审批中', color: 'processing' },
  APPROVED: { label: '已通过', color: 'success' },
  REJECTED: { label: '已拒绝', color: 'error' },
  CANCELED: { label: '已撤销', color: 'default' },
};

export const STEP_STATUS_META: Record<StepStatus, { label: string; color: string }> = {
  WAITING: { label: '未到级', color: 'default' },
  PENDING: { label: '待审批', color: 'processing' },
  APPROVED: { label: '已通过', color: 'success' },
  REJECTED: { label: '已拒绝', color: 'error' },
  SKIPPED: { label: '已跳过', color: 'warning' },
};

/** 纯展示:审批单终态/在途状态标签。 */
export const ApprovalStatusTag = ({ status }: { status: InstanceStatus }) => {
  const meta = STATUS_META[status];
  return <Tag color={meta?.color ?? 'default'}>{meta?.label ?? status}</Tag>;
};

/**
 * 业务页嵌入(ticket 107):按业务对象查一次审批状态(在途优先,否则最近一单),
 * 无单据时返回 null 由业务页自行渲染"提交审批"按钮态。
 */
export const ApprovalBizTag = ({
  flowCode,
  bizType,
  bizId,
}: {
  flowCode: string;
  bizType: string;
  bizId: string | number;
}) => {
  const [status, setStatus] = useState<InstanceStatus | null>(null);

  useEffect(() => {
    let alive = true;
    setStatus(null);
    findByBiz(flowCode, bizType, bizId)
      .then((instance) => {
        if (alive) setStatus(instance?.status ?? null);
      })
      .catch(() => {
        /* 查询失败不阻塞业务页,保持无标签态 */
      });
    return () => {
      alive = false;
    };
  }, [flowCode, bizType, bizId]);

  return status ? <ApprovalStatusTag status={status} /> : null;
};
