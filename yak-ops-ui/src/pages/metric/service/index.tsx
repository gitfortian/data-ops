import { Form, Input, Modal, message, Space, Table, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'umi';
import { YakButton, YakEmpty, YakTab } from '@/components/ui';
import {
  createMetricTag,
  deleteMetricTag,
  getMetricStats,
  listMetricTags,
  pageMetrics,
  updateMetricTag,
} from '@/services/metric/api';
import type { MetricRecord, MetricStats, MetricTagRecord } from '@/services/metric/types';
import { formatMetricTime } from '../constants';

interface TagFormValues {
  tagName: string;
  sortOrder?: number;
}

const MetricServicePage = () => {
  const navigate = useNavigate();
  const [tags, setTags] = useState<MetricTagRecord[]>([]);
  const [tagMetricCounts, setTagMetricCounts] = useState<Record<number, number>>({});
  const [tagsLoading, setTagsLoading] = useState(false);
  const [tagModalOpen, setTagModalOpen] = useState(false);
  const [editingTag, setEditingTag] = useState<MetricTagRecord | null>(null);
  const [tagForm] = Form.useForm<TagFormValues>();
  const [savingTag, setSavingTag] = useState(false);
  const [stats, setStats] = useState<MetricStats | null>(null);
  const [metrics, setMetrics] = useState<MetricRecord[]>([]);
  const [metricsTotal, setMetricsTotal] = useState(0);

  const loadTags = useCallback(async () => {
    setTagsLoading(true);
    try {
      const result = await listMetricTags();
      const list = result ?? [];
      setTags(list);
      // 已挂载指标数：复用 page(tagIds) 过滤取 total，单标签一次轻量查询
      const entries = await Promise.all(
        list.map(async (tag: MetricTagRecord) => {
          try {
            const page = await pageMetrics({ pageNo: 1, pageSize: 1, tagIds: [tag.id] });
            return [tag.id, page.total ?? 0] as const;
          } catch {
            return null;
          }
        }),
      );
      setTagMetricCounts(Object.fromEntries(entries.filter((e): e is [number, number] => e !== null)));
    } catch {
      message.error('加载标签列表失败');
    } finally {
      setTagsLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadTags();
    getMetricStats()
      .then(setStats)
      .catch(() => setStats(null));
    pageMetrics({ pageNo: 1, pageSize: 20 })
      .then((result) => {
        setMetrics(result.records ?? []);
        setMetricsTotal(result.total ?? 0);
      })
      .catch(() => {
        setMetrics([]);
        setMetricsTotal(0);
      });
  }, [loadTags]);

  const openCreateTag = () => {
    setEditingTag(null);
    tagForm.resetFields();
    setTagModalOpen(true);
  };

  const openEditTag = (record: MetricTagRecord) => {
    setEditingTag(record);
    tagForm.setFieldsValue({ tagName: record.tagName, sortOrder: record.sortOrder });
    setTagModalOpen(true);
  };

  const submitTag = async () => {
    const values = await tagForm.validateFields();
    setSavingTag(true);
    try {
      if (editingTag) {
        await updateMetricTag(editingTag.id, values.tagName, values.sortOrder);
        message.success('标签已更新');
      } else {
        await createMetricTag(values.tagName);
        message.success('标签已创建');
      }
      setTagModalOpen(false);
      await loadTags();
    } catch {
      message.error('保存失败（标签名可能已存在），请检查后重试');
    } finally {
      setSavingTag(false);
    }
  };

  const removeTag = (record: MetricTagRecord) => {
    Modal.confirm({
      title: '删除标签',
      content: `确定删除标签「${record.tagName}」？已关联该标签的指标将解除绑定。`,
      okText: '删除',
      okType: 'danger',
      cancelText: '取消',
      onOk: async () => {
        try {
          await deleteMetricTag(record.id);
          message.success('已删除');
          await loadTags();
        } catch {
          message.error('删除失败，请稍后重试');
        }
      },
    });
  };

  const tagColumns: ColumnsType<MetricTagRecord> = [
    { title: '标签名', dataIndex: 'tagName', width: 180 },
    {
      title: '标签编码',
      dataIndex: 'tagCode',
      width: 160,
      render: (v: string) => <Typography.Text code>{v}</Typography.Text>,
    },
    { title: '排序', dataIndex: 'sortOrder', width: 80, align: 'right' as const },
    {
      title: '已挂载指标',
      key: 'metricCount',
      width: 110,
      align: 'right' as const,
      render: (_, record) => {
        const count = tagMetricCounts[record.id];
        if (count === undefined) return '-';
        if (!count) return '0';
        return <Typography.Link onClick={() => navigate(`/metric/manage?tagIds=${record.id}`)}>{count}</Typography.Link>;
      },
    },
    { title: '状态', dataIndex: 'status', width: 80, render: (v: string) => v || '-' },
    {
      title: '操作',
      key: 'actions',
      width: 140,
      render: (_, record) => (
        <Space size={0}>
          <Typography.Link onClick={() => openEditTag(record)}>编辑</Typography.Link>
          <Typography.Link type="danger" onClick={() => removeTag(record)}>
            删除
          </Typography.Link>
        </Space>
      ),
    },
  ];

  const metricColumns: ColumnsType<MetricRecord> = [
    { title: '指标编码', dataIndex: 'metricCode', width: 160 },
    { title: '指标名称', dataIndex: 'metricName', width: 180 },
    { title: '版本', dataIndex: 'version', width: 60, align: 'right' as const, render: (v: number) => `v${v}` },
    {
      title: '更新时间',
      dataIndex: 'updateTime',
      width: 170,
      render: (v?: string) => formatMetricTime(v),
    },
  ];

  return (
    <div className="min-h-[calc(100dvh-64px)] bg-white px-6 pb-4 pt-5 text-[#242731] max-md:px-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <div className="text-[20px] font-semibold leading-7">指标服务</div>
          <div className="mt-1 text-[13px] text-[#667085]">标签管理、指标统计与元数据服务概览</div>
        </div>
      </div>

      <div className="mt-4">
        <YakTab
          defaultActiveKey="tags"
          items={[
            {
              key: 'tags',
              label: `标签管理 (${tags.length})`,
              children: (
                <div>
                  <div className="mb-3 flex justify-end">
                    <YakButton type="primary" className="!h-8 !rounded-lg !px-3 !text-white" onClick={openCreateTag}>
                      新建标签
                    </YakButton>
                  </div>
                  <Table<MetricTagRecord>
                    rowKey="id"
                    columns={tagColumns}
                    dataSource={tags}
                    loading={tagsLoading}
                    pagination={false}
                    size="small"
                    locale={{
                      emptyText: <YakEmpty compact title="暂无标签" description="创建标签后可为指标分组标记" />,
                    }}
                  />
                </div>
              ),
            },
            {
              key: 'stats',
              label: '指标统计',
              children: stats ? (
                <div className="grid grid-cols-4 gap-4">
                  <StatCard title="指标总数" value={stats.total} />
                  <StatCard title="原子指标" value={stats.atomic} color="#1677ff" />
                  <StatCard title="派生指标" value={stats.derived} color="#fa8c16" />
                  <StatCard title="复合指标" value={stats.composite} color="#722ed1" />
                </div>
              ) : (
                <YakEmpty compact title="暂无统计数据" description="指标创建后自动统计" />
              ),
            },
            {
              key: 'catalog',
              label: `指标目录 (${metricsTotal})`,
              children: (
                <Table<MetricRecord>
                  rowKey="id"
                  columns={metricColumns}
                  dataSource={metrics}
                  pagination={false}
                  size="small"
                  locale={{
                    emptyText: <YakEmpty compact title="暂无指标" description="在指标管理页创建指标" />,
                  }}
                />
              ),
            },
          ]}
        />
      </div>

      <Modal
        title={editingTag ? '编辑标签' : '新建标签'}
        open={tagModalOpen}
        onOk={submitTag}
        confirmLoading={savingTag}
        onCancel={() => setTagModalOpen(false)}
        okText={editingTag ? '保存' : '创建'}
        cancelText="取消"
        destroyOnClose
      >
        <Form form={tagForm} layout="vertical" preserve={false}>
          <Form.Item
            name="tagName"
            label="标签名"
            rules={[
              { required: true, message: '请输入标签名' },
              { max: 32, message: '标签名不超过 32 个字符' },
            ]}
          >
            <Input placeholder="如 核心指标、实验指标" />
          </Form.Item>
          {editingTag ? (
            <Form.Item name="sortOrder" label="排序">
              <Input type="number" placeholder="数值越小越靠前" />
            </Form.Item>
          ) : null}
        </Form>
      </Modal>
    </div>
  );
};

const StatCard = ({ title, value, color }: { title: string; value: number; color?: string }) => (
  <div className="rounded-lg border border-[#e5e7eb] px-4 py-3">
    <div className="text-[22px] font-semibold" style={color ? { color } : undefined}>
      {value}
    </div>
    <div className="mt-1 text-[13px] text-[#667085]">{title}</div>
  </div>
);

export default MetricServicePage;
