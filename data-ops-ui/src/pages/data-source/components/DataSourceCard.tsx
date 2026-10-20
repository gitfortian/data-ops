import { YakButton } from '@/components/ui';
import {
  Button,
  Dropdown,
  Tooltip,
  type MenuProps,
} from 'antd';
import type { DataSourceRecord } from '@/services/data-source';
import { history, useIntl } from '@umijs/max';
import { motion } from 'framer-motion';
import {
  MoreHorizontal,
  ArrowUpRight,
  Pencil,
  ScrollText,
  Trash2,
  Unplug,
} from 'lucide-react';

import DatabaseIcons from '@/components/data-source/icons/DatabaseIcons';
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
  const environmentTagConfigMap = getEnvironmentTagConfigMap(intl);
  const environmentConfig = environmentTagConfigMap[record.environment || ''] || {
    text: record.environmentName || intl.formatMessage({ id: 'pages.datasource.environment.uncategorized' }),
    color: '#667085',
    backgroundColor: '#f2f4f7',
    icon: null,
  };
  const currentId = dataSourceRecordKey(record.id);
  const actionAvailable =
    permissions.canTest ||
    permissions.canUpdate ||
    permissions.canDelete ||
    permissions.canReadSqlExecutions;
  const isListView = viewMode === 'list';

  const menuItems: MenuProps['items'] = [
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
    </motion.article>
  );
};

export default DataSourceCard;
