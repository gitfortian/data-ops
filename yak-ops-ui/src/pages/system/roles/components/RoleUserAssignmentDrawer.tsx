import { SearchOutlined } from '@ant-design/icons';
import { Checkbox, Drawer, Input, Spin, message } from 'antd';
import { useEffect, useMemo, useState } from 'react';

import { YakButton, YakEmpty } from '@/components/ui';
import {
  assignUsersToRole,
  getRoleUserAssignments,
  type RoleAssignmentInfo,
  type SystemRole,
} from '@/services/security/roles';

import { getSystemErrorMessage } from '../../utils';

interface RoleUserAssignmentDrawerProps {
  open: boolean;
  role?: SystemRole;
  onClose: () => void;
  onSuccess: () => void;
}

export default function RoleUserAssignmentDrawer({
  open,
  role,
  onClose,
  onSuccess,
}: RoleUserAssignmentDrawerProps) {
  const [assignments, setAssignments] =
    useState<RoleAssignmentInfo[]>([]);
  const [selectedIds, setSelectedIds] = useState<number[]>([]);
  const [keyword, setKeyword] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [isSaving, setIsSaving] = useState(false);

  useEffect(() => {
    if (!open || !role) return;

    let active = true;
    setAssignments([]);
    setSelectedIds([]);
    setKeyword('');
    setIsLoading(true);

    void getRoleUserAssignments(role.id)
      .then((values) => {
        if (!active) return;
        const data = Array.isArray(values) ? values : [];
        setAssignments(data);
        setSelectedIds(
          data
            .filter((item) => item.has)
            .map((item) => Number(item.id))
            .filter(Number.isFinite),
        );
      })
      .catch((error) => {
        if (active) {
          message.error(
            getSystemErrorMessage(error, '可分配用户加载失败'),
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

  const visibleAssignments = useMemo(() => {
    const value = keyword.trim().toLowerCase();
    if (!value) return assignments;
    return assignments.filter((item) =>
      item.name.toLowerCase().includes(value),
    );
  }, [assignments, keyword]);

  const close = () => {
    if (!isSaving) onClose();
  };

  const save = async () => {
    if (!role || isLoading || isSaving) return;
    setIsSaving(true);

    try {
      await assignUsersToRole(role.id, selectedIds);
      message.success('角色用户已更新');
      onClose();
      onSuccess();
    } catch (error) {
      message.error(
        getSystemErrorMessage(error, '角色用户更新失败'),
      );
    } finally {
      setIsSaving(false);
    }
  };

  return (
    <Drawer
      open={open}
      title="分配用户"
      width={560}
      maskClosable={false}
      keyboard={!isSaving}
      closable={!isSaving}
      onClose={close}
      extra={
        <div className="flex items-center gap-2">
          <YakButton disabled={isSaving} onClick={close}>
            取消
          </YakButton>
          <YakButton
            type="primary"
            loading={isSaving}
            disabled={isLoading || !role}
            onClick={() => void save()}
          >
            保存
          </YakButton>
        </div>
      }
    >
      <Input
        allowClear
        value={keyword}
        prefix={<SearchOutlined className="text-slate-400" />}
        placeholder="搜索用户名或姓名"
        className="mb-4"
        onChange={(event) => setKeyword(event.target.value)}
      />

      <Spin spinning={isLoading}>
        {!isLoading && visibleAssignments.length === 0 ? (
          <YakEmpty compact title="暂无可分配用户" />
        ) : (
          <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
            {visibleAssignments.map((item) => {
              const userId = Number(item.id);
              const checked = selectedIds.includes(userId);
              return (
                <Checkbox
                  key={item.id}
                  checked={checked}
                  disabled={isSaving}
                  className="m-0 flex min-h-12 items-center rounded-lg border border-slate-200 bg-white px-4 py-3"
                  onChange={(event) => {
                    setSelectedIds((current) =>
                      event.target.checked
                        ? Array.from(new Set([...current, userId]))
                        : current.filter((id) => id !== userId),
                    );
                  }}
                >
                  <span className="ml-1 text-sm">{item.name}</span>
                </Checkbox>
              );
            })}
          </div>
        )}
      </Spin>
    </Drawer>
  );
}
