import { Button, Input, Modal, message, Select, Space, Table, Tag, Tooltip } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useCallback, useEffect, useState } from 'react';
import { YakButton, YakEmpty } from '@/components/ui';
import { usePermissionAccess } from '@/hooks/usePermissionAccess';
import {
  changePolicyStatus,
  deletePolicy,
  initializeLayerDefaults,
  offlinePolicy,
  pagePolicies,
  publishPolicy,
} from '@/services/data-lifecycle/api';
import type { PolicyRecord, PolicyScope, PublishStateType } from '@/services/data-lifecycle/types';
import {
  formatLifecycleTime,
  formatRetention,
  GRANULARITY_LABELS,
  LAYER_LABELS,
  POLICY_SCOPE_LABELS,
  STATUS_LABELS,
} from '../constants';
import PolicyEditDrawer from './components/PolicyEditDrawer';
import PolicyVersionsDrawer from './components/PolicyVersionsDrawer';

const PUBLISH_STATE_LABELS: Record<PublishStateType, string> = {
  DRAFT: '草稿',
  PUBLISHED: '已发布',
  OFFLINE: '已下线',
};
const PUBLISH_STATE_COLORS: Record<PublishStateType, string> = {
  DRAFT: 'default',
  PUBLISHED: 'green',
  OFFLINE: 'orange',
};

const PolicyPage = () => {
  const { can } = usePermissionAccess();
  const canCreate = can('data-lifecycle:create');
  const canUpdate = can('data-lifecycle:update');
  const canDelete = can('data-lifecycle:delete');

  const [records, setRecords] = useState<PolicyRecord[]>([]);
  const [total, setTotal] = useState(0);
  const [pageNo, setPageNo] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [keyword, setKeyword] = useState('');
  const [scopeType, setScopeType] = useState<PolicyScope | ''>('');
  const [layerCode, setLayerCode] = useState('');
  const [loading, setLoading] = useState(false);
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [editing, setEditing] = useState<PolicyRecord | null>(null);
  const [versionPolicy, setVersionPolicy] = useState<PolicyRecord | null>(null);

  const loadPolicies = useCallback(
    async (targetPageNo: number, targetPageSize: number) => {
      setLoading(true);
      try {
        const result = await pagePolicies({
          pageNo: targetPageNo,
          pageSize: targetPageSize,
          keyword: keyword.trim() || undefined,
          scopeType: scopeType || undefined,
          layerCode: layerCode || undefined,
        });
        setRecords(result.records ?? []);
        setTotal(result.total ?? 0);
      } catch {
        setRecords([]);
        setTotal(0);
        message.error('加载策略列表失败');
      } finally {
        setLoading(false);
      }
    },
    [keyword, scopeType, layerCode],
  );

  useEffect(() => {
    void loadPolicies(pageNo, pageSize);
  }, [pageNo, pageSize, loadPolicies]);

  const runInitialize = () => {
    Modal.confirm({
      title: '初始化分层默认策略',
      content:
        '将按数仓分层预置 ODS / DIM / DWD / DWS / ADS 五条默认 TTL 策略（已存在的不覆盖）。初始化后可随时修改各层保留周期。',
      okText: '开始初始化',
      cancelText: '取消',
      onOk: async () => {
        try {
          const created = await initializeLayerDefaults();
          message.success(created > 0 ? `已初始化 ${created} 条分层默认策略` : '分层默认策略已存在，无需初始化');
        } catch {
          // 全局错误提示已展示原因
        } finally {
          await loadPolicies(pageNo, pageSize);
        }
      },
    });
  };

  const openCreate = () => {
    setEditing(null);
    setDrawerOpen(true);
  };

  const openEdit = (record: PolicyRecord) => {
    setEditing(record);
    setDrawerOpen(true);
  };

  const runPublish = (record: PolicyRecord) => {
    Modal.confirm({
      title: '发布策略',
      content:
        record.referenceCount > 0
          ? `「${record.policyName}」正被 ${record.referenceCount} 个模型引用，发布后这些模型将按新版本内容生效 TTL。确定发布？`
          : `将当前草稿内容发布为新版本，发布后模型绑定按该版本生效。确定发布「${record.policyName}」？`,
      okText: '发布',
      cancelText: '取消',
      onOk: async () => {
        try {
          const result = await publishPolicy(record.id);
          message.success(
            result.appended
              ? `已发布 V${result.version.versionNo}`
              : `内容未变化，沿用 V${result.version.versionNo}`,
          );
        } catch {
          // 全局错误提示已展示原因
        } finally {
          await loadPolicies(pageNo, pageSize);
        }
      },
    });
  };

  const runOffline = (record: PolicyRecord) => {
    Modal.confirm({
      title: '下线策略',
      content:
        record.referenceCount > 0
          ? `「${record.policyName}」正被 ${record.referenceCount} 个模型引用，下线后这些模型将回退到分层默认。确定下线？`
          : `下线后该策略不再对任何模型生效（版本历史保留，可随时重新发布）。确定下线「${record.policyName}」？`,
      okText: '下线',
      cancelText: '取消',
      onOk: async () => {
        try {
          await offlinePolicy(record.id);
          message.success('已下线');
        } catch {
          // 全局错误提示已展示原因
        } finally {
          await loadPolicies(pageNo, pageSize);
        }
      },
    });
  };

  const toggleStatus = (record: PolicyRecord) => {
    const next = record.status === 'ENABLED' ? 'DISABLED' : 'ENABLED';
    const action = next === 'DISABLED' ? '停用' : '启用';
    Modal.confirm({
      title: `${action}策略`,
      content:
        next === 'DISABLED' && record.referenceCount > 0
          ? `「${record.policyName}」正被 ${record.referenceCount} 个模型引用，停用后这些模型将回退到分层默认。确定停用？`
          : `确定${action}「${record.policyName}」？`,
      okText: action,
      cancelText: '取消',
      onOk: async () => {
        try {
          await changePolicyStatus(record.id, next);
          message.success(`已${action}`);
        } catch {
          // 全局错误提示已展示原因
        } finally {
          await loadPolicies(pageNo, pageSize);
        }
      },
    });
  };

  const removePolicy = (record: PolicyRecord) => {
    Modal.confirm({
      title: '删除策略',
      content:
        record.referenceCount > 0
          ? `「${record.policyName}」正被 ${record.referenceCount} 个模型引用，需先解除绑定后才能删除。`
          : `确定删除「${record.policyName}」？删除后不可恢复。`,
      okText: '删除',
      okType: 'danger',
      cancelText: '取消',
      okButtonProps: record.referenceCount > 0 ? { disabled: true } : undefined,
      onOk: async () => {
        try {
          await deletePolicy(record.id);
          message.success('已删除');
        } catch {
          // 全局错误提示已展示原因（内置策略/被引用）
        } finally {
          await loadPolicies(pageNo, pageSize);
        }
      },
    });
  };

  const columns: ColumnsType<PolicyRecord> = [
    {
      title: '策略名称',
      dataIndex: 'policyName',
      width: 220,
      ellipsis: true,
      render: (name: string, record) => (
        <div>
          <div className="flex items-center gap-1">
            <span className="truncate">{name}</span>
            {record.builtin && <Tag color="blue">内置</Tag>}
          </div>
          <div className="text-[12px] text-[#98a2b3]">{record.policyCode}</div>
        </div>
      ),
    },
    {
      title: '适用范围',
      dataIndex: 'scopeType',
      width: 130,
      render: (value: PolicyScope, record) =>
        value === 'LAYER_DEFAULT' ? (
          <Tag color="geekblue">{LAYER_LABELS[(record.layerCode ?? '').toUpperCase()] ?? record.layerCode}</Tag>
        ) : (
          <Tag>{POLICY_SCOPE_LABELS.CUSTOM}</Tag>
        ),
    },
    {
      title: '保留周期',
      key: 'retention',
      width: 240,
      render: (_, record) => formatRetention(record.hotDays, record.coldDays, record.destroyDays),
    },
    {
      title: '分区粒度',
      dataIndex: 'partitionGranularity',
      width: 90,
      render: (value: keyof typeof GRANULARITY_LABELS) => GRANULARITY_LABELS[value] ?? value,
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 70,
      render: (value: keyof typeof STATUS_LABELS) => (
        <Tag color={value === 'ENABLED' ? 'green' : 'default'}>{STATUS_LABELS[value]}</Tag>
      ),
    },
    {
      title: '发布态',
      dataIndex: 'publishState',
      width: 150,
      render: (value: PublishStateType | undefined, record) => (
        <Space size={4}>
          <Tag color={PUBLISH_STATE_COLORS[value ?? 'DRAFT']}>
            {PUBLISH_STATE_LABELS[value ?? 'DRAFT']}
          </Tag>
          {record.latestVersionNo ? (
            <span className="text-[12px] text-[#667085]">V{record.latestVersionNo}</span>
          ) : null}
          {record.hasPendingDraft ? (
            <Tooltip title="草稿与线上版本不一致，发布后生效">
              <Tag color="orange">未发布修改</Tag>
            </Tooltip>
          ) : null}
        </Space>
      ),
    },
    {
      title: '引用模型',
      dataIndex: 'referenceCount',
      width: 85,
      align: 'right' as const,
      render: (count: number) => (count > 0 ? `${count} 个` : '-'),
    },
    {
      title: '更新时间',
      dataIndex: 'updateTime',
      width: 160,
      render: (value?: string) => formatLifecycleTime(value),
    },
    {
      title: '备注',
      dataIndex: 'remark',
      ellipsis: true,
      render: (value?: string | null) => value || '-',
    },
    {
      title: '操作',
      key: 'action',
      width: 250,
      fixed: 'right' as const,
      render: (_, record) => (
        <Space size={4}>
          <Button type="link" size="small" disabled={!canUpdate} onClick={() => openEdit(record)}>
            编辑
          </Button>
          {record.publishState === 'PUBLISHED' && !record.hasPendingDraft ? (
            <Button
              type="link"
              size="small"
              danger
              disabled={!canUpdate}
              onClick={() => runOffline(record)}
            >
              下线
            </Button>
          ) : (
            <Tooltip
              title={
                record.publishState === 'PUBLISHED'
                  ? '存在未发布的草稿修改，点击发布新版本'
                  : '将草稿内容发布为新版本'
              }
            >
              <Button type="link" size="small" disabled={!canUpdate} onClick={() => runPublish(record)}>
                发布
              </Button>
            </Tooltip>
          )}
          <Button type="link" size="small" onClick={() => setVersionPolicy(record)}>
            版本
          </Button>
          <Button
            type="link"
            size="small"
            danger={record.status === 'ENABLED'}
            disabled={!canUpdate}
            onClick={() => toggleStatus(record)}
          >
            {record.status === 'ENABLED' ? '停用' : '启用'}
          </Button>
          {record.builtin ? (
            <Tooltip title="内置分层策略不可删除">
              <Button type="link" size="small" disabled>
                删除
              </Button>
            </Tooltip>
          ) : (
            <Button type="link" size="small" danger disabled={!canDelete} onClick={() => removePolicy(record)}>
              删除
            </Button>
          )}
        </Space>
      ),
    },
  ];

  const hasFilter = Boolean(keyword || scopeType || layerCode);

  return (
    <div className="flex min-h-[calc(100dvh-64px)] flex-col bg-white px-6 pb-4 pt-5 text-[#242731] max-md:px-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <div className="text-[20px] font-semibold leading-7">TTL 策略管理</div>
          <div className="mt-1 text-[13px] text-[#667085]">
            定义热 / 冷 / 销毁三段保留周期，平台生成语句下发到 Doris / Paimon，由存储自动清理分区
          </div>
        </div>
        <Space>
          {canCreate && (
            <YakButton className="!h-9 !rounded-lg !px-4" onClick={runInitialize}>
              初始化分层默认策略
            </YakButton>
          )}
          {canCreate && (
            <YakButton type="primary" className="!h-9 !rounded-lg !px-4 !text-white" onClick={openCreate}>
              新建策略
            </YakButton>
          )}
        </Space>
      </div>

      <div className="mt-4 flex flex-wrap items-center gap-3">
        <Input.Search
          allowClear
          placeholder="按名称或编码搜索"
          className="!w-[240px]"
          onSearch={(value) => {
            setKeyword(value.trim());
            setPageNo(1);
          }}
        />
        <Select
          allowClear
          placeholder="适用范围"
          className="!w-[140px]"
          value={scopeType || undefined}
          onChange={(value) => {
            setScopeType((value ?? '') as PolicyScope | '');
            setPageNo(1);
          }}
          options={(Object.keys(POLICY_SCOPE_LABELS) as PolicyScope[]).map((value) => ({
            value,
            label: POLICY_SCOPE_LABELS[value],
          }))}
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
      </div>

      <div className="mt-4 flex-1">
        <Table<PolicyRecord>
          rowKey="id"
          columns={columns}
          dataSource={records}
          loading={loading}
          scroll={{ x: 1450 }}
          locale={{
            emptyText: hasFilter ? (
              <YakEmpty compact title="没有符合条件的策略" description="调整筛选条件或重置后再试" />
            ) : (
              <YakEmpty
                compact
                title="还没有 TTL 策略"
                description="一键初始化 ODS / DIM / DWD / DWS / ADS 分层默认策略，模型将自动继承所属分层的保留周期"
              >
                {canCreate && (
                  <YakButton type="primary" className="!mt-3 !rounded-lg !text-white" onClick={runInitialize}>
                    一键初始化分层默认策略
                  </YakButton>
                )}
              </YakEmpty>
            ),
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
      </div>

      <PolicyEditDrawer
        open={drawerOpen}
        editing={editing}
        onClose={() => setDrawerOpen(false)}
        onSaved={() => {
          setDrawerOpen(false);
          void loadPolicies(pageNo, pageSize);
        }}
      />
      <PolicyVersionsDrawer
        open={versionPolicy !== null}
        policy={versionPolicy}
        onClose={() => setVersionPolicy(null)}
        onChanged={() => void loadPolicies(pageNo, pageSize)}
      />
    </div>
  );
};

export default PolicyPage;
