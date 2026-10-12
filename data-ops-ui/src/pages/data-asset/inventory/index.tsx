import { Alert, Input, Modal, message, Segmented, Select, Space, Table, Tag, Tooltip } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useCallback, useEffect, useState } from 'react';
import { useSecurityProject } from '@/contexts/SecurityProjectContext';
import { classifyAssetListFailure, type AssetListReadFailure } from '../read-state';
import { classifyReconcileLastRun, formatHistoricalReconcileCounts } from '../reconcileEvidence';
import { useLatestAssetListRequest } from '../useLatestAssetListRequest';
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
  formatChangeDiff,
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
  const [loadError, setLoadError] = useState<AssetListReadFailure | null>(null);
  const beginLatestRequest = useLatestAssetListRequest();
  const [selectedRows, setSelectedRows] = useState<AssetRecord[]>([]);
  const [wizardOpen, setWizardOpen] = useState(false);
  const [wizardAssets, setWizardAssets] = useState<AssetRecord[]>([]);
  const [manualOpen, setManualOpen] = useState(false);

  const load = useCallback(async () => {
    const isCurrent = beginLatestRequest();
    setLoading(true);
    setLoadError(null);
    setRecords([]);
    setTotal(0);
    setSelectedRows([]);
    try {
      const result = await pageAssets({
        pageNo,
        pageSize,
        statuses: ['PENDING'],
        keyword: keyword.trim() || undefined,
      });
      if (isCurrent()) {
        setRecords(result.records);
        setTotal(result.total);
      }
    } catch (error) {
      if (isCurrent()) setLoadError(classifyAssetListFailure(error));
    } finally {
      if (isCurrent()) setLoading(false);
    }
  }, [pageNo, pageSize, keyword, beginLatestRequest]);

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
        <YakButton size="small" type="link" disabled={!canUpdate || Boolean(loadError) || loading} onClick={() => openWizard([record])}>
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
            disabled={selectedRows.length === 0 || Boolean(loadError) || loading}
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
      {acceptedRequest && (
        <Alert className="mb-3" type="info" showIcon message={acceptedRequest}
          description="请稍后使用「刷新状态」核对历史 Provider 状态，并在资产台账独立查询来源；当前接口不提供本次请求与 lastRun 的关联，不能据此判定本次成功。" />
      )}
      {loadError && (
        <Alert className="mb-3" type="error" showIcon message={loadError === 'FORBIDDEN' ? '无权读取待上架资产' : '待上架池读取失败'}
          description="本次读取未成功，不再展示旧筛选或旧项目的数据；请核对权限后重试。"
          action={<YakButton size="small" onClick={() => void load()}>重试</YakButton>} />
      )}
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
          emptyText: loadError
            ? <YakEmpty compact title="未能读取待上架资产" description="重试后才能确定是否为空" />
            : <YakEmpty compact title="没有待上架资产" description="对账或手工登记后出现在这里" />,
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
  const [loadError, setLoadError] = useState<AssetListReadFailure | null>(null);
  const beginLatestRequest = useLatestAssetListRequest();

  const load = useCallback(async () => {
    const isCurrent = beginLatestRequest();
    setLoading(true);
    setLoadError(null);
    setRecords([]);
    setTotal(0);
    try {
      const result = await pageChanges({ handleStatus, pageNo, pageSize });
      if (isCurrent()) {
        setRecords(result.records);
        setTotal(result.total);
      }
    } catch (error) {
      if (isCurrent()) setLoadError(classifyAssetListFailure(error));
    } finally {
      if (isCurrent()) setLoading(false);
    }
  }, [handleStatus, pageNo, pageSize, beginLatestRequest]);

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
      render: (_: unknown, record) => {
        const summary = formatChangeDiff(record.changeType, record.diff);
        return (
          <Tooltip title={summary}>
            <span className="text-[12px] text-[#667085]">{summary}</span>
          </Tooltip>
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
            <YakButton size="small" type="link" disabled={!canUpdate || Boolean(loadError) || loading} onClick={() => runConfirm(record)}>
              确认
            </YakButton>
            <YakButton size="small" type="link" disabled={!canUpdate || Boolean(loadError) || loading} onClick={() => runIgnore(record)}>
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
      {loadError && (
        <Alert className="mb-3" type="error" showIcon message={loadError === 'FORBIDDEN' ? '无权读取变更确认记录' : '变更确认记录读取失败'}
          description="本次读取未成功，旧筛选条件的记录不再作为当前变更展示；请重试。"
          action={<YakButton size="small" onClick={() => void load()}>重试</YakButton>} />
      )}
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
              title={loadError ? '未能读取变更记录' : handleStatus === 'OPEN' ? '没有待确认的变更' : '暂无该状态的流水'}
              description={loadError ? '重试后才能确定是否为空' : '手动/每日对账发现源域变化后写入这里'}
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
  const [loadError, setLoadError] = useState<AssetListReadFailure | null>(null);
  const beginLatestRequest = useLatestAssetListRequest();
  const [submitting, setSubmitting] = useState('');
  // This receipt describes this browser action only. The status table is historical Provider evidence.
  const [acceptedRequest, setAcceptedRequest] = useState<string | null>(null);

  const load = useCallback(async () => {
    const isCurrent = beginLatestRequest();
    setLoading(true);
    setLoadError(null);
    setRows([]);
    try {
      const result = await getReconcileStatus();
      if (isCurrent()) setRows(result);
    } catch (error) {
      if (isCurrent()) setLoadError(classifyAssetListFailure(error));
    } finally {
      if (isCurrent()) setLoading(false);
    }
  }, [beginLatestRequest]);

  useEffect(() => {
    void load();
  }, [load]);

  const run = (sourceTypes: string[], label: string) => {
    if (!canUpdate || sourceTypes.length === 0) return;
    Modal.confirm({
      title: '发起本项目的来源对账？',
      content: `将按当前项目的 Provider 范围对「${label}」执行对账，而非仅对当前数据源/表执行。请求受理并不等于已完成，也不代表本次创建了 PENDING 资产。继续？`,
      okText: '确认发起',
      cancelText: '取消',
      onOk: async () => {
        setSubmitting(sourceTypes.length === 1 ? sourceTypes[0] : 'ALL');
        setAcceptedRequest(null);
        try {
          await triggerReconcile(sourceTypes);
          setAcceptedRequest(`${label}：请求已受理；本次执行状态与最终结果尚未核验`);
          message.info('对账请求已受理，完成情况待确认；请稍后主动核对来源与台账');
          // /status only reports Provider lastRun. It cannot correlate this request to that run.
        } catch {
          // 48012 mutual exclusion / 48011 missing provider: handled by the global API error handler.
        } finally {
          setSubmitting('');
        }
      },
    });
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
        formatHistoricalReconcileCounts(record.lastRun),
    },
    {
      title: '结果',
      key: 'result',
      ellipsis: true,
      render: (_, record) => {
        const evidence = classifyReconcileLastRun(record.lastRun);
        if (evidence === 'FAILED') return (
          <Tooltip title={record.lastRun?.error}><Tag color="red">历史失败</Tag></Tooltip>
        );
        if (evidence === 'SUCCESS') return <Tag color="green">历史成功</Tag>;
        if (evidence === 'NEVER') return <span className="text-[12px] text-[#98a2b3]">从未运行</span>;
        return <Tag color="warning">历史结果未确认</Tag>;
      },
    },
    {
      title: '操作',
      key: 'action',
      width: 110,
      render: (_, record) => (
        <YakButton
          size="small"
          type="link"
          disabled={!canUpdate || !record.registered || Boolean(loadError) || loading || Boolean(submitting)}
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
          disabled={!canUpdate || Boolean(submitting) || Boolean(loadError) || loading || !rows.some((row) => row.registered)}
          loading={submitting === 'ALL'}
          onClick={() => run(rows.filter((row) => row.registered).map((row) => row.sourceType), 'ALL')}
        >
          全部对账
        </YakButton>
        <YakButton size="small" onClick={load}>
          刷新状态
        </YakButton>
        <span className="text-[12px] text-[#98a2b3]">
          手动触发仅代表受理；下表显示 Provider 历史 lastRun，不是本次请求回执。缺席判定由服务端有效对账窗口决定（默认 7 天）。
        </span>
      </div>
      {loadError && (
        <Alert className="mb-3" type="error" showIcon message={loadError === 'FORBIDDEN' ? '无权读取源接入状态' : '源接入状态读取失败'}
          description="本次读取未成功，旧项目的 Provider 状态不再作为当前结果；请重试。"
          action={<YakButton size="small" onClick={() => void load()}>重试</YakButton>} />
      )}
      <Table<ReconcileStatusRow>
        rowKey="sourceType"
        size="small"
        columns={columns}
        dataSource={rows}
        loading={loading}
        pagination={false}
        locale={{ emptyText: <YakEmpty compact title={loadError ? '未能读取来源域状态' : '暂无已定义来源域'} /> }}
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

/** Remount tabs on Project or permission changes to discard old rows and selections. */
export default function ScopedAssetInventoryPage() {
  const { currentProject } = useSecurityProject();
  const { permissionCodes } = usePermissionAccess();
  return <AssetInventoryPage key={JSON.stringify([currentProject?.id, permissionCodes])} />;
}
