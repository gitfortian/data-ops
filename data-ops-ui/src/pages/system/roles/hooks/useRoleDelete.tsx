import { Modal, message } from 'antd';
import { useCallback } from 'react';

import {
  checkRoleBeforeDelete,
  deleteRole,
  type SystemRole,
} from '@/services/security/roles';

import { getSystemErrorMessage } from '../../utils';

export function useRoleDelete(onDeleted: () => void) {
  return useCallback(
    async (role: SystemRole) => {
      try {
        const check = await checkRoleBeforeDelete(role.id);
        const users = Array.isArray(check?.userNameList)
          ? check.userNameList
          : [];

        Modal.confirm({
          title: '删除角色',
          width: 480,
          centered: true,
          okText: '删除',
          cancelText: '取消',
          okButtonProps: { danger: true },
          content: (
            <div className="space-y-3">
              <div>
                确定删除角色
                <span className="mx-1 font-medium text-slate-900">
                  {role.roleName}
                </span>
                吗？
              </div>
              {users.length > 0 && (
                <div className="rounded-lg border border-amber-200 bg-amber-50 px-3 py-2 text-sm text-amber-800">
                  当前角色已关联 {users.length} 个用户，删除后会同步解除关联。
                </div>
              )}
            </div>
          ),
          onOk: async () => {
            try {
              await deleteRole(role.id);
              message.success('角色已删除');
              onDeleted();
            } catch (error) {
              message.error(
                getSystemErrorMessage(error, '角色删除失败'),
              );
              throw error;
            }
          },
        });
      } catch (error) {
        message.error(
          getSystemErrorMessage(error, '角色删除检查失败'),
        );
      }
    },
    [onDeleted],
  );
}
