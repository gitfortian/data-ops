import { history, useIntl, useSearchParams } from '@umijs/max';
import { Alert, Button, message, Modal, Pagination, Spin } from 'antd';
import { motion } from 'framer-motion';
import { useEffect, useRef, useState } from 'react';
import { useSecurityProject } from '@/contexts/SecurityProjectContext';
import usePermissionAccess from '@/hooks/usePermissionAccess';
import { getDataSource, listAllDataSources } from '@/services/data-source/api';
import { parseLiveSourceReturn } from '@/utils/liveSourceOdsJourney';

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
  const [params] = useSearchParams();
  const [workspaceRecord, setWorkspaceRecord] = useState<DataSourceRecord | null>(null);
  const [returnProblem, setReturnProblem] = useState('');
  const returnKey = params.toString();
  const returnHint = parseLiveSourceReturn(params);
  const returnRequested = params.has('sourceId');

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

  useEffect(() => {
    if (!returnRequested) { setReturnProblem(''); return; }
    if (!returnHint) {
      setReturnProblem('来源坐标无效，请从数据源列表重新选择，禁止按同名表自动恢复。');
      return;
    }
    let active = true;
    setWorkspaceRecord(null);
    setReturnProblem('');
    // This URL is a hint only. Both the scoped list and the exact record
    // must independently confirm the DataSource Owner identity in this Project.
    void Promise.all([
      getDataSource(returnHint.dataSourceId),
      listAllDataSources(),
    ]).then(([record, available]) => {
      if (!active) return;
      const exists = (available.bizData ?? []).some((item) =>
        Number(item.id) === returnHint.dataSourceId);
      if (!record || Number(record.id) !== returnHint.dataSourceId || !exists) {
        throw new Error('当前项目无法核验原数据源；请从列表手动核查，不跨项目显示结构。');
      }
      setWorkspaceRecord(record);
    }).catch((error) => {
      if (active) setReturnProblem(error instanceof Error ? error.message : '无法核验来源，请手动打开数据源。');
    });
    return () => { active = false; };
  }, [returnKey]);

  const closeWorkspace = () => {
    setWorkspaceRecord(null);
    if (returnRequested) {
      const cleaned = new URLSearchParams(params.toString());
      ['sourceId', 'database', 'schema', 'table'].forEach((key) => cleaned.delete(key));
      history.replace(`/data-source${cleaned.toString() ? `?${cleaned}` : ''}`);
    }
  };

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
              {returnProblem ? (
                <Alert type="warning" showIcon message="未能恢复来源表"
                  description={returnProblem}
                  action={<Button onClick={() => history.replace('/data-source')}>查看数据源列表</Button>} />
              ) : null}
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
          initialTable={returnHint && Number(workspaceRecord.id) === returnHint.dataSourceId
            ? { database: returnHint.database, schema: returnHint.schema, table: returnHint.table }
            : undefined}
          onClose={closeWorkspace}
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
