import { history } from '@umijs/max';
import { useCallback, useEffect, useRef } from 'react';

import {
  SecurityPagination,
  SecurityQueryTable,
} from '@/components/security';
import type { OperationLog } from '@/services/security/operationLogs';

import OperationLogDetailDrawer, {
  type OperationLogDetailDrawerRef,
} from './OperationLogDetailDrawer';
import OperationLogFilterBar from './OperationLogFilterBar';
import { useOperationLogColumns } from '../hooks/useOperationLogColumns';
import { useOperationLogs } from '../hooks/useOperationLogs';

/** Existing Yak Security HTTP/page operation log preserved as a secondary Audit Center view. */
export default function SecurityOperationLogsPanel() {
  const detailRef = useRef<OperationLogDetailDrawerRef>(null);
  const {
    logs,
    options,
    isLoading,
    pagination,
    refreshLogs,
    searchLogs,
    changePage,
  } = useOperationLogs();

  useEffect(() => {
    const value = new URLSearchParams(history.location.search).get('messageLogId');
    if (!value) return;

    const logId = Number(value);
    if (Number.isSafeInteger(logId) && logId > 0) {
      void detailRef.current?.open(logId);
    }
  }, []);

  const showDetail = useCallback((log: OperationLog) => {
    void detailRef.current?.open(log.id);
  }, []);

  const columns = useOperationLogColumns({ onDetail: showDetail });

  return (
    <div className="flex h-full min-h-0 flex-1 flex-col">
      <div className="shrink-0">
        <OperationLogFilterBar
          options={options}
          loading={isLoading}
          onSearch={searchLogs}
          onRefresh={refreshLogs}
        />
      </div>

      <div className="min-h-0 flex-1 overflow-y-auto overflow-x-hidden">
        <SecurityQueryTable<OperationLog>
          rowKey="id"
          columns={columns}
          dataSource={logs}
          loading={isLoading}
          pagination={false}
          bordered
          scroll={{ x: 'max-content' }}
        />
      </div>

      {pagination.total > 0 ? (
        <div className="shrink-0 pt-3">
          <SecurityPagination
            current={pagination.current}
            pageSize={pagination.pageSize}
            total={pagination.total}
            disabled={isLoading}
            bordered={false}
            onChange={changePage}
          />
        </div>
      ) : null}

      <OperationLogDetailDrawer ref={detailRef} />
    </div>
  );
}
