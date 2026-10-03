import { Form, Input, Modal, Select, Space, Table, Tag, Typography, message } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'umi';
import { YakButton, YakEmpty } from '@/components/ui';
import {
  changeMdmEntityStatus,
  createMdmEntity,
  deleteMdmEntity,
  pageMdmEntities,
  updateMdmEntity,
} from '@/services/mdm/api';
import type {
  MdmEntityRecord,
  MdmEntitySavePayload,
  MdmEntityStatus,
  MdmEntityUpdatePayload,
} from '@/services/mdm/types';

const STATUS_LABELS: Record<MdmEntityStatus, string> = {
  DRAFT: '草稿',
  ACTIVE: '生效',
  DISABLED: '停用',
};

const STATUS_COLORS: Record<MdmEntityStatus, string> = {
  DRAFT: 'default',
  ACTIVE: 'green',
  DISABLED: 'red',
};

const STATUS_OPTIONS = [
  { label: '全部', value: '' as const },
  { label: '草稿', value: 'DRAFT' },
  { label: '生效', value: 'ACTIVE' },
  { label: '停用', value: 'DISABLED' },
];

interface EntityFormValues {
  code?: string;
  name: string;
  owner?: string;
  description?: string;
}

const MdmModelingPage = () => {
  const navigate = useNavigate();
  const [form] = Form.useForm<EntityFormValues>();
  const [records, setRecords] = useState<MdmEntityRecord[]>([]);
  const [total, setTotal] = useState(0);
  const [pageNo, setPageNo] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [keyword, setKeyword] = useState('');
  const [status, setStatus] = useState<MdmEntityStatus | ''>('');
  const [loading, setLoading] = useState(false);
  const [editorOpen, setEditorOpen] = useState(false);
  const [editing, setEditing] = useState<MdmEntityRecord | null>(null);
  const [saving, setSaving] = useState(false);

  const loadEntities = useCallback(
    async (targetPageNo: number, targetPageSize: number) => {
      setLoading(true);
      try {
        const result = await pageMdmEntities({
          pageNo: targetPageNo,
          pageSize: targetPageSize,
          keyword: keyword.trim() || undefined,
          status,
        });
        setRecords(result.bizData ?? []);
        setTotal(result.pagination?.total ?? 0);
      } catch {
        setRecords([]);
        setTotal(0);
        message.error('加载主数据实体失败');
      } finally {
        setLoading(false);
      }
    },
    [keyword, status],
  );

  useEffect(() => {
    void loadEntities(pageNo, pageSize);
  }, [pageNo, pageSize, keyword, status, loadEntities]);

  const openCreate = () => {
    setEditing(null);
    setEditorOpen(true);
    form.resetFields();
  };

  const openEdit = (record: MdmEntityRecord) => {
    setEditing(record);
    setEditorOpen(true);
    form.setFieldsValue({
      name: record.name,
      owner: record.owner,
      description: record.description,
    });
  };

  const submitEditor = async () => {
    const values = await form.validateFields();
    setSaving(true);
    try {
      if (editing) {
        const payload: MdmEntityUpdatePayload = {
          name: values.name,
          owner: values.owner,
          description: values.description,
        };
        await updateMdmEntity(editing.id, payload);
        message.success('实体已更新');
      } else {
        const payload: MdmEntitySavePayload = {
          code: values.code ?? '',
          name: values.name,
          owner: values.owner,
          description: values.description,
        };
        await createMdmEntity(payload);
        message.success('实体已创建');
      }
      setEditorOpen(false);
      await loadEntities(pageNo, pageSize);
    } catch {
      message.error('保存失败（编码可能已存在），请检查后重试');
    } finally {
      setSaving(false);
    }
  };

  const changeStatus = (record: MdmEntityRecord) => {
    const next: MdmEntityStatus =
      record.status === 'DRAFT' ? 'ACTIVE' : record.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE';
    const action = record.status === 'ACTIVE' ? '停用' : '生效';
    Modal.confirm({
      title: `${action}实体`,
      content: `确定${action}「${record.name}（${record.code}）」？`,
      okText: action,
      cancelText: '取消',
      onOk: async () => {
        try {
          await changeMdmEntityStatus(record.id, next);
          message.success(`已${action}`);
          await loadEntities(pageNo, pageSize);
        } catch {
          message.error(`${action}失败（状态流转不合法）`);
        }
      },
    });
  };

  const removeEntity = (record: MdmEntityRecord) => {
    Modal.confirm({
      title: '删除实体',
      content: `确定删除「${record.name}（${record.code}）」？已被属性/来源引用时将被阻断。`,
      okText: '删除',
      okType: 'danger',
      cancelText: '取消',
      onOk: async () => {
        try {
          await deleteMdmEntity(record.id);
          message.success('已删除');
          await loadEntities(pageNo, pageSize);
        } catch {
          message.error('删除失败（可能存在属性或来源引用）');
        }
      },
    });
  };

  const columns: ColumnsType<MdmEntityRecord> = [
    {
      title: '实体编码',
      dataIndex: 'code',
      width: 160,
      render: (code: string, record) => (
        <Typography.Link onClick={() => navigate(`/mdm/modeling/${record.id}`)}>{code}</Typography.Link>
      ),
    },
    { title: '实体名称', dataIndex: 'name', width: 180 },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (value: MdmEntityStatus) => (
        <Tag color={STATUS_COLORS[value]}>{STATUS_LABELS[value]}</Tag>
      ),
    },
    { title: '负责人', dataIndex: 'owner', width: 120, render: (value?: string) => value || '-' },
    {
      title: '描述',
      dataIndex: 'description',
      ellipsis: true,
      render: (value?: string) => value || '-',
    },
    {
      title: '更新时间',
      dataIndex: 'updateTime',
      width: 170,
      render: (value?: string) => (value ? String(value).replace('T', ' ').slice(0, 19) : '-'),
    },
    {
      title: '操作',
      key: 'action',
      width: 220,
      render: (_, record) => (
        <Space size={4}>
          <Typography.Link onClick={() => navigate(`/mdm/modeling/${record.id}`)}>详情</Typography.Link>
          <Typography.Link onClick={() => openEdit(record)}>编辑</Typography.Link>
          {record.status === 'ACTIVE' ? (
            <Typography.Link type="danger" onClick={() => changeStatus(record)}>
              停用
            </Typography.Link>
          ) : (
            <Typography.Link onClick={() => changeStatus(record)}>生效</Typography.Link>
          )}
          <Typography.Link type="danger" onClick={() => removeEntity(record)}>
            删除
          </Typography.Link>
        </Space>
      ),
    },
  ];

  return (
    <div className="min-h-[calc(100dvh-64px)] bg-white px-6 pb-4 pt-5 text-[#242731] max-md:px-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <div className="text-[20px] font-semibold leading-7">主数据建模</div>
          <div className="mt-1 text-[13px] text-[#667085]">
            定义客户、商品、供应商等主数据实体，进入详情管理属性、来源与记录。
          </div>
        </div>
        <YakButton
          type="primary"
          className="!h-9 !rounded-lg !px-4 !text-white"
          onClick={openCreate}
        >
          新建实体
        </YakButton>
      </div>

      <div className="mt-4 flex flex-wrap items-center gap-3">
        <Input
          allowClear
          placeholder="按编码/名称搜索"
          className="!w-64"
          value={keyword}
          onChange={(event) => setKeyword(event.target.value)}
        />
        <Select
          className="!w-32"
          options={STATUS_OPTIONS}
          value={status}
          onChange={setStatus}
        />
      </div>

      <div className="mt-4">
        <Table<MdmEntityRecord>
          rowKey="id"
          columns={columns}
          dataSource={records}
          loading={loading}
          locale={{ emptyText: <YakEmpty compact title="暂无主数据实体" description="点击右上角「新建实体」创建第一个主数据实体" /> }}
          pagination={{
            current: pageNo,
            pageSize,
            total,
            showSizeChanger: true,
            showTotal: (count) => `共 ${count} 条`,
            onChange: (nextPageNo, nextPageSize) => {
              setPageNo(nextPageNo);
              setPageSize(nextPageSize);
            },
          }}
        />
      </div>

      <Modal
        title={editing ? '编辑实体（编码不可修改）' : '新建实体'}
        open={editorOpen}
        onOk={submitEditor}
        confirmLoading={saving}
        onCancel={() => setEditorOpen(false)}
        okText={editing ? '保存' : '创建'}
        cancelText="取消"
        destroyOnClose
      >
        <Form form={form} layout="vertical" preserve={false}>
          {!editing ? (
            <Form.Item
              name="code"
              label="实体编码"
              rules={[
                { required: true, message: '请输入实体编码' },
                { pattern: /^[A-Za-z0-9_]{1,64}$/, message: '仅允许字母、数字和下划线，1~64 位' },
              ]}
            >
              <Input placeholder="如 customer" />
            </Form.Item>
          ) : null}
          <Form.Item
            name="name"
            label="实体名称"
            rules={[{ required: true, message: '请输入实体名称' }]}
          >
            <Input placeholder="如 客户" />
          </Form.Item>
          <Form.Item name="owner" label="负责人">
            <Input placeholder="选填" />
          </Form.Item>
          <Form.Item name="description" label="描述">
            <Input.TextArea placeholder="选填" rows={3} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default MdmModelingPage;
