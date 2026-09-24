import { Alert, Button, Input, message, Modal, Select, Space, Statistic, Table, Tabs, Tag, Tooltip } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useCallback, useEffect, useState } from 'react';
import { history, useSearchParams } from '@umijs/max';
import { YakButton, YakEmpty } from '@/components/ui';
import { usePermissionAccess } from '@/hooks/usePermissionAccess';
import {
  getMonitorSummary,
  pageDispatchRecords,
  pageMonitorModels,
  retryDispatchRecord,
} from '@/services/data-lifecycle/api';
import type {
  DispatchRecord,
  DispatchStatus,
  MonitorModelView,
  MonitorSummary,
  ModelState,
} from '@/services/data-lifecycle/types';
import TtlDispatchWizard from '../components/TtlDispatchWizard';
import {
  BINDING_SOURCE_LABELS,
  DISPATCH_STATUS_COLORS,
  DISPATCH_STATUS_LABELS,
  formatLifecycleTime,
  LAYER_LABELS,
  MODEL_STATE_COLORS,
  MODEL_STATE_LABELS,
} from '../constants';

const MODEL_STATE_OPTIONS = (Object.keys(MODEL_STATE_LABELS) as ModelState[]).map((value) => ({
  value,
  label: MODEL_STATE_LABELS[value],
}));

const DISPATCH_STATUS_OPTIONS = (Object.keys(DISPATCH_STATUS_LABELS) as DispatchStatus[]).map((value) => ({
  value,
  label: DISPATCH_STATUS_LABELS[value],
}));

const ModelStatusTab = ({ onOpenWizard, modelId }: {
  onOpenWizard: (modelIds: number[]) => void;
  modelId?: number;
}) => {
  const { can } = usePermissionAccess();
  const canUpdate = can('data-lifecycle:update');
  const [records, setRecords] = useState<MonitorModelView[]>([]);
  const [total, setTotal] = useState(0);
  const [pageNo, setPageNo] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [state, setState] = useState<ModelState | ''>('');
  const [layerCode, setLayerCode] = useState('');
  const [keyword, setKeyword] = useState('');
  const [loading, setLoading] = useState(false);
  const [selectedIds, setSelectedIds] = useState<number[]>([]);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const result = await pageMonitorModels({
        pageNo,
        pageSize,
        modelId,
        state: state || undefined,
        layerCode: layerCode || undefined,
        keyword: keyword.trim() || undefined,
      });
      setRecords(result.records ?? []);
      setTotal(result.total ?? 0);
    } catch {
      setRecords([]);
      setTotal(0);
    } finally {
      setLoading(false);
    }
  }, [pageNo, pageSize, state, layerCode, keyword, modelId]);

  useEffect(() => {
    void load();
  }, [load]);

  const columns: ColumnsType<MonitorModelView> = [
    {
      title: '模型',
      dataIndex: 'modelName',
      width: 220,
      ellipsis: true,
      render: (name: string, record) => (
        <div>
          <div className="truncate">{name}</div>
          <div className="text-[12px] text-[#98a2b3]">{record.modelCode}</div>
        </div>
      ),
    },
    {
      title: '分层',
      dataIndex: 'layerCode',
      width: 110,
      render: (value?: string | null) =>
        value ? <Tag color="geekblue">{LAYER_LABELS[value.toUpperCase()] ?? value}</Tag> : '-',
    },
    {
      title: 'TTL 状态',
      dataIndex: 'state',
      width: 100,
      render: (value: ModelState) => <Tag color={MODEL_STATE_COLORS[value]}>{MODEL_STATE_LABELS[value]}</Tag>,
    },
    {
      title: '生效策略',
      dataIndex: 'policyName',
      width: 180,
      ellipsis: true,
      render: (value: string | null | undefined, record) => (
        <div>
          <div className="truncate">{value || '-'}</div>
          {record.bindingSource && (
            <div className="text-[12px] text-[#98a2b3]">{BINDING_SOURCE_LABELS[record.bindingSource]}</div>
          )}
        </div>
      ),
    },
    {
      title: '销毁周期',
      dataIndex: 'destroyDays',
      width: 95,
      align: 'right' as const,
      render: (value?: number | null) => (value == null ? '永久' : `${value} 天`),
    },
    {
      title: '最近下发',
      dataIndex: 'lastDispatchTime',
      width: 180,
      render: (value: string | null | undefined, record) =>
        value ? (
          <Space size={6}>
            {record.lastDispatchStatus && (
              <Tag color={DISPATCH_STATUS_COLORS[record.lastDispatchStatus]}>
                {DISPATCH_STATUS_LABELS[record.lastDispatchStatus]}
              </Tag>
            )}
            <span className="text-[12px] text-[#667085]">{formatLifecycleTime(value)}</span>
          </Space>
        ) : (
          '-'
        ),
    },
    {
      title: '说明',
      dataIndex: 'message',
      ellipsis: true,
      render: (value?: string | null) =>
        value ? (
          <Tooltip title={value}>
            <span className="text-[#d92d20]">{value}</span>
          </Tooltip>
        ) : (
          '-'
        ),
    },
    {
      title: '操作',
      key: 'action',
      width: 110,
      fixed: 'right' as const,
      render: (_, record) =>
        record.state === 'DRIFT' || record.state === 'FAILED' ? (
          <Button
            type="link"
            size="small"
            disabled={!canUpdate}
            onClick={() => onOpenWizard([record.modelId])}
          >
            预览并下发
          </Button>
        ) : null,
    },
  ];

  return (
    <>
      <div className="mb-3 flex flex-wrap items-center gap-3">
        <Input.Search
          allowClear
          placeholder="按模型名称或表名搜索"
          className="!w-[240px]"
          onSearch={(value) => {
            setKeyword(value.trim());
            setPageNo(1);
          }}
        />
        <Select
          allowClear
          placeholder="TTL 状态"
          className="!w-[130px]"
          value={state || undefined}
          onChange={(value) => {
            setState((value ?? '') as ModelState | '');
            setPageNo(1);
          }}
          options={MODEL_STATE_OPTIONS}
        />
        <Select
          allowClear
          placeholder="分层"
          className="!w-[150px]"
          value={layerCode || undefined}
          onChange={(value) => {
            setLayerCode((value ?? '') as string);
            setPageNo(1);
          }}
          options={Object.entries(LAYER_LABELS).map(([value, label]) => ({ value, label }))}
        />
        <div className="flex-1" />
        <YakButton
          type="primary"
          disabled={!canUpdate || selectedIds.length === 0}
          className="!h-9 !rounded-lg !px-4 !text-white"
          onClick={() => onOpenWizard(selectedIds)}
        >
          批量预览并下发（{selectedIds.length}）
        </YakButton>
      </div>
      <Table<MonitorModelView>
        rowKey="modelId"
        columns={columns}
        dataSource={records}
        loading={loading}
        scroll={{ x: 1180 }}
        rowSelection={{
          selectedRowKeys: selectedIds,
          onChange: (keys) => setSelectedIds(keys as number[]),
        }}
        locale={{
          emptyText: <YakEmpty compact title="暂无建模模型" description="创建带时间分区的模型后，模型会自动出现在这里" />,
        }}
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
    </>
  );
};

const DispatchRecordsTab = () => {
  const { can } = usePermissionAccess();
  const canUpdate = can('data-lifecycle:update');
  const [records, setRecords] = useState<DispatchRecord[]>([]);
  const [total, setTotal] = useState(0);
  const [pageNo, setPageNo] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [status, setStatus] = useState<DispatchStatus | ''>('');
  const [loading, setLoading] = useState(false);
  const [statementOf, setStatementOf] = useState<DispatchRecord | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const result = await pageDispatchRecords({ pageNo, pageSize, status: status || undefined });
      setRecords(result.records ?? []);
      setTotal(result.total ?? 0);
    } catch {
      setRecords([]);
      setTotal(0);
    } finally {
      setLoading(false);
    }
  }, [pageNo, pageSize, status]);

  useEffect(() => {
    void load();
  }, [load]);

  const retry = (record: DispatchRecord) => {
    Modal.confirm({
      title: '重试下发',
      content: `确定重试「${record.databaseName ? `${record.databaseName}.` : ''}${record.tableName}」的下发？将按当前生效策略重新生成语句执行。`,
      okText: '重试',
      cancelText: '取消',
      onOk: async () => {
        try {
          const outcome = await retryDispatchRecord(record.id);
          if (outcome.success) {
            message.success('重试成功');
          } else {
            message.warning(`重试未成功：${outcome.message ?? '请查看新记录'}`);
          }
        } catch {
          // 全局错误提示已展示原因
        } finally {
          await load();
        }
      },
    });
  };

  const columns: ColumnsType<DispatchRecord> = [
    { title: 'ID', dataIndex: 'id', width: 70 },
    {
      title: '目标表',
      key: 'table',
      width: 220,
      ellipsis: true,
      render: (_, r) => `${r.databaseName ? `${r.databaseName}.` : ''}${r.tableName ?? '-'}`,
    },
    { title: '存储', dataIndex: 'storageType', width: 80, render: (v?: string | null) => v ?? '-' },
    {
      title: '结果',
      dataIndex: 'status',
      width: 100,
      render: (value: DispatchStatus) => (
        <Tag color={DISPATCH_STATUS_COLORS[value]}>{DISPATCH_STATUS_LABELS[value] ?? value}</Tag>
      ),
    },
    {
      title: '触发',
      dataIndex: 'triggerType',
      width: 80,
      render: (value?: string | null) =>
        value === 'MANUAL' ? '手动' : value === 'BATCH' ? '批量' : value === 'RETRY' ? '重试' : value ?? '-',
    },
    { title: '尝试', dataIndex: 'attempts', width: 60, align: 'right' as const },
    {
      title: '分区(热/冷/删)',
      key: 'partitions',
      width: 120,
      render: (_, r) => `${r.partitionHot ?? '-'} / ${r.partitionCold ?? '-'} / ${r.partitionDeleted ?? '-'}`,
    },
    { title: '操作人', dataIndex: 'operator', width: 90, ellipsis: true, render: (v?: string | null) => v ?? '-' },
    {
      title: '时间',
      dataIndex: 'createTime',
      width: 160,
      render: (value?: string | null) => formatLifecycleTime(value),
    },
    {
      title: '错误信息',
      dataIndex: 'errorMessage',
      ellipsis: true,
      render: (value?: string | null) =>
        value ? (
          <Tooltip title={value}>
            <span className="text-[#d92d20]">{value}</span>
          </Tooltip>
        ) : (
          '-'
        ),
    },
    {
      title: '操作',
      key: 'action',
      width: 130,
      fixed: 'right' as const,
      render: (_, record) => (
        <Space size={4}>
          {record.statement && (
            <Button type="link" size="small" onClick={() => setStatementOf(record)}>
              语句
            </Button>
          )}
          {(record.status === 'FAILED' || record.status === 'EXHAUSTED') && (
            <Button type="link" size="small" disabled={!canUpdate} onClick={() => retry(record)}>
              重试
            </Button>
          )}
        </Space>
      ),
    },
  ];

  return (
    <>
      <div className="mb-3 flex flex-wrap items-center gap-3">
        <Select
          allowClear
          placeholder="下发结果"
          className="!w-[130px]"
          value={status || undefined}
          onChange={(value) => {
            setStatus((value ?? '') as DispatchStatus | '');
            setPageNo(1);
          }}
          options={DISPATCH_STATUS_OPTIONS}
        />
      </div>
      <Table<DispatchRecord>
        rowKey="id"
        columns={columns}
        dataSource={records}
        loading={loading}
        scroll={{ x: 1280 }}
        locale={{
          emptyText: <YakEmpty compact title="暂无下发记录" description="在模型状态页选择模型预览并下发后，这里会留下记录" />,
        }}
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
      <Modal
        open={statementOf != null}
        title="下发的 TTL 语句"
        footer={null}
        width={720}
        onCancel={() => setStatementOf(null)}
      >
        <pre className="m-0 overflow-auto whitespace-pre-wrap text-[12px]">{statementOf?.statement ?? '-'}</pre>
      </Modal>
    </>
  );
};

const MonitorPage = () => {
  const [searchParams] = useSearchParams();
  const returnAssetIdValue = searchParams.get('returnAssetId');
  const returnAssetId = returnAssetIdValue && /^\d+$/.test(returnAssetIdValue)
    ? Number(returnAssetIdValue)
    : undefined;
  const modelIdValue = searchParams.get('modelId');
  const modelId = modelIdValue && /^\d+$/.test(modelIdValue) ? Number(modelIdValue) : undefined;
  const [summary, setSummary] = useState<MonitorSummary | null>(null);
  const [tab, setTab] = useState<'models' | 'records'>('models');
  const [wizardModelIds, setWizardModelIds] = useState<number[] | null>(null);
  const [reloadToken, setReloadToken] = useState(0);
  const [recordsReloadToken, setRecordsReloadToken] = useState(0);

  useEffect(() => {
    getMonitorSummary()
      .then(setSummary)
      .catch(() => setSummary(null));
  }, [reloadToken]);

  return (
    <div className="flex min-h-[calc(100dvh-64px)] flex-col bg-white px-6 pb-4 pt-5 text-[#242731] max-md:px-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <div className="text-[20px] font-semibold leading-7">TTL 监控</div>
          <div className="mt-1 text-[13px] text-[#667085]">
            跟踪各模型 TTL 生效状态；「待下发」表示策略已更新但尚未同步到存储
          </div>
        </div>
        {returnAssetId ? (
          <Button onClick={() => history.push(`/data-asset/detail/${returnAssetId}`)}>返回资产详情</Button>
        ) : null}
      </div>

      <div className="mt-4 grid grid-cols-5 gap-4">
        {[
          { title: '模型总数', value: summary?.total ?? 0 },
          { title: '已生效', value: summary?.applied ?? 0, color: '#12833f' },
          { title: '待下发', value: summary?.drift ?? 0, color: '#d97706' },
          { title: '下发失败', value: summary?.failed ?? 0, color: '#d92d20' },
          { title: '未配置', value: summary?.unset ?? 0 },
        ].map((card) => (
          <div key={card.title} className="rounded-lg border border-[#e5e7eb] px-4 py-3">
            <Statistic title={card.title} value={card.value} valueStyle={{ fontSize: 22, color: card.color }} />
          </div>
        ))}
      </div>

      {(summary?.alerts.length ?? 0) > 0 && (
        <Alert
          className="mt-4"
          type="error"
          showIcon
          message={`异常告警（${summary?.alerts.length}）`}
          description={
            <div className="max-h-[96px] overflow-auto">
              {summary?.alerts.map((a) => (
                <div key={a.recordId} className="text-[12px]">
                  模型 {a.modelId} · {DISPATCH_STATUS_LABELS[a.status] ?? a.status}
                  {a.attempts != null ? ` · 已尝试 ${a.attempts} 次` : ''} ·{' '}
                  {a.errorMessage ?? '无错误信息'} · {formatLifecycleTime(a.time)}
                </div>
              ))}
            </div>
          }
        />
      )}

      <div className="mt-4 flex-1">
        <Tabs
          activeKey={tab}
          onChange={(key) => setTab(key as 'models' | 'records')}
          items={[
            { key: 'models', label: '模型 TTL 状态' },
            { key: 'records', label: '下发记录' },
          ]}
        />
        {tab === 'models' ? (
          <ModelStatusTab
            modelId={modelId}
            onOpenWizard={(ids) => ids.length > 0 && setWizardModelIds(ids)}
          />
        ) : (
          <DispatchRecordsTab key={recordsReloadToken} />
        )}
      </div>

      <TtlDispatchWizard
        open={wizardModelIds != null}
        modelIds={wizardModelIds ?? []}
        onClose={() => setWizardModelIds(null)}
        onDone={() => {
          setReloadToken((t) => t + 1);
          setRecordsReloadToken((t) => t + 1);
        }}
      />
    </div>
  );
};

export default MonitorPage;
