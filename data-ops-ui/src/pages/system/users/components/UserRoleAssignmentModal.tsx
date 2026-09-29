import { Checkbox, Drawer, Spin, message } from 'antd';
import {
  forwardRef,
  useCallback,
  useImperativeHandle,
  useState,
} from 'react';

import { YakButton, YakEmpty } from '@/components/ui';
import {
  assignRolesToUser,
  getUserRoleAssignments,
  type AssignInfo,
  type SystemUser,
} from '@/services/security/users';

import { getSystemErrorMessage } from '../../utils';

export interface UserRoleAssignmentModalRef {
  open: (user: SystemUser) => Promise<void>;
}

interface UserRoleAssignmentModalProps {
  onSuccess: () => void;
}

const UserRoleAssignmentModal = forwardRef<
  UserRoleAssignmentModalRef,
  UserRoleAssignmentModalProps
>(({ onSuccess }, ref) => {
  const [open, setOpen] = useState(false);
  const [targetUser, setTargetUser] = useState<SystemUser>();
  const [assignments, setAssignments] = useState<AssignInfo[]>([]);
  const [selectedRoleIds, setSelectedRoleIds] = useState<number[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [isSaving, setIsSaving] = useState(false);

  const close = useCallback(() => {
    if (isSaving) return;
    setOpen(false);
    setTargetUser(undefined);
    setAssignments([]);
    setSelectedRoleIds([]);
    setIsLoading(false);
  }, [isSaving]);

  const show = useCallback(async (user: SystemUser) => {
    setTargetUser(user);
    setAssignments([]);
    setSelectedRoleIds([]);
    setOpen(true);
    setIsLoading(true);

    try {
      const values = await getUserRoleAssignments(user.id);
      const result = Array.isArray(values) ? values : [];
      setAssignments(result);
      setSelectedRoleIds(
        result
          .filter((item) => item.has)
          .map((item) => Number(item.id))
          .filter(Number.isFinite),
      );
    } catch (error) {
      message.error(
        getSystemErrorMessage(error, '角色分配信息加载失败'),
      );
    } finally {
      setIsLoading(false);
    }
  }, []);

  useImperativeHandle(ref, () => ({ open: show }), [show]);

  const save = async () => {
    if (!targetUser || isSaving || isLoading) return;
    setIsSaving(true);

    try {
      await assignRolesToUser(targetUser.id, selectedRoleIds);
      message.success('用户角色已更新');
      setOpen(false);
      setTargetUser(undefined);
      setAssignments([]);
      setSelectedRoleIds([]);
      onSuccess();
    } catch (error) {
      message.error(
        getSystemErrorMessage(error, '用户角色更新失败'),
      );
    } finally {
      setIsSaving(false);
    }
  };

  return (
    <Drawer
      open={open}
      title="分配角色"
      width={520}
      forceRender
      maskClosable={false}
      keyboard={!isSaving}
      closable={!isSaving}
      onClose={close}
      extra={
        <div className="flex items-center gap-2">
          <YakButton disabled={isSaving} onClick={close}>取消</YakButton>
          <YakButton
            type="primary"
            loading={isSaving}
            disabled={isLoading || !targetUser}
            onClick={() => void save()}
          >
            保存
          </YakButton>
        </div>
      }
    >
      {targetUser && (
        <div className="mb-5 rounded-lg border border-gray-200 bg-gray-50 px-4 py-3">
          <div className="text-xs text-gray-500">当前用户</div>
          <div className="mt-1 font-medium text-gray-900">
            {targetUser.realName || targetUser.userName}
          </div>
          {targetUser.realName && (
            <div className="mt-0.5 text-sm text-gray-500">
              用户名：{targetUser.userName}
            </div>
          )}
        </div>
      )}

      <Spin spinning={isLoading}>
        {!isLoading && assignments.length === 0 ? (
          <YakEmpty compact title="暂无可分配角色" />
        ) : (
          <Checkbox.Group
            className="grid w-full grid-cols-2 gap-3"
            value={selectedRoleIds}
            onChange={(values) => {
              setSelectedRoleIds(
                values
                  .map((value) => Number(value))
                  .filter(Number.isFinite),
              );
            }}
          >
            {assignments.map((item) => {
              const roleId = Number(item.id);
              const checked = selectedRoleIds.includes(roleId);
              return (
                <Checkbox
                  key={item.id}
                  value={roleId}
                  disabled={isSaving}
                  className={[
                    'm-0 flex min-h-12 items-center rounded-lg border px-4 py-3',
                    'transition-colors duration-200',
                    checked
                      ? 'border-primary bg-primary/5'
                      : 'border-gray-200 bg-white hover:border-gray-300',
                  ].join(' ')}
                >
                  <span className="ml-1 text-sm">{item.name}</span>
                </Checkbox>
              );
            })}
          </Checkbox.Group>
        )}
      </Spin>
    </Drawer>
  );
});

UserRoleAssignmentModal.displayName = 'UserRoleAssignmentModal';
export default UserRoleAssignmentModal;
