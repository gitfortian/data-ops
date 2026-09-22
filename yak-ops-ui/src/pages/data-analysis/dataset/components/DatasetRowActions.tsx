import {
  CloudUploadOutlined,
  EyeOutlined,
  StopOutlined,
} from '@ant-design/icons';
import { Popconfirm, Space } from 'antd';

import { YakButton } from '@/components/ui';
import type { DatasetManagementItem } from '@/services/dataset';

interface DatasetRowActionsProps {
  dataset: DatasetManagementItem;
  loading?: boolean;
  onDetail: (dataset: DatasetManagementItem) => void;
  onToggleStatus: (dataset: DatasetManagementItem) => void;
}

export default function DatasetRowActions({
  dataset,
  loading = false,
  onDetail,
  onToggleStatus,
}: DatasetRowActionsProps) {
  const online = dataset.status === 'ONLINE';

  return (
    <Space size={2} onClick={(event) => event.stopPropagation()}>
      <YakButton
        type="text"
        size="small"
        icon={<EyeOutlined />}
        className="!px-1.5 !text-slate-600 hover:!text-slate-900"
        onClick={() => onDetail(dataset)}
      >
        详情
      </YakButton>

      <Popconfirm
        title={online ? '确认下线这个 Dataset？' : '确认上线这个 Dataset？'}
        description={
          online
            ? '下线后 Analysis、仪表盘和大屏将无法继续查询。'
            : '上线后可重新用于下游消费。'
        }
        okText="确认"
        cancelText="取消"
        onConfirm={() => onToggleStatus(dataset)}
      >
        <YakButton
          type="text"
          size="small"
          loading={loading}
          icon={online ? <StopOutlined /> : <CloudUploadOutlined />}
          className="!px-1.5 !text-slate-600 hover:!text-slate-900"
        >
          {online ? '下线' : '上线'}
        </YakButton>
      </Popconfirm>
    </Space>
  );
}
