import { Modal, Tag } from 'antd';

import type { CollectRunRecord } from '@/services/metadata/types';
import {
  formatDuration,
  formatMetadataTime,
  PROVIDER_LABELS,
  RUN_STATUS_COLORS,
  RUN_STATUS_LABELS,
  summarizeRunCounts,
} from '../constants';

/**
 * 一轮采集/预演的结果。同步执行所以按钮转圈完立刻有 RunView;
 * SUSPECT(熔断)用警告样式突出——它意味着整轮拒绝执行删除,不是普通失败。
 */
const RunResultModal = ({
  run,
  title,
  onClose,
}: {
  run: CollectRunRecord | null;
  title: string;
  onClose: () => void;
}) => (
  <Modal
    open={Boolean(run)}
    title={title}
    okText="知道了"
    cancelButtonProps={{ hidden: true }}
    onOk={onClose}
    onCancel={onClose}
  >
    {run ? (
      <div className="space-y-3 py-1">
        <div className="flex items-center gap-2">
          <Tag color={RUN_STATUS_COLORS[run.status ?? 'FAILED']}>
            {RUN_STATUS_LABELS[run.status ?? 'FAILED']}
          </Tag>
          {run.providerType ? (
            <span className="text-[12px] text-[#667085]">{PROVIDER_LABELS[run.providerType]}</span>
          ) : null}
          <span className="text-[12px] text-[#98a2b3]">耗时 {formatDuration(run.durationMs)}</span>
        </div>
        <div className="rounded-lg bg-[#f9fafb] px-3 py-2 text-[13px]">
          {summarizeRunCounts(run)}
          <div className="mt-1 text-[12px] text-[#98a2b3]">
            {formatMetadataTime(run.startedAt)} ~ {formatMetadataTime(run.finishedAt)} · 触发人{' '}
            {run.createdBy || '-'}
          </div>
        </div>
        {run.errorMessage ? (
          <div className="rounded-lg border border-solid border-[#fdded8] bg-[#fff4f0] px-3 py-2 text-[12px] leading-5 text-[#b42318]">
            {run.errorMessage}
          </div>
        ) : null}
      </div>
    ) : null}
  </Modal>
);

export default RunResultModal;
