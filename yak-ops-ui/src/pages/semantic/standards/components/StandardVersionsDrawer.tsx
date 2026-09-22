import { List, Modal, Typography } from 'antd';

import { formatSemanticTime } from '@/pages/semantic/constants';
import type { SemanticStandardRecord, SemanticStandardVersionRecord } from '@/services/semantic/types';

interface StandardVersionsDrawerProps {
  open: boolean;
  standard: SemanticStandardRecord | null;
  versions: SemanticStandardVersionRecord[];
  loading: boolean;
  onClose: () => void;
}

const StandardVersionsDrawer = ({ open, standard, versions, loading, onClose }: StandardVersionsDrawerProps) => {
  return (
    <Modal
      open={open}
      title={standard ? `版本回溯：${standard.name}（${standard.code}）` : '版本回溯'}
      width={720}
      footer={null}
      destroyOnClose
      onCancel={onClose}
    >
      <Typography.Paragraph type="secondary" className="!mb-2 !text-[12px]">
        每次编辑前自动记录修改前的完整状态（版本号 = 被替换的版本）；启用/停用等状态变更不产生版本记录。本期仅支持查看，不支持一键回滚。
      </Typography.Paragraph>
      <List
        loading={loading}
        dataSource={versions}
        locale={{ emptyText: '该标准还没有历史版本（首次编辑后生成）' }}
        renderItem={(item) => (
          <List.Item>
            <div className="w-full">
              <div className="flex items-center justify-between">
                <Typography.Text strong>版本 {item.version}</Typography.Text>
                <Typography.Text type="secondary" className="!text-[12px]">
                  {formatSemanticTime(item.createTime)} · {item.operatedBy || '-'}
                </Typography.Text>
              </div>
              <pre className="mt-1 max-h-[220px] overflow-auto rounded bg-[#f6f8fa] p-2 text-[12px] leading-5">
                {JSON.stringify(item.payload, null, 2)}
              </pre>
            </div>
          </List.Item>
        )}
      />
    </Modal>
  );
};

export default StandardVersionsDrawer;
