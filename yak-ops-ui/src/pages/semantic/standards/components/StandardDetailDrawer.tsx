import { Button, Descriptions, Drawer, Space } from 'antd';
import { history } from '@umijs/max';
import { useMemo } from 'react';
import { ApprovalStatusTag } from '@/components/ApprovalStatusTag';
import type { ApprovalInstance } from '@/services/approval/types';
import { formatSemanticTime, SEMANTIC_STANDARD_KIND_LABELS, SEMANTIC_STATUS_LABELS } from '@/pages/semantic/constants';
import { STANDARD_KIND_FIELD_LABELS } from '@/pages/semantic/standards/constants';
import type { SemanticStandardRecord, SemanticStandardUsageSummary } from '@/services/semantic/types';

interface StandardDetailDrawerProps {
  open: boolean;
  standard: SemanticStandardRecord | null;
  usageSummary: SemanticStandardUsageSummary | null;
  /** 该标准的在途/最近发布审批单(缺口单03);无单据为 null。 */
  publishApproval?: ApprovalInstance | null;
  /** STANDARD_PUBLISH 流程开关;关闭时维持直发,不展示审批入口。 */
  publishFlowEnabled?: boolean;
  onSubmitPublish?: (standard: SemanticStandardRecord) => void;
  onClose: () => void;
}

/** 标准详情抽屉(ticket 32):全量字段 + 引用/绕过统计与反哺建议。 */
const StandardDetailDrawer = ({
  open,
  standard,
  usageSummary,
  publishApproval,
  publishFlowEnabled,
  onSubmitPublish,
  onClose,
}: StandardDetailDrawerProps) => {
  const kindFieldItems = useMemo(() => {
    if (!standard) {
      return [];
    }
    return STANDARD_KIND_FIELD_LABELS.filter((item) => {
      const value = standard[item.key];
      return value !== undefined && value !== null && String(value).length > 0;
    }).map((item) => ({
      key: item.key as string,
      label: item.label,
      children: String(standard[item.key]),
    }));
  }, [standard]);

  const usageAdvice =
    usageSummary && usageSummary.applyCount + usageSummary.bypassCount > 0
      ? usageSummary.bypassCount >= usageSummary.applyCount
        ? '（⚠ 绕过偏高，建议复核该标准：修改或废弃）'
        : '（引用健康）'
      : '';

  return (
    <Drawer
      open={open}
      width={520}
      title={standard ? `${standard.name}（${standard.code}）` : '标准详情'}
      onClose={onClose}
      destroyOnClose
    >
      {standard ? (
        <Descriptions
          bordered
          size="small"
          column={1}
          items={[
            {
              key: 'kind',
              label: '类别',
              children: SEMANTIC_STANDARD_KIND_LABELS[standard.kind],
            },
            {
              key: 'status',
              label: '状态',
              children: SEMANTIC_STATUS_LABELS[standard.status],
            },
            { key: 'preset', label: '预置', children: standard.preset ? '是' : '否' },
            { key: 'version', label: '版本', children: String(standard.version) },
            { key: 'desc', label: '描述', children: standard.description || '-' },
            { key: 'creator', label: '创建人', children: standard.createdBy || '-' },
            {
              key: 'createTime',
              label: '创建时间',
              children: formatSemanticTime(standard.createTime),
            },
            {
              key: 'updateTime',
              label: '更新时间',
              children: formatSemanticTime(standard.updateTime),
            },
            ...kindFieldItems,
            ...(usageSummary
              ? [
                  {
                    key: 'usage',
                    label: '引用/绕过',
                    children: `${usageSummary.applyCount} / ${usageSummary.bypassCount}${usageAdvice}`,
                  },
                ]
              : []),
            ...(publishApproval
              ? [
                  {
                    key: 'publishApproval',
                    label: '发布审批',
                    children: (
                      <Space size={6}>
                        <ApprovalStatusTag status={publishApproval.status} />
                        <Button
                          type="link"
                          size="small"
                          className="!px-0"
                          onClick={() => history.push(`/approval/instance/${publishApproval.id}`)}
                        >
                          查看审批单
                        </Button>
                      </Space>
                    ),
                  },
                ]
              : []),
            ...(publishFlowEnabled &&
            standard.kind !== 'CODE' &&
            standard.status === 'DISABLED' &&
            onSubmitPublish &&
            publishApproval?.status !== 'PENDING'
              ? [
                  {
                    key: 'publishAction',
                    label: '发布',
                    children: (
                      <Space size={8}>
                        <Button type="primary" size="small" onClick={() => onSubmitPublish(standard)}>
                          提交发布
                        </Button>
                        <span className="text-[12px] text-[#98a2b3]">启用需经审批，批准后自动生效</span>
                      </Space>
                    ),
                  },
                ]
              : []),
          ]}
        />
      ) : null}
    </Drawer>
  );
};

export default StandardDetailDrawer;
