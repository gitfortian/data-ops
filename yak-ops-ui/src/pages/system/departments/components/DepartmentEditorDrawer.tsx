import { ApartmentOutlined } from '@ant-design/icons';
import {
  Alert,
  Drawer,
  Form,
  Input,
  TreeSelect,
  message,
} from 'antd';
import { useEffect, useMemo, useState } from 'react';

import { YakButton } from '@/components/ui';
import {
  createDepartment,
  type DepartmentInput,
  type DepartmentVO,
  updateDepartment,
} from '@/services/security/departments';

import { getSystemErrorMessage } from '../../utils';
import {
  collectDepartmentIds,
  getDepartmentForest,
  getDirectChildren,
} from '../tree';
import { getDepartmentName } from '../utils';

interface DepartmentEditorDrawerProps {
  open: boolean;
  root?: DepartmentVO;
  department?: DepartmentVO;
  defaultParentId?: number;
  onClose: () => void;
  onSuccess: () => void;
}

interface DepartmentFormValues {
  deptName: string;
  description?: string;
  parentId: number;
}

interface ParentTreeNode {
  value: number;
  title: string;
  children?: ParentTreeNode[];
}

const toParentTreeData = (
  departments: DepartmentVO[],
  blockedIds: Set<number>,
  path = new Set<string>(),
): ParentTreeNode[] =>
  departments.flatMap((department) => {
    const key = String(department.id);
    if (path.has(key) || blockedIds.has(department.id)) return [];

    const nextPath = new Set(path);
    nextPath.add(key);
    const children = toParentTreeData(
      getDirectChildren(department),
      blockedIds,
      nextPath,
    );

    return [
      {
        value: department.id,
        title: getDepartmentName(department),
        ...(children.length > 0 ? { children } : {}),
      },
    ];
  });

export default function DepartmentEditorDrawer({
  open,
  root,
  department,
  defaultParentId,
  onClose,
  onSuccess,
}: DepartmentEditorDrawerProps) {
  const [form] = Form.useForm<DepartmentFormValues>();
  const [isSaving, setIsSaving] = useState(false);
  const editing = Boolean(department);

  const blockedIds = useMemo(() => {
    if (!department) return new Set<number>();
    return new Set([
      department.id,
      ...collectDepartmentIds(getDirectChildren(department)),
    ]);
  }, [department]);

  const parentTreeData = useMemo<ParentTreeNode[]>(
    () => [
      {
        value: 0,
        title: '根部门',
        children: toParentTreeData(
          getDepartmentForest(root),
          blockedIds,
        ),
      },
    ],
    [blockedIds, root],
  );

  useEffect(() => {
    if (!open) return;
    form.resetFields();
    form.setFieldsValue({
      deptName: department?.deptName ?? '',
      description: department?.description ?? '',
      parentId: department?.parentId ?? defaultParentId ?? 0,
    });
  }, [defaultParentId, department, form, open]);

  const close = () => {
    if (!isSaving) onClose();
  };

  const save = async (values: DepartmentFormValues) => {
    if (isSaving) return;
    setIsSaving(true);

    try {
      const body: DepartmentInput = {
        deptName: values.deptName.trim(),
        description: values.description?.trim() ?? '',
        parentId: Number(values.parentId ?? 0),
      };

      if (department) {
        await updateDepartment({ ...body, id: department.id });
      } else {
        await createDepartment(body);
      }

      message.success(editing ? '部门已更新' : '部门已创建');
      onClose();
      onSuccess();
    } catch (error) {
      message.error(
        getSystemErrorMessage(
          error,
          editing ? '部门更新失败' : '部门创建失败',
        ),
      );
    } finally {
      setIsSaving(false);
    }
  };

  return (
    <Drawer
      open={open}
      title={editing ? '编辑部门' : '新增部门'}
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
            {editing ? '更新' : '保存'}
          </YakButton>
        </div>
      }
    >
      <Alert
        showIcon
        type="info"
        className="mb-5"
        icon={<ApartmentOutlined />}
        message={
          editing
            ? '修改上级部门时，后端会同步更新当前部门及全部下级部门的层级。'
            : '可创建根部门，也可以在当前选中部门下新增子部门。'
        }
      />

      <Form<DepartmentFormValues>
        form={form}
        layout="vertical"
        preserve={false}
        disabled={isSaving}
        onFinish={(values) => void save(values)}
      >
        <Form.Item
          name="deptName"
          label="部门名称"
          rules={[
            {
              required: true,
              whitespace: true,
              message: '请输入部门名称',
            },
          ]}
        >
          <Input
            placeholder="请输入部门名称"
            maxLength={64}
            showCount
          />
        </Form.Item>

        <Form.Item
          name="parentId"
          label="上级部门"
          rules={[{ required: true, message: '请选择上级部门' }]}
        >
          <TreeSelect
            treeData={parentTreeData}
            treeDefaultExpandAll
            showSearch
            allowClear={false}
            placeholder="请选择上级部门"
            filterTreeNode={(input, node) =>
              String(node.title ?? '')
                .toLocaleLowerCase()
                .includes(input.trim().toLocaleLowerCase())
            }
          />
        </Form.Item>

        <Form.Item name="description" label="部门描述">
          <Input.TextArea
            placeholder="请输入部门职责或适用范围"
            maxLength={500}
            showCount
            autoSize={{ minRows: 4, maxRows: 8 }}
          />
        </Form.Item>
      </Form>
    </Drawer>
  );
}
