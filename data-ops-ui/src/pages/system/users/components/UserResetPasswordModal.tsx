import { Drawer, Form, Input, message } from 'antd';
import {
  forwardRef,
  useCallback,
  useImperativeHandle,
  useState,
} from 'react';

import { YakButton } from '@/components/ui';
import {
  resetUserPassword,
  type SystemUser,
} from '@/services/security/users';

import { getSystemErrorMessage } from '../../utils';

interface ResetPasswordValues {
  password: string;
  confirmPassword: string;
}

export interface UserResetPasswordModalRef {
  open: (user: SystemUser) => void;
}

const UserResetPasswordModal = forwardRef<UserResetPasswordModalRef>((_, ref) => {
  const [form] = Form.useForm<ResetPasswordValues>();
  const [open, setOpen] = useState(false);
  const [targetUser, setTargetUser] = useState<SystemUser>();
  const [isSaving, setIsSaving] = useState(false);

  const show = useCallback(
    (user: SystemUser) => {
      setTargetUser(user);
      form.resetFields();
      setOpen(true);
    },
    [form],
  );

  useImperativeHandle(ref, () => ({ open: show }), [show]);

  const close = useCallback(() => {
    if (isSaving) return;
    setOpen(false);
    setTargetUser(undefined);
    form.resetFields();
  }, [form, isSaving]);

  const save = async (values: ResetPasswordValues) => {
    if (!targetUser || isSaving) return;
    setIsSaving(true);

    try {
      await resetUserPassword(targetUser.id, values.password);
      message.success('密码已重置');
      setOpen(false);
      setTargetUser(undefined);
      form.resetFields();
    } catch (error) {
      message.error(
        getSystemErrorMessage(error, '密码重置失败'),
      );
    } finally {
      setIsSaving(false);
    }
  };

  return (
    <Drawer
      open={open}
      title="重置密码"
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
            danger
            loading={isSaving}
            disabled={!targetUser}
            onClick={() => form.submit()}
          >
            确认重置
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

      <Form<ResetPasswordValues>
        form={form}
        layout="vertical"
        preserve={false}
        disabled={isSaving}
        onFinish={save}
      >
        <Form.Item
          name="password"
          label="新密码"
          rules={[
            { required: true, message: '请输入新密码' },
            { min: 8, message: '密码至少 8 位' },
          ]}
        >
          <Input.Password
            autoComplete="new-password"
            placeholder="请输入新密码"
            maxLength={64}
          />
        </Form.Item>
        <Form.Item
          name="confirmPassword"
          label="确认新密码"
          dependencies={['password']}
          rules={[
            { required: true, message: '请再次输入新密码' },
            ({ getFieldValue }) => ({
              validator(_, value) {
                if (!value || value === getFieldValue('password')) {
                  return Promise.resolve();
                }
                return Promise.reject(
                  new Error('两次输入的密码不一致'),
                );
              },
            }),
          ]}
        >
          <Input.Password
            autoComplete="new-password"
            placeholder="请再次输入新密码"
            maxLength={64}
          />
        </Form.Item>
      </Form>
    </Drawer>
  );
});

UserResetPasswordModal.displayName = 'UserResetPasswordModal';
export default UserResetPasswordModal;
