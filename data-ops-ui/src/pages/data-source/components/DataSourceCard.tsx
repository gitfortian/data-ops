import { YakButton } from '@/components/ui';
import {
  Alert,
  Button,
  Checkbox,
  Input,
  Modal,
  Spin,
  Typography,
  Dropdown,
  Tooltip,
  message,
  type MenuProps,
} from 'antd';
import { useState } from 'react';
import { previewSourceSemanticEvidence, searchMetadata } from '@/services/metadata/api';
import type { SourceSemanticEvidence } from '@/services/metadata/api';
import type { MetadataSearchItem } from '@/services/metadata/types';
import type { DataSourceRecord } from '@/services/data-source';
import { history, useAccess, useIntl } from '@umijs/max';
import { motion } from 'framer-motion';
import {
  MoreHorizontal,
  ArrowUpRight,
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
import { formatMetadataTime } from '@/services/metadata/presentation';

interface DataSourceCardProps {
  record: DataSourceRecord;
  viewMode: DataSourceViewMode;
  permissions: DataSourcePermissions;
  testingId: string;
  editingId: string;
  onOpenWorkspace: (record: DataSourceRecord) => void;
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
  onOpenWorkspace,
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
  const environmentConfig = environmentTagConfigMap[record.environment || ''] || {
    text: record.environmentName || intl.formatMessage({ id: 'pages.datasource.environment.uncategorized' }),
    color: '#667085',
    backgroundColor: '#f2f4f7',
    icon: null,
  };
  const currentId = dataSourceRecordKey(record.id);
  const canCreateRealtime =
    Boolean(currentId) &&
    String(record.dbType || '').toUpperCase() === 'MYSQL' &&
    access.hasPermission('task:realtime:create');
  const canStartSourceSemantic =
    Boolean(currentId) && access.hasPermission('agent:chat:run') && access.hasPermission('resource:data-source:read');
  const canPreviewSource =
    Boolean(currentId) &&
    Number.isSafeInteger(Number(currentId)) &&
    Number(currentId) > 0 &&
    access.hasPermission('data-metadata:read');
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
      setScopeTables(
        result.items.filter(
          (item) =>
            item.typeName === 'table' &&
            item.providerType === 'HARVESTED' &&
            String(item.dataSourceId) === currentId &&
            typeof item.assetKey === 'string',
        ),
      );
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

  const menuItems: MenuProps['items'] = [
    ...(canPreviewSource
      ? [
          {
            key: 'collect',
            label: '管理采集任务',
            icon: <ScanSearch size={16} />,
            onClick: () => history.push(`/data-metadata/collect?dataSourceId=${encodeURIComponent(currentId)}`),
          },
        ]
      : []),
    ...(canCreateRealtime
      ? [
          {
            key: 'realtime',
            label: intl.formatMessage({ id: 'pages.realtimeSync.page.create' }),
            icon: <Waypoints size={16} />,
            onClick: openRealtimeCreate,
          },
        ]
      : []),
    ...(canStartSourceSemantic
      ? [
          {
            key: 'semantic',
            label: '理解来源业务',
            icon: <BrainCircuit size={16} />,
            onClick: () => history.push(sourceTaskPath(currentId)),
          },
        ]
      : []),
    ...(canPreviewSource
      ? [
          {
            key: 'evidence',
            label: '核验表结构证据',
            icon: <ScanSearch size={16} />,
            onClick: () => {
              setSelectedKeys([]);
              setScopeEvidence(null);
              setScopeKeyword('');
              setScopeOpen(true);
              void loadScopeTables();
            },
          },
        ]
      : []),
    ...(permissions.canTest
      ? [
          {
            key: 'test',
            label:
              testingId === currentId
                ? '正在检测连接…'
                : intl.formatMessage({ id: 'pages.datasource.card.testConnection' }),
            icon: <Unplug size={16} />,
            disabled: Boolean(testingId),
            onClick: () => onTestConnection(record),
          },
        ]
      : []),
    ...(permissions.canReadSqlExecutions && currentId
      ? [
          {
            key: 'sql',
            label: intl.formatMessage({ id: 'pages.datasource.card.sqlExecutions' }),
            icon: <ScrollText size={16} />,
            onClick: () => history.push(`/sql-execution-audit?dataSourceId=${encodeURIComponent(currentId)}`),
          },
        ]
      : []),
    ...(permissions.canUpdate
      ? [
          {
            key: 'edit',
            label: intl.formatMessage({ id: 'pages.datasource.card.edit' }),
            icon: <Pencil size={16} />,
            disabled: Boolean(editingId),
            onClick: () => onEdit(record),
          },
        ]
      : []),
    ...(permissions.canDelete
      ? [
          {
            key: 'delete',
            danger: true,
            label: intl.formatMessage({ id: 'pages.datasource.card.delete' }),
            icon: <Trash2 size={16} />,
            onClick: () => onDelete(record),
          },
        ]
      : []),
  ];

  return (
    <motion.article variants={PAGE_ANIMATION.fadeUp} className={`source-card${isListView ? ' source-card-list' : ''}`}>
      <div className="source-card-heading">
        <div className="source-card-icon">
          <DatabaseIcons dbType={record.dbType} width="28" height="28" />
        </div>
        <div className="source-card-identity">
          <button
            type="button"
            className="source-card-name"
            disabled={!currentId}
            onClick={() => onOpenWorkspace(record)}
          >
            {record.name || intl.formatMessage({ id: 'pages.datasource.card.unnamed' })}
          </button>
          <div className="source-card-meta">
            <span>{String(record.dbType || '—')}</span>
            <span
              className="source-card-environment"
              style={{ color: environmentConfig.color, background: environmentConfig.backgroundColor }}
            >
              {environmentConfig.text}
            </span>
          </div>
        </div>
        {actionAvailable ? (
          <Dropdown trigger={['click']} menu={{ items: menuItems }} placement="bottomRight">
            <Button
              type="text"
              className="source-card-menu"
              aria-label={`${record.name || '数据源'}的更多操作`}
              icon={<MoreHorizontal size={18} />}
            />
          </Dropdown>
        ) : null}
      </div>
      {record.remark ? (
        <p className="source-card-description" title={record.remark}>
          {record.remark}
        </p>
      ) : null}
      <div className="source-card-facts">
        <span>连接</span>
        <DataSourceStatus status={record.connStatus} />
      </div>
      <div className="source-card-footer">
        <div className="source-card-time">
          <span>配置更新</span>
          <time>{formatMetadataTime(record.updateTime)}</time>
        </div>
        <Tooltip title={!currentId ? '数据源标识缺失' : undefined}>
          <YakButton
            type="primary"
            disabled={!currentId}
            onClick={() => onOpenWorkspace(record)}
            icon={<ArrowUpRight size={15} />}
          >
            探查结构
          </YakButton>
        </Tooltip>
      </div>
      <Modal
        open={scopeOpen}
        title="来源结构证据预览（只读）"
        width={760}
        onCancel={() => setScopeOpen(false)}
        footer={<Button onClick={() => setScopeOpen(false)}>关闭</Button>}
        destroyOnHidden
      >
        <Alert
          type="info"
          showIcon
          message="仅核对当前已采集元数据，不读取业务数据行、执行 SQL 或自动开启 Agent 分析任务。"
        />
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
          <Button disabled={!selectedKeys.length || scopeLoading} onClick={() => void previewScope()}>
            核验所选范围
          </Button>
        </div>
        {scopeError ? <Alert className="mb-3" showIcon type="warning" message={scopeError} /> : null}
        <Spin spinning={scopeLoading}>
          <div className="max-h-56 overflow-auto rounded border p-2">
            {scopeTables.length ? (
              scopeTables.map((table) => (
                <div key={table.assetKey} className="py-1">
                  <Checkbox
                    checked={selectedKeys.includes(table.assetKey)}
                    disabled={!selectedKeys.includes(table.assetKey) && selectedKeys.length >= 20}
                    onChange={(event) => {
                      setScopeEvidence(null);
                      setSelectedKeys((current) =>
                        event.target.checked
                          ? [...current, table.assetKey]
                          : current.filter((value) => value !== table.assetKey),
                      );
                    }}
                  >
                    {table.displayName || table.name || table.tableName || table.assetKey}
                    <Typography.Text type="secondary">
                      {' '}
                      ({table.databaseName}.{table.schemaName}.{table.tableName})
                    </Typography.Text>
                  </Checkbox>
                </div>
              ))
            ) : (
              <Typography.Text type="secondary">未找到当前源的已采集表，请先执行元数据采集。</Typography.Text>
            )}
          </div>
        </Spin>
        {scopeEvidence ? (
          <section className="mt-4 rounded border p-3">
            <Typography.Text strong>
              已核验：{scopeEvidence.tables.length} 张表，
              {scopeEvidence.tables.reduce((sum, table) => sum + table.declaredColumnCount, 0)} 个字段
            </Typography.Text>
            <div className="mt-2 text-xs text-slate-500">
              采集任务：{scopeEvidence.collectJobId}；最近采集：{scopeEvidence.lastCollectAt}
            </div>
            <Typography.Paragraph
              className="mt-2"
              copyable={{ text: scopeEvidence.fingerprint }}
              ellipsis={{ rows: 1, expandable: true }}
            >
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
