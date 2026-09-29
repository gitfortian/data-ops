import { Alert, Drawer, Form, Input, TreeSelect } from 'antd';
import { useEffect } from 'react';

import { YakButton } from '@/components/ui';
import type {
  SecurityProjectInput,
  SecurityProjectSummary,
} from '@/services/security/projects';

import type { WorkspaceDepartmentTreeNode } from '../workspace';

interface WorkspaceEditorDrawerProps {
  open: boolean;
  editing?: SecurityProjectSummary;
  saving: boolean;
  departmentLoading: boolean;
  departmentTreeData: WorkspaceDepartmentTreeNode[];
  onClose: () => void;
  onSave: (values: SecurityProjectInput) => void;
}

export default function WorkspaceEditorDrawer({
  open,
  editing,
  saving,
  departmentLoading,
  departmentTreeData,
  onClose,
  onSave,
}: WorkspaceEditorDrawerProps) {
  const [form] = Form.useForm<SecurityProjectInput>();

  useEffect(() => {
    if (!open) return;
    form.resetFields();
    form.setFieldsValue({
      projectName: editing?.projectName ?? '',
      description: editing?.description ?? '',
      deptId: editing?.deptId ?? undefined,
    });
  }, [editing, form, open]);

  return (
    <Drawer
      open={open}
      title={editing ? '编辑工作空间' : '新增工作空间'}
      width={560}
      forceRender
      maskClosable={!saving}
      keyboard={!saving}
      closable={!saving}
      onClose={onClose}
      extra={
        <div className="flex items-center gap-2">
          <YakButton disabled={saving} onClick={onClose}>
            取消
          </YakButton>
          <YakButton
            type="primary"
            loading={saving}
            onClick={() => form.submit()}
          >
            {editing ? '更新' : '创建'}
          </YakButton>
        </div>
      }
    >
      <Alert
        showIcon
        type="info"
        className="mb-5"
        message={
          editing
            ? '修改名称或所属部门不会改变已有负责人和成员关系。'
            : '创建后当前用户会自动成为负责人，工作空间会立即出现在 Header 切换列表中。'
        }
      />

      {departmentTreeData.length === 0 && !departmentLoading ? (
        <Alert
          showIcon
          type="warning"
          className="mb-4"
          message="暂无可用部门"
          description="工作空间必须归属真实部门，请先在“系统管理 > 部门管理”创建部门。"
        />
      ) : null}

      <Form<SecurityProjectInput>
        form={form}
        layout="vertical"
        preserve={false}
        disabled={saving}
        onFinish={onSave}
      >
        {editing ? (
          <Form.Item label="工作空间编码">
            <Input
              disabled
              variant="filled"
              value={editing.projectCode || '-'}
            />
          </Form.Item>
        ) : null}

        <Form.Item
          name="projectName"
          label="工作空间名称"
          rules={[
            {
              required: true,
              whitespace: true,
              message: '请输入工作空间名称',
            },
          ]}
        >
          <Input
            variant="filled"
            maxLength={128}
            showCount
            placeholder="例如：成都一院"
          />
        </Form.Item>

        <Form.Item
          name="deptId"
          label="所属部门"
          rules={[{ required: true, message: '请选择所属部门' }]}
        >
          <TreeSelect
            variant="filled"
            treeData={departmentTreeData}
            treeDefaultExpandAll
            showSearch
            allowClear={false}
            loading={departmentLoading}
            placeholder="请选择所属部门"
            filterTreeNode={(input, node) =>
              String(node.title ?? '')
                .toLocaleLowerCase()
                .includes(input.trim().toLocaleLowerCase())
            }
          />
        </Form.Item>

        <Form.Item name="description" label="工作空间描述">
          <Input.TextArea
            variant="filled"
            maxLength={500}
            showCount
            autoSize={{ minRows: 4, maxRows: 8 }}
            placeholder="说明这个工作空间对应的医院、团队或业务范围"
          />
        </Form.Item>
      </Form>
    </Drawer>
  );
}
