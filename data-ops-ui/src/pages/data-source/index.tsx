import { useIntl } from '@umijs/max';
import { Alert, Button, message, Modal, Pagination, Spin } from 'antd';
import { motion } from 'framer-motion';
import { useRef, useState } from 'react';
import { useSecurityProject } from '@/contexts/SecurityProjectContext';
import usePermissionAccess from '@/hooks/usePermissionAccess';

import AddOrEditDataSourceModal from './components/AddOrEditDataSourceModal';
import DataSourceCard from './components/DataSourceCard';
import DataSourceEmptyState from './components/DataSourceEmptyState';
import DataSourcePageHeader from './components/DataSourcePageHeader';
import DataSourceSummaryCards from './components/DataSourceSummaryCards';
import DataSourceToolbar from './components/DataSourceToolbar';
import DataSourceWorkspace from './components/DataSourceWorkspace';
import './index.less';
import { DATA_SOURCE_PAGE_SIZE_OPTIONS, PAGE_ANIMATION } from './constants';
import { useDataSourcePage } from './hooks/useDataSourcePage';
import type { DataSourceModalRef, DataSourceRecord } from './types';
import { DataSourceOperateType, dataSourceRecordKey } from './types';

const { confirm } = Modal;

const DataSourcePage = () => {
  const intl = useIntl();
  const modalRef = useRef<DataSourceModalRef>(null);
  const [workspaceRecord, setWorkspaceRecord] = useState<DataSourceRecord | null>(null);

  const {
    loading,
    records,
    summary,
    loadError,
    summaryUnavailable,
    pagination,
    keyword,
    dbType,
    environment,
    viewMode,
    hasActiveFilters,
    permissions,
    testingId,
    editingId,
    setKeyword,
    setDbType,
    setEnvironment,
    setViewMode,
    resetFilters,
    changePage,
    refresh,
    loadRecordForEdit,
    removeRecord,
    testRecord,
  } = useDataSourcePage();

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

  return (
    <>
      <div className="min-h-[calc(100dvh-64px)] bg-[#f7f8fa] text-[#242731]">
        <motion.main
          initial="hidden"
          animate="visible"
          variants={PAGE_ANIMATION.sectionStagger}
          // className="px-4 pb-4 pt-4"
        >
          <motion.section variants={PAGE_ANIMATION.fadeUp} className="source-page">
            <div className="space-y-5">
              <DataSourcePageHeader canCreate={permissions.canCreate} onCreate={handleCreate} />

              <DataSourceSummaryCards summary={summary} unavailable={summaryUnavailable} />

              <DataSourceToolbar
                environment={environment}
                dbType={dbType}
                keyword={keyword}
                viewMode={viewMode}
                hasActiveFilters={hasActiveFilters}
                onEnvironmentChange={setEnvironment}
                onDbTypeChange={setDbType}
                onKeywordChange={setKeyword}
                onViewModeChange={setViewMode}
                onReset={resetFilters}
              />
            </div>

            <div className="source-page-content">
              {loadError ? (
                <Alert
                  type="error"
                  showIcon
                  message="暂时无法读取数据源"
                  description="请重试后查看当前项目的数据源。"
                  action={<Button onClick={refresh}>重新加载</Button>}
                />
              ) : null}
              <Spin spinning={loading}>
                <motion.section
                  variants={PAGE_ANIMATION.cardStagger}
                  initial="hidden"
                  animate="visible"
                  className={viewMode === 'list' ? 'source-records source-records-list' : 'source-records'}
                >
                  {records.map((record, index) => (
                    <DataSourceCard
                      key={dataSourceRecordKey(record.id) || `${record.name || 'data-source'}-${index}`}
                      record={record}
                      viewMode={viewMode}
                      permissions={permissions}
                      testingId={testingId}
                      editingId={editingId}
                      onEdit={(item) => void handleEdit(item)}
                      onDelete={handleDelete}
                      onOpenWorkspace={setWorkspaceRecord}
                      onTestConnection={(item) => void handleTestConnection(item)}
                    />
                  ))}
                </motion.section>

                {!loading && !loadError && records.length === 0 ? (
                  <div className="mt-6">
                    <DataSourceEmptyState
                      filtered={hasActiveFilters}
                      canCreate={permissions.canCreate}
                      onReset={resetFilters}
                      onCreate={handleCreate}
                    />
                  </div>
                ) : null}
              </Spin>

              {pagination.total > 0 ? (
                <motion.footer variants={PAGE_ANIMATION.fadeUp} className="mt-auto flex shrink-0 justify-end pt-6">
                  <Pagination
                    current={pagination.pageNo}
                    pageSize={pagination.pageSize}
                    total={pagination.total}
                    showSizeChanger
                    showQuickJumper
                    pageSizeOptions={DATA_SOURCE_PAGE_SIZE_OPTIONS}
                    disabled={loading}
                    showTotal={(total, range) =>
                      intl.formatMessage(
                        { id: 'pages.datasource.pagination.total' },
                        { start: range[0], end: range[1], total },
                      )
                    }
                    onChange={changePage}
                  />
                </motion.footer>
              ) : null}
            </div>
          </motion.section>
        </motion.main>
      </div>

      <AddOrEditDataSourceModal ref={modalRef} />
      {workspaceRecord ? (
        <DataSourceWorkspace
          key={dataSourceRecordKey(workspaceRecord.id)}
          record={
            records.find((item) => dataSourceRecordKey(item.id) === dataSourceRecordKey(workspaceRecord.id)) ||
            workspaceRecord
          }
          permissions={permissions}
          testingId={testingId}
          onClose={() => setWorkspaceRecord(null)}
          onTestConnection={(record) => void handleTestConnection(record)}
          onEdit={(record) => void handleEdit(record)}
        />
      ) : null}
    </>
  );
};

export default function ScopedDataSourcePage() {
  const { currentProject } = useSecurityProject();
  const { permissionCodes } = usePermissionAccess();
  return <DataSourcePage key={JSON.stringify([currentProject?.id, permissionCodes])} />;
}
