import {
  DeleteOutlined,
  DownOutlined,
  EditOutlined,
  EyeOutlined,
  TeamOutlined,
} from '@ant-design/icons';
import { Dropdown, Space, type MenuProps } from 'antd';

import { YakButton } from '@/components/ui';
import { SECURITY_PERMISSIONS } from '@/constants/securityPermissions';
import { usePermissionAccess } from '@/hooks/usePermissionAccess';
import type { SystemRole } from '@/services/security/roles';

interface RoleRowActionsProps {
  role: SystemRole;
  onDetail: (role: SystemRole) => void;
  onEdit: (role: SystemRole) => void;
  onAssignUsers: (role: SystemRole) => void;
  onDelete: (role: SystemRole) => void;
}

export default function RoleRowActions({
  role,
  onDetail,
  onEdit,
  onAssignUsers,
  onDelete,
}: RoleRowActionsProps) {
  const { can, canAny } = usePermissionAccess();
  const canUpdate = can(SECURITY_PERMISSIONS.role.update);
  const canAssign = can(SECURITY_PERMISSIONS.role.assign);
  const canDelete = can(SECURITY_PERMISSIONS.role.delete);
  const hasMore = canAny([
    SECURITY_PERMISSIONS.role.assign,
    SECURITY_PERMISSIONS.role.delete,
  ]);

  const items: MenuProps['items'] = [
    ...(canAssign
      ? [
          {
            key: 'assignUsers',
            icon: <TeamOutlined />,
            label: '分配用户',
          },
        ]
      : []),
    ...(canAssign && canDelete ? [{ type: 'divider' as const }] : []),
    ...(canDelete
      ? [
          {
            key: 'delete',
            icon: <DeleteOutlined />,
            label: '删除角色',
            danger: true,
          },
        ]
      : []),
  ];

  return (
    <Space size={2}>
      <YakButton
        type="text"
        size="small"
        icon={<EyeOutlined />}
        className="!px-1.5 !text-slate-600 hover:!text-slate-900"
        onClick={() => onDetail(role)}
      >
        详情
      </YakButton>

      {canUpdate && (
        <YakButton
          type="text"
          size="small"
          icon={<EditOutlined />}
          className="!px-1.5 !text-slate-600 hover:!text-slate-900"
          onClick={() => onEdit(role)}
        >
          编辑
        </YakButton>
      )}

      {hasMore && (
        <Dropdown
          trigger={['click']}
          placement="bottomRight"
          menu={{
            items,
            onClick: ({ key }) => {
              if (key === 'assignUsers') onAssignUsers(role);
              if (key === 'delete') onDelete(role);
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
