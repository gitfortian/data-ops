import { SafetyCertificateOutlined } from '@ant-design/icons';
import type { TableColumnsType } from 'antd';
import { Avatar, Typography } from 'antd';
import { useMemo } from 'react';

import type { SystemRole } from '@/services/security/roles';

import { formatSystemDateTime } from '../../utils';
import RoleRowActions from '../components/RoleRowActions';

interface UseRoleColumnsOptions {
  onDetail: (role: SystemRole) => void;
  onEdit: (role: SystemRole) => void;
  onAssignUsers: (role: SystemRole) => void;
  onDelete: (role: SystemRole) => void;
}

export function useRoleColumns({
  onDetail,
  onEdit,
  onAssignUsers,
  onDelete,
}: UseRoleColumnsOptions): TableColumnsType<SystemRole> {
  return useMemo<TableColumnsType<SystemRole>>(
    () => [
      {
        title: '角色',
        dataIndex: 'roleName',
        key: 'roleName',
        width: 300,
        render: (_, role) => (
          <div className="flex min-w-0 items-center gap-3">
            <Avatar
              size={40}
              icon={<SafetyCertificateOutlined />}
              className="shrink-0 !bg-slate-600"
            />
            <div className="min-w-0">
              <Typography.Text
                strong
                ellipsis
                className="max-w-52 !text-slate-800"
              >
                {role.roleName}
              </Typography.Text>
              <div className="mt-1 truncate text-xs text-slate-500">
                {role.roleCode || '暂无编码'} · ID {role.id}
              </div>
            </div>
          </div>
        ),
      },
      {
        title: '角色描述',
        dataIndex: 'description',
        key: 'description',
        width: 280,
        render: (value?: string) => (
          <Typography.Paragraph
            ellipsis={{ rows: 2, tooltip: value }}
            className="!mb-0 !text-sm !text-slate-600"
          >
            {value || '暂无描述'}
          </Typography.Paragraph>
        ),
      },
      {
        title: '授权用户',
        key: 'authedUsers',
        width: 240,
        render: (_, role) => (
          <div>
            <div className="text-sm font-medium text-slate-700">
              {role.authedUserCnt ?? role.authedUsers?.length ?? 0} 人
            </div>
            <div className="mt-1 text-xs text-slate-400">
              {role.authedUsers?.slice(0, 2).join('、') || '暂无授权用户'}
            </div>
          </div>
        ),
      },
      {
        title: '最近更新',
        key: 'updateTime',
        width: 220,
        render: (_, role) => (
          <div>
            <div className="text-sm text-slate-700">
              {formatSystemDateTime(role.updateTime || role.createTime)}
            </div>
            <div className="mt-1 text-xs text-slate-400">
              {role.lastReviser
                ? `修改人：${role.lastReviser}`
                : `创建于 ${formatSystemDateTime(role.createTime)}`}
            </div>
          </div>
        ),
      },
      {
        title: '操作',
        key: 'action',
        fixed: 'right',
        width: 190,
        align: 'center',
        render: (_, role) => (
          <RoleRowActions
            role={role}
            onDetail={onDetail}
            onEdit={onEdit}
            onAssignUsers={onAssignUsers}
            onDelete={onDelete}
          />
        ),
      },
    ],
    [onAssignUsers, onDelete, onDetail, onEdit],
  );
}
