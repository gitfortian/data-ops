import {
  DeleteOutlined,
  DownOutlined,
  EditOutlined,
  EyeOutlined,
  TeamOutlined,
  UserOutlined,
} from '@ant-design/icons';
import { Dropdown, Space, type MenuProps } from 'antd';

import { YakButton } from '@/components/ui';
import type { SecurityProjectSummary } from '@/services/security/projects';

interface WorkspaceRowActionsProps {
  project: SecurityProjectSummary;
  canManage: boolean;
  onDetail: (project: SecurityProjectSummary) => void;
  onEdit: (project: SecurityProjectSummary) => void;
  onAssignOwner: (project: SecurityProjectSummary) => void;
  onAssignMembers: (project: SecurityProjectSummary) => void;
  onDelete: (project: SecurityProjectSummary) => void;
}

export default function WorkspaceRowActions({
  project,
  canManage,
  onDetail,
  onEdit,
  onAssignOwner,
  onAssignMembers,
  onDelete,
}: WorkspaceRowActionsProps) {
  const items: MenuProps['items'] = canManage
    ? [
        {
          key: 'owner',
          icon: <UserOutlined />,
          label: '分配负责人',
        },
        {
          key: 'members',
          icon: <TeamOutlined />,
          label: '分配成员',
        },
        { type: 'divider' },
        {
          key: 'delete',
          icon: <DeleteOutlined />,
          label: '删除工作空间',
          danger: true,
        },
      ]
    : [];

  return (
    <Space size={2}>
      <YakButton
        type="text"
        size="small"
        icon={<EyeOutlined />}
        className="!px-1.5 !text-slate-600 hover:!text-slate-900"
        onClick={() => onDetail(project)}
      >
        详情
      </YakButton>

      {canManage && (
        <YakButton
          type="text"
          size="small"
          icon={<EditOutlined />}
          className="!px-1.5 !text-slate-600 hover:!text-slate-900"
          onClick={() => onEdit(project)}
        >
          编辑
        </YakButton>
      )}

      {canManage && (
        <Dropdown
          trigger={['click']}
          placement="bottomRight"
          menu={{
            items,
            onClick: ({ key }) => {
              if (key === 'owner') onAssignOwner(project);
              if (key === 'members') onAssignMembers(project);
              if (key === 'delete') onDelete(project);
            },
          }}
        >
          <YakButton
            type="text"
            size="small"
            className="!px-1.5 !text-slate-600 hover:!text-slate-900"
          >
            更多
            <DownOutlined className="ml-1 text-[10px]" />
          </YakButton>
        </Dropdown>
      )}
    </Space>
  );
}
