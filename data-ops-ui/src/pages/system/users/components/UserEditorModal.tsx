import {
  Drawer,
  Form,
  Input,
  Select,
  message,
} from 'antd';
import {
  forwardRef,
  useCallback,
  useImperativeHandle,
  useState,
} from 'react';

import { YakButton } from '@/components/ui';
import {
  checkUserField,
  createUser,
  getUserDetail,
  type SystemUser,
  type UserCheckType,
  type UserInput,
  updateUser,
} from '@/services/security/users';

import { getSystemErrorMessage } from '../../utils';
import {
  PHONE_PATTERN,
  USER_NAME_PATTERN,
} from '../constants';
import type { RoleOption } from '../types';
import { cleanUserText } from '../utils';

interface UserFormValues extends UserInput {
  confirmPassword?: string;
}

export interface UserEditorModalRef {
  openCreate: () => void;
  openEdit: (user: SystemUser) => Promise<void>;
}

interface UserEditorModalProps {
  roleOptions: RoleOption[];
  onSuccess: () => void;
}

const UserEditorModal = forwardRef<
  UserEditorModalRef,
  UserEditorModalProps
>(({ roleOptions, onSuccess }, ref) => {
  const [form] = Form.useForm<UserFormValues>();
  const [open, setOpen] = useState(false);
  const [editingUser, setEditingUser] = useState<SystemUser>();
  const [isSaving, setIsSaving] = useState(false);

  const close = useCallback(() => {
    if (isSaving) return;

    setOpen(false);
    setEditingUser(undefined);
    form.resetFields();
  }, [form, isSaving]);

  const openCreate = useCallback(() => {
    setEditingUser(undefined);
    form.resetFields();
    form.setFieldsValue({
      userName: '',
      realName: '',
      phone: '',
      email: '',
      pw: '',
      confirmPassword: '',
      roleIds: [],
    });
    setOpen(true);
  }, [form]);

  const openEdit = useCallback(
    async (row: SystemUser) => {
      try {
        const user = await getUserDetail(row.id);
        setEditingUser(user);
        form.resetFields();
        form.setFieldsValue({
          userName: user.userName,
          realName: user.realName ?? '',
          phone: user.phone ?? '',
          email: user.email ?? '',
          roleIds:
            user.roleList?.map((role) => Number(role.id)) ?? [],
        });
        setOpen(true);
      } catch (error) {
        message.error(
          getSystemErrorMessage(error, '用户详情加载失败'),
        );
      }
    },
    [form],
  );

  useImperativeHandle(
    ref,
    () => ({ openCreate, openEdit }),
    [openCreate, openEdit],
  );

  const uniqueValidator = (
    type: UserCheckType,
    originalValue?: string,
  ) =>
    async (_rule: unknown, value?: string) => {
      const normalized = cleanUserText(value);
      if (
        !normalized ||
        normalized === cleanUserText(originalValue)
      ) {
        return;
      }

      try {
        await checkUserField(type, normalized);
      } catch (error) {
        throw new Error(
          getSystemErrorMessage(
            error,
            '该字段已存在或格式不正确',
          ),
        );
      }
    };

  const save = async (values: UserFormValues) => {
    if (isSaving) return;
    setIsSaving(true);

    try {
      const body: UserInput = {
        userName: cleanUserText(values.userName),
        realName: cleanUserText(values.realName),
        phone: cleanUserText(values.phone),
        email: cleanUserText(values.email),
        roleIds: values.roleIds ?? [],
      };

      if (editingUser) {
        await updateUser(body);
      } else {
        body.pw = values.pw ?? '';
        await createUser(body);
      }

      message.success(editingUser ? '用户已更新' : '用户已创建');
      setOpen(false);
      setEditingUser(undefined);
      form.resetFields();
      onSuccess();
    } catch (error) {
      message.error(
        getSystemErrorMessage(
          error,
          editingUser ? '用户更新失败' : '用户创建失败',
        ),
      );
    } finally {
      setIsSaving(false);
    }
  };

  return (
    <Drawer
      open={open}
      title={editingUser ? '编辑用户' : '新增用户'}
      width={520}
      forceRender
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
            onClick={() => form.submit()}
          >
            {editingUser ? '更新' : '保存'}
          </YakButton>
        </div>
      }
    >
      <Form<UserFormValues>
        form={form}
        layout="vertical"
        preserve={false}
        disabled={isSaving}
        onFinish={save}
      >
        <Form.Item
          name="userName"
          label="用户名"
          validateTrigger="onBlur"
          rules={[
            { required: true, message: '请输入用户名' },
            {
              pattern: USER_NAME_PATTERN,
              message: '用户名须为 4～50 位字母、数字或下划线',
            },
            {
              validator: uniqueValidator(
                1,
                editingUser?.userName,
              ),
            },
          ]}
        >
          <Input
            disabled={Boolean(editingUser) || isSaving}
            placeholder="请输入用户名"
            maxLength={50}
          />
        </Form.Item>

        <Form.Item
          name="realName"
          label="真实姓名"
          rules={[
            {
              required: true,
              whitespace: true,
              message: '请输入真实姓名',
            },
          ]}
        >
          <Input placeholder="请输入真实姓名" maxLength={64} />
        </Form.Item>

        {!editingUser && (
          <>
            <Form.Item
              name="pw"
              label="初始密码"
              rules={[
                { required: true, message: '请输入初始密码' },
                { min: 8, message: '密码至少 8 位' },
              ]}
            >
              <Input.Password
                autoComplete="new-password"
                placeholder="请输入初始密码"
                maxLength={64}
              />
            </Form.Item>

            <Form.Item
              name="confirmPassword"
              label="确认密码"
              dependencies={['pw']}
              rules={[
                { required: true, message: '请再次输入密码' },
                ({ getFieldValue }) => ({
                  validator(_, value) {
                    if (!value || value === getFieldValue('pw')) {
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
                placeholder="请再次输入密码"
                maxLength={64}
              />
            </Form.Item>
          </>
        )}

        <Form.Item
          name="phone"
          label="手机号"
          validateTrigger="onBlur"
          rules={[
            {
              validator: async (rule, value?: string) => {
                const phone = cleanUserText(value);
                if (!phone) return;
                if (!PHONE_PATTERN.test(phone)) {
                  throw new Error('手机号格式不正确');
                }
                await uniqueValidator(
                  2,
                  editingUser?.phone,
                )(rule, phone);
              },
            },
          ]}
        >
          <Input placeholder="请输入手机号" maxLength={11} />
        </Form.Item>

        <Form.Item
          name="email"
          label="邮箱"
          validateTrigger="onBlur"
          rules={[
            { type: 'email', message: '邮箱格式不正确' },
            {
              validator: uniqueValidator(
                3,
                editingUser?.email,
              ),
            },
          ]}
        >
          <Input placeholder="请输入邮箱" maxLength={128} />
        </Form.Item>

        <Form.Item name="roleIds" label="角色">
          <Select
            mode="multiple"
            allowClear
            showSearch
            optionFilterProp="label"
            options={roleOptions}
            placeholder="请选择角色"
            maxTagCount="responsive"
          />
        </Form.Item>
      </Form>
    </Drawer>
  );
});

UserEditorModal.displayName = 'UserEditorModal';

export default UserEditorModal;
