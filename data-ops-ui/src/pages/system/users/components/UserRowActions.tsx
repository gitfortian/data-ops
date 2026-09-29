import {
  DeleteOutlined,
  DownOutlined,
  EditOutlined,
  EyeOutlined,
  KeyOutlined,
  LogoutOutlined,
  SafetyCertificateOutlined,
} from '@ant-design/icons';
import {
  Dropdown,
  Modal,
  Space,
  message,
  type MenuProps,
} from 'antd';

import { YakButton } from '@/components/ui';
import { SECURITY_PERMISSIONS } from '@/constants/securityPermissions';
import { usePermissionAccess } from '@/hooks/usePermissionAccess';
import {
  deleteUser as deleteSystemUser,
  forceLogoutUser,
  type SystemUser,
} from '@/services/security/users';

import { getSystemErrorMessage } from '../../utils';

interface UserRowActionsProps {
  user: SystemUser;
  currentUserName?: string;
  onDetail: (user: SystemUser) => void;
  onEdit: (user: SystemUser) => void;
  onAssignRole: (user: SystemUser) => void;
  onResetPassword: (user: SystemUser) => void;
  onDeleted: () => void;
}

type ActionKey =
  | 'assignRole'
  | 'resetPassword'
  | 'forceLogout'
  | 'delete';

export default function UserRowActions({
  user,
  currentUserName,
  onDetail,
  onEdit,
  onAssignRole,
  onResetPassword,
  onDeleted,
}: UserRowActionsProps) {
  const { can } = usePermissionAccess();
  const isCurrentUser = user.userName === currentUserName;
  const canEdit = can(SECURITY_PERMISSIONS.user.update);
  const canAssignRole = can(SECURITY_PERMISSIONS.role.assign);
  const canResetPassword = can(
    SECURITY_PERMISSIONS.user.resetPassword,
  );
  const canDelete = can(SECURITY_PERMISSIONS.user.delete);

  const remove = async () => {
    if (!canDelete) return;

    try {
      await deleteSystemUser(user.id);
      message.success('用户已删除');
      onDeleted();
    } catch (error) {
      message.error(getSystemErrorMessage(error, '用户删除失败'));
      throw error;
    }
  };

  const confirmDelete = () => {
    if (!canDelete || isCurrentUser) return;

    Modal.confirm({
      title: '删除用户',
      content: (
        <div>
          确定删除用户
          <span className="mx-1 font-medium text-slate-900">
            {user.realName || user.userName}
          </span>
          吗？
        </div>
      ),
      okText: '删除',
      cancelText: '取消',
      okButtonProps: { danger: true },
      centered: true,
      onOk: remove,
    });
  };

  const confirmForceLogout = () => {
    if (!canEdit || isCurrentUser) return;

    Modal.confirm({
      title: '强制下线用户',
      content: (
        <div>
          确定让用户
          <span className="mx-1 font-medium text-slate-900">
            {user.realName || user.userName}
          </span>
          的全部登录终端立即失效吗？
        </div>
      ),
      okText: '强制下线',
      cancelText: '取消',
      centered: true,
      onOk: async () => {
        try {
          await forceLogoutUser(user.id);
          message.success('用户已强制下线');
        } catch (error) {
          message.error(
            getSystemErrorMessage(error, '用户强制下线失败'),
          );
          throw error;
        }
      },
    });
  };

  const menuItems: NonNullable<MenuProps['items']> = [];

  if (canAssignRole) {
    menuItems.push({
      key: 'assignRole',
      icon: <SafetyCertificateOutlined />,
      label: '分配角色',
    });
  }
  if (canResetPassword) {
    menuItems.push({
      key: 'resetPassword',
      icon: <KeyOutlined />,
      label: '重置密码',
    });
  }
  if (canEdit) {
    menuItems.push({
      key: 'forceLogout',
      icon: <LogoutOutlined />,
      label: '强制下线',
      disabled: isCurrentUser,
    });
  }
  if (canDelete) {
    if (menuItems.length > 0) menuItems.push({ type: 'divider' });
    menuItems.push({
      key: 'delete',
      icon: <DeleteOutlined />,
      label: '删除用户',
      danger: true,
      disabled: isCurrentUser,
    });
  }

  const handleMenuClick: MenuProps['onClick'] = ({ key }) => {
    switch (key as ActionKey) {
      case 'assignRole':
        if (canAssignRole) onAssignRole(user);
        break;
      case 'resetPassword':
        if (canResetPassword) onResetPassword(user);
        break;
      case 'forceLogout':
        confirmForceLogout();
        break;
      case 'delete':
        confirmDelete();
        break;
      default:
        break;
    }
  };

  return (
    <Space size={2}>
      <YakButton
        type="text"
        size="small"
        icon={<EyeOutlined />}
        className="!px-1.5 !text-[#667085]"
        onClick={() => onDetail(user)}
      >
        详情
      </YakButton>

      {canEdit && (
        <YakButton
          type="text"
          size="small"
          icon={<EditOutlined />}
          className="!px-1.5 !text-[#667085]"
          onClick={() => onEdit(user)}
        >
          编辑
        </YakButton>
      )}

      {menuItems.length > 0 && (
        <Dropdown
          trigger={['click']}
          placement="bottomRight"
          menu={{ items: menuItems, onClick: handleMenuClick }}
        >
          <YakButton
            type="text"
            size="small"
            className="!px-1.5 !text-[#667085]"
          >
            更多
            <DownOutlined className="ml-1 text-[10px]" />
          </YakButton>
        </Dropdown>
      )}
    </Space>
  );
}
