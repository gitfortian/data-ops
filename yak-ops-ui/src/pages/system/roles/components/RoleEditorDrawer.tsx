import { Drawer, Form, Input, Spin, message } from 'antd';
import { useEffect, useState, type Key } from 'react';

import { YakButton } from '@/components/ui';
import {
  createRole,
  getPermissionTree,
  getRoleDetail,
  type PermissionTreeNode,
  type RoleInput,
  type SystemRole,
  updateRole,
} from '@/services/security/roles';

import { getSystemErrorMessage } from '../../utils';
import type { RoleFormValues } from '../types';
import {
  checkedKeysToRolePermissionIds,
  cleanRoleText,
} from '../utils';
import RoleCapabilityTree, {
  collectCapabilityCheckedKeys,
} from './RoleCapabilityTree';

interface RoleEditorDrawerProps {
  open: boolean;
  role?: SystemRole;
  onClose: () => void;
  onSuccess: () => void;
}

export default function RoleEditorDrawer({
  open,
  role,
  onClose,
  onSuccess,
}: RoleEditorDrawerProps) {
  const [form] = Form.useForm<RoleFormValues>();
  const [detail, setDetail] = useState<SystemRole>();
  const [permissionTree, setPermissionTree] =
    useState<PermissionTreeNode>();
  const [checkedKeys, setCheckedKeys] = useState<Key[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [isSaving, setIsSaving] = useState(false);

  useEffect(() => {
    if (!open) return;

    let active = true;
    setDetail(undefined);
    setPermissionTree(undefined);
    setCheckedKeys([]);
    form.resetFields();
    form.setFieldsValue({ roleName: '', description: '' });
    setIsLoading(true);

    const load = async () => {
      try {
        const value = role ? await getRoleDetail(role.id) : undefined;
        const tree = value?.permissionTreeVO ?? await getPermissionTree();
        if (!active) return;

        setDetail(value);
        setPermissionTree(tree);
        setCheckedKeys(collectCapabilityCheckedKeys(tree));
        if (value) {
          form.setFieldsValue({
            roleName: value.roleName,
            description: value.description ?? '',
          });
        }
      } catch (error) {
        if (active) {
          message.error(
            getSystemErrorMessage(error, '角色权限加载失败'),
          );
        }
      } finally {
        if (active) setIsLoading(false);
      }
    };

    void load();
    return () => {
      active = false;
    };
  }, [form, open, role]);

  const close = () => {
    if (!isSaving) onClose();
  };

  const save = async (values: RoleFormValues) => {
    if (isSaving || isLoading) return;
    setIsSaving(true);

    try {
      const body: RoleInput = {
        roleName: cleanRoleText(values.roleName),
        description: cleanRoleText(values.description),
        permissionIdList:
          checkedKeysToRolePermissionIds(checkedKeys),
      };

      if (role) {
        await updateRole({ ...body, id: detail?.id ?? role.id });
      } else {
        await createRole(body);
      }

      message.success(role ? '角色已更新' : '角色已创建');
      onClose();
      onSuccess();
    } catch (error) {
      message.error(
        getSystemErrorMessage(
          error,
          role ? '角色更新失败' : '角色创建失败',
        ),
      );
    } finally {
      setIsSaving(false);
    }
  };

  return (
    <Drawer
      open={open}
      title={role ? '编辑角色' : '新增角色'}
      width={720}
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
            disabled={isLoading}
            onClick={() => form.submit()}
          >
            {role ? '更新' : '保存'}
          </YakButton>
        </div>
      }
    >
      <Form<RoleFormValues>
        form={form}
        layout="vertical"
        preserve={false}
        disabled={isSaving}
        onFinish={(values) => void save(values)}
      >
        <Form.Item
          name="roleName"
          label="角色名称"
          rules={[
            {
              required: true,
              whitespace: true,
              message: '请输入角色名称',
            },
          ]}
        >
          <Input
            placeholder="请输入角色名称"
            maxLength={64}
            showCount
          />
        </Form.Item>

        <Form.Item name="description" label="角色描述">
          <Input.TextArea
            placeholder="请输入角色职责或适用范围"
            maxLength={500}
            showCount
            autoSize={{ minRows: 3, maxRows: 6 }}
          />
        </Form.Item>

        <Form.Item label="菜单与按钮权限">
          <Spin spinning={isLoading}>
            <RoleCapabilityTree
              tree={permissionTree}
              checkedKeys={checkedKeys}
              loading={isLoading}
              onChange={setCheckedKeys}
            />
          </Spin>
        </Form.Item>
      </Form>
    </Drawer>
  );
}
