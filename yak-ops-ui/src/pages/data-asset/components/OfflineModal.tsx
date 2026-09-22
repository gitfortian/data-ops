import { Input, Modal, message } from 'antd';
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

  useEffect(() => {
    if (open) setReason('');
  }, [open]);

  const submit = async () => {
    if (!reason.trim()) {
      message.warning('下架原因必填');
      return;
    }
    try {
      const count = await offlineAssets(assets.map((asset) => asset.id), reason.trim());
      message.success(`已下架 ${count} 个资产`);
      onDone();
    } catch {
      // 全局错误提示已展示原因(原因必填 48013 / 状态不允许 48003)
    }
  };

  return (
    <Modal
      open={open}
      title={`下架 ${assets.length} 个资产`}
      okText="确认下架"
      okButtonProps={{ danger: true, disabled: !reason.trim() }}
      onOk={submit}
      onCancel={onClose}
      destroyOnClose
    >
      <div className="mb-2 text-[13px] text-[#667085]">下架原因必填，将写入台账与审计。</div>
      <Input.TextArea
        rows={3}
        maxLength={512}
        showCount
        value={reason}
        onChange={(event) => setReason(event.target.value)}
        placeholder="如：口径变更，暂停对外提供"
      />
    </Modal>
  );
};

export default OfflineModal;
