import YakTab from '@/components/YakTab';
import { consumptionProductPath } from '@/services/consumption';
import {
  DATA_SERVICE_NODE_SOURCE,
  DATA_SERVICE_PROVIDER_SOURCE_LABELS,
  LEGACY_DATA_DEVELOPMENT_RELEASE_SOURCE,
  getDataService,
  getDataServiceRuntime,
  getDataServiceInvocationEvidence,
  listDataServiceDataSources,
  listDataServiceKeys,
  listDataServiceLogs,
  type DataServiceApi,
  type DataServiceApiKey,
  type DataServiceCallLog,
  type DataServiceInvocationEvidence,
  type DataServiceRuntimeStatus,
  type DataSourceOption,
} from '@/services/data-service';
import { BRAND_THEME } from '@/styles/brand';
import { history, useAccess, useParams, useSearchParams } from '@umijs/max';
import {
  Alert,
  Button,
  ConfigProvider,
  Descriptions,
  Empty,
  Spin,
  Table,
  Tooltip,
  message,
  type TableColumnsType,
} from 'antd';
import { ArrowLeft, PlayCircle } from 'lucide-react';
import { useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';

import DataServiceAccessControlPanel from '../components/DataServiceAccessControlPanel';
import DataServiceApiCallPanel from '../components/DataServiceApiCallPanel';
import { dataServiceDevelopmentSourceUrl } from '../utils';
import { verifiedPersistedInvocation } from './invocation-evidence';

type DetailTabKey = 'overview' | 'access' | 'network' | 'runtime' | 'logs';

const formatTime = (value?: string | null) =>
  value ? value.replace('T', ' ').slice(0, 19) : '-';

const percent = (value?: number) =>
  `${Math.round((value || 0) * 1000) / 10}%`;

const callerLabel = (record: DataServiceCallLog) => {
  if (record.callerType === 'CONSOLE') return '控制台测试';
  if (record.callerType === 'API_KEY') return record.apiKeyName || 'API Key';
  if (record.callerType === 'PUBLIC') return '公开调用';
  return '历史调用';
};

const latestActivity = (runtime?: DataServiceRuntimeStatus) => {
  const values = [runtime?.lastSuccessAt, runtime?.lastFailureAt].filter(Boolean) as string[];
  if (!values.length) return '-';
  return formatTime(
    values.sort((left, right) =>
      new Date(right).getTime() - new Date(left).getTime())[0],
  );
};

const MetricTile = ({ label, value }: { label: string; value: ReactNode }) => (
  <div className="rounded-md bg-[#f7f7f8] px-4 py-4">
    <div className="text-[12px] leading-4 text-[#7c828c]">{label}</div>
    <div className="mt-2 truncate text-[20px] font-semibold leading-7 tracking-[-0.02em] text-[#161823]">
      {value}
    </div>
  </div>
);

const InfoField = ({
  label,
  children,
  className = '',
}: {
  label: string;
  children: ReactNode;
  className?: string;
}) => (
  <div className={className}>
    <div className="text-[12px] text-[#8a8f98]">{label}</div>
    <div className="mt-2 min-w-0 break-words text-[14px] font-medium text-[#161823]">
      {children}
    </div>
  </div>
);

const SectionCard = ({
  title,
  children,
  className = '',
}: {
  title: ReactNode;
  children: ReactNode;
  className?: string;
}) => (
  <section className={`min-w-0 rounded-lg bg-white ${className}`}>
    <div className="flex min-h-[52px] items-center px-5">
      <div className="text-[15px] font-semibold text-[#161823]">{title}</div>
    </div>
    {children}
  </section>
);

const ApiIllustration = () => (
  <div className="relative flex h-[116px] w-[116px] shrink-0 items-center justify-center overflow-hidden rounded-lg bg-white">
    <svg
      width="78"
      height="78"
      viewBox="0 0 78 78"
      fill="none"
      aria-hidden="true"
      className="relative z-10 -translate-y-1"
      shapeRendering="crispEdges"
    >
      <rect x="16" y="14" width="4" height="4" fill="#161823" />
      <rect x="12" y="18" width="4" height="4" fill="#161823" />
      <rect x="20" y="18" width="4" height="4" fill="#161823" />
      <rect x="16" y="22" width="4" height="4" fill="#161823" />
      <rect x="58" y="18" width="4" height="4" fill="#FE2C55" />
      <rect x="54" y="22" width="4" height="4" fill="#FE2C55" />
      <rect x="62" y="22" width="4" height="4" fill="#FE2C55" />
      <rect x="58" y="26" width="4" height="4" fill="#FE2C55" />
      <rect x="23" y="29" width="32" height="4" fill="#161823" />
      <rect x="19" y="33" width="4" height="27" fill="#161823" />
      <rect x="55" y="33" width="4" height="27" fill="#161823" />
      <rect x="23" y="60" width="32" height="4" fill="#161823" />
      <rect x="23" y="33" width="32" height="27" fill="#F5F6F8" />
      <rect x="23" y="33" width="32" height="8" fill="#FFFFFF" />
      <rect x="23" y="41" width="32" height="4" fill="#E3E7EC" />
      <rect x="23" y="49" width="32" height="11" fill="#E8EBEF" />
      <rect x="27" y="36" width="4" height="4" fill="#FE2C55" />
      <rect x="34" y="36" width="4" height="4" fill="#AEB4BF" />
      <rect x="41" y="36" width="4" height="4" fill="#AEB4BF" />
      <rect x="29" y="52" width="20" height="4" fill="#161823" />
      <rect x="33" y="56" width="12" height="4" fill="#FE2C55" />
      <rect x="25" y="64" width="8" height="3" fill="#161823" />
      <rect x="45" y="64" width="8" height="3" fill="#161823" />
    </svg>
    <div className="pointer-events-none absolute inset-x-0 bottom-0 z-20 h-[46px] bg-gradient-to-b from-transparent via-black/10 to-black/25" />
  </div>
);

export default function DataServiceDetailPage() {
  const params = useParams<{ id?: string }>();
  const [searchParams] = useSearchParams();
  const requestedInvocationId = searchParams.get('invocationId') || '';
  // SQL BIGINT is preserved as decimal text by the exact audit endpoint.
  const focusedInvocationId = /^[1-9]\d*$/.test(requestedInvocationId)
    ? requestedInvocationId : undefined;
  const logsTab = searchParams.get('tab') === 'logs';
  const apiId = Number(params.id || 0);
  const access = useAccess();
  const canManageAccess = access.hasPermission('data-service:access');
  const canRuntime = access.hasPermission('data-service:runtime');
  const canObserve = access.hasPermission('data-service:observe');

  const [service, setService] = useState<DataServiceApi>();
  const [dataSources, setDataSources] = useState<DataSourceOption[]>([]);
  const [runtime, setRuntime] = useState<DataServiceRuntimeStatus>();
  const [keys, setKeys] = useState<DataServiceApiKey[]>([]);
  const [logs, setLogs] = useState<DataServiceCallLog[]>([]);
  const [auditEvidence, setAuditEvidence] = useState<DataServiceInvocationEvidence | null>(null);
  const [logIssue, setLogIssue] = useState('');
  const [loading, setLoading] = useState(true);
  const loadRequestId = useRef(0);
  const [activeTab, setActiveTab] = useState<DetailTabKey>(
    (logsTab || focusedInvocationId) && canObserve ? 'logs' : 'overview');

  useEffect(() => {
    if ((focusedInvocationId || logsTab) && canObserve) setActiveTab('logs');
  }, [focusedInvocationId, logsTab, canObserve]);

  const load = useCallback(async () => {
    const requestId = ++loadRequestId.current;
    if (!Number.isSafeInteger(apiId) || apiId <= 0) {
      setLoading(false);
      return;
    }
    setLoading(true);
    setLogIssue('');
    setLogs([]);
    setAuditEvidence(null);
    try {
      const [serviceResponse, dataSourceResponse] = await Promise.all([
        getDataService(apiId),
        listDataServiceDataSources(),
      ]);
      if (requestId !== loadRequestId.current) return;
      setService(serviceResponse);
      setDataSources(dataSourceResponse);

      const [runtimeResponse, keyResponse, logResult] = await Promise.all([
        canRuntime ? getDataServiceRuntime(apiId) : Promise.resolve(undefined),
        canManageAccess ? listDataServiceKeys(apiId) : Promise.resolve(undefined),
        !canObserve ? Promise.resolve({ kind: 'none' as const })
          : focusedInvocationId
            ? getDataServiceInvocationEvidence(apiId, focusedInvocationId)
              .then((value) => ({ kind: 'exact' as const, value }))
              .catch((cause) => ({ kind: 'error' as const,
                reason: cause instanceof Error ? cause.message : '精确调用记录不可读取' }))
            : listDataServiceLogs(apiId, 50)
              .then((value) => ({ kind: 'recent' as const, value }))
              .catch((cause) => ({ kind: 'error' as const,
                reason: cause instanceof Error ? cause.message : '调用日志来源不可用' })),
      ]);
      if (requestId !== loadRequestId.current) return;
      setRuntime(runtimeResponse);
      setKeys(keyResponse || []);
      if (logResult.kind === 'exact') setAuditEvidence(logResult.value);
      if (logResult.kind === 'recent') setLogs(logResult.value);
      if (logResult.kind === 'error') setLogIssue(logResult.reason);
    } catch (cause: any) {
      if (requestId === loadRequestId.current) {
        message.error(cause?.message || '加载 API 详情失败');
      }
    } finally {
      if (requestId === loadRequestId.current) setLoading(false);
    }
  }, [apiId, canManageAccess, canObserve, canRuntime, focusedInvocationId]);

  useEffect(() => {
    void load();
    return () => { loadRequestId.current += 1; };
  }, [load]);

  const sourceManaged = service?.sourceType === DATA_SERVICE_NODE_SOURCE;
  const legacySqlRelease = service?.sourceType === LEGACY_DATA_DEVELOPMENT_RELEASE_SOURCE;
  const developmentSourceUrl = service
    ? dataServiceDevelopmentSourceUrl(service)
    : undefined;

  const dataSourceName = useMemo(() => {
    if (!service?.dataSourceId) return '-';
    return dataSources.find((item) => String(item.value) === String(service.dataSourceId))?.label
      || `#${service.dataSourceId}`;
  }, [dataSources, service?.dataSourceId]);

  const exactInvocation = verifiedPersistedInvocation(auditEvidence, apiId, focusedInvocationId);

  const logColumns: TableColumnsType<DataServiceCallLog> = [
    {
      title: '调用方',
      key: 'caller',
      width: 150,
      render: (_, record) => (
        <div>
          <div className="text-[12px] text-[#475467]">{callerLabel(record)}</div>
          {record.apiKeyPrefix ? (
            <div className="mt-0.5 font-mono text-[12px] text-[#667085]">
              {record.apiKeyPrefix}••••
            </div>
          ) : null}
        </div>
      ),
    },
    {
      title: '状态',
      dataIndex: 'success',
      width: 76,
      render: (value: boolean) => (
        <span className={value ? 'text-[#475467]' : 'text-[var(--yak-brand-color)]'}>
          {value ? '成功' : '失败'}
        </span>
      ),
    },
    {
      title: '耗时',
      dataIndex: 'durationMs',
      width: 88,
      render: (value) => `${value ?? 0} ms`,
    },
    { title: '行数', dataIndex: 'rowCount', width: 70 },
    {
      title: '错误',
      dataIndex: 'errorMessage',
      ellipsis: true,
      render: (value) => value
        ? <Tooltip title={value}><span className="text-[#b42318]">{value}</span></Tooltip>
        : <span className="text-black/20">-</span>,
    },
    { title: '时间', dataIndex: 'createTime', width: 150, render: formatTime },
  ];

  if (loading) {
    return (
      <div className="flex min-h-[calc(100vh-64px)] items-center justify-center bg-[#f7f7f8]">
        <Spin size="large" />
      </div>
    );
  }

  if (!service) {
    return (
      <div className="flex min-h-[calc(100vh-64px)] items-center justify-center bg-[#f7f7f8]">
        <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="未找到 API">
          <Button onClick={() => history.push('/data-service')}>返回 API 集市</Button>
        </Empty>
      </div>
    );
  }

  const sourceTypeLabel = sourceManaged
    ? 'Data Service Node'
    : legacySqlRelease
      ? 'Legacy SQL Release'
      : DATA_SERVICE_PROVIDER_SOURCE_LABELS[service.sourceType ?? ''] ?? 'Legacy';
  const sourceRevisionLabel = sourceManaged
    ? `DS R${service.sourceRevisionNo || '-'}`
    : legacySqlRelease
      ? `SQL v${service.sourceRevisionNo || '-'}`
      : '-';

  const overviewContent = (
    <div className="grid gap-3 xl:grid-cols-2">
      <SectionCard title="API 概览">
        <div className="grid grid-cols-2 gap-3 p-5 md:grid-cols-3">
          <MetricTile label="调用次数" value={canRuntime ? runtime?.totalCalls || 0 : '—'} />
          <MetricTile label="成功率" value={canRuntime ? percent(runtime?.successRate) : '—'} />
          <MetricTile label="平均耗时" value={canRuntime ? `${runtime?.averageDurationMs || 0} ms` : '—'} />
          <MetricTile label="P95" value={canRuntime ? `${runtime?.p95DurationMs || 0} ms` : '—'} />
          <MetricTile label="API Keys" value={canManageAccess ? keys.length : '—'} />
          <MetricTile label="最近调用" value={canRuntime ? latestActivity(runtime) : '—'} />
        </div>
      </SectionCard>

      <SectionCard title="服务信息">
        <div className="grid grid-cols-1 gap-x-10 gap-y-6 p-5 sm:grid-cols-2">
          <InfoField label="请求方式">GET</InfoField>
          <InfoField label="数据源">{dataSourceName}</InfoField>
          <InfoField label="来源">{sourceTypeLabel}</InfoField>
          <InfoField label="版本">{sourceRevisionLabel}</InfoField>
          <InfoField label="最大返回行数">{service.maxRows || '-'}</InfoField>
          <InfoField label="超时时间">
            {service.timeoutSeconds ? `${service.timeoutSeconds}s` : '-'}
          </InfoField>
          <InfoField label="请求参数">{service.parameterNames?.length || 0} 个</InfoField>
          <InfoField label="访问模式">
            {service.authMode === 'API_KEY' ? 'API Key' : 'Public'}
          </InfoField>
          <InfoField label="Endpoint" className="sm:col-span-2">
            <span className="font-mono text-[12px] font-normal text-[#475467]">
              {service.runtimePath}
            </span>
          </InfoField>
          {service.description ? (
            <InfoField label="描述" className="sm:col-span-2">
              <span className="font-normal text-[#475467]">{service.description}</span>
            </InfoField>
          ) : null}
        </div>
      </SectionCard>
    </div>
  );

  const accessContent = (
    <DataServiceApiCallPanel
      service={service}
      keys={keys}
      canManageAccess={canManageAccess}
      onAuthModeChange={(mode) => {
        setService((current) => current ? { ...current, authMode: mode } : current);
      }}
      onKeysChange={setKeys}
    />
  );

  const networkAccessContent = (
    <DataServiceAccessControlPanel apiId={service.id} />
  );

  const runtimeContent = (
    <div className="grid gap-3 xl:grid-cols-2">
      <SectionCard title="运行指标">
        <div className="grid grid-cols-2 gap-3 p-5">
          <MetricTile label="调用总数" value={runtime?.totalCalls || 0} />
          <MetricTile label="成功率" value={percent(runtime?.successRate)} />
          <MetricTile label="平均耗时" value={`${runtime?.averageDurationMs || 0} ms`} />
          <MetricTile label="P95" value={`${runtime?.p95DurationMs || 0} ms`} />
        </div>
      </SectionCard>

      <SectionCard title="运行保护">
        <div className="grid grid-cols-1 gap-3 p-5 md:grid-cols-2">
          <div className="rounded-md bg-[#f7f7f8] px-4 py-4">
            <div className="text-[12px] font-medium text-[#344054]">结果缓存</div>
            <div className="mt-4 grid grid-cols-3 gap-3">
              <InfoField label="状态">{runtime?.cacheEnabled ? '启用' : '关闭'}</InfoField>
              <InfoField label="命中率">{percent(runtime?.cacheHitRate)}</InfoField>
              <InfoField label="条目">{runtime?.cacheEntries || 0}</InfoField>
            </div>
          </div>
          <div className="rounded-md bg-[#f7f7f8] px-4 py-4">
            <div className="text-[12px] font-medium text-[#344054]">熔断器</div>
            <div className="mt-4 grid grid-cols-3 gap-3">
              <InfoField label="状态">{runtime?.circuitState || 'DISABLED'}</InfoField>
              <InfoField label="拒绝">{runtime?.circuitRejected || 0}</InfoField>
              <InfoField label="最近失败">{formatTime(runtime?.lastFailureAt)}</InfoField>
            </div>
          </div>
        </div>
      </SectionCard>
    </div>
  );

  const logsContent = (
    <SectionCard title={focusedInvocationId ? '精确调用证据' : '调用记录'}>
      <div className="p-5">
        {logIssue ? <Alert className="mb-4" type="warning" showIcon
          message="调用日志来源暂不可用" description={logIssue} /> : null}
        {focusedInvocationId ? (
          <>
            <Alert className="mb-4"
              type={exactInvocation && !logIssue ? 'success' : 'warning'} showIcon
              message={logIssue ? '无法核对指定来源调用'
                : exactInvocation ? '已核对持久化调用原始记录'
                  : '没有找到可核对的该 API 调用记录'}
              description={(
                <div>
                  <div>Invocation ID：{focusedInvocationId}。按当前 Project、API 与完整 BIGINT
                    调用 ID 直接核对持久日志，不受最近 200 条窗口限制。
                    不存在、已清理或不属于当前项目/API 的记录均不会被邻近调用代替。</div>
                  <Button type="link" size="small"
                    onClick={() => history.push(`/data-service/api/${apiId}?tab=logs`)}>
                    返回最近调用记录
                  </Button>
                </div>
              )}
            />
            {exactInvocation && !logIssue ? (
              <Descriptions bordered size="small" column={{ xs: 1, sm: 2 }}>
                <Descriptions.Item label="Invocation ID">{exactInvocation.id}</Descriptions.Item>
                <Descriptions.Item label="所属 API ID">{exactInvocation.apiId}</Descriptions.Item>
                <Descriptions.Item label="状态">{exactInvocation.success ? '成功' : '失败'}</Descriptions.Item>
                <Descriptions.Item label="调用方">{exactInvocation.callerType || '-'}</Descriptions.Item>
                <Descriptions.Item label="Consumer ID">{exactInvocation.consumerId || '-'}</Descriptions.Item>
                <Descriptions.Item label="Key 名称">{exactInvocation.apiKeyName || '-'}</Descriptions.Item>
                <Descriptions.Item label="Source Revision ID">{exactInvocation.sourceRevisionId || '-'}</Descriptions.Item>
                <Descriptions.Item label="Source Revision No">{exactInvocation.sourceRevisionNo ?? '-'}</Descriptions.Item>
                <Descriptions.Item label="耗时">{exactInvocation.durationMs} ms</Descriptions.Item>
                <Descriptions.Item label="返回行数">{exactInvocation.rowCount}</Descriptions.Item>
                <Descriptions.Item label="调用时间">{formatTime(exactInvocation.createTime)}</Descriptions.Item>
                <Descriptions.Item label="服务路径">{exactInvocation.servicePath || '-'}</Descriptions.Item>
                <Descriptions.Item label="失败详情" span={2}>{exactInvocation.errorMessage || '-'}</Descriptions.Item>
                <Descriptions.Item label="已脱敏参数" span={2}>
                  <span className="break-all font-mono text-xs">{exactInvocation.paramsJson || '{}'}</span>
                </Descriptions.Item>
              </Descriptions>
            ) : <Empty image={Empty.PRESENTED_IMAGE_SIMPLE}
              description="暂无经过 Project、API 和调用 ID 三重核对的记录" />}
          </>
        ) : !logIssue && logs.length ? (
          <Table<DataServiceCallLog>
            rowKey="id" size="small" columns={logColumns} dataSource={logs}
            pagination={false} scroll={{ x: 760 }}
            className="[&_.ant-table-container]:!rounded-md [&_.ant-table-container]:!border [&_.ant-table-container]:!border-solid [&_.ant-table-container]:!border-[#eceef1] [&_.ant-table-thead>tr>th]:!h-10 [&_.ant-table-thead>tr>th]:!bg-[#f7f7f8] [&_.ant-table-thead>tr>th]:!text-[12px] [&_.ant-table-tbody>tr>td]:!py-3 [&_.ant-table-tbody>tr>td]:!text-[12px]"
          />
        ) : <Empty image={Empty.PRESENTED_IMAGE_SIMPLE}
          description={logIssue ? '暂无法读取调用日志' : '暂无调用记录'} />}
      </div>
    </SectionCard>
  );

  const tabItems: Array<{
    key: DetailTabKey;
    label: string;
    children: ReactNode;
  }> = [
    { key: 'overview', label: '总览', children: overviewContent },
    { key: 'access', label: 'API 调用', children: accessContent },
    ...(canManageAccess
      ? [{ key: 'network' as const, label: '访问控制', children: networkAccessContent }]
      : []),
    ...(canRuntime ? [{ key: 'runtime' as const, label: 'Runtime', children: runtimeContent }] : []),
    ...(canObserve ? [{ key: 'logs' as const, label: '调用记录', children: logsContent }] : []),
  ];

  return (
    <ConfigProvider theme={BRAND_THEME}>
      <div className="min-h-[calc(100vh-64px)] bg-[#f7f7f8] text-[#161823]">
        <div className="mx-auto w-full max-w-[1800px] px-4 pb-8 pt-0 lg:px-5">
          <div className="mb-2 flex h-10 items-center justify-between">
            <Button
              type="text"
              icon={<ArrowLeft size={15} />}
              className="!h-9 !px-1 !text-[14px] !font-semibold !text-[#30343b]"
              onClick={() => history.push('/data-service')}
            >
              返回 API 集市
            </Button>
            <div className="flex flex-wrap items-center gap-2">
              <Button onClick={() => history.push(consumptionProductPath('DATA_SERVICE', String(service.id)))}>
                查看消费与治理
              </Button>
              {developmentSourceUrl ? (
                <Button onClick={() => history.push(developmentSourceUrl)}>
                  查看 Data Development 来源
                </Button>
              ) : null}
            </div>
          </div>

          <section className="rounded-lg bg-white">
            <div className="grid gap-4 px-4 py-4 lg:px-6 xl:grid-cols-[minmax(0,1fr)_180px] xl:items-center">
              <div className="min-w-0">
                <div className="break-words text-[20px] font-semibold leading-7 text-[#161823]">
                  {service.name}
                </div>
                <div className="mt-1 text-[12px] leading-4 text-[#8a8f98]">
                  {formatTime(service.updateTime || service.createTime)}
                </div>
                <div className="mt-1 flex items-center gap-1 text-[12px] leading-4 text-[#667085]">
                  <span className={[
                    'inline-block h-[10px] w-[10px] rounded-full',
                    service.enabled ? 'bg-[#20c77a]' : 'bg-[#b0b5bd]',
                  ].join(' ')} />
                  <span>{service.enabled ? '运行中' : '已停用'}</span>
                </div>
                <div className="mt-2 flex min-w-0 items-center gap-2 text-[12px] leading-4 text-[#8a8f98]">
                  <span className="font-mono">GET</span>
                  <span className="text-[#d0d5dd]">·</span>
                  <span className="truncate font-mono">{service.runtimePath}</span>
                </div>
                <div className="mt-1.5 flex min-w-0 items-center gap-1.5 text-[12px] leading-4 text-[#8a8f98]">
                  <span>{sourceTypeLabel}</span>
                  <span className="text-[#d0d5dd]">·</span>
                  <span>{sourceRevisionLabel}</span>
                  <span className="text-[#d0d5dd]">·</span>
                  <span className="truncate">{dataSourceName}</span>
                </div>
              </div>
              <div className="min-w-0 xl:justify-self-end">
                {canRuntime ? (
                  <Button
                    type="primary"
                    icon={<PlayCircle size={14} />}
                    onClick={() => history.push(`/data-service/debug?apiId=${service.id}`)}
                  >
                    调试
                  </Button>
                ) : null}
              </div>
            </div>
          </section>

          <div className="px-5 lg:px-6">
            <YakTab
              activeKey={activeTab}
              onChange={(key) => setActiveTab(key as DetailTabKey)}
              items={tabItems.map(({ key, label }) => ({ key, label }))}
            />
          </div>

          {focusedInvocationId && !canObserve ? (
            <Alert type="warning" showIcon className="mt-3"
              message="当前身份无权查看调用日志"
              description="消费影响中的证据引用并不授予 Data Service 观测权限；请联系服务 Owner 进行核对。"
            />
          ) : null}
          <div className="mt-3">
            {tabItems.find((item) => item.key === activeTab)?.children}
          </div>
        </div>
      </div>
    </ConfigProvider>
  );
}
