import { BRAND_THEME } from '@/styles/brand';
import { history, useParams } from '@umijs/max';
import { ConfigProvider, Spin } from 'antd';

import { useMonitorDetailPage } from './hooks/useMonitorDetailPage';
import MonitorListTab from './MonitorListTab';
import OperationLogDrawer from './OperationLogDrawer';
import QualityReportTab from './QualityReportTab';
import RuleManagementTab from './RuleManagementTab';
import WorkspaceHeader from './WorkspaceHeader';

const MonitorDetailPage = () => {
  const params = useParams<{ id: string }>();
  const detail = useMonitorDetailPage(params.id);

  const handleEdit = () => {
    history.push(`/data-quality/monitor/${params.id}/edit`);
  };

  return (
    <ConfigProvider theme={BRAND_THEME}>
      <div className="flex h-[calc(100vh-64px)] min-h-[620px] flex-col overflow-hidden bg-white">
        <WorkspaceHeader
          workspace={detail.workspace}
          activeTab={detail.activeTab}
          onTabChange={detail.setActiveTab}
          onBack={() => history.push('/data-quality/table-config')}
          onEdit={handleEdit}
        />

        <Spin
          spinning={detail.loading}
          wrapperClassName="min-h-0 flex-1 overflow-hidden "
        >
          {detail.workspace ? (
            <div className="flex h-full min-h-0 flex-col">
              {detail.activeTab === 'rules' ? (
                <RuleManagementTab
                  workspace={detail.workspace}
                  running={detail.running}
                  onRun={() => void detail.run()}
                  onOpenLog={detail.openLog}
                  onRefresh={detail.loadWorkspace}
                  onRemoveMonitor={detail.removeMonitor}
                  onEdit={handleEdit}
                />
              ) : null}

              {detail.activeTab === 'monitors' ? (
                <MonitorListTab
                  workspace={detail.workspace}
                  running={detail.running}
                  onRun={() => void detail.run()}
                  onRefresh={detail.loadWorkspace}
                  onRemove={detail.removeMonitor}
                  onOpenLog={detail.openLog}
                  onEdit={handleEdit}
                />
              ) : null}

              {detail.activeTab === 'report' ? (
                <QualityReportTab
                  report={detail.report}
                  loading={detail.reportLoading}
                  reportDate={detail.reportDate}
                  onDateChange={detail.setReportDate}
                />
              ) : null}
            </div>
          ) : null}
        </Spin>

        <OperationLogDrawer
          open={detail.logOpen}
          loading={detail.logLoading}
          data={detail.operationLog}
          onClose={() => detail.setLogOpen(false)}
          onPageChange={(current, pageSize) =>
            void detail.loadOperationLog(current, pageSize)
          }
        />
      </div>
    </ConfigProvider>
  );
};

export default MonitorDetailPage;
