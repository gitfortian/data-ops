import { YakButton } from '@/components/ui';
import { Alert, Button, Checkbox, Input, Modal, Spin, Typography, message } from 'antd';
import { useState } from 'react';
import { previewSourceSemanticEvidence, searchMetadata } from '@/services/metadata/api';
import type { SourceSemanticEvidence } from '@/services/metadata/api';
import type { MetadataSearchItem } from '@/services/metadata/types';
import type { DataSourceRecord } from '@/services/data-source';
import { history, useAccess, useIntl } from '@umijs/max';
import { motion } from 'framer-motion';
import {
  Clock3,
  BrainCircuit,
  Pencil,
  ScrollText,
  Trash2,
  Unplug,
  Waypoints,
  ScanSearch,
} from 'lucide-react';

import DatabaseIcons from '@/components/data-source/icons/DatabaseIcons';
import { buildIntegrationCreatePath } from '@/services/integration/sourceHandoff';
import { sourceTaskPath } from '@/services/agent/sourceSemantic';
import { getEnvironmentTagConfigMap, PAGE_ANIMATION } from '../constants';
import type { DataSourcePermissions, DataSourceViewMode } from '../types';
import { dataSourceRecordKey } from '../types';
import DataSourceStatus from './DataSourceStatus';

interface DataSourceCardProps {
  record: DataSourceRecord;
  viewMode: DataSourceViewMode;
  permissions: DataSourcePermissions;
  testingId: string;
  editingId: string;
  onEdit: (record: DataSourceRecord) => void;
  onDelete: (record: DataSourceRecord) => void;
  onTestConnection: (record: DataSourceRecord) => void;
}

const DataSourceCard = ({
  record,
  viewMode,
  permissions,
  testingId,
  editingId,
  onEdit,
  onDelete,
  onTestConnection,
}: DataSourceCardProps) => {
  const intl = useIntl();
  const [scopeOpen, setScopeOpen] = useState(false);
  const [scopeLoading, setScopeLoading] = useState(false);
  const [scopeError, setScopeError] = useState('');
  const [scopeKeyword, setScopeKeyword] = useState('');
  const [scopeTables, setScopeTables] = useState<MetadataSearchItem[]>([]);
  const [selectedKeys, setSelectedKeys] = useState<string[]>([]);
  const [scopeEvidence, setScopeEvidence] = useState<SourceSemanticEvidence | null>(null);
  const access = useAccess();
  const environmentTagConfigMap = getEnvironmentTagConfigMap(intl);
  const environmentConfig = environmentTagConfigMap[
    record.environment || ''
  ] || {
    text:
      record.environmentName ||
      intl.formatMessage({ id: 'pages.datasource.environment.uncategorized' }),
    color: '#667085',
    backgroundColor: '#f2f4f7',
    icon: null,
  };
  const currentId = dataSourceRecordKey(record.id);
  const canCreateRealtime =
    Boolean(currentId) &&
    String(record.dbType || '').toUpperCase() === 'MYSQL' &&
    access.hasPermission('task:realtime:create');
  const canStartSourceSemantic = Boolean(currentId)
    && access.hasPermission('agent:chat:run')
    && access.hasPermission('resource:data-source:read');
  const canPreviewSource = Boolean(currentId) && Number.isSafeInteger(Number(currentId))
    && Number(currentId) > 0 && access.hasPermission('data-metadata:read');
  const loadScopeTables = async (keyword = '') => {
    if (!canPreviewSource) return;
    setScopeLoading(true);
    setScopeError('');
    setScopeEvidence(null);
    try {
      const result = await searchMetadata({
        q: keyword || undefined,
        index: ['table'],
        queryFilter: { datasourceId: Number(currentId), providerType: 'HARVESTED' },
        from: 0,
        size: 100,
      });
      setScopeTables(result.items.filter((item) =>
        item.typeName === 'table'
        && item.providerType === 'HARVESTED'
        && String(item.dataSourceId) === currentId
        && typeof item.assetKey === 'string'
      ));
    } catch {
      setScopeError('读取元数据失败；请确认当前项目权限及采集任务。');
      setScopeTables([]);
    } finally {
      setScopeLoading(false);
    }
  };
  const previewScope = async () => {
    if (!selectedKeys.length || selectedKeys.length > 20) {
      message.warning('请选择 1–20 张已采集表');
      return;
    }
    setScopeLoading(true);
    setScopeError('');
    setScopeEvidence(null);
    try {
      const result = await previewSourceSemanticEvidence(Number(currentId), selectedKeys);
      setScopeEvidence(result);
    } catch {
      setScopeError('来源未完整采集、采集批次不一致或当前无权读取；请先在元数据采集任务中处理。');
    } finally {
      setScopeLoading(false);
    }
  };
  const actionAvailable =
    canPreviewSource ||
    permissions.canTest ||
    permissions.canUpdate ||
    permissions.canDelete ||
    permissions.canReadSqlExecutions ||
    canCreateRealtime ||
    canStartSourceSemantic;
  const isListView = viewMode === 'list';

  const openRealtimeCreate = () => {
    if (!currentId) return;
    history.push(
      buildIntegrationCreatePath('realtime', {
        dataSourceId: currentId,
        dbType: record.dbType ? String(record.dbType) : undefined,
      }),
    );
  };

  return (
    <motion.article
      variants={PAGE_ANIMATION.fadeUp}
      className={[
        'group relative min-w-0 overflow-hidden rounded-[16px] border border-[rgba(31,35,41,0.075)] bg-white/[0.98]',
        'shadow-[0_3px_10px_rgba(31,35,41,0.035),0_1px_2px_rgba(31,35,41,0.02)]',
        'transition-[transform,border-color,box-shadow] duration-[260ms] ease-[cubic-bezier(0.22,1,0.36,1)]',
        'hover:-translate-y-px hover:border-[rgba(31,35,41,0.11)] hover:shadow-[0_10px_24px_rgba(31,35,41,0.065),0_1px_2px_rgba(31,35,41,0.02)]',
        isListView
          ? 'grid grid-cols-[minmax(380px,1.5fr)_minmax(390px,1fr)] max-xl:grid-cols-1'
          : '',
      ]
        .filter(Boolean)
        .join(' ')}
    >
      <span
        aria-hidden="true"
        className="pointer-events-none absolute inset-0 z-0 opacity-0 transition-opacity duration-300 group-hover:opacity-100 [background-image:radial-gradient(circle,rgba(94,117,163,0.14)_0.7px,transparent_0.8px)] [background-size:8px_8px] [mask-image:linear-gradient(115deg,#000_0%,rgba(0,0,0,0.18)_40%,transparent_72%)]"
      />
      <span
        aria-hidden="true"
        className="pointer-events-none absolute -right-16 -top-20 z-0 h-48 w-48 rounded-full bg-[#dce7ff]/35 blur-3xl transition-transform duration-300 group-hover:scale-110"
      />

      <div className="relative z-[1] flex min-h-[116px] items-start justify-between gap-4 px-[18px] pb-[17px] pt-[18px]">
        <div className="flex min-w-0 items-start gap-[13px]">
          <div className="flex h-[52px] w-[52px] shrink-0 items-center justify-center rounded-[14px] border border-[rgba(31,35,41,0.07)] bg-[linear-gradient(145deg,#ffffff_0%,#f5f7fa_100%)] shadow-[0_5px_14px_rgba(31,35,41,0.055)] transition-transform duration-[260ms] group-hover:scale-[1.025]">
            <DatabaseIcons dbType={record.dbType} width="31" height="31" />
          </div>

          <div className="min-w-0 pt-0.5">
            <div className="flex min-w-0 flex-wrap items-center gap-2">
              <h3
                title={record.name}
                className="m-0 max-w-[260px] truncate text-[15px] font-semibold leading-[22px] text-[#292c35]"
              >
                {record.name ||
                  intl.formatMessage({ id: 'pages.datasource.card.unnamed' })}
              </h3>

              <span
                className="inline-flex h-5 shrink-0 items-center gap-1 whitespace-nowrap rounded-full px-[7px] text-[12px] font-semibold"
                style={{
                  color: environmentConfig.color,
                  background: environmentConfig.backgroundColor,
                }}
              >
                {environmentConfig.icon}
                {environmentConfig.text}
              </span>
            </div>

            <p
              title={record.jdbcUrl}
              className="mb-0 mt-2 max-w-[460px] truncate rounded-[7px] bg-[#f7f8fa]/90 px-2 py-1 text-[12px] leading-[18px] text-[#858a94]"
            >
              {record.jdbcUrl ||
                intl.formatMessage({ id: 'pages.datasource.card.noJdbcUrl' })}
            </p>
          </div>
        </div>

        {actionAvailable ? (
          <div className="flex shrink-0 -translate-y-1 gap-1 opacity-0 transition-all duration-150 group-hover:translate-y-0 group-hover:opacity-100 group-focus-within:translate-y-0 group-focus-within:opacity-100">
            {canStartSourceSemantic ? (
              <YakButton
                type="text"
                size="small"
                iconOnly
                title="来源结构业务理解（需 Agent 侧显式启用）"
                className="!h-[30px] !w-[30px] !rounded-[8px] !border !border-[#e9ebef] !bg-white/90 !p-0"
                icon={<BrainCircuit size={14} strokeWidth={1.9} />}
                onClick={() => history.push(sourceTaskPath(currentId))}
              />
            ) : null}
            {canPreviewSource ? (
              <YakButton
                type="text"
                size="small"
                iconOnly
                title="来源语义分析范围与采集证据"
                className="!h-[30px] !w-[30px] !rounded-[8px] !border !border-[#e9ebef] !bg-white/90 !p-0"
                icon={<ScanSearch size={14} strokeWidth={1.9} />}
                onClick={() => {
                  setSelectedKeys([]);
                  setScopeEvidence(null);
                  setScopeKeyword('');
                  setScopeOpen(true);
                  void loadScopeTables();
                }}
              />
            ) : null}
            {canCreateRealtime ? (
              <YakButton
                type="text"
                size="small"
                iconOnly
                title={intl.formatMessage({ id: 'pages.realtimeSync.page.create' })}
                className="!h-[30px] !w-[30px] !rounded-[8px] !border !border-[#e9ebef] !bg-white/90 !p-0 !text-[#7e838d] !shadow-[0_1px_3px_rgba(31,35,41,0.035)] hover:!text-[#4058c8]"
                icon={<Waypoints size={14} strokeWidth={1.9} />}
                onClick={openRealtimeCreate}
              />
            ) : null}

            {permissions.canTest ? (
              <YakButton
                type="text"
                size="small"
                iconOnly
                title={intl.formatMessage({
                  id: 'pages.datasource.card.testConnection',
                })}
                loading={testingId === currentId}
                disabled={Boolean(testingId) && testingId !== currentId}
                className="!h-[30px] !w-[30px] !rounded-[8px] !border !border-[#e9ebef] !bg-white/90 !p-0 !text-[#7e838d] !shadow-[0_1px_3px_rgba(31,35,41,0.035)] hover:!text-[#4058c8]"
                icon={<Unplug size={14} strokeWidth={1.9} />}
                onClick={() => onTestConnection(record)}
              />
            ) : null}

            {permissions.canReadSqlExecutions && record.id !== undefined && record.id !== null ? (
              <YakButton
                type="text"
                size="small"
                iconOnly
                title={intl.formatMessage({
                  id: 'pages.datasource.card.sqlExecutions',
                })}
                className="!h-[30px] !w-[30px] !rounded-[8px] !border !border-[#e9ebef] !bg-white/90 !p-0 !text-[#7e838d] !shadow-[0_1px_3px_rgba(31,35,41,0.035)] hover:!text-[#4058c8]"
                icon={<ScrollText size={14} strokeWidth={1.9} />}
                onClick={() =>
                  history.push(
                    `/sql-execution-audit?dataSourceId=${encodeURIComponent(currentId)}`,
                  )
                }
              />
            ) : null}

            {permissions.canUpdate ? (
              <YakButton
                type="text"
                size="small"
                iconOnly
                title={intl.formatMessage({ id: 'pages.datasource.card.edit' })}
                loading={editingId === currentId}
                disabled={Boolean(editingId) && editingId !== currentId}
                className="!h-[30px] !w-[30px] !rounded-[8px] !border !border-[#e9ebef] !bg-white/90 !p-0 !text-[#7e838d] !shadow-[0_1px_3px_rgba(31,35,41,0.035)] hover:!text-[#4058c8]"
                icon={<Pencil size={14} strokeWidth={1.9} />}
                onClick={() => onEdit(record)}
              />
            ) : null}

            {permissions.canDelete ? (
              <YakButton
                type="text"
                size="small"
                danger
                iconOnly
                title={intl.formatMessage({ id: 'pages.datasource.card.delete' })}
                className="!h-[30px] !w-[30px] !rounded-[8px] !border !border-[#e9ebef] !bg-white/90 !p-0 !shadow-[0_1px_3px_rgba(31,35,41,0.035)]"
                icon={<Trash2 size={14} strokeWidth={1.9} />}
                onClick={() => onDelete(record)}
              />
            ) : null}
          </div>
        ) : null}
      </div>

      <div
        className={[
          'relative z-[1] grid grid-cols-[1.05fr_0.8fr_1.15fr] border-t border-[#eef0f3] bg-white/75 px-[18px] py-[14px]',
          isListView
            ? 'items-center border-l border-t-0 max-xl:border-l-0 max-xl:border-t'
            : '',
        ]
          .filter(Boolean)
          .join(' ')}
      >
        <div className="flex min-w-0 flex-col gap-1.5 pr-3">
          <span className="text-[12px] leading-4 text-[#a0a4ad]">
            {intl.formatMessage({ id: 'pages.datasource.card.connectionStatus' })}
          </span>
          <DataSourceStatus status={record.connStatus} />
        </div>

        <div className="flex min-w-0 flex-col gap-1.5 border-l border-[#eff0f2] px-3">
          <span className="text-[12px] leading-4 text-[#a0a4ad]">
            {intl.formatMessage({ id: 'pages.datasource.card.type' })}
          </span>
          <strong className="truncate text-[12px] font-semibold leading-[18px] text-[#5c616b]">
            {String(record.dbType || '-')}
          </strong>
        </div>

        <div className="flex min-w-0 flex-col gap-1.5 border-l border-[#eff0f2] pl-3">
          <span className="text-[12px] leading-4 text-[#a0a4ad]">
            {intl.formatMessage({ id: 'pages.datasource.card.lastUpdated' })}
          </span>
          <strong className="flex min-w-0 items-center gap-1.5 truncate text-[12px] font-medium leading-[18px] text-[#737882]">
            <Clock3 size={11} strokeWidth={1.8} className="shrink-0 text-[#9ca0a9]" />
            <span className="truncate">{record.updateTime || '-'}</span>
          </strong>
        </div>
      </div>
      <Modal
        open={scopeOpen}
        title="来源结构证据预览（只读）"
        width={760}
        onCancel={() => setScopeOpen(false)}
        footer={<Button onClick={() => setScopeOpen(false)}>关闭</Button>}
        destroyOnHidden
      >
        <Alert type="info" showIcon
          message="仅核对当前已采集元数据，不读取业务数据行、执行 SQL 或自动开启 Agent 分析任务。" />
        <div className="my-3 flex gap-2">
          <Input.Search
            placeholder="按表名检索已采集元数据"
            value={scopeKeyword}
            onChange={(event) => setScopeKeyword(event.target.value)}
            onSearch={(value) => {
              setSelectedKeys([]);
              void loadScopeTables(value);
            }}
          />
          <Button disabled={!selectedKeys.length || scopeLoading}
            onClick={() => void previewScope()}>核验所选范围</Button>
        </div>
        {scopeError ? <Alert className="mb-3" showIcon type="warning"
          message={scopeError} /> : null}
        <Spin spinning={scopeLoading}>
          <div className="max-h-56 overflow-auto rounded border p-2">
            {scopeTables.length ? scopeTables.map((table) => (
              <div key={table.assetKey} className="py-1">
                <Checkbox checked={selectedKeys.includes(table.assetKey)}
                  disabled={!selectedKeys.includes(table.assetKey) && selectedKeys.length >= 20}
                  onChange={(event) => {
                    setScopeEvidence(null);
                    setSelectedKeys((current) => event.target.checked
                      ? [...current, table.assetKey]
                      : current.filter((value) => value !== table.assetKey));
                  }}>
                  {table.displayName || table.name || table.tableName || table.assetKey}
                  <Typography.Text type="secondary"> ({table.databaseName}.{table.schemaName}.{table.tableName})</Typography.Text>
                </Checkbox>
              </div>
            )) : <Typography.Text type="secondary">未找到当前源的已采集表，请先执行元数据采集。</Typography.Text>}
          </div>
        </Spin>
        {scopeEvidence ? (
          <section className="mt-4 rounded border p-3">
            <Typography.Text strong>已核验：{scopeEvidence.tables.length} 张表，
              {scopeEvidence.tables.reduce((sum, table) => sum + table.declaredColumnCount, 0)} 个字段</Typography.Text>
            <div className="mt-2 text-xs text-slate-500">
              采集任务：{scopeEvidence.collectJobId}；最近采集：{scopeEvidence.lastCollectAt}
            </div>
            <Typography.Paragraph className="mt-2" copyable={{ text: scopeEvidence.fingerprint }}
              ellipsis={{ rows: 1, expandable: true }}>
              来源指纹：{scopeEvidence.fingerprint}
            </Typography.Paragraph>
            <div className="max-h-52 overflow-auto text-sm">
              {scopeEvidence.tables.map((table) => (
                <div key={table.assetKey} className="border-t py-2">
                  <strong>{table.name}</strong> · {table.declaredColumnCount} 列
                  <div className="mt-1 text-xs text-slate-500">
                    {table.columns.map((column) => column.name).join('、')}
                  </div>
                </div>
              ))}
            </div>
          </section>
        ) : null}
      </Modal>
    </motion.article>
  );
};

export default DataSourceCard;
