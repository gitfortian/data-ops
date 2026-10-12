import { Alert, Input, Modal, message } from 'antd';
import { useEffect, useState } from 'react';

import { offlineAssets } from '@/services/data-asset/api';
import type { AssetRecord } from '@/services/data-asset/types';

/** 下架确认(原因必填 48013,单个/批量共用)。 */
const OfflineModal = ({
  open,
  assets,
  onClose,
  onDone,
}: {
  open: boolean;
  assets: AssetRecord[];
  onClose: () => void;
  onDone: () => void;
}) => {
  const [reason, setReason] = useState('');
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (open) setReason('');
  }, [open]);

  const submit = async () => {
    if (submitting) return;
    if (!reason.trim()) {
      message.warning('下架原因必填');
      return;
    }
    setSubmitting(true);
    try {
      const count = await offlineAssets(assets.map((asset) => asset.id), reason.trim());
      message.success(`已下架 ${count} 个资产`);
      onDone();
    } catch {
      // 全局错误提示已展示原因(原因必填 48013 / 状态不允许 48003)
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Modal
      open={open}
      title={<div><div className="text-[18px] font-semibold text-[#273950]">确认下架 {assets.length} 个资产</div>
        <div className="mt-1 text-[12px] font-normal text-[#8492a5]">下架是资产发布状态变更，不会删除源系统对象。</div></div>}
      okText="确认下架"
      width={560}
      okButtonProps={{ danger: true, disabled: !reason.trim() || submitting, loading: submitting }}
      onOk={submit}
      onCancel={() => { if (!submitting) onClose(); }}
      maskClosable={!submitting}
      keyboard={!submitting}
      destroyOnClose
    >
      <Alert type="warning" showIcon className="!mb-4" message="下架后将从已上架可消费目录移出"
        description="该操作不删除源数据库、元数据实体或历史记录。原因会记录在资产台账和审计中。" />
      <div className="mb-2 text-[13px] font-medium text-[#41536c]">下架原因 <span className="text-[#c75e4a]">*</span></div>
      <Input.TextArea
        rows={3}
        maxLength={512}
        showCount
        value={reason}
        onChange={(event) => setReason(event.target.value)}
        placeholder="说明下架的业务原因，例如：口径变更，暂停对外提供"
      />
    </Modal>
  );
};

export default OfflineModal;
