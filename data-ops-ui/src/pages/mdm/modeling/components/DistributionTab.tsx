import { Form, Input, Modal, Popconfirm, Select, Space, Table, Tag, Tooltip, Typography, message } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useCallback, useEffect, useState } from 'react';
import { history } from '@umijs/max';
import { YakButton, YakEmpty } from '@/components/ui';
import {
  changeMdmDistributionStatus,
  createMdmDistribution,
  deleteMdmDistribution,
  executeMdmDistribution,
  getMdmDistributionPublication,
  listMdmDistributions,
  updateMdmDistribution,
} from '@/services/mdm/api';
import type {
  MdmDistributionFreq,
  MdmDistributionMode,
  MdmDistributionPublication,
  MdmDistributionRecord,
  MdmDistributionStatus,
} from '@/services/mdm/types';

const MODE_LABELS: Record<MdmDistributionMode, string> = {
  API: 'API 供数',
  MESSAGE: '消息推送',
  FILE: '文件导出',
};

/** 频率即闹钟:后端把 DAILY/HOURLY 登记成 Yak Schedule 定义(P0-1.8)。 */
const FREQ_LABELS: Record<MdmDistributionFreq, string> = {
  MANUAL: '手动',
  DAILY: '每日 02:00',
  HOURLY: '每小时',
};

const STATUS_LABELS: Record<MdmDistributionStatus, { text: string; color: string }> = {
  DRAFT: { text: '草稿', color: 'default' },
  ACTIVE: { text: '生效', color: 'green' },
  DISABLED: { text: '停用', color: 'orange' },
};

const formatTime = (value?: string | null) =>
  value ? String(value).replace('T', ' ').slice(0, 19) : '';

type FormValues = {
  targetSystem: string;
  targetName?: string;
  distributeMode: MdmDistributionMode;
  distributeFreq: MdmDistributionFreq;
  distributeScope: 'FULL' | 'INCREMENTAL';
};

/**
 * 实体详情"分发配置"Tab(R5 真接):对外供数复用「数据服务」(data-service,零改动)——
 * 一条配置对应一个发布态 API，「执行分发」= 幂等推送发布/刷新并回写当前可供数条数，
 * DAILY/HOURLY 由 Yak Schedule 到点自动做同一件事；API Key 与访问控制留在数据服务页配置。
 * MESSAGE/FILE 通道未接入，后端会明确报错，这里也不给按钮。
 */
const DistributionTab = ({ entityId }: { entityId: number }) => {
  const [rows, setRows] = useState<MdmDistributionRecord[]>([]);
  const [publications, setPublications] = useState<Record<number, MdmDistributionPublication>>({});
  const [loading, setLoading] = useState(false);
  const [editing, setEditing] = useState<MdmDistributionRecord | null>(null);
  const [creating, setCreating] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [running, setRunning] = useState<number | null>(null);
  const [form] = Form.useForm<FormValues>();

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const list = await listMdmDistributions(entityId);
      setRows(list);
      const entries = await Promise.all(
        list
          .filter((item) => item.distributeMode === 'API')
          .map(async (item) => {
            try {
              return [item.id, await getMdmDistributionPublication(item.id)] as const;
            } catch {
              return null;
            }
          }),
      );
      setPublications(
        Object.fromEntries(entries.filter(Boolean) as [number, MdmDistributionPublication][]),
      );
    } catch {
      setRows([]);
    } finally {
      setLoading(false);
    }
  }, [entityId]);

  useEffect(() => {
    void load();
  }, [load]);

  const openCreate = () => {
    form.setFieldsValue({
      targetSystem: '',
      targetName: '',
      distributeMode: 'API',
      distributeFreq: 'MANUAL',
      distributeScope: 'FULL',
    });
    setEditing(null);
    setCreating(true);
  };

  const openEdit = (record: MdmDistributionRecord) => {
    form.setFieldsValue({
      targetSystem: record.targetSystem,
      targetName: record.targetName,
      distributeMode: record.distributeMode,
      distributeFreq: record.distributeFreq,
      distributeScope: (record.distributeScope as 'FULL' | 'INCREMENTAL') || 'FULL',
    });
    setEditing(record);
    setCreating(true);
  };

  const submit = async () => {
    const values = await form.validateFields();
    setSubmitting(true);
    try {
      if (editing) {
        await updateMdmDistribution(editing.id, {
          targetName: values.targetName,
          distributeMode: values.distributeMode,
          distributeFreq: values.distributeFreq,
          distributeScope: values.distributeScope,
        });
        message.success('分发配置已更新，定时频率即时生效');
      } else {
        await createMdmDistribution({ entityId, ...values });
        message.success('分发配置已创建，生效后点「执行分发」即可发布对外 API');
      }
      setCreating(false);
      void load();
    } catch (error: any) {
      message.error(error?.message || '保存分发配置失败');
    } finally {
      setSubmitting(false);
    }
  };

  const execute = async (record: MdmDistributionRecord) => {
    setRunning(record.id);
    try {
      const result = await executeMdmDistribution(record.id);
      message.success(
        `已发布至数据服务：${result.count} 条生效记录可对外供数（${result.apiPath ?? '-'}）`,
      );
      void load();
    } catch (error: any) {
      message.error(error?.message || '执行分发失败');
    } finally {
      setRunning(null);
    }
  };

  const toggle = async (record: MdmDistributionRecord, status: MdmDistributionStatus) => {
    try {
      await changeMdmDistributionStatus(record.id, status);
      message.success(status === 'ACTIVE' ? '分发配置已生效' : '分发配置已停用');
      void load();
    } catch (error: any) {
      message.error(error?.message || '状态切换失败');
    }
  };

  const remove = async (record: MdmDistributionRecord) => {
    try {
      await deleteMdmDistribution(record.id);
      message.success('分发配置已删除');
      void load();
    } catch (error: any) {
      message.error(error?.message || '删除分发配置失败');
    }
  };

  const columns: ColumnsType<MdmDistributionRecord> = [
    {
      title: '目标系统',
      key: 'target',
      width: 200,
      render: (_, record) => (
        <div>
          <div className="font-mono text-[13px]">{record.targetSystem}</div>
          {record.targetName ? (
            <div className="text-[12px] text-[#667085]">{record.targetName}</div>
          ) : null}
        </div>
      ),
    },
    {
      title: '通道',
      dataIndex: 'distributeMode',
      width: 110,
      render: (value: MdmDistributionMode) => (
        <Tag color={value === 'API' ? 'blue' : 'default'}>{MODE_LABELS[value]}</Tag>
      ),
    },
    {
      title: '频率',
      dataIndex: 'distributeFreq',
      width: 110,
      render: (value: MdmDistributionFreq) => FREQ_LABELS[value] ?? value,
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (value: MdmDistributionStatus) => (
        <Tag color={STATUS_LABELS[value]?.color}>{STATUS_LABELS[value]?.text ?? value}</Tag>
      ),
    },
    {
      title: '对外 API',
      key: 'publication',
      width: 260,
      render: (_, record) => {
        if (record.distributeMode !== 'API') {
          return <Tag>通道未接入</Tag>;
        }
        const state = publications[record.id];
        if (!state?.available) {
          return <Tag color="red">数据服务未启用</Tag>;
        }
        if (!state.published) {
          return <Tag>未发布</Tag>;
        }
        return (
          <Space size={6}>
            <Tag color={state.enabled ? 'green' : 'orange'}>{state.enabled ? '已发布' : '已停用'}</Tag>
            <Typography.Link
              className="font-mono text-[12px]"
              onClick={() => history.push(`/data-service/api/${state.apiId}`)}
            >
              {state.path}
            </Typography.Link>
          </Space>
        );
      },
    },
    {
      title: '最近分发',
      key: 'last',
      width: 170,
      render: (_, record) =>
        record.lastDistributeTime ? (
          <Tooltip title={`成功 ${record.lastDistributeCount ?? 0} 条 / 失败 ${record.lastDistributeFail ?? 0} 条`}>
            <span className="text-[12px] text-[#667085]">
              {formatTime(record.lastDistributeTime)}
              <br />
              {record.lastDistributeCount ?? 0} 条可服务
            </span>
          </Tooltip>
        ) : (
          <span className="text-[12px] text-[#98a2b3]">尚未执行</span>
        ),
    },
    {
      title: '操作',
      key: 'action',
      width: 260,
      render: (_, record) => {
        const state = publications[record.id];
        return (
          <Space size={10} wrap>
            {record.distributeMode === 'API' && record.status === 'ACTIVE' ? (
              <Typography.Link
                disabled={running === record.id}
                onClick={() => execute(record)}
              >
                {running === record.id ? '发布中…' : state?.published ? '重新发布' : '执行分发'}
              </Typography.Link>
            ) : null}
            {record.status === 'ACTIVE' ? (
              <Typography.Link onClick={() => toggle(record, 'DISABLED')}>停用</Typography.Link>
            ) : (
              <Typography.Link onClick={() => toggle(record, 'ACTIVE')}>生效</Typography.Link>
            )}
            <Typography.Link onClick={() => openEdit(record)}>编辑</Typography.Link>
            {state?.published ? (
              <Typography.Link onClick={() => history.push('/data-service/access')}>
                API 密钥
              </Typography.Link>
            ) : null}
            <Popconfirm title="删除该分发配置？" onConfirm={() => remove(record)}>
              <Typography.Link type="danger">删除</Typography.Link>
            </Popconfirm>
          </Space>
        );
      },
    },
  ];

  return (
    <div>
      <div className="mb-3 rounded-lg bg-[#f6f7f8] px-3 py-2 text-[13px] text-[#667085]">
        对外供数复用「数据服务」(data-service)：一条配置对应一个发布态 API，读统一主数据表的生效记录、
        数据实时查表；「执行分发」= 发布/刷新该 API，DAILY/HOURLY 由调度到点自动刷新。
        API Key 与访问白名单在数据服务「API 调用」页按 API 授权。
      </div>
      <div className="mb-3 flex justify-end">
        <Space>
          <YakButton className="!h-8 !rounded-lg !px-3" onClick={load}>
            刷新
          </YakButton>
          <YakButton type="primary" className="!h-8 !rounded-lg !px-3" onClick={openCreate}>
            新建分发配置
          </YakButton>
        </Space>
      </div>
      <Table<MdmDistributionRecord>
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
              title="暂无分发配置"
              description="新建一条 API 通道配置并生效，即可把该实体的主数据对外供数"
            />
          ),
        }}
      />
      <Modal
        title={editing ? '编辑分发配置' : '新建分发配置'}
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
            name="targetSystem"
            label="目标系统编码"
            rules={[{ required: true, message: '请填写目标系统编码' }]}
            extra={editing ? '目标系统编码创建后不可修改' : undefined}
          >
            <Input placeholder="如 CRM" maxLength={64} disabled={!!editing} />
          </Form.Item>
          <Form.Item name="targetName" label="目标系统名称">
            <Input placeholder="如 销售中台" maxLength={128} />
          </Form.Item>
          <Form.Item name="distributeMode" label="分发通道" rules={[{ required: true }]}>
            <Select
              options={[
                { label: MODE_LABELS.API, value: 'API' },
                { label: `${MODE_LABELS.MESSAGE}（后续增量接入）`, value: 'MESSAGE' },
                { label: `${MODE_LABELS.FILE}（后续增量接入）`, value: 'FILE' },
              ]}
            />
          </Form.Item>
          <Form.Item
            name="distributeFreq"
            label="分发频率"
            rules={[{ required: true }]}
            extra="DAILY/HOURLY 会登记为定时任务，到点自动刷新发布态"
          >
            <Select
              options={(Object.keys(FREQ_LABELS) as MdmDistributionFreq[]).map((value) => ({
                label: FREQ_LABELS[value],
                value,
              }))}
            />
          </Form.Item>
          <Form.Item name="distributeScope" label="分发范围" rules={[{ required: true }]}>
            <Select
              options={[
                { label: '全量生效记录', value: 'FULL' },
                { label: '增量（按记录更新时间，后续增量接入）', value: 'INCREMENTAL' },
              ]}
            />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default DistributionTab;
