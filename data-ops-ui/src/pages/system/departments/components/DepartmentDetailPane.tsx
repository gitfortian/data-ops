import {
  ApartmentOutlined,
  DeleteOutlined,
  EditOutlined,
  PlusOutlined,
} from '@ant-design/icons';
import {
  Avatar,
  Descriptions,
  Spin,
  Tag,
  Typography,
} from 'antd';

import { PermissionGuard } from '@/components/security';
import { YakButton, YakEmpty } from '@/components/ui';
import { SECURITY_PERMISSIONS } from '@/constants/securityPermissions';
import type { DepartmentVO } from '@/services/security/departments';

import { getDepartmentName } from '../utils';

interface DepartmentDetailPaneProps {
  department?: DepartmentVO;
  treeDepartment?: DepartmentVO;
  path: DepartmentVO[];
  children: DepartmentVO[];
  descendantCount: number;
  loading?: boolean;
  filtered?: boolean;
  onSelect: (departmentId: number) => void;
  onCreateChild: (parentId: number) => void;
  onEdit: (department: DepartmentVO) => void;
  onDelete: (department: DepartmentVO) => void;
}

export default function DepartmentDetailPane({
  department,
  treeDepartment,
  path,
  children,
  descendantCount,
  loading = false,
  filtered = false,
  onSelect,
  onCreateChild,
  onEdit,
  onDelete,
}: DepartmentDetailPaneProps) {
  if (!department) {
    return (
      <main className="flex min-h-0 items-center justify-center overflow-y-auto p-8">
        <YakEmpty
          compact
          title={
            filtered
              ? '没有符合条件的部门'
              : '请从左侧选择部门节点'
          }
        />
      </main>
    );
  }

  const isGroup = department.leaf === false || children.length > 0;

  return (
    <main className="min-h-0 overflow-y-auto">
      <Spin spinning={loading}>
        <div className="min-h-full">
          <div className="flex min-h-20 items-center justify-between gap-4 border-b border-slate-100 px-6 py-4">
            <div className="flex min-w-0 items-center gap-3">
              <Avatar
                size={46}
                icon={<ApartmentOutlined />}
                className="shrink-0 !bg-slate-600"
              />
              <div className="min-w-0">
                <div className="flex min-w-0 flex-wrap items-center gap-2">
                  <Typography.Title
                    level={5}
                    className="!mb-0 !text-slate-800"
                  >
                    {getDepartmentName(department)}
                  </Typography.Title>
                  <Tag>{isGroup ? '部门分组' : '末级部门'}</Tag>
                </div>
                <div className="mt-1 text-xs text-slate-400">
                  部门 ID：{department.id}
                </div>
              </div>
            </div>

            <div className="flex flex-wrap items-center gap-2">
              <PermissionGuard
                mode="one"
                permission={SECURITY_PERMISSIONS.department.create}
              >
                <YakButton
                  icon={<PlusOutlined />}
                  onClick={() => onCreateChild(department.id)}
                >
                  新增子部门
                </YakButton>
              </PermissionGuard>

              <PermissionGuard
                mode="one"
                permission={SECURITY_PERMISSIONS.department.edit}
              >
                <YakButton
                  icon={<EditOutlined />}
                  onClick={() => onEdit(treeDepartment ?? department)}
                >
                  编辑
                </YakButton>
              </PermissionGuard>

              <PermissionGuard
                mode="one"
                permission={SECURITY_PERMISSIONS.department.delete}
              >
                <YakButton
                  danger
                  icon={<DeleteOutlined />}
                  onClick={() => onDelete(department)}
                >
                  删除
                </YakButton>
              </PermissionGuard>
            </div>
          </div>

          <div className="space-y-6 p-6">
            <div>
              <div className="mb-2 text-sm font-medium text-slate-800">
                部门路径
              </div>
              <div className="flex flex-wrap items-center gap-1 rounded-lg border border-slate-200 bg-slate-50/60 px-3 py-2 text-sm text-slate-600">
                {path.map((item, index) => (
                  <span
                    key={item.id}
                    className="flex items-center gap-1"
                  >
                    {index > 0 && (
                      <span className="text-slate-300">/</span>
                    )}
                    <button
                      type="button"
                      className="rounded px-1 py-0.5 hover:bg-white hover:text-slate-900"
                      onClick={() => onSelect(item.id)}
                    >
                      {getDepartmentName(item)}
                    </button>
                  </span>
                ))}
              </div>
            </div>

            <Descriptions
              bordered
              size="small"
              column={{ xs: 1, sm: 2 }}
              items={[
                {
                  key: 'id',
                  label: '部门 ID',
                  children: department.id,
                },
                {
                  key: 'parentId',
                  label: '父部门 ID',
                  children:
                    !department.parentId
                      ? '根部门'
                      : department.parentId,
                },
                {
                  key: 'level',
                  label: '部门层级',
                  children: department.level ?? path.length,
                },
                {
                  key: 'nodeType',
                  label: '节点类型',
                  children: isGroup ? '部门分组' : '末级部门',
                },
                {
                  key: 'children',
                  label: '直属子部门',
                  children: `${department.childDeptCount ?? children.length} 个`,
                },
                {
                  key: 'descendants',
                  label: '全部下级部门',
                  children: `${descendantCount} 个`,
                },
                {
                  key: 'users',
                  label: '直属用户',
                  children: `${department.userCount ?? 0} 人`,
                },
              ]}
            />

            <div>
              <div className="mb-2 text-sm font-medium text-slate-800">
                部门描述
              </div>
              <div className="min-h-20 rounded-lg border border-slate-200 bg-white px-4 py-3 text-sm leading-6 text-slate-600">
                {department.description || '暂无部门描述'}
              </div>
            </div>

            <div>
              <div className="mb-2 flex items-center justify-between">
                <span className="text-sm font-medium text-slate-800">
                  直属子部门
                </span>
                <span className="text-xs text-slate-400">
                  {children.length} 个
                </span>
              </div>

              <div className="rounded-lg border border-slate-200 bg-white p-3">
                {children.length === 0 ? (
                  <span className="text-sm text-slate-400">
                    当前节点没有子部门
                  </span>
                ) : (
                  <div className="grid grid-cols-1 gap-2 xl:grid-cols-2">
                    {children.map((child) => (
                      <button
                        key={child.id}
                        type="button"
                        className="min-w-0 rounded-lg border border-slate-200 px-3 py-2 text-left transition-colors hover:border-slate-300 hover:bg-slate-50"
                        onClick={() => onSelect(child.id)}
                      >
                        <div className="truncate text-sm font-medium text-slate-700">
                          {getDepartmentName(child)}
                        </div>
                        <div className="mt-1 truncate text-xs text-slate-400">
                          {child.description || `ID ${child.id}`}
                        </div>
                      </button>
                    ))}
                  </div>
                )}
              </div>
            </div>
          </div>
        </div>
      </Spin>
    </main>
  );
}
