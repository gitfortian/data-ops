import Table from '@/components/ReadableTable';
import YakButton from '@/components/YakButton';
import { getMetadataOverview } from '@/services/metadata';
import type {
  MetadataOverviewData,
  MetadataOverviewEntityType,
  MetadataOverviewOpenTask,
  MetadataOverviewRecentRun,
} from '@/services/metadata/types';
import { history } from '@umijs/max';
import {
  Alert,
  Button,
  Card,
  Col,
  Empty,
  Row,
  Spin,
  Statistic,
  Tag,
  Typography,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useCallback, useEffect, useState } from 'react';
import { PageHeader } from '../shared';

const channelLabels: Record<string, string> = {
  HARVESTED: '物理采集',
  REGISTERED: '源域对账',
};

const taskLabels: Record<string, string> = {
  FILL_COMMENT: '待补充注释',
  CONFIRM_LABEL: '待确认标签',
  FIX_CONFORMANCE: '待修复一致性',
  REVIEW_GONE: '待复核已撤销实体',
};

const runStatus = (status: string) => {
  if (status === 'SUCCESS') return { color: 'success', label: '成功' };
  if (status === 'SUSPECT') return { color: 'warning', label: '疑似坍塌，已熔断' };
  if (status === 'FAILED') return { color: 'error', label: '失败' };
  return { color: 'processing', label: status || '运行中' };
};

const formatDateTime = (value?: string | null) =>
  value ? new Date(value).toLocaleString() : '—';

const formatDuration = (durationMs?: number | null) => {
  if (durationMs == null) return '—';
  if (durationMs < 1000) return `${durationMs} ms`;
  return `${(durationMs / 1000).toFixed(1)} 秒`;
};

const MetadataOverviewPage = () => {
  const [overview, setOverview] = useState<MetadataOverviewData>();
  const [loading, setLoading] = useState(true);
  const [failed, setFailed] = useState(false);

  const loadOverview = useCallback(async () => {
    setLoading(true);
    setFailed(false);
    try {
      setOverview(await getMetadataOverview());
    } catch {
      setOverview(undefined);
      setFailed(true);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadOverview();
  }, [loadOverview]);

  const entityColumns: ColumnsType<MetadataOverviewEntityType> = [
    { title: '实体类型', dataIndex: 'displayName', key: 'displayName' },
    { title: '类型编码', dataIndex: 'typeName', key: 'typeName' },
    {
      title: '来源方式',
      dataIndex: 'collectible',
      key: 'collectible',
      render: (collectible: boolean) =>
        collectible ? <Tag color="blue">物理采集</Tag> : <Tag>源域登记</Tag>,
    },
    {
      title: '类型状态',
      dataIndex: 'status',
      key: 'status',
      render: (status: string) =>
        status === 'ACTIVE' ? <Tag color="success">使用中</Tag> : <Tag>{status}</Tag>,
    },
    { title: '目录实体数', dataIndex: 'entityCount', key: 'entityCount', align: 'right' },
  ];

  const runColumns: ColumnsType<MetadataOverviewRecentRun> = [
    {
      title: '运行状态',
      dataIndex: 'status',
      key: 'status',
      render: (status: string) => {
        const view = runStatus(status);
        return <Tag color={view.color}>{view.label}</Tag>;
      },
    },
    {
      title: '任务',
      key: 'job',
      render: (_, run) => run.jobName || run.jobCode || `任务 #${run.jobId}`,
    },
    {
      title: '通道',
      dataIndex: 'providerType',
      key: 'providerType',
      render: (value: string) => channelLabels[value] || value,
    },
    {
      title: '触发方式',
      key: 'trigger',
      render: (_, run) =>
        run.dryRun
          ? '预演'
          : run.triggerType === 'SCHEDULE'
            ? '定时'
            : '手动',
    },
    {
      title: '对象计数',
      key: 'counts',
      render: (_, run) =>
        `总 ${run.totalCount} · 新增 ${run.newCount} · 变更 ${run.changedCount} · 撤销 ${run.goneCount}${
          run.partialFailedCount ? ` · 部分失败 ${run.partialFailedCount}` : ''
        }`,
    },
    {
      title: '开始时间',
      dataIndex: 'startedAt',
      key: 'startedAt',
      render: formatDateTime,
    },
    {
      title: '耗时',
      dataIndex: 'durationMs',
      key: 'durationMs',
      render: formatDuration,
    },
  ];

  const taskColumns: ColumnsType<MetadataOverviewOpenTask> = [
    {
      title: '待办类型',
      dataIndex: 'taskType',
      key: 'taskType',
      render: (taskType: string) => taskLabels[taskType] || taskType,
    },
    { title: '未办结数量', dataIndex: 'openCount', key: 'openCount', align: 'right' },
  ];

  const recentAttentionCount =
    overview?.recentRuns.filter((run) => run.status === 'FAILED' || run.status === 'SUSPECT').length ?? 0;

  return (
    <div className="p-6 max-md:p-4">
      <PageHeader
        title="元数据概览"
        subtitle="查看当前项目的技术目录、采集与对账运行状态，以及元数据治理待办。"
        extra={
          <div className="flex flex-wrap gap-2">
            <Button onClick={() => history.push('/metadata/explorer')}>浏览元数据实体</Button>
            <YakButton type="primary" onClick={() => history.push('/data-metadata/collect')}>
              管理采集与对账
            </YakButton>
          </div>
        }
      />

      {failed ? (
        <Alert
          className="mt-4"
          type="error"
          showIcon
          message="元数据概览暂时无法加载"
          description="请检查项目上下文和服务状态后重试。"
          action={<Button onClick={() => void loadOverview()}>重试</Button>}
        />
      ) : null}

      <Spin spinning={loading}>
        {overview ? (
          <div className="mt-4 space-y-4">
            <Row gutter={[16, 16]}>
              <Col xs={24} sm={12} xl={6}>
                <Card>
                  <Statistic title="目录实体" value={overview.catalogEntityCount} />
                  <Typography.Text type="secondary">当前项目中仍在目录的实体总数</Typography.Text>
                </Card>
              </Col>
              <Col xs={24} sm={12} xl={6}>
                <Card>
                  <Statistic title="采集与对账任务" value={overview.totalJobCount} />
                  <Typography.Text type="secondary">
                    已启用 {overview.enabledJobCount} 个 · 待预演 {overview.dryRunRequiredJobCount} 个
                  </Typography.Text>
                </Card>
              </Col>
              <Col xs={24} sm={12} xl={6}>
                <Card>
                  <Statistic
                    title={`最近 ${overview.recentRunLimit} 次中的异常运行`}
                    value={recentAttentionCount}
                    valueStyle={{ color: recentAttentionCount ? '#cf1322' : undefined }}
                  />
                  <Typography.Text type="secondary">包括失败和已熔断的疑似坍塌运行</Typography.Text>
                </Card>
              </Col>
              <Col xs={24} sm={12} xl={6}>
                <Card>
                  <Statistic title="未办结治理待办" value={overview.openTaskCount} />
                  <Typography.Text type="secondary">按待办类型汇总</Typography.Text>
                </Card>
              </Col>
            </Row>

            <Row gutter={[16, 16]}>
              <Col xs={24} xl={14}>
                <Card title="目录实体类型" extra={<Typography.Text type="secondary">目录计数不代表源侧完整率</Typography.Text>}>
                  <Table
                    rowKey="typeName"
                    size="small"
                    pagination={false}
                    columns={entityColumns}
                    dataSource={overview.entityTypes}
                    locale={{ emptyText: <Empty description="暂无实体类型定义" /> }}
                  />
                </Card>
              </Col>
              <Col xs={24} xl={10}>
                <Card title="采集与对账任务">
                  {overview.jobsByProvider.length ? (
                    <Table
                      rowKey="providerType"
                      size="small"
                      pagination={false}
                      columns={[
                        {
                          title: '任务通道',
                          dataIndex: 'providerType',
                          key: 'providerType',
                          render: (value: string) => channelLabels[value] || value,
                        },
                        { title: '总数', dataIndex: 'totalCount', key: 'totalCount', align: 'right' },
                        { title: '已启用', dataIndex: 'enabledCount', key: 'enabledCount', align: 'right' },
                        {
                          title: '待预演',
                          dataIndex: 'dryRunRequiredCount',
                          key: 'dryRunRequiredCount',
                          align: 'right',
                        },
                      ]}
                      dataSource={overview.jobsByProvider}
                    />
                  ) : (
                    <Empty description="尚未配置采集或对账任务" />
                  )}
                </Card>
              </Col>
            </Row>

            <Card title="最近运行记录">
              <Table
                rowKey="runId"
                size="small"
                pagination={false}
                columns={runColumns}
                dataSource={overview.recentRuns}
                scroll={{ x: 980 }}
                locale={{ emptyText: <Empty description="暂无采集或对账运行记录" /> }}
              />
            </Card>

            <Card title="治理待办">
              <Table
                rowKey="taskType"
                size="small"
                pagination={false}
                columns={taskColumns}
                dataSource={overview.openTasks}
                locale={{ emptyText: <Empty description="暂无未办结治理待办" /> }}
              />
            </Card>
          </div>
        ) : !loading && !failed ? (
          <div className="mt-4">
            <Empty description="当前项目暂无元数据概览数据" />
          </div>
        ) : null}
      </Spin>
    </div>
  );
};

export default MetadataOverviewPage;
