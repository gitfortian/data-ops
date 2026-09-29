import { Input, Modal, message, Segmented, Select, Space, Table, Tag, Tooltip } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useCallback, useEffect, useState } from 'react';
import { history, useSearchParams } from '@umijs/max';

import { YakButton, YakEmpty } from '@/components/ui';
import { usePermissionAccess } from '@/hooks/usePermissionAccess';
import {
  confirmChange,
  getReconcileStatus,
  ignoreChange,
  pageAssets,
  pageChanges,
  triggerReconcile,
} from '@/services/data-asset/api';
import type {
  AssetRecord,
  ChangeHandleStatus,
  ChangeRecord,
  ReconcileStatusRow,
} from '@/services/data-asset/types';
import {
  ASSET_SOURCE_TYPE_LABELS,
  ASSET_TYPE_LABELS,
  CHANGE_TYPE_COLORS,
  CHANGE_TYPE_LABELS,
  formatAssetTime,
  HANDLE_STATUS_COLORS,
  HANDLE_STATUS_LABELS,
} from '../constants';
import AssetStatusTag from '../components/AssetStatusTag';
import HealthRing from '../components/HealthRing';
import ManualRegisterModal from '../components/ManualRegisterModal';
import PublishPrecheckModal from '../components/PublishPrecheckModal';

type InventoryTab = 'publish' | 'changes' | 'sources';

const TAB_LABELS: Record<InventoryTab, string> = {
  publish: '待上架池',
  changes: '变更确认',
  sources: '源接入与对账',
};

/** 待上架池:只列 PENDING,行内/批量走上架向导。 */
const PendingPool = () => {
  const { can } = usePermissionAccess();
  const canUpdate = can('data-asset:update');
  const canCreate = can('data-asset:create');

  const [records, setRecords] = useState<AssetRecord[]>([]);
  const [total, setTotal] = useState(0);
  const [pageNo, setPageNo] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [keyword, setKeyword] = useState('');
  const [loading, setLoading] = useState(false);
  const [selectedRows, setSelectedRows] = useState<AssetRecord[]>([]);
  const [wizardOpen, setWizardOpen] = useState(false);
  const [wizardAssets, setWizardAssets] = useState<AssetRecord[]>([]);
  const [manualOpen, setManualOpen] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const result = await pageAssets({
        pageNo,
        pageSize,
        statuses: ['PENDING'],
        keyword: keyword.trim() || undefined,
      });
      setRecords(result.records);
      setTotal(result.total);
    } catch {
      setRecords([]);
      setTotal(0);
    } finally {
      setLoading(false);
    }
  }, [pageNo, pageSize, keyword]);

  useEffect(() => {
    void load();
  }, [load]);

  const openWizard = (assets: AssetRecord[]) => {
    setWizardAssets(assets);
    setWizardOpen(true);
  };

  const columns: ColumnsType<AssetRecord> = [
    {
      title: '资产',
      dataIndex: 'name',
      ellipsis: true,
      render: (name: string, record) => (
        <div>
          <a
            className="font-medium text-[#161823] hover:text-[#FE2C55]"
            onClick={() => history.push(`/data-asset/detail/${record.id}`)}
          >
            {name}
          </a>
          <div className="text-[12px] text-[#98a2b3]">{record.assetKey}</div>
        </div>
      ),
    },
    {
      title: '来源',
      dataIndex: 'sourceType',
      width: 100,
      render: (value: string) => ASSET_SOURCE_TYPE_LABELS[value as keyof typeof ASSET_SOURCE_TYPE_LABELS] ?? value,
    },
    {
      title: '类型',
      dataIndex: 'assetType',
      width: 100,
      render: (value: string) => ASSET_TYPE_LABELS[value as keyof typeof ASSET_TYPE_LABELS] ?? value ?? '-',
    },
    { title: '负责人', dataIndex: 'owner', width: 110, render: (value?: string) => value || <span className="text-[#f5222d]">缺</span> },
    {
      title: '健康度',
      dataIndex: 'healthScore',
      width: 88,
      render: (score: number | null, record) => <HealthRing score={score} grade={record.healthGrade} size={38} />,
    },
    {
      title: '登记时间',
      dataIndex: 'createTime',
      width: 150,
      render: (value?: string) => formatAssetTime(value),
    },
    {
      title: '操作',
      key: 'action',
      width: 90,
      render: (_, record) => (
        <YakButton size="small" type="link" disabled={!canUpdate} onClick={() => openWizard([record])}>
          上架
        </YakButton>
      ),
    },
  ];

  return (
    <div>
      <div className="mb-3 flex flex-wrap items-center gap-3">
        <Input.Search
          allowClear
          placeholder="名称 / 描述 / 负责人 / 标签"
          className="!w-[240px]"
          onSearch={(value) => {
            setKeyword(value.trim());
            setPageNo(1);
          }}
        />
        {canUpdate && (
          <YakButton
            size="small"
            disabled={selectedRows.length === 0}
            onClick={() => openWizard(selectedRows)}
          >
            批量上架({selectedRows.length})
          </YakButton>
        )}
        {canCreate && (
          <YakButton size="small" type="primary" className="!text-white" onClick={() => setManualOpen(true)}>
            手工登记
          </YakButton>
        )}
        <span className="ml-auto text-[12px] text-[#98a2b3]">
          对账发现的源域对象自动落入这里；上架前必经预检
        </span>
      </div>
      <Table<AssetRecord>
        rowKey="id"
        size="small"
        columns={columns}
        dataSource={records}
        loading={loading}
        rowSelection={{
          selectedRowKeys: selectedRows.map((row) => row.id),
          onChange: (_, rows) => setSelectedRows(rows),
        }}
        locale={{
          emptyText: <YakEmpty compact title="没有待上架资产" description="对账或手工登记后出现在这里" />,
        }}
        pagination={{
          current: pageNo,
          pageSize,
          total,
          showSizeChanger: true,
          showTotal: (count) => `共 ${count} 条`,
          onChange: (next, size) => {
            setPageNo(next);
            setPageSize(size);
          },
        }}
      />
      <PublishPrecheckModal
        open={wizardOpen}
        assets={wizardAssets}
        onClose={() => setWizardOpen(false)}
        onDone={() => {
          setWizardOpen(false);
          setSelectedRows([]);
          void load();
        }}
      />
      <ManualRegisterModal
        open={manualOpen}
        onClose={() => setManualOpen(false)}
        onCreated={() => {
          setManualOpen(false);
          void load();
        }}
      />
    </div>
  );
};

/** 变更确认:对账产生的 NEW/META_CHANGED/GONE/REAPPEARED 流水逐条确认或忽略。 */
const ChangeConfirm = () => {
  const { can } = usePermissionAccess();
  const canUpdate = can('data-asset:update');

  const [handleStatus, setHandleStatus] = useState<ChangeHandleStatus>('OPEN');
  const [records, setRecords] = useState<ChangeRecord[]>([]);
  const [total, setTotal] = useState(0);
  const [pageNo, setPageNo] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [loading, setLoading] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const result = await pageChanges({ handleStatus, pageNo, pageSize });
      setRecords(result.records);
      setTotal(result.total);
    } catch {
      setRecords([]);
      setTotal(0);
    } finally {
      setLoading(false);
    }
  }, [handleStatus, pageNo, pageSize]);

  useEffect(() => {
    void load();
  }, [load]);

  const runConfirm = (record: ChangeRecord) => {
    Modal.confirm({
      title: '确认变更',
      content: `将用快照新值覆盖「${record.assetName ?? `#${record.assetId}`}」的台账展示字段并即时重算健康度。确定确认？`,
      okText: '确认',
      cancelText: '取消',
      onOk: async () => {
        try {
          await confirmChange(record.id);
          message.success('已确认，台账已刷新为源域最新值');
          void load();
        } catch {
          // 已处理 48014,提示已由全局展示
        }
      },
    });
  };

  const runIgnore = (record: ChangeRecord) => {
    Modal.confirm({
      title: '忽略变更',
      content: '仅关闭本条流水，不改动台账；源域下次变化仍会重新登记。确定忽略？',
      okText: '忽略',
      cancelText: '取消',
      onOk: async () => {
        try {
          await ignoreChange(record.id);
          message.success('已忽略');
          void load();
        } catch {
          // 已处理 48014
        }
      },
    });
  };

  const columns: ColumnsType<ChangeRecord> = [
    {
      title: '资产',
      dataIndex: 'assetName',
      ellipsis: true,
      render: (name: string | undefined, record) =>
        record.assetId ? (
          <a className="text-[#161823] hover:text-[#FE2C55]" onClick={() => history.push(`/data-asset/detail/${record.assetId}`)}>
            {name ?? `#${record.assetId}`}
          </a>
        ) : (
          name ?? '-'
        ),
    },
    {
      title: '变更类型',
      dataIndex: 'changeType',
      width: 120,
      render: (value: keyof typeof CHANGE_TYPE_LABELS) => (
        <Tag color={CHANGE_TYPE_COLORS[value]}>{CHANGE_TYPE_LABELS[value] ?? value}</Tag>
      ),
    },
    {
      title: '差异摘要',
      dataIndex: 'diff',
      ellipsis: true,
      render: (diff?: Record<string, unknown> | null) => {
        const text = diff ? JSON.stringify(diff) : '';
        return text ? (
          <Tooltip title={<pre className="max-h-[240px] overflow-auto text-[12px]">{text}</pre>}>
            <span className="text-[12px] text-[#667085]">{text.slice(0, 80)}</span>
          </Tooltip>
        ) : (
          '-'
        );
      },
    },
    {
      title: '状态',
      dataIndex: 'handleStatus',
      width: 90,
      render: (value: ChangeHandleStatus) => (
        <Tag color={HANDLE_STATUS_COLORS[value]}>{HANDLE_STATUS_LABELS[value] ?? value}</Tag>
      ),
    },
    { title: '登记人', dataIndex: 'createdBy', width: 100, render: (value?: string) => value || '-' },
    {
      title: '时间',
      dataIndex: 'createTime',
      width: 150,
      render: (value?: string) => formatAssetTime(value),
    },
    {
      title: '操作',
      key: 'action',
      width: 130,
      render: (_, record) =>
        record.handleStatus === 'OPEN' ? (
          <Space size={2}>
            <YakButton size="small" type="link" disabled={!canUpdate} onClick={() => runConfirm(record)}>
              确认
            </YakButton>
            <YakButton size="small" type="link" disabled={!canUpdate} onClick={() => runIgnore(record)}>
              忽略
            </YakButton>
          </Space>
        ) : (
          <span className="text-[12px] text-[#98a2b3]">已处理</span>
        ),
    },
  ];

  return (
    <div>
      <div className="mb-3 flex items-center gap-3">
        <Segmented
          options={(Object.keys(HANDLE_STATUS_LABELS) as ChangeHandleStatus[]).map((value) => ({
            value,
            label: HANDLE_STATUS_LABELS[value],
          }))}
          value={handleStatus}
          onChange={(value) => {
            setHandleStatus(value as ChangeHandleStatus);
            setPageNo(1);
          }}
        />
        <span className="text-[12px] text-[#98a2b3]">
          确认=用源域新值覆盖台账快照；忽略=只关流水不动台账；SOURCE_GONE 确认后资产转「源已消失」
        </span>
      </div>
      <Table<ChangeRecord>
        rowKey="id"
        size="small"
        columns={columns}
        dataSource={records}
        loading={loading}
        locale={{
          emptyText: (
            <YakEmpty
              compact
              title={handleStatus === 'OPEN' ? '没有待确认的变更' : '暂无该状态的流水'}
              description="手动/每日对账发现源域变化后写入这里"
            />
          ),
        }}
        pagination={{
          current: pageNo,
          pageSize,
          total,
          showSizeChanger: true,
          showTotal: (count) => `共 ${count} 条`,
          onChange: (next, size) => {
            setPageNo(next);
            setPageSize(size);
          },
        }}
      />
    </div>
  );
};

/** 源接入与对账:各来源注册状态 + 最近一次对账结果 + 手动触发。 */
const SourceAccess = () => {
  const { can } = usePermissionAccess();
  const canUpdate = can('data-asset:update');

  const [rows, setRows] = useState<ReconcileStatusRow[]>([]);
  const [loading, setLoading] = useState(false);
  const [submitting, setSubmitting] = useState('');

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setRows(await getReconcileStatus());
    } catch {
      setRows([]);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const run = async (sourceTypes: string[], label: string) => {
    setSubmitting(label);
    try {
      const accepted = await triggerReconcile(sourceTypes);
      message.success(accepted || `已受理「${label}」对账，稍后刷新查看结果`);
      setTimeout(() => void load(), 3000);
    } catch {
      // 互斥 48012 / 来源缺失 48011,提示已由全局展示
    } finally {
      setSubmitting('');
    }
  };

  const columns: ColumnsType<ReconcileStatusRow> = [
    {
      title: '来源域',
      dataIndex: 'sourceType',
      width: 130,
      render: (value: string) => ASSET_SOURCE_TYPE_LABELS[value as keyof typeof ASSET_SOURCE_TYPE_LABELS] ?? value,
    },
    {
      title: 'Provider',
      dataIndex: 'registered',
      width: 110,
      render: (registered: boolean) =>
        registered ? <Tag color="green">已注册</Tag> : <Tag>未注册</Tag>,
    },
    {
      title: '支持 Section',
      dataIndex: 'supportedSections',
      render: (sections: string[] = []) => sections.length > 0
        ? <Space size={[0, 4]} wrap>{sections.map((section) => <Tag key={section}>{section}</Tag>)}</Space>
        : <span className="text-[12px] text-[#98a2b3]">暂无</span>,
    },
    {
      title: '最近对账',
      dataIndex: ['lastRun', 'at'],
      width: 150,
      render: (_: unknown, record) => formatAssetTime(record.lastRun?.at),
    },
    {
      title: '扫描/新增/变化/恢复',
      key: 'counts',
      width: 170,
      render: (_, record) =>
        record.lastRun
          ? `${record.lastRun.scanned ?? 0} / ${record.lastRun.created ?? 0} / ${record.lastRun.changed ?? 0} / ${record.lastRun.restored ?? 0}`
          : '-',
    },
    {
      title: '结果',
      key: 'result',
      ellipsis: true,
      render: (_, record) =>
        record.lastRun?.error ? (
          <Tooltip title={record.lastRun.error}>
            <Tag color="red">失败</Tag>
          </Tooltip>
        ) : record.lastRun ? (
          <Tag color="green">成功</Tag>
        ) : (
          <span className="text-[12px] text-[#98a2b3]">从未运行</span>
        ),
    },
    {
      title: '操作',
      key: 'action',
      width: 110,
      render: (_, record) => (
        <YakButton
          size="small"
          type="link"
          disabled={!canUpdate || !record.registered}
          loading={submitting === record.sourceType}
          onClick={() => run([record.sourceType], ASSET_SOURCE_TYPE_LABELS[record.sourceType] ?? record.sourceType)}
        >
          对账此源
        </YakButton>
      ),
    },
  ];

  return (
    <div>
      <div className="mb-3 flex items-center gap-3">
        <YakButton
          type="primary"
          size="small"
          className="!text-white"
          disabled={!canUpdate || submitting === 'ALL'}
          loading={submitting === 'ALL'}
          onClick={() => run(rows.filter((row) => row.registered).map((row) => row.sourceType), 'ALL')}
        >
          全部对账
        </YakButton>
        <YakButton size="small" onClick={load}>
          刷新状态
        </YakButton>
        <span className="text-[12px] text-[#98a2b3]">
          手动触发为异步受理；每日凌晨自动对账，游标分批 ≤500；SOURCE_GONE 需连续两周期缺失才判定
        </span>
      </div>
      <Table<ReconcileStatusRow>
        rowKey="sourceType"
        size="small"
        columns={columns}
        dataSource={rows}
        loading={loading}
        pagination={false}
        locale={{ emptyText: <YakEmpty compact title="暂无已定义来源域" /> }}
      />
    </div>
  );
};

const AssetInventoryPage = () => {
  const [searchParams, setSearchParams] = useSearchParams();
  const tabParam = (searchParams.get('tab') ?? 'publish') as InventoryTab;
  const tab: InventoryTab = tabParam in TAB_LABELS ? tabParam : 'publish';

  return (
    <div className="min-h-[calc(100dvh-64px)] bg-white px-6 pb-6 pt-5 text-[#242731] max-md:px-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <div className="text-[20px] font-semibold leading-7">盘点上架</div>
          <div className="mt-1 text-[13px] text-[#667085]">
            待上架池 / 变更确认 / 源接入三段闭环：对账发现 → 人工确认 → 向导上架
          </div>
        </div>
        <Segmented
          options={(Object.keys(TAB_LABELS) as InventoryTab[]).map((value) => ({
            value,
            label: TAB_LABELS[value],
          }))}
          value={tab}
          onChange={(value) => setSearchParams({ tab: String(value) })}
        />
      </div>
      <div className="mt-4">
        {tab === 'publish' && <PendingPool />}
        {tab === 'changes' && <ChangeConfirm />}
        {tab === 'sources' && <SourceAccess />}
      </div>
    </div>
  );
};

export default AssetInventoryPage;
