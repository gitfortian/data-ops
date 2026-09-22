import { YakButton, YakEmpty, YakTab } from '@/components/ui';
import { BRAND_THEME } from '@/styles/brand';
import { history, useParams } from '@umijs/max';
import { ConfigProvider, Spin } from 'antd';
import { useMemo, useState } from 'react';

import { ExecutionDetailHeader } from './components/ExecutionDetailHeader';
import { ExecutionHistoryTable } from './components/ExecutionHistoryTable';
import { ExecutionLogPanel } from './components/ExecutionLogPanel';
import { ExecutionRuleTable } from './components/ExecutionRuleTable';
import {
  ExecutionInfoItem,
  ExecutionMetricTile,
  ExecutionSectionCard,
} from './components/ExecutionSectionCard';
import { useExecutionDetailPage } from './hooks/useExecutionDetailPage';
import {
  type ExecutionDetailTabKey,
  formatExecutionDuration,
  formatExecutionTime,
  qualityExecutionIssueCount,
  qualityExecutionTriggerLabel,
} from './utils';

const ExecutionDetailPage = () => {
  const { executionNo = '' } = useParams<{ executionNo: string }>();
  const [activeTab, setActiveTab] =
    useState<ExecutionDetailTabKey>('overview');
  const {
    detail,
    logs,
    historyRecords,
    issueRules,
    loading,
    logsLoading,
    historyLoading,
    refreshing,
    refresh,
    loadLogs,
  } = useExecutionDetailPage(executionNo);

  const tabItems = useMemo(
    () => [
      { key: 'overview', label: '总览' },
      { key: 'history', label: `历史运行 (${historyRecords.length})` },
      {
        key: 'issues',
        label: `问题数据${issueRules.length ? ` (${issueRules.length})` : ''}`,
      },
      { key: 'logs', label: '原始日志' },
    ],
    [historyRecords.length, issueRules.length],
  );

  if (!detail && loading) {
    return (
      <ConfigProvider theme={BRAND_THEME}>
        <div className="flex min-h-[calc(100vh-64px)] items-center justify-center bg-[#f7f7f8]">
          <Spin size="large" />
        </div>
      </ConfigProvider>
    );
  }

  if (!detail) {
    return (
      <ConfigProvider theme={BRAND_THEME}>
        <div className="flex min-h-[calc(100vh-64px)] items-center justify-center bg-[#f7f7f8]">
          <div className="min-w-[360px] rounded-lg bg-white px-6 py-4">
            <YakEmpty
              title="执行记录不存在"
              description="该运行记录可能已被删除，或当前账号无法访问"
            />
            <div className="flex justify-center pb-5">
              <YakButton onClick={() => history.push('/data-quality/execution')}>
                返回运行记录
              </YakButton>
            </div>
          </div>
        </div>
      </ConfigProvider>
    );
  }

  const issueCount = qualityExecutionIssueCount(detail);

  const overviewContent = (
    <div className="space-y-3">
      <div className="grid gap-3 xl:grid-cols-2">
        <ExecutionSectionCard title="质量概览">
          <div className="grid grid-cols-2 gap-3 p-5 sm:grid-cols-4 xl:grid-cols-2 2xl:grid-cols-4">
            <ExecutionMetricTile
              label="规则数量"
              value={detail.totalRules}
              hint="本次参与检测"
            />
            <ExecutionMetricTile
              label="通过规则"
              value={detail.passedRules}
              hint="符合质量预期"
            />
            <ExecutionMetricTile
              label="问题规则"
              value={issueCount}
              hint="未通过 + 异常"
              valueClassName={issueCount ? '!text-[#d92d20]' : ''}
            />
            <ExecutionMetricTile
              label="执行耗时"
              value={formatExecutionDuration(detail.durationMs)}
              hint="端到端检测耗时"
            />
          </div>
        </ExecutionSectionCard>

        <ExecutionSectionCard title="执行情况">
          <div className="grid grid-cols-2 gap-x-10 gap-y-6 p-5">
            <ExecutionInfoItem
              label="数据源"
              value={detail.dataSourceName}
            />
            <ExecutionInfoItem label="监控对象" value={detail.objectName} />
            <ExecutionInfoItem
              label="触发方式"
              value={qualityExecutionTriggerLabel(detail.triggerType)}
            />
            <ExecutionInfoItem
              label="触发人"
              value={detail.operator || 'system'}
            />
            <ExecutionInfoItem
              label="入队时间"
              value={formatExecutionTime(detail.queuedAt)}
            />
            <ExecutionInfoItem
              label="开始时间"
              value={formatExecutionTime(detail.startedAt)}
            />
            <ExecutionInfoItem
              label="结束时间"
              value={formatExecutionTime(detail.finishedAt)}
            />
            <ExecutionInfoItem label="监控 ID" value={detail.monitorId} />
          </div>
        </ExecutionSectionCard>
      </div>

      <ExecutionSectionCard
        title="规则检测结果"
        extra={
          <span className="text-[12px] text-[#8a8f98]">
            共 {detail.rules.length} 条规则
          </span>
        }
      >
        <div className="px-5 pb-5 pt-1">
          <ExecutionRuleTable records={detail.rules} />
        </div>
      </ExecutionSectionCard>
    </div>
  );

  const historyContent = (
    <ExecutionSectionCard
      title="历史运行记录"
      extra={
        <span className="text-[12px] text-[#8a8f98]">
          最近 {historyRecords.length} 条
        </span>
      }
    >
      <div className="px-5 pb-5 pt-1">
        <ExecutionHistoryTable
          records={historyRecords}
          loading={historyLoading}
          currentExecutionNo={detail.executionNo}
          onOpen={(targetExecutionNo) =>
            history.push(`/data-quality/execution/${targetExecutionNo}`)
          }
        />
      </div>
    </ExecutionSectionCard>
  );

  const issuesContent = (
    <ExecutionSectionCard
      title="问题数据处理"
      extra={
        <span className="text-[12px] text-[#8a8f98]">
          {issueRules.length} 条待关注规则
        </span>
      }
    >
      <div className="px-5 pb-5 pt-1">
        <ExecutionRuleTable records={issueRules} issueMode />
      </div>
    </ExecutionSectionCard>
  );

  return (
    <ConfigProvider theme={BRAND_THEME}>
      <div className="min-h-[calc(100vh-64px)] bg-[#f7f7f8] text-[#161823]">
        <div className="mx-auto w-full max-w-[1800px] px-4 pb-8 pt-0 lg:px-5">
          <ExecutionDetailHeader
            detail={detail}
            historyRecords={historyRecords}
            historyLoading={historyLoading}
            refreshing={refreshing}
            onBack={() => history.push('/data-quality/execution')}
            onRefresh={() => void refresh()}
            onSelectExecution={(targetExecutionNo) => {
              if (targetExecutionNo !== detail.executionNo) {
                history.push(`/data-quality/execution/${targetExecutionNo}`);
              }
            }}
          />

          <div className="px-5 lg:px-6">
            <YakTab
              activeKey={activeTab}
              onChange={(key) => setActiveTab(key as ExecutionDetailTabKey)}
              items={tabItems}
            />
          </div>

          <div className="mt-3">
            {activeTab === 'overview' ? overviewContent : null}
            {activeTab === 'history' ? historyContent : null}
            {activeTab === 'issues' ? issuesContent : null}
            {activeTab === 'logs' ? (
              <ExecutionLogPanel
                logs={logs}
                loading={logsLoading}
                onRefresh={() => void loadLogs()}
              />
            ) : null}
          </div>
        </div>
      </div>
    </ConfigProvider>
  );
};

export default ExecutionDetailPage;
