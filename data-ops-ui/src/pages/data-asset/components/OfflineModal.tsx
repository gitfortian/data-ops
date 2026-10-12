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
      width={560}
      title={<div>
        <div className="text-[18px] font-semibold text-[#27374c]">确认下架 · {assets.length} 项资产</div>
        <div className="mt-1 text-[12px] font-normal text-[#8794a6]">下架前请确认业务影响，操作原因会写入审计记录。</div>
      </div>}
      okText="确认下架"
      okButtonProps={{ danger: true, disabled: !reason.trim() || submitting }}
      confirmLoading={submitting}
      maskClosable={!submitting}
      onOk={submit}
      onCancel={onClose}
      destroyOnClose
    >
      <div className="mt-3 space-y-4">
        <Alert type="warning" showIcon message="下架会改变资产的对外可用状态"
          description="仅调整资产台账发布状态，不删除源数据库表或元数据。请先与依赖该资产的使用方确认影响。" />
        <div className="rounded-xl border border-solid border-[#e9edf3] bg-[#f8fafc] px-4 py-3">
          <div className="mb-2 text-[12px] font-medium text-[#65758b]">本次下架范围</div>
          <div className="text-[13px] leading-6 text-[#34445a]">
            {assets.slice(0, 4).map((asset) => asset.name).join('、')}{assets.length > 4 ? ` 等 ${assets.length} 项` : ''}
          </div>
        </div>
        <label htmlFor="asset-offline-reason" className="block text-[13px] font-medium text-[#3f5065]">下架原因（必填）</label>
        <Input.TextArea
        id="asset-offline-reason"
        rows={4}
        maxLength={512}
        showCount
        value={reason}
        onChange={(event) => setReason(event.target.value)}
        placeholder="例如：业务口径变更，暂停对外提供，待使用方确认后重新上架"
      />
      </div>
    </Modal>
  );
};

export default OfflineModal;
