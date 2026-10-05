import {
  Button,
  Drawer,
  Form,
  Input,
  Modal,
  Radio,
  Space,
  Switch,
  Table,
  Tag,
  message,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useCallback, useEffect, useState } from 'react';

import UserSelect from '@/components/UserSelect';
import { YakEmpty } from '@/components/ui';
import { usePermissionAccess } from '@/hooks/usePermissionAccess';
import {
  createFlow,
  deleteFlow,
  listFlows,
  toggleFlow,
  updateFlow,
} from '@/services/approval/api';
import type { ApprovalFlow } from '@/services/approval/types';

interface FlowFormValues {
  flowCode: string;
  flowName: string;
  description?: string;
  levelCount: 1 | 2;
  approvers: string[][];
}

const FlowDrawer = ({
  open,
  editing,
  onClose,
  onSaved,
}: {
  open: boolean;
  editing: ApprovalFlow | null;
  onClose: () => void;
  onSaved: () => void;
}) => {
  const [form] = Form.useForm<FlowFormValues>();
  const [submitting, setSubmitting] = useState(false);
  const levelCount = Form.useWatch('levelCount', form) ?? editing?.steps.length ?? 1;

  useEffect(() => {
    if (!open) return;
    if (editing) {
      form.setFieldsValue({
        flowCode: editing.flowCode,
        flowName: editing.flowName,
        description: editing.description ?? undefined,
        levelCount: (editing.steps.length || 1) as 1 | 2,
        approvers: editing.steps.map((step) => step.approvers),
      });
    } else {
      form.resetFields();
      form.setFieldsValue({ levelCount: 1, approvers: [[]] });
    }
  }, [open, editing, form]);

  const submit = async () => {
    const values = await form.validateFields();
    const payload = {
      flowCode: values.flowCode?.trim().toUpperCase(),
      flowName: values.flowName.trim(),
      description: values.description?.trim() || undefined,
      steps: values.approvers
        .slice(0, values.levelCount)
        .map((approvers) => ({ approvers: (approvers ?? []).map((name) => name.trim()).filter(Boolean) })),
    };
    setSubmitting(true);
    try {
      if (editing) {
        await updateFlow(editing.id, payload);
        message.success('流程已更新');
      } else {
        await createFlow(payload);
        message.success('流程已创建（默认启用）');
      }
      onSaved();
      onClose();
    } catch (error: any) {
      message.error(error?.message ?? '保存失败');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Drawer
      open={open}
      title={editing ? `编辑流程：${editing.flowName}` : '新建审批流程'}
      width={520}
      onClose={onClose}
      destroyOnClose
      extra={
        <Space>
          <Button onClick={onClose}>取消</Button>
          <Button type="primary" loading={submitting} onClick={submit}>
            保存
          </Button>
        </Space>
      }
    >
      <Form form={form} layout="vertical" initialValues={{ levelCount: 1, approvers: [[]] }}>
        <Form.Item
          name="flowCode"
          label="流程编码"
          tooltip="业务代码按编码引用（如 MODEL_PUBLISH），保存后不可修改"
          rules={[
            { required: true, message: '请输入流程编码' },
            { pattern: /^[A-Z][A-Z0-9_]{1,63}$/, message: '大写字母开头，仅 A-Z/0-9/_，2~64 位' },
          ]}
        >
          <Input placeholder="如 MODEL_PUBLISH" disabled={Boolean(editing)} />
        </Form.Item>
        <Form.Item
          name="flowName"
          label="流程名称"
          rules={[{ required: true, message: '请输入流程名称' }]}
        >
          <Input placeholder="如 模型发布审批" maxLength={128} />
        </Form.Item>
        <Form.Item name="description" label="说明">
          <Input.TextArea rows={2} maxLength={512} placeholder="流程用途说明（可选）" />
        </Form.Item>
        <Form.Item
          name="levelCount"
          label="审批级数"
          tooltip="级间串行，同级任一审批人处理即定级；任一级拒绝则整单拒绝"
          rules={[{ required: true }]}
        >
          <Radio.Group
            options={[
              { value: 1, label: '单级审批' },
              { value: 2, label: '两级审批（先业务后管理）' },
            ]}
            onChange={(event) => {
              const next = event.target.value as 1 | 2;
              const current: string[][] = form.getFieldValue('approvers') ?? [];
              form.setFieldValue(
                'approvers',
                Array.from({ length: next }, (_, i) => current[i] ?? []),
              );
            }}
          />
        </Form.Item>
        {Array.from({ length: levelCount }).map((_, level) => (
          <Form.Item
            key={level}
            name={['approvers', level]}
            label={`第 ${level + 1} 级审批人`}
            tooltip="同级多人=任一人处理即定级（其余同侪自动跳过），最多 10 人"
            rules={[{ required: true, message: '请至少选择一位审批人' }]}
          >
            <UserSelect placeholder="搜索系统用户" max={10} />
          </Form.Item>
        ))}
      </Form>
    </Drawer>
  );
};

const FlowConfigPage = () => {
  const { can } = usePermissionAccess();
  const canCreate = can('data-approval:create');
  const canManage = can('data-approval:manage');

  const [rows, setRows] = useState<ApprovalFlow[]>([]);
  const [keyword, setKeyword] = useState('');
  const [pageNo, setPageNo] = useState(1);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [editing, setEditing] = useState<ApprovalFlow | null>(null);

  const load = useCallback(async (search = '', page = 1) => {
    setLoading(true);
    try {
      const result = await listFlows(search.trim() || undefined, page, 20);
      setRows(result.bizData ?? []);
      setTotal(result.pagination?.total ?? 0);
    } catch (error: any) {
      message.error(error?.message ?? '流程列表加载失败');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load('', 1);
  }, [load]);

  const toggle = async (flow: ApprovalFlow) => {
    try {
      await toggleFlow(flow.id);
      message.success(flow.enabled ? '已停用' : '已启用');
      void load(keyword, pageNo);
    } catch (error: any) {
      message.error(error?.message ?? '操作失败');
    }
  };

  const remove = (flow: ApprovalFlow) => {
    Modal.confirm({
      title: `删除流程「${flow.flowName}」`,
      content: '软删除并释放编码，历史审批单不受影响；存在在途审批单时无法删除。',
      okText: '删除',
      okButtonProps: { danger: true },
      cancelText: '取消',
      onOk: async () => {
        try {
          await deleteFlow(flow.id);
          message.success('已删除');
          const nextPage = rows.length === 1 && pageNo > 1 ? pageNo - 1 : pageNo;
          setPageNo(nextPage);
          void load(keyword, nextPage);
        } catch (error: any) {
          message.error(error?.message ?? '删除失败');
        }
      },
    });
  };

  const columns: ColumnsType<ApprovalFlow> = [
    { title: '流程编码', dataIndex: 'flowCode', width: 180 },
    { title: '流程名称', dataIndex: 'flowName', width: 180, ellipsis: true },
    {
      title: '级次配置',
      render: (_, flow) => (
        <Space direction="vertical" size={2}>
          {flow.steps.map((step) => (
            <span key={step.level} className="text-[13px]">
              <span className="mr-2 text-[#667085]">第 {step.level} 级</span>
              {step.approvers.map((name) => (
                <Tag key={name} className="mr-1">
                  {name}
                </Tag>
              ))}
            </span>
          ))}
        </Space>
      ),
    },
    { title: '说明', dataIndex: 'description', ellipsis: true, render: (value) => value || '-' },
    {
      title: '状态',
      dataIndex: 'enabled',
      width: 90,
      render: (enabled: boolean, flow) =>
        canManage ? (
          <Switch checked={enabled} checkedChildren="启用" unCheckedChildren="停用" onChange={() => toggle(flow)} />
        ) : (
          <Tag color={enabled ? 'success' : 'default'}>{enabled ? '启用' : '停用'}</Tag>
        ),
    },
    {
      title: '操作',
      width: 140,
      render: (_, flow) => (
        <Space size={0}>
          <Button
            type="link"
            size="small"
            disabled={!canManage}
            onClick={() => {
              setEditing(flow);
              setDrawerOpen(true);
            }}
          >
            编辑
          </Button>
          <Button type="link" size="small" danger disabled={!canManage} onClick={() => remove(flow)}>
            删除
          </Button>
        </Space>
      ),
    },
  ];

  return (
    <div className="min-h-[calc(100dvh-64px)] bg-white px-6 pb-6 pt-5 text-[#242731] max-md:px-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <div className="text-[20px] font-semibold leading-7">流程配置</div>
          <div className="mt-1 text-[13px] text-[#667085]">
            定义审批级次与审批人；业务代码按流程编码发起审批
          </div>
        </div>
        <Space>
          <Input.Search
            allowClear
            placeholder="搜索编码/名称"
            className="!w-60"
            value={keyword}
            onChange={(event) => setKeyword(event.target.value)}
            onSearch={(value) => {
              setKeyword(value);
              setPageNo(1);
              void load(value, 1);
            }}
          />
          <Button
            type="primary"
            disabled={!canCreate}
            onClick={() => {
              setEditing(null);
              setDrawerOpen(true);
            }}
          >
            新建流程
          </Button>
        </Space>
      </div>
      <Table
        className="mt-4"
        rowKey="id"
        size="middle"
        loading={loading}
        columns={columns}
        dataSource={rows}
        locale={{
          emptyText: (
            <YakEmpty
              description={canCreate ? '暂无流程定义，先新建一条' : '暂无流程定义，或无配置权限'}
            />
          ),
        }}
        pagination={{
          current: pageNo,
          pageSize: 20,
          total,
          showTotal: (count) => `共 ${count} 条`,
          onChange: (page) => {
            setPageNo(page);
            void load(keyword, page);
          },
        }}
      />
      <FlowDrawer
        open={drawerOpen}
        editing={editing}
        onClose={() => {
          setDrawerOpen(false);
          setEditing(null);
        }}
        onSaved={() => void load(keyword, pageNo)}
      />
    </div>
  );
};

export default FlowConfigPage;
