import { Modal, Space, Tag, message } from 'antd';
import { useCallback } from 'react';

import {
  checkDepartmentBeforeDelete,
  deleteDepartment,
  type DepartmentVO,
} from '@/services/security/departments';

import { getSystemErrorMessage } from '../../utils';
import { getDepartmentName } from '../utils';

interface UseDepartmentDeleteOptions {
  onDeleted: (department: DepartmentVO) => void | Promise<void>;
}

export function useDepartmentDelete({
  onDeleted,
}: UseDepartmentDeleteOptions) {
  return useCallback(
    async (department: DepartmentVO) => {
      try {
        const check = await checkDepartmentBeforeDelete(
          department.id,
        );
        const childNames = Array.isArray(check.childDeptNameList)
          ? check.childDeptNameList
          : [];
        const userNames = Array.isArray(check.userNameList)
          ? check.userNameList
          : [];

        if (!check.deletable) {
          Modal.warning({
            title: '当前部门不能删除',
            width: 520,
            centered: true,
            content: (
              <div className="space-y-3">
                {childNames.length > 0 && (
                  <div className="rounded-lg border border-amber-200 bg-amber-50 px-3 py-2 text-sm text-amber-800">
                    <div className="mb-2 font-medium">
                      存在 {childNames.length} 个直属子部门
                    </div>
                    <Space size={[4, 6]} wrap>
                      {childNames.slice(0, 10).map((name) => (
                        <Tag key={name}>{name}</Tag>
                      ))}
                      {childNames.length > 10 && (
                        <Tag>+{childNames.length - 10}</Tag>
                      )}
                    </Space>
                  </div>
                )}
                {userNames.length > 0 && (
                  <div className="rounded-lg border border-amber-200 bg-amber-50 px-3 py-2 text-sm text-amber-800">
                    <div className="mb-2 font-medium">
                      存在 {userNames.length} 个关联用户
                    </div>
                    <Space size={[4, 6]} wrap>
                      {userNames.slice(0, 10).map((name) => (
                        <Tag key={name}>{name}</Tag>
                      ))}
                      {userNames.length > 10 && (
                        <Tag>+{userNames.length - 10}</Tag>
                      )}
                    </Space>
                  </div>
                )}
              </div>
            ),
          });
          return;
        }

        Modal.confirm({
          title: '删除部门',
          width: 480,
          centered: true,
          okText: '删除',
          cancelText: '取消',
          okButtonProps: { danger: true },
          content: (
            <div className="space-y-3">
              <div>
                确定删除部门
                <span className="mx-1 font-medium text-slate-900">
                  {getDepartmentName(department)}
                </span>
                吗？
              </div>
              <div className="rounded-lg border border-red-100 bg-red-50 px-3 py-2 text-sm text-red-700">
                删除后无法恢复。只有没有子部门且没有关联用户的部门才能删除。
              </div>
            </div>
          ),
          onOk: async () => {
            try {
              await deleteDepartment(department.id);
              message.success('部门已删除');
              await onDeleted(department);
            } catch (error) {
              message.error(
                getSystemErrorMessage(error, '部门删除失败'),
              );
              throw error;
            }
          },
        });
      } catch (error) {
        message.error(
          getSystemErrorMessage(error, '部门删除检查失败'),
        );
      }
    },
    [onDeleted],
  );
}
