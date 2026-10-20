import DatabaseIcons from '@/components/data-source/icons/DatabaseIcons';
import type { DataSourceRecord } from '@/services/data-source';
import { formatMetadataTime } from '@/services/metadata/presentation';
import { Button, Dropdown, Table, Tag, Tooltip, type MenuProps } from 'antd';
import { history } from '@umijs/max';
import { MoreHorizontal, Search, Unplug } from 'lucide-react';
import type { ColumnsType } from 'antd/es/table';
import { getEnvironmentTagConfigMap } from '../constants';
import { useIntl } from '@umijs/max';
import type { DataSourcePermissions } from '../types';
import { dataSourceRecordKey } from '../types';
import DataSourceStatus from './DataSourceStatus';

/**
 * Only display host:port from a strict, noncredential JDBC authority form.
 * Never show raw JDBC URLs, userinfo, query parameters or connection secrets.
 * Unknown connector syntaxes deliberately display —.
 */
export const publicJdbcEndpoint = (jdbcUrl?: string): string => {
  if (!jdbcUrl || jdbcUrl.length > 2048) return '—';
  const match = /^jdbc:(?:mysql|postgresql|mariadb|clickhouse|doris):\/\/([A-Za-z0-9.-]+)(?::([0-9]{1,5}))?(?:\/[A-Za-z0-9_-]+)?$/i.exec(jdbcUrl.trim());
  if (!match || match[1].includes('..')) return '—';
  const port = match[2] ? Number(match[2]) : undefined;
  if (port != null && (port < 1 || port > 65535)) return '—';
  return match[1] + (port != null ? ':' + port : '');
};

interface Props {
  records: DataSourceRecord[];
  loading: boolean;
  permissions: DataSourcePermissions;
  testingId: string;
  editingId: string;
  onEdit: (record: DataSourceRecord) => void;
  onDelete: (record: DataSourceRecord) => void;
  onTestConnection: (record: DataSourceRecord) => void;
}
export default function DataSourceTable({
  records, loading, permissions, testingId, editingId,
  onEdit, onDelete, onTestConnection,
}: Props) {
  const intl = useIntl();
  const environmentMap = getEnvironmentTagConfigMap(intl);
  const cols: ColumnsType<DataSourceRecord> = [
    {
      title: '数据源名称', dataIndex: 'name', key: 'name', width: 210,
      render: (_: unknown, item) => (
        <span className="flex min-w-0 items-center gap-2">
          <DatabaseIcons dbType={item.dbType} width="22px" height="22px" />
          <span className="truncate font-semibold text-[#344054]" title={item.name}>{item.name || '未命名数据源'}</span>
        </span>
      ),
    },
    { title: '数据库类型', dataIndex: 'dbType', key: 'dbType', width: 125,
      render: (value?: string) => <span>{value || '—'}</span> },
    { title: '所属环境', dataIndex: 'environment', key: 'environment', width: 110,
      render: (_: unknown, item) => {
        const setting = environmentMap[item.environment || ''];
        return <Tag color={setting?.backgroundColor}>{setting?.text || item.environmentName || item.environment || '未分类'}</Tag>;
      } },
    { title: '连接状态', dataIndex: 'connStatus', key: 'connStatus', width: 135,
      render: (value?: string) => <DataSourceStatus status={value} /> },
    { title: '连接地址', dataIndex: 'jdbcUrl', key: 'jdbcUrl', width: 185,
      render: (jdbc?: string) => {
        const safe = publicJdbcEndpoint(jdbc);
        return <Tooltip title={safe === '—' ? '未提供可安全展示的地址摘要' : undefined}><span className="text-[#667085]">{safe}</span></Tooltip>;
      } },
    { title: '配置更新时间', dataIndex: 'updateTime', key: 'updateTime', width: 180,
      render: (time?: string) => <span className="whitespace-nowrap">{formatMetadataTime(time)}</span> },
    { title: '备注', dataIndex: 'remark', key: 'remark', ellipsis: true,
      render: (value?: string) => <Tooltip title={value || undefined}><span>{value || '—'}</span></Tooltip> },
    { title: '操作', key: 'actions', width: 235, fixed: 'right',
      render: (_: unknown, item) => {
        const id = dataSourceRecordKey(item.id);
        const entries: MenuProps['items'] = [
          ...(permissions.canUpdate ? [{ key: 'edit', label: '编辑连接',
            disabled: Boolean(editingId), onClick: () => onEdit(item) }] : []),
          ...(permissions.canReadSqlExecutions && id ? [{ key: 'audit', label: 'SQL 执行记录',
            onClick: () => history.push('/sql-execution-audit?dataSourceId=' + encodeURIComponent(id)) }] : []),
          ...(permissions.canDelete ? [{ key: 'delete', label: '删除数据源', danger: true,
            onClick: () => onDelete(item) }] : []),
        ];
        return <div className="flex items-center justify-end gap-1">
          <Button type="link" icon={<Search size={13} />} disabled={!id}
            onClick={() => history.push('/data-source/explore?sourceId=' + encodeURIComponent(id))}>探查结构</Button>
          {permissions.canTest ? <Button type="link" icon={<Unplug size={13} />}
            loading={testingId === id} disabled={Boolean(testingId) && testingId !== id}
            onClick={() => onTestConnection(item)}>检测连接</Button> : null}
          {entries.length ? <Dropdown menu={{ items: entries }} trigger={['click']}>
            <Button type="text" aria-label={(item.name || '数据源') + '更多操作'} icon={<MoreHorizontal size={17} />} />
          </Dropdown> : null}
        </div>;
      } },
  ];
  return <div className="source-table-wrapper" aria-label="数据源列表">
    <Table<DataSourceRecord> className="source-table" columns={cols} dataSource={records}
      loading={loading} rowKey={(record) => dataSourceRecordKey(record.id)}
      pagination={false} scroll={{ x: 1250 }} size="middle" />
  </div>;
}
