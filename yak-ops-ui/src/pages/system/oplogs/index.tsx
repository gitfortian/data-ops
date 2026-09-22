import { SafetyCertificateOutlined } from '@ant-design/icons';
import { history } from '@umijs/max';
import { useMemo, useState } from 'react';

import YakTab from '@/components/YakTab';

import SystemManagementPage from '../components/SystemManagementPage';
import BusinessAuditPanel from './components/BusinessAuditPanel';
import SecurityOperationLogsPanel from './components/SecurityOperationLogsPanel';

export default function AuditCenterPage() {
  const initialTab = useMemo(
    () =>
      new URLSearchParams(history.location.search).has('messageLogId')
        ? 'security'
        : 'business',
    [],
  );
  const [activeTab, setActiveTab] = useState(initialTab);

  return (
    <SystemManagementPage
      title="审计中心"
      titleId="system-operation-logs-title"
      icon={<SafetyCertificateOutlined className="text-slate-500" />}
      className="h-[calc(100vh-64px)] min-h-0 overflow-hidden"
    >
      <div className="flex min-h-0 flex-1 flex-col bg-white px-4 pt-1">
        <YakTab
          activeKey={activeTab}
          onChange={setActiveTab}
          className="flex min-h-0 flex-1 flex-col [&_.ant-tabs-content]:h-full [&_.ant-tabs-content-holder]:min-h-0 [&_.ant-tabs-content-holder]:flex-1 [&_.ant-tabs-content-holder]:pt-4 [&_.ant-tabs-tabpane]:h-full [&_.ant-tabs-tabpane-active]:flex [&_.ant-tabs-tabpane-active]:min-h-0 [&_.ant-tabs-tabpane-active]:flex-col"
          items={[
            {
              key: 'business',
              label: '业务审计',
              children: <BusinessAuditPanel />,
            },
            {
              key: 'security',
              label: 'Security 操作日志',
              children: <SecurityOperationLogsPanel />,
            },
          ]}
        />
      </div>
    </SystemManagementPage>
  );
}
