import { SafetyCertificateOutlined } from '@ant-design/icons';
import {
  Avatar,
  Descriptions,
  Drawer,
  Space,
  Spin,
  Tag,
  Typography,
  message,
} from 'antd';
import { useEffect, useState } from 'react';

import { YakEmpty } from '@/components/ui';
import {
  getRoleDetail,
  type SystemRole,
} from '@/services/security/roles';

import {
  formatSystemDateTime,
  getSystemErrorMessage,
} from '../../utils';
import RoleCapabilityTree, {
  collectCapabilityCheckedKeys,
} from './RoleCapabilityTree';

interface RoleDetailDrawerProps {
  open: boolean;
  role?: SystemRole;
  onClose: () => void;
}

export default function RoleDetailDrawer({
  open,
  role,
  onClose,
}: RoleDetailDrawerProps) {
  const [detail, setDetail] = useState<SystemRole>();
  const [isLoading, setIsLoading] = useState(false);

  useEffect(() => {
    if (!open || !role) return;

    let active = true;
    setDetail(role);
    setIsLoading(true);

    void getRoleDetail(role.id)
      .then((value) => {
        if (active) setDetail(value);
      })
      .catch((error) => {
        if (active) {
          message.error(
            getSystemErrorMessage(error, '角色详情加载失败'),
          );
        }
      })
      .finally(() => {
        if (active) setIsLoading(false);
      });

    return () => {
      active = false;
    };
  }, [open, role]);

  const users = Array.isArray(detail?.authedUsers)
    ? detail.authedUsers
    : [];

  return (
    <Drawer
      open={open}
      title="角色详情"
      width={720}
      onClose={onClose}
    >
      <Spin spinning={isLoading}>
        {detail ? (
          <div className="space-y-5">
            <div className="flex items-center gap-3 rounded-xl border border-slate-200 bg-slate-50/60 p-4">
              <Avatar
                size={48}
                icon={<SafetyCertificateOutlined />}
                className="shrink-0 !bg-slate-600"
              />
              <div className="min-w-0">
                <Typography.Title
                  level={5}
                  className="!mb-0 !text-slate-800"
                >
                  {detail.roleName}
                </Typography.Title>
                <div className="mt-1 text-xs text-slate-400">
                  {detail.roleCode || '暂无角色编码'} · ID {detail.id}
                </div>
              </div>
            </div>

            <Descriptions
              bordered
              size="small"
              column={1}
              items={[
                {
                  key: 'description',
                  label: '角色描述',
                  children: detail.description || '暂无描述',
                },
                {
                  key: 'createTime',
                  label: '创建时间',
                  children: formatSystemDateTime(detail.createTime),
                },
                {
                  key: 'updateTime',
                  label: '更新时间',
                  children: formatSystemDateTime(detail.updateTime),
                },
              ]}
            />

            <div>
              <div className="mb-2 font-medium text-slate-800">授权用户</div>
              <div className="rounded-lg border border-slate-200 bg-white p-3">
                {users.length === 0 ? (
                  <span className="text-sm text-slate-400">暂无授权用户</span>
                ) : (
                  <Space size={[6, 8]} wrap>
                    {users.map((userName) => (
                      <Tag key={userName}>{userName}</Tag>
                    ))}
                  </Space>
                )}
              </div>
            </div>

            <div>
              <div className="mb-2 font-medium text-slate-800">
                菜单与按钮权限
              </div>
              <RoleCapabilityTree
                tree={detail.permissionTreeVO}
                checkedKeys={collectCapabilityCheckedKeys(
                  detail.permissionTreeVO,
                )}
                readOnly
              />
            </div>
          </div>
        ) : (
          !isLoading && <YakEmpty compact title="暂无角色详情" />
        )}
      </Spin>
    </Drawer>
  );
}
