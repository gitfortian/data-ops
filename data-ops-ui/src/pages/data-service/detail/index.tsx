import YakTab from '@/components/YakTab';
import { consumptionProductPath } from '@/services/consumption';
import {
  DATA_SERVICE_NODE_SOURCE,
  DATA_SERVICE_PROVIDER_SOURCE_LABELS,
  LEGACY_DATA_DEVELOPMENT_RELEASE_SOURCE,
  getDataService,
  getDataServiceRuntime,
  listDataServiceDataSources,
  listDataServiceKeys,
  listDataServiceLogs,
  type DataServiceApi,
  type DataServiceApiKey,
  type DataServiceCallLog,
  type DataServiceRuntimeStatus,
  type DataSourceOption,
} from '@/services/data-service';
import { BRAND_THEME } from '@/styles/brand';
import { history, useAccess, useParams, useSearchParams } from '@umijs/max';
import {
  Alert,
  Button,
  ConfigProvider,
  Empty,
  Spin,
  Table,
  Tooltip,
  message,
  type TableColumnsType,
} from 'antd';
import { ArrowLeft, PlayCircle } from 'lucide-react';
import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react';

import DataServiceAccessControlPanel from '../components/DataServiceAccessControlPanel';
import DataServiceApiCallPanel from '../components/DataServiceApiCallPanel';
import { dataServiceDevelopmentSourceUrl } from '../utils';

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
  // Keep the audit ID in its original decimal form; frontend JSON numeric IDs above
  // MAX_SAFE_INTEGER are not sufficient evidence for an exact invocation match.
  const focusedInvocationId = /^[1-9]\d*$/.test(requestedInvocationId)
    ? requestedInvocationId : undefined;
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
  const [loading, setLoading] = useState(true);
  const [activeTab, setActiveTab] = useState<DetailTabKey>(
    focusedInvocationId && canObserve ? 'logs' : 'overview');

  useEffect(() => {
    if (focusedInvocationId && canObserve) setActiveTab('logs');
  }, [focusedInvocationId, canObserve]);

  const load = useCallback(async () => {
    if (!Number.isFinite(apiId) || apiId <= 0) {
      setLoading(false);
      return;
    }

    setLoading(true);
    try {
      const [serviceResponse, dataSourceResponse] = await Promise.all([
        getDataService(apiId),
        listDataServiceDataSources(),
      ]);
      setService(serviceResponse);
      setDataSources(dataSourceResponse);

      const [runtimeResponse, keyResponse, logResponse] = await Promise.all([
        canRuntime ? getDataServiceRuntime(apiId) : Promise.resolve(undefined),
        canManageAccess ? listDataServiceKeys(apiId) : Promise.resolve(undefined),
        canObserve ? listDataServiceLogs(apiId, focusedInvocationId ? 200 : 50) : Promise.resolve(undefined),
      ]);
      setRuntime(runtimeResponse);
      setKeys(keyResponse || []);
      setLogs(logResponse || []);
    } catch (error: any) {
      message.error(error?.message || '加载 API 详情失败');
    } finally {
      setLoading(false);
    }
  }, [apiId, canManageAccess, canObserve, canRuntime, focusedInvocationId]);

  useEffect(() => {
    void load();
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

  const exactInvocation = focusedInvocationId
    ? logs.find((item) => Number.isSafeInteger(item.id)
        && String(item.id) === focusedInvocationId)
    : undefined;

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
    <SectionCard title="调用记录">
      <div className="p-5">
        {focusedInvocationId ? (
          <Alert
            className="mb-4"
            type={exactInvocation ? 'info' : 'warning'}
            showIcon
            message={exactInvocation ? '已在此服务的近期日志中核对调用 ID'
              : '未能在此服务的可读日志窗口精确定位调用'}
            description={(
              <div>
                <div>Invocation ID：{focusedInvocationId}。只认可相同 ID 且属于当前 API 的记录；
                  近期日志最多读取 200 条，超出窗口或超过 JS 安全整数精度的记录不能在此页冒充已核对。</div>
                <Button type="link" size="small"
                  onClick={() => history.push(`/data-service/api/${apiId}?tab=logs`)}>
                  查看此服务最近调用
                </Button>
              </div>
            )}
          />
        ) : null}
        {(!focusedInvocationId || exactInvocation) ? (
          <Table<DataServiceCallLog>
            rowKey="id"
            size="small"
            columns={logColumns}
            dataSource={focusedInvocationId ? exactInvocation ? [exactInvocation] : [] : logs}
            pagination={false}
            scroll={{ x: 760 }}
            className="[&_.ant-table-container]:!rounded-md [&_.ant-table-container]:!border [&_.ant-table-container]:!border-solid [&_.ant-table-container]:!border-[#eceef1] [&_.ant-table-thead>tr>th]:!h-10 [&_.ant-table-thead>tr>th]:!bg-[#f7f7f8] [&_.ant-table-thead>tr>th]:!text-[12px] [&_.ant-table-tbody>tr>td]:!py-3 [&_.ant-table-tbody>tr>td]:!text-[12px]"
          />
        ) : (
          <div className="flex min-h-[320px] items-center justify-center">
            <Empty image={Empty.PRESENTED_IMAGE_SIMPLE}
              description={focusedInvocationId ? '无法精确定位指定调用记录' : '暂无调用记录'} />
          </div>
        )}
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
