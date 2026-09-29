import { useModel, useParams } from '@umijs/max';
import {
  Button,
  Card,
  Col,
  Input,
  Modal,
  Row,
  Space,
  Spin,
  Tag,
  Timeline,
  message,
} from 'antd';
import dayjs from 'dayjs';
import { useCallback, useEffect, useMemo, useState } from 'react';

import { ApprovalStatusTag, STEP_STATUS_META } from '@/components/ApprovalStatusTag';
import { YakEmpty } from '@/components/ui';
import {
  approveInstance,
  cancelInstance,
  getApprovalDetail,
  rejectInstance,
} from '@/services/approval/api';
import type { ApprovalDetail } from '@/services/approval/types';

const fmt = (value?: string | null) =>
  value ? dayjs(value).format('YYYY-MM-DD HH:mm:ss') : '-';

const parsePayload = (payloadJson?: string | null): Record<string, unknown> => {
  if (!payloadJson) return {};
  try {
    const parsed = JSON.parse(payloadJson);
    return parsed && typeof parsed === 'object' ? parsed : { value: parsed };
  } catch {
    return { payload: payloadJson };
  }
};

const STEP_DOT_COLOR: Record<string, string> = {
  APPROVED: 'green',
  REJECTED: 'red',
  PENDING: 'blue',
  SKIPPED: 'gray',
  WAITING: 'gray',
};

const ApprovalDetailPage = () => {
  const params = useParams<{ id: string }>();
  const id = Number(params.id);
  const { initialState } = useModel('@@initialState');
  const operator = initialState?.currentUser?.userName;
  const permissionCodes = initialState?.currentUser?.permissionCodes ?? [];
  const canApprove =
    permissionCodes.includes('data-approval:approve') ||
    permissionCodes.includes('security:root');

  const [detail, setDetail] = useState<ApprovalDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [decision, setDecision] = useState<'approve' | 'reject' | 'cancel' | null>(null);
  const [comment, setComment] = useState('');
  const [submitting, setSubmitting] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setDetail(await getApprovalDetail(id));
    } catch (error: any) {
      message.error(error?.message ?? '审批单加载失败');
      setDetail(null);
    } finally {
      setLoading(false);
    }
  }, [id]);

  useEffect(() => {
    if (Number.isFinite(id)) void load();
  }, [id, load]);

  const instance = detail?.instance;
  const steps = detail?.steps ?? [];

  /** 轮到我:单据在途且我有本级 PENDING 步骤(后端同规则 49004 兜底)。 */
  const myTurn = useMemo(
    () =>
      Boolean(
        instance &&
          instance.status === 'PENDING' &&
          operator &&
          steps.some((step) => step.approver === operator && step.status === 'PENDING'),
      ),
    [instance, steps, operator],
  );
  const canCancel = Boolean(
    instance && instance.status === 'PENDING' && operator && instance.applicant === operator,
  );

  const payload = useMemo(() => parsePayload(instance?.payloadJson), [instance]);

  const openDecision = (next: 'approve' | 'reject' | 'cancel') => {
    setComment('');
    setDecision(next);
  };

  const submitDecision = async () => {
    if (!decision) return;
    if (decision === 'reject' && !comment.trim()) {
      message.warning('拒绝必须填写审批意见');
      return;
    }
    setSubmitting(true);
    try {
      if (decision === 'approve') {
        await approveInstance(id, comment.trim() || undefined);
        message.success('已通过');
      } else if (decision === 'reject') {
        await rejectInstance(id, comment.trim());
        message.success('已拒绝');
      } else {
        await cancelInstance(id, comment.trim() || undefined);
        message.success('已撤销');
      }
      setDecision(null);
      void load();
    } catch (error: any) {
      message.error(error?.message ?? '操作失败');
    } finally {
      setSubmitting(false);
    }
  };

  if (loading) {
    return (
      <div className="flex min-h-[50dvh] items-center justify-center">
        <Spin size="large" />
      </div>
    );
  }

  if (!instance) {
    return (
      <div className="min-h-[calc(100dvh-64px)] bg-white px-6 py-10">
        <YakEmpty description="审批单不存在或无权查看" />
      </div>
    );
  }

  return (
    <div className="min-h-[calc(100dvh-64px)] bg-white px-6 pb-8 pt-5 text-[#242731] max-md:px-4">
      <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
        <div className="flex items-center gap-3">
          <span className="text-[20px] font-semibold leading-7">{instance.title}</span>
          <ApprovalStatusTag status={instance.status} />
        </div>
        <Space>
          {myTurn && canApprove ? (
            <>
              <Button onClick={() => openDecision('reject')}>拒绝</Button>
              <Button type="primary" onClick={() => openDecision('approve')}>
                通过
              </Button>
            </>
          ) : null}
          {canCancel ? <Button danger onClick={() => openDecision('cancel')}>撤销</Button> : null}
        </Space>
      </div>

      <Row gutter={16}>
        <Col xs={24} lg={14}>
          <Card title="审批依据" size="small">
            <div className="mb-3 grid grid-cols-2 gap-x-6 gap-y-2 text-[13px] md:grid-cols-3">
              <div><span className="text-[#667085]">流程：</span>{instance.flowName}</div>
              <div><span className="text-[#667085]">发起人：</span>{instance.applicant}</div>
              <div><span className="text-[#667085]">发起时间：</span>{fmt(instance.createTime)}</div>
              <div><span className="text-[#667085]">业务对象：</span>{instance.bizType} / {instance.bizId}</div>
              <div><span className="text-[#667085]">当前级次：</span>{instance.status === 'PENDING' ? `第 ${instance.currentLevel} 级` : '-'}</div>
              <div><span className="text-[#667085]">结束时间：</span>{fmt(instance.finishTime)}</div>
            </div>
            {Object.keys(payload).length > 0 ? (
              <table className="w-full border-collapse text-[13px]">
                <tbody>
                  {Object.entries(payload).map(([key, value]) => (
                    <tr key={key} className="border-b border-solid border-[#f0f0f0] last:border-b-0">
                      <th className="w-40 bg-[#fafafa] px-3 py-2 text-left font-normal text-[#667085]">
                        {key}
                      </th>
                      <td className="px-3 py-2 break-all">
                        {typeof value === 'object' ? JSON.stringify(value) : String(value)}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            ) : (
              <YakEmpty description="无附加审批依据" />
            )}
          </Card>
        </Col>
        <Col xs={24} lg={10}>
          <Card title="审批记录" size="small">
            <Timeline
              items={steps.map((step) => {
                const meta = STEP_STATUS_META[step.status];
                return {
                  key: step.id,
                  color: STEP_DOT_COLOR[step.status] ?? 'gray',
                  children: (
                    <div className="text-[13px]">
                      <div className="flex items-center gap-2">
                        <span className="font-medium">第 {step.levelNo} 级 · {step.approver}</span>
                        {meta ? <Tag color={meta.color}>{meta.label}</Tag> : null}
                      </div>
                      {step.comment ? <div className="mt-1 text-[#4d5769]">{step.comment}</div> : null}
                      <div className="mt-1 text-[#98a2b3]">{fmt(step.handledTime)}</div>
                    </div>
                  ),
                };
              })}
            />
          </Card>
        </Col>
      </Row>

      <Modal
        open={decision !== null}
        title={decision === 'approve' ? '通过审批' : decision === 'reject' ? '拒绝审批' : '撤销审批'}
        okText={decision === 'approve' ? '确认通过' : decision === 'reject' ? '确认拒绝' : '确认撤销'}
        okButtonProps={{
          danger: decision !== 'approve',
          loading: submitting,
        }}
        cancelText="返回"
        onOk={submitDecision}
        onCancel={() => setDecision(null)}
        destroyOnClose
      >
        <Input.TextArea
          rows={3}
          value={comment}
          placeholder={
            decision === 'reject'
              ? '拒绝必须填写审批意见'
              : decision === 'cancel'
                ? '撤销原因（可选，会记入审批记录）'
                : '审批意见（可选）'
          }
          onChange={(event) => setComment(event.target.value)}
        />
      </Modal>
    </div>
  );
};

export default ApprovalDetailPage;
