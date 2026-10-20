import { history, useIntl, useSearchParams } from '@umijs/max';
import { Alert, Button, message, Modal, Pagination, Spin } from 'antd';
import { useEffect, useRef } from 'react';
import { useSecurityProject } from '@/contexts/SecurityProjectContext';
import usePermissionAccess from '@/hooks/usePermissionAccess';
import { parseLiveSourceReturn } from '@/utils/liveSourceOdsJourney';

import AddOrEditDataSourceModal from './components/AddOrEditDataSourceModal';
import DataSourceEmptyState from './components/DataSourceEmptyState';
import DataSourcePageHeader from './components/DataSourcePageHeader';
import DataSourceSummaryCards from './components/DataSourceSummaryCards';
import DataSourceTable from './components/DataSourceTable';
import DataSourceToolbar from './components/DataSourceToolbar';
import './index.less';
import { DATA_SOURCE_PAGE_SIZE_OPTIONS } from './constants';
import { useDataSourcePage } from './hooks/useDataSourcePage';
import type { DataSourceModalRef, DataSourceRecord } from './types';
import { DataSourceOperateType } from './types';

const { confirm } = Modal;

function DataSourcePage() {
  const intl = useIntl();
  const modalRef = useRef<DataSourceModalRef>(null);
  const [params] = useSearchParams();
  const returnRequested = params.has('sourceId');
  const exact = parseLiveSourceReturn(params);
  const invalidLegacyReturn = returnRequested && !exact;

  const {
    loading, records, summary, loadError, summaryUnavailable, pagination,
    keyword, dbType, environment, hasActiveFilters, permissions, testingId, editingId,
    setKeyword, setDbType, setEnvironment, resetFilters, changePage, refresh,
    loadRecordForEdit, removeRecord, testRecord,
  } = useDataSourcePage();

  // Legacy ODS return URLs still work. Only structurally valid complete
  // coordinates transfer; the dedicated explorer verifies Project membership.
  useEffect(() => {
    if (!returnRequested || !exact) return;
    history.replace('/data-source/explore?' + params.toString());
  }, [returnRequested, exact?.dataSourceId, exact?.database, exact?.schema, exact?.table, params.toString()]);

  const handleCreate = () => {
    if (!permissions.canCreate) return;

    modalRef.current?.open({
      operateType: DataSourceOperateType.Create,
      onSuccess: refresh,
    });
  };

  const handleEdit = async (record: DataSourceRecord) => {
    try {
      const detail = await loadRecordForEdit(record);
      if (!detail) return;

      modalRef.current?.open({
        operateType: DataSourceOperateType.Edit,
        currentRecord: detail,
        onSuccess: refresh,
      });
    } catch {
      // The shared request layer owns request error feedback.
    }
  };

  const handleDelete = (record: DataSourceRecord) => {
    if (!permissions.canDelete) return;

    confirm({
      title: intl.formatMessage({ id: 'pages.datasource.delete.confirmTitle' }),
      centered: true,
      content: (
        <span>
          {intl.formatMessage({ id: 'pages.datasource.delete.content' }, { name: record.name || '-' })}
          <br />
          {intl.formatMessage({ id: 'pages.datasource.delete.warning' })}
        </span>
      ),
      okText: intl.formatMessage({ id: 'pages.datasource.delete.okText' }),
      cancelText: intl.formatMessage({
        id: 'pages.datasource.delete.cancelText',
      }),
      okType: 'primary',
      okButtonProps: { size: 'small', danger: true },
      cancelButtonProps: { size: 'small' },
      maskClosable: true,
      async onOk() {
        if (record.id === undefined || record.id === null) {
          message.error(intl.formatMessage({ id: 'pages.datasource.delete.idMissing' }));
          return;
        }

        try {
          const deleted = await removeRecord(record.id);
          if (deleted) {
            message.success(intl.formatMessage({ id: 'pages.datasource.delete.success' }));
          }
        } catch {
          // The shared request layer owns request error feedback.
        }
      },
    });
  };

  const handleTestConnection = async (record: DataSourceRecord) => {
    try {
      const connected = await testRecord(record);
      if (connected) {
        message.success(intl.formatMessage({ id: 'pages.datasource.test.success' }));
      }
    } catch {
      // The shared request layer owns request error feedback.
    }
  };


  return <main className="source-page" data-testid="data-source-management">
    <DataSourcePageHeader canCreate={permissions.canCreate} onCreate={handleCreate} />
    <DataSourceSummaryCards summary={summary} unavailable={summaryUnavailable} />
    <DataSourceToolbar
      environment={environment} dbType={dbType} keyword={keyword}
      hasActiveFilters={hasActiveFilters}
      onEnvironmentChange={setEnvironment} onDbTypeChange={setDbType}
      onKeywordChange={setKeyword} onReset={resetFilters} />
    {invalidLegacyReturn ? <Alert className="!mb-3" showIcon type="warning"
      message="原始来源坐标不完整" description="请进入「数据探查」重新选择源表，不能按同名表恢复。"
      action={<Button onClick={() => history.push('/data-source/explore')}>进入数据探查</Button>} /> : null}
    {loadError ? <Alert className="!mb-3" type="error" showIcon message="暂时无法读取数据源"
      description="请重新加载当前项目的连接。" action={<Button onClick={refresh}>重新加载</Button>} /> : null}
    <div className="source-page-content">
      <Spin spinning={loading}>
        <DataSourceTable records={records} loading={loading} permissions={permissions}
          testingId={testingId} editingId={editingId}
          onEdit={(item) => void handleEdit(item)} onDelete={handleDelete}
          onTestConnection={(item) => void handleTestConnection(item)} />
        {!loading && !loadError && records.length === 0 ? (
          <DataSourceEmptyState filtered={hasActiveFilters} canCreate={permissions.canCreate}
            onReset={resetFilters} onCreate={handleCreate} />
        ) : null}
      </Spin>
      {pagination.total > 0 ? <footer className="source-list-pagination">
        <Pagination current={pagination.pageNo} pageSize={pagination.pageSize}
          total={pagination.total} showSizeChanger showQuickJumper
          pageSizeOptions={DATA_SOURCE_PAGE_SIZE_OPTIONS} disabled={loading}
          showTotal={(total, range) => intl.formatMessage({
            id: 'pages.datasource.pagination.total',
          }, { start: range[0], end: range[1], total })}
          onChange={changePage} />
      </footer> : null}
    </div>
    <AddOrEditDataSourceModal ref={modalRef} />
  </main>;
}

export default function ScopedDataSourcePage() {
  const { currentProject } = useSecurityProject();
  const { permissionCodes } = usePermissionAccess();
  return <DataSourcePage key={JSON.stringify([currentProject?.id, permissionCodes])} />;
}
