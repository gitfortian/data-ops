import {
  MinusSquareOutlined,
  PlusSquareOutlined,
} from '@ant-design/icons';
import type { DataNode } from 'antd/es/tree';
import { Spin, Tooltip, Tree } from 'antd';
import type { Key } from 'react';
import { useMemo } from 'react';

import { YakButton, YakEmpty } from '@/components/ui';
import type { DepartmentVO } from '@/services/security/departments';

import {
  collectDepartmentIds,
  getDirectChildren,
} from '../tree';
import { getDepartmentName } from '../utils';

interface DepartmentTreePaneProps {
  departments: DepartmentVO[];
  selectedId?: number;
  expandedKeys: Key[];
  loading?: boolean;
  filtered?: boolean;
  onSelect: (departmentId: number) => void;
  onExpandedKeysChange: (keys: Key[]) => void;
}

const toTreeData = (
  departments: DepartmentVO[],
  path = new Set<string>(),
): DataNode[] =>
  departments.flatMap((department) => {
    const key = String(department.id);
    if (path.has(key)) return [];

    const nextPath = new Set(path);
    nextPath.add(key);
    const children = getDirectChildren(department);

    return [
      {
        key: department.id,
        title: (
          <div className="min-w-0 py-0.5">
            <div className="truncate text-sm font-medium text-slate-700">
              {getDepartmentName(department)}
            </div>
            <div className="mt-0.5 truncate text-xs text-slate-400">
              {department.description ||
                (children.length > 0
                  ? `${children.length} 个直属部门`
                  : `ID ${department.id}`)}
            </div>
          </div>
        ),
        children: toTreeData(children, nextPath),
      },
    ];
  });

export default function DepartmentTreePane({
  departments,
  selectedId,
  expandedKeys,
  loading = false,
  filtered = false,
  onSelect,
  onExpandedKeysChange,
}: DepartmentTreePaneProps) {
  const treeData = useMemo(
    () => toTreeData(departments),
    [departments],
  );
  const departmentIds = useMemo(
    () => collectDepartmentIds(departments),
    [departments],
  );

  return (
    <aside className="flex min-h-0 flex-col border-b border-slate-200 lg:border-b-0 lg:border-r">
      <div className="flex h-14 shrink-0 items-center justify-between border-b border-slate-100 px-4">
        <div>
          <div className="font-medium text-slate-800">部门树</div>
          <div className="mt-0.5 text-xs text-slate-400">
            当前显示 {departmentIds.length} 个节点
          </div>
        </div>

        <div className="flex items-center gap-1">
          <Tooltip title="展开全部">
            <YakButton
              type="text"
              size="small"
              iconOnly
              icon={<PlusSquareOutlined />}
              onClick={() => onExpandedKeysChange(departmentIds)}
            />
          </Tooltip>
          <Tooltip title="收起全部">
            <YakButton
              type="text"
              size="small"
              iconOnly
              icon={<MinusSquareOutlined />}
              onClick={() => onExpandedKeysChange([])}
            />
          </Tooltip>
        </div>
      </div>

      <div className="min-h-0 flex-1 overflow-y-auto px-2 py-3">
        <Spin spinning={loading}>
          {!loading && treeData.length === 0 ? (
            <YakEmpty
              compact
              title={filtered ? '没有匹配的部门' : '暂无部门数据'}
            />
          ) : (
            <Tree
              blockNode
              showLine={{ showLeafIcon: false }}
              treeData={treeData}
              selectedKeys={
                selectedId === undefined ? [] : [selectedId]
              }
              expandedKeys={expandedKeys}
              autoExpandParent={filtered}
              onExpand={onExpandedKeysChange}
              onSelect={(keys) => {
                const value = Number(keys[0]);
                if (Number.isFinite(value)) onSelect(value);
              }}
            />
          )}
        </Spin>
      </div>
    </aside>
  );
}
