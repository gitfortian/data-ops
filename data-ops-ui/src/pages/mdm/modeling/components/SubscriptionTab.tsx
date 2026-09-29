import {
  Alert,
  Form,
  Input,
  Modal,
  Popconfirm,
  Select,
  Space,
  Table,
  Tag,
  Tooltip,
  Typography,
  message,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useCallback, useEffect, useRef, useState } from 'react';
import { YakButton, YakEmpty } from '@/components/ui';
import { pageUsers, type SystemUser } from '@/services/security/users';
import {
  changeMdmSubscriptionStatus,
  createMdmSubscription,
  deleteMdmSubscription,
  listMdmSubscriptions,
  updateMdmSubscription,
} from '@/services/mdm/api';
import type {
  MdmSubscriptionRecord,
  MdmSubscriptionStatus,
} from '@/services/mdm/types';

const EVENT_LABEL = '站内信';

const formatTime = (value?: string | null) =>
  value ? String(value).replace('T', ' ').slice(0, 19) : '';

type UserOption = { value: string; label: string; realName?: string };

const toOption = (user: SystemUser): UserOption => ({
  value: user.userName,
  label: user.realName?.trim() ? `${user.realName}（${user.userName}）` : user.userName,
  realName: user.realName,
});

type FormValues = { subscriberCode: string; subscriberName?: string };

/**
 * 实体详情「订阅」Tab(R6):订阅=该实体发生「变更生效 / 合并完成 / 分发发布」时收到站内信。
 * 收件人由订阅方编码解析成平台用户，所以这里让用户「选」而不是「填」——填错的编码后端会
 * 标 reachable=false，列表里直接显红，避免订阅建好了却静默收不到。
 * WEBHOOK 依赖外部 HTTP 出口，一期未接入，因此不提供该选项。
 */
const SubscriptionTab = ({ entityId }: { entityId: number }) => {
  const [rows, setRows] = useState<MdmSubscriptionRecord[]>([]);
  const [loading, setLoading] = useState(false);
  const [editing, setEditing] = useState<MdmSubscriptionRecord | null>(null);
  const [creating, setCreating] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [options, setOptions] = useState<UserOption[]>([]);
  const [fetching, setFetching] = useState(false);
  const pickedRealName = useRef<string | undefined>(undefined);
  const [form] = Form.useForm<FormValues>();

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setRows(await listMdmSubscriptions(entityId));
    } catch {
      setRows([]);
    } finally {
      setLoading(false);
    }
  }, [entityId]);

  useEffect(() => {
    void load();
  }, [load]);

  const searchUsers = useCallback(async (keyword: string) => {
    setFetching(true);
    try {
      const result = await pageUsers({ pageNum: 1, pageSize: 20, userName: keyword || undefined });
      setOptions((result.records ?? []).map(toOption));
    } catch {
      message.error('用户列表加载失败');
    } finally {
      setFetching(false);
    }
  }, []);

  useEffect(() => {
    if (creating) void searchUsers('');
  }, [creating, searchUsers]);

  const openCreate = () => {
    pickedRealName.current = undefined;
    form.setFieldsValue({ subscriberCode: undefined, subscriberName: undefined });
    setEditing(null);
    setCreating(true);
  };

  const openEdit = (record: MdmSubscriptionRecord) => {
    pickedRealName.current = record.subscriberName;
    form.setFieldsValue({
      subscriberCode: record.subscriberCode,
      subscriberName: record.subscriberName,
    });
    setEditing(record);
    setCreating(true);
  };

  const onPickUser = (code: string) => {
    const hit = options.find((item) => item.value === code);
    pickedRealName.current = hit?.realName;
    if (hit?.realName && !form.getFieldValue('subscriberName')) {
      form.setFieldsValue({ subscriberName: hit.realName });
    }
  };

  const submit = async () => {
    const values = await form.validateFields();
    setSubmitting(true);
    try {
      if (editing) {
        await updateMdmSubscription(editing.id, { subscriberName: values.subscriberName });
      } else {
        await createMdmSubscription({
          entityId,
          subscriberCode: values.subscriberCode,
          subscriberName: values.subscriberName || pickedRealName.current,
          notifyMode: 'EVENT',
        });
      }
      message.success(editing ? '订阅已更新' : '订阅已生效，该实体发生变更时会收到站内信');
      setCreating(false);
      void load();
    } catch (error: any) {
      message.error(error?.message || '保存订阅失败');
    } finally {
      setSubmitting(false);
    }
  };

  const toggle = async (record: MdmSubscriptionRecord, status: MdmSubscriptionStatus) => {
    try {
      await changeMdmSubscriptionStatus(record.id, status);
      message.success(status === 'ACTIVE' ? '订阅已生效' : '订阅已停用');
      void load();
    } catch (error: any) {
      message.error(error?.message || '状态切换失败');
    }
  };

  const remove = async (record: MdmSubscriptionRecord) => {
    try {
      await deleteMdmSubscription(record.id);
      message.success('订阅已删除');
      void load();
    } catch (error: any) {
      message.error(error?.message || '删除订阅失败');
    }
  };

  const columns: ColumnsType<MdmSubscriptionRecord> = [
    {
      title: '订阅方',
      key: 'subscriber',
      width: 240,
      render: (_, record) => (
        <div>
          <div>{record.subscriberName || record.subscriberCode}</div>
          <div className="font-mono text-[12px] text-[#667085]">{record.subscriberCode}</div>
        </div>
      ),
    },
    {
      title: '通知方式',
      dataIndex: 'notifyMode',
      width: 200,
      render: (value: string, record) => (
        <Space size={6}>
          <Tag color="blue">{value === 'EVENT' ? EVENT_LABEL : value}</Tag>
          {record.reachable ? null : (
            <Tooltip title={`订阅方编码 ${record.subscriberCode} 不是有效的平台用户，站内信无法投递；请删除后重新选择用户`}>
              <Tag color="red">账号不可达</Tag>
            </Tooltip>
          )}
        </Space>
      ),
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (value: MdmSubscriptionStatus) => (
        <Tag color={value === 'ACTIVE' ? 'green' : 'orange'}>
          {value === 'ACTIVE' ? '生效' : '停用'}
        </Tag>
      ),
    },
    {
      title: '订阅人',
      dataIndex: 'createdBy',
      width: 120,
      render: (value?: string | null) => value || '-',
    },
    {
      title: '订阅时间',
      dataIndex: 'createTime',
      width: 170,
      render: (value?: string) => (
        <span className="text-[12px] text-[#667085]">{formatTime(value) || '-'}</span>
      ),
    },
    {
      title: '操作',
      key: 'action',
      width: 180,
      render: (_, record) => (
        <Space size={10} wrap>
          <Typography.Link onClick={() => openEdit(record)}>编辑</Typography.Link>
          {record.status === 'ACTIVE' ? (
            <Typography.Link onClick={() => toggle(record, 'DISABLED')}>停用</Typography.Link>
          ) : (
            <Typography.Link onClick={() => toggle(record, 'ACTIVE')}>生效</Typography.Link>
          )}
          <Popconfirm title="删除该订阅？" onConfirm={() => remove(record)}>
            <Typography.Link type="danger">删除</Typography.Link>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <div>
      <Alert
        className="mb-3"
        type="info"
        showIcon
        message={
          <span className="text-[13px]">
            订阅后，该实体发生「变更审批生效 / 重复记录合并 / 分发 API 发布」时会向订阅方发<strong>站内信</strong>，
            在右上角「消息中心」查看并点击跳回本实体。订阅方须是平台用户（按登录名投递）；
            WEBHOOK 回调依赖外部 HTTP 出口，本期未接入。
          </span>
        }
      />
      <div className="mb-3 flex justify-end">
        <Space>
          <YakButton className="!h-8 !rounded-lg !px-3" onClick={load}>
            刷新
          </YakButton>
          <YakButton type="primary" className="!h-8 !rounded-lg !px-3" onClick={openCreate}>
            新建订阅
          </YakButton>
        </Space>
      </div>
      <Table<MdmSubscriptionRecord>
        rowKey="id"
        columns={columns}
        dataSource={rows}
        loading={loading}
        size="middle"
        pagination={false}
        locale={{
          emptyText: (
            <YakEmpty
              compact
              title="暂无订阅方"
              description="选择一个平台用户作为订阅方，该实体的主数据变更即可通知到人"
            />
          ),
        }}
      />
      <Modal
        title={editing ? '编辑订阅' : '新建订阅'}
        open={creating}
        onOk={submit}
        confirmLoading={submitting}
        onCancel={() => setCreating(false)}
        okText="保存"
        cancelText="取消"
        destroyOnClose
      >
        <Form form={form} layout="vertical" className="mt-4">
          <Form.Item
            name="subscriberCode"
            label="订阅方（平台用户）"
            rules={[{ required: true, message: '请选择订阅方' }]}
            extra={editing ? '订阅方创建后不可修改' : '按登录名投递站内信，可选范围即系统用户'}
          >
            <Select
              showSearch
              allowClear
              disabled={!!editing}
              placeholder="搜索并选择订阅方"
              filterOption={false}
              loading={fetching}
              onSearch={(keyword) => void searchUsers(keyword)}
              onChange={onPickUser}
              options={options}
            />
          </Form.Item>
          <Form.Item name="subscriberName" label="订阅方名称" extra="选择订阅方后自动带出，可覆盖">
            <Input placeholder="如 销售中台值班" maxLength={128} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default SubscriptionTab;
