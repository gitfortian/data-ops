import { YakFilterSwitch } from '@/components/ui';
import { API_SUCCESS_CODE } from '@/services/http/response';
import { ReloadOutlined, SearchOutlined } from '@ant-design/icons';
import { history, useAccess, useIntl } from '@umijs/max';
import {
  Button,
  ConfigProvider,
  Descriptions,
  Drawer,
  Empty,
  Input,
  Pagination,
  Popconfirm,
  Select,
  Spin,
  Table,
  Tooltip,
  message,
} from 'antd';
import moment from 'moment';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';

import { WorkspaceLoadFailureState } from '../components/WorkspaceStateFeedback';
import {
  activateDevelopmentReleaseRevision,
  getDevelopmentRelease,
  listDevelopmentReleases,
  offlineDevelopmentRelease,
  onlineDevelopmentRelease,
} from '../service';
import type {
  DevelopmentId,
  DevelopmentReleaseDetail,
  DevelopmentReleaseStatus,
  DevelopmentReleaseSummary,
  DevelopmentTaskRevisionSummary,
  DevelopmentTaskType,
} from '../types';
import {
  classifyWorkspaceLoadFailure,
  type WorkspaceLoadFailure,
} from '../workspaceState';
import {
  isReleaseMutableStatus,
  releaseNodeUrl,
  releaseRevisionState,
} from './releaseExperience';

const taskTypeOptions = [
  { label: 'SQL', value: 'SQL' },
  { label: 'SHELL', value: 'SHELL' },
  { label: 'PYTHON', value: 'PYTHON' },
  { label: 'JAVA', value: 'JAVA' },
  { label: 'HTTP', value: 'HTTP' },
];

const statusClassName: Record<string, string> = {
  ONLINE: 'bg-[#ecfdf3] text-[#027a48]',
  OFFLINE: 'bg-[#f2f4f7] text-[#667085]',
  DISABLED: 'bg-[#fff6ed] text-[#c4320a]',
};

const responseData = <T,>(
  response: { code?: number; data?: T; msg?: string; message?: string },
  fallback: string,
): T => {
  if (response?.code !== API_SUCCESS_CODE || response.data === undefined) {
    throw new Error(response?.message || response?.msg || fallback);
  }
  return response.data;
};

const StatusBadge = ({ status }: { status?: DevelopmentReleaseStatus }) => {
  const intl = useIntl();
  const normalized = String(status || '').toUpperCase();
  const messageId: Record<string, string> = {
    ONLINE: 'pages.dataDevelopment.release.online',
    OFFLINE: 'pages.dataDevelopment.release.offline',
    DISABLED: 'pages.dataDevelopment.release.disabled',
  };
  return (
    <span
      className={[
        'inline-flex h-6 items-center rounded-md px-2 text-[12px] font-medium',
        statusClassName[normalized] || 'bg-[#f2f4f7] text-[#667085]',
      ].join(' ')}
    >
      {messageId[normalized]
        ? intl.formatMessage({ id: messageId[normalized] })
        : normalized || '-'}
    </span>
  );
};

const ReleaseCenterPage = () => {
  const intl = useIntl();
  const access = useAccess();
  const canRelease = access.hasPermission('data-development:release');
  const intlRef = useRef(intl);
  intlRef.current = intl;
  const text = useCallback(
    (id: string, values?: Record<string, string | number>) =>
      intlRef.current.formatMessage({ id }, values),
    [],
  );

  const [records, setRecords] = useState<DevelopmentReleaseSummary[]>([]);
  const [loading, setLoading] = useState(false);
  const [loadFailure, setLoadFailure] = useState<WorkspaceLoadFailure>();
  const [pageNo, setPageNo] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [total, setTotal] = useState(0);
  const [onlineCount, setOnlineCount] = useState(0);
  const [offlineCount, setOfflineCount] = useState(0);
  const [status, setStatus] = useState<'ALL' | 'ONLINE' | 'OFFLINE'>('ALL');
  const [taskType, setTaskType] = useState<DevelopmentTaskType | undefined>();
  const [keyword, setKeyword] = useState('');
  const [keywordDraft, setKeywordDraft] = useState('');
  const [refreshKey, setRefreshKey] = useState(0);
  const [detailOpen, setDetailOpen] = useState(false);
  const [detailAssetId, setDetailAssetId] = useState<DevelopmentId>();
  const [detailLoading, setDetailLoading] = useState(false);
  const [detailFailure, setDetailFailure] = useState<WorkspaceLoadFailure>();
  const [detail, setDetail] = useState<DevelopmentReleaseDetail>();
  const [actionLoading, setActionLoading] = useState('');

  const loadRecords = useCallback(async () => {
    setLoading(true);
    setLoadFailure(undefined);
    try {
      const response = await listDevelopmentReleases({
        pageNo,
        pageSize,
        status,
        taskType,
        keyword: keyword || undefined,
      });
      const data = responseData(
        response,
        text('pages.dataDevelopment.release.loadFailed'),
      );
      setRecords(data.records || []);
      setTotal(data.total || 0);
      setOnlineCount(data.onlineCount || 0);
      setOfflineCount(data.offlineCount || 0);
    } catch (error) {
      setRecords([]);
      setTotal(0);
      setOnlineCount(0);
      setOfflineCount(0);
      setLoadFailure(classifyWorkspaceLoadFailure(error));
    } finally {
      setLoading(false);
    }
  }, [keyword, pageNo, pageSize, refreshKey, status, taskType, text]);

  useEffect(() => {
    void loadRecords();
  }, [loadRecords]);

  const loadDetail = useCallback(
    async (assetId: DevelopmentId) => {
      setDetailLoading(true);
      setDetailFailure(undefined);
      try {
        const response = await getDevelopmentRelease(assetId);
        setDetail(
          responseData(
            response,
            text('pages.dataDevelopment.release.detailFailed'),
          ),
        );
      } catch (error) {
        setDetail(undefined);
        setDetailFailure(classifyWorkspaceLoadFailure(error));
      } finally {
        setDetailLoading(false);
      }
    },
    [text],
  );

  const openDetail = (record: DevelopmentReleaseSummary) => {
    setDetailOpen(true);
    setDetailAssetId(record.assetId);
    setDetail(undefined);
    setDetailFailure(undefined);
    void loadDetail(record.assetId);
  };

  const openDevelopmentNode = (nodeId: DevelopmentId) => {
    history.push(releaseNodeUrl(nodeId));
  };

  const search = () => {
    setKeyword(keywordDraft.trim());
    setPageNo(1);
  };

  const reset = () => {
    setKeyword('');
    setKeywordDraft('');
    setStatus('ALL');
    setTaskType(undefined);
    setPageNo(1);
  };

  const updateReleaseStatus = async (
    record: DevelopmentReleaseSummary,
    target: 'ONLINE' | 'OFFLINE',
  ) => {
    const actionKey = `${record.assetId}:${target}`;
    setActionLoading(actionKey);
    try {
      const response =
        target === 'ONLINE'
          ? await onlineDevelopmentRelease(record.assetId)
          : await offlineDevelopmentRelease(record.assetId);
      responseData(
        response,
        text(
          target === 'ONLINE'
            ? 'pages.dataDevelopment.release.reonlineFailed'
            : 'pages.dataDevelopment.release.offlineFailed',
        ),
      );
      message.success(
        text(
          target === 'ONLINE'
            ? 'pages.dataDevelopment.release.reonlineSuccess'
            : 'pages.dataDevelopment.release.offlineSuccess',
        ),
      );
      await loadRecords();
      if (detailOpen && detailAssetId === record.assetId) {
        await loadDetail(record.assetId);
      }
    } catch (error) {
      message.error(
        error instanceof Error
          ? error.message
          : text('pages.dataDevelopment.release.statusUpdateFailed'),
      );
    } finally {
      setActionLoading('');
    }
  };

  const activateRevision = async (
    assetId: DevelopmentId,
    revision: DevelopmentTaskRevisionSummary,
  ) => {
    const actionKey = `${assetId}:revision:${revision.revisionNo}`;
    setActionLoading(actionKey);
    try {
      const response = await activateDevelopmentReleaseRevision(
        assetId,
        revision.revisionNo,
      );
      responseData(response, text('pages.dataDevelopment.release.switchFailed'));
      message.success(
        text('pages.dataDevelopment.release.switched', {
          revision: revision.revisionNo,
        }),
      );
      await Promise.all([loadRecords(), loadDetail(assetId)]);
    } catch (error) {
      message.error(
        error instanceof Error
          ? error.message
          : text('pages.dataDevelopment.release.switchFailed'),
      );
    } finally {
      setActionLoading('');
    }
  };

  const statusTabs: Array<{
    label: string;
    value: 'ALL' | 'ONLINE' | 'OFFLINE';
  }> = [
    {
      label: intl.formatMessage({ id: 'pages.dataDevelopment.release.all' }),
      value: 'ALL',
    },
    {
      label: intl.formatMessage({ id: 'pages.dataDevelopment.release.online' }),
      value: 'ONLINE',
    },
    {
      label: intl.formatMessage({ id: 'pages.dataDevelopment.release.offline' }),
      value: 'OFFLINE',
    },
  ];

  const tabCount = (value: 'ALL' | 'ONLINE' | 'OFFLINE') => {
    if (value === 'ONLINE') return onlineCount;
    if (value === 'OFFLINE') return offlineCount;
    return onlineCount + offlineCount;
  };

  const columns = useMemo(
    () => [
      {
        title: intl.formatMessage({ id: 'pages.dataDevelopment.release.taskAndNode' }),
        dataIndex: 'taskName',
        width: 260,
        render: (_: unknown, record: DevelopmentReleaseSummary) => (
          <div className="min-w-0 py-0.5">
            <button
              type="button"
              className="max-w-full truncate border-0 bg-transparent p-0 text-left text-[13px] font-medium text-[#344054] hover:text-[#161823]"
              onClick={() => openDetail(record)}
            >
              {record.taskName || '-'}
            </button>
            <div className="mt-0.5 truncate text-[11px] text-[#98a2b3]">
              {intl.formatMessage({ id: 'pages.dataDevelopment.common.nodeId' })}:{' '}
              <button
                type="button"
                className="border-0 bg-transparent p-0 font-mono text-[#667085] hover:text-[#175cd3]"
                onClick={() => openDevelopmentNode(record.nodeId)}
              >
                {record.nodeId}
              </button>
            </div>
          </div>
        ),
      },
      {
        title: intl.formatMessage({ id: 'pages.dataDevelopment.common.type' }),
        dataIndex: 'taskType',
        width: 90,
        render: (value: string) => (
          <span className="text-[12px] font-medium text-[#475467]">{value || '-'}</span>
        ),
      },
      {
        title: intl.formatMessage({ id: 'pages.dataDevelopment.common.status' }),
        dataIndex: 'status',
        width: 100,
        align: 'center' as const,
        render: (value: DevelopmentReleaseStatus) => <StatusBadge status={value} />,
      },
      {
        title: intl.formatMessage({ id: 'pages.dataDevelopment.releaseExperience.activeRevision' }),
        dataIndex: 'currentRevisionNo',
        width: 170,
        render: (_: unknown, record: DevelopmentReleaseSummary) => {
          const state = releaseRevisionState(record);
          return (
            <div>
              <div className="text-[13px] font-medium text-[#344054]">
                V{state.activeRevisionNo}
              </div>
              {state.hasNewerPublishedRevision ? (
                <div className="mt-0.5 text-[11px] text-[#b54708]">
                  {intl.formatMessage(
                    { id: 'pages.dataDevelopment.release.latestRevision' },
                    { revision: state.latestPublishedRevisionNo },
                  )}
                </div>
              ) : (
                <div className="mt-0.5 text-[11px] text-[#98a2b3]">
                  {intl.formatMessage({ id: 'pages.dataDevelopment.release.latestCurrent' })}
                </div>
              )}
            </div>
          );
        },
      },
      {
        title: intl.formatMessage({ id: 'pages.dataDevelopment.release.checksum' }),
        dataIndex: 'checksum',
        width: 180,
        render: (value?: string) => (
          <Tooltip title={value || undefined}>
            <span className="font-mono text-[11px] text-[#667085]">
              {value ? value.slice(0, 12) : '-'}
            </span>
          </Tooltip>
        ),
      },
      {
        title: intl.formatMessage({ id: 'pages.dataDevelopment.release.revisionTime' }),
        dataIndex: 'revisionCreateTime',
        width: 170,
        render: (value?: string | null) => (
          <span className="whitespace-nowrap text-[12px] text-[#667085]">
            {value ? moment(value).format('YYYY-MM-DD HH:mm:ss') : '-'}
          </span>
        ),
      },
      {
        title: intl.formatMessage({ id: 'pages.dataDevelopment.release.statusTime' }),
        dataIndex: 'updateTime',
        width: 170,
        render: (value?: string | null) => (
          <span className="whitespace-nowrap text-[12px] text-[#98a2b3]">
            {value ? moment(value).format('YYYY-MM-DD HH:mm:ss') : '-'}
          </span>
        ),
      },
      {
        title: intl.formatMessage({ id: 'pages.dataDevelopment.common.action' }),
        key: 'action',
        width: 190,
        fixed: 'right' as const,
        render: (_: unknown, record: DevelopmentReleaseSummary) => (
          <div className="flex items-center gap-3">
            <Button
              type="link"
              size="small"
              className="!px-0 !text-[12px] !text-[#475467]"
              onClick={() => openDetail(record)}
            >
              {intl.formatMessage({ id: 'pages.dataDevelopment.release.versionDetail' })}
            </Button>
            {canRelease && record.status === 'ONLINE' ? (
              <Popconfirm
                title={intl.formatMessage({ id: 'pages.dataDevelopment.release.offlineConfirm' })}
                description={intl.formatMessage({ id: 'pages.dataDevelopment.release.offlineDescription' })}
                okText={intl.formatMessage({ id: 'pages.dataDevelopment.release.confirmOffline' })}
                cancelText={intl.formatMessage({ id: 'pages.dataDevelopment.common.cancel' })}
                onConfirm={() => updateReleaseStatus(record, 'OFFLINE')}
              >
                <Button
                  type="link"
                  size="small"
                  loading={actionLoading === `${record.assetId}:OFFLINE`}
                  className="!px-0 !text-[12px] !text-[#667085]"
                >
                  {intl.formatMessage({ id: 'pages.dataDevelopment.release.offline' })}
                </Button>
              </Popconfirm>
            ) : canRelease && record.status === 'OFFLINE' ? (
              <Popconfirm
                title={intl.formatMessage({ id: 'pages.dataDevelopment.release.reonlineConfirm' })}
                description={intl.formatMessage(
                  { id: 'pages.dataDevelopment.release.reonlineDescription' },
                  { revision: record.currentRevisionNo },
                )}
                okText={intl.formatMessage({ id: 'pages.dataDevelopment.release.confirmOnline' })}
                cancelText={intl.formatMessage({ id: 'pages.dataDevelopment.common.cancel' })}
                onConfirm={() => updateReleaseStatus(record, 'ONLINE')}
              >
                <Button
                  type="link"
                  size="small"
                  loading={actionLoading === `${record.assetId}:ONLINE`}
                  className="!px-0 !text-[12px] !text-[#475467]"
                >
                  {intl.formatMessage({ id: 'pages.dataDevelopment.release.confirmOnline' })}
                </Button>
              </Popconfirm>
            ) : !canRelease && isReleaseMutableStatus(record.status) ? (
              <span className="text-[11px] text-[#98a2b3]">
                {intl.formatMessage({ id: 'pages.dataDevelopment.releaseExperience.readOnly' })}
              </span>
            ) : null}
          </div>
        ),
      },
    ],
    [actionLoading, canRelease, intl],
  );

  const detailRevisionState = detail
    ? releaseRevisionState(detail.release)
    : undefined;

  return (
    <ConfigProvider
      theme={{
        token: { borderRadius: 8, colorBorder: '#f0f0f0', colorBgContainer: '#ffffff' },
        components: {
          Input: { activeShadow: 'none' },
          Select: { activeOutlineColor: 'transparent' },
        },
      }}
    >
      <div className="flex min-h-[calc(100vh-64px)] flex-col bg-white px-5 pt-4">
        <div className="flex items-center justify-between">
          <div>
            <h1 className="m-0 text-[17px] font-semibold text-[#161823]">
              {intl.formatMessage({ id: 'pages.dataDevelopment.release.title' })}
            </h1>
            <div className="mt-1 text-[12px] text-[#98a2b3]">
              {intl.formatMessage({ id: 'pages.dataDevelopment.release.description' })}
            </div>
          </div>
          <Button icon={<ReloadOutlined />} onClick={() => setRefreshKey((value) => value + 1)}>
            {intl.formatMessage({ id: 'pages.dataDevelopment.common.refresh' })}
          </Button>
        </div>

        {!canRelease ? (
          <div className="mt-3 rounded-md border border-[#eaecf0] bg-[#f9fafb] px-3 py-2 text-[11px] text-[#667085]">
            {intl.formatMessage({ id: 'pages.dataDevelopment.releaseExperience.permissionHint' })}
          </div>
        ) : null}

        <div className="mt-3 border-b border-[#f0f0f0]">
          <div className="flex min-h-[54px] items-center justify-between gap-4 py-2">
            <YakFilterSwitch
              value={status}
              options={statusTabs.map((item) => ({
                value: item.value,
                label: (
                  <span className="inline-flex items-baseline gap-1">
                    <span>{item.label}</span>
                    <span className="text-[11px] opacity-60">{tabCount(item.value)}</span>
                  </span>
                ),
              }))}
              onChange={(value) => {
                setStatus(value as 'ALL' | 'ONLINE' | 'OFFLINE');
                setPageNo(1);
              }}
            />

            <div className="flex min-w-0 flex-1 items-center justify-end gap-2 overflow-x-auto">
              <Input
                allowClear
                variant="filled"
                value={keywordDraft}
                prefix={<SearchOutlined className="text-[#98a2b3]" />}
                placeholder={intl.formatMessage({ id: 'pages.dataDevelopment.release.searchPlaceholder' })}
                className="!h-9 !w-[240px] !min-w-[200px]"
                onChange={(event) => setKeywordDraft(event.target.value)}
                onPressEnter={search}
              />
              <Select
                allowClear
                variant="filled"
                value={taskType}
                options={taskTypeOptions}
                placeholder={intl.formatMessage({ id: 'pages.dataDevelopment.common.taskType' })}
                className="!h-9 !w-[130px] !min-w-[120px]"
                onChange={(value) => {
                  setTaskType(value);
                  setPageNo(1);
                }}
              />
              <Button type="primary" onClick={search}>
                {intl.formatMessage({ id: 'pages.dataDevelopment.common.search' })}
              </Button>
              <Button onClick={reset}>
                {intl.formatMessage({ id: 'pages.dataDevelopment.common.reset' })}
              </Button>
            </div>
          </div>
        </div>

        <div className="min-h-0 flex-1 pt-4">
          {loadFailure ? (
            <div className="flex h-full min-h-[360px]">
              <WorkspaceLoadFailureState
                failure={loadFailure}
                loading={loading}
                onRetry={() => setRefreshKey((value) => value + 1)}
              />
            </div>
          ) : (
            <Table
              rowKey="assetId"
              size="small"
              bordered
              loading={loading}
              columns={columns}
              dataSource={records}
              pagination={false}
              scroll={{ x: 1440, y: 'calc(100vh - 315px)' }}
              locale={{
                emptyText: (
                  <Empty
                    image={Empty.PRESENTED_IMAGE_SIMPLE}
                    description={intl.formatMessage({ id: 'pages.dataDevelopment.release.empty' })}
                  />
                ),
              }}
            />
          )}
        </div>

        {!loadFailure ? (
          <div className="flex h-16 shrink-0 items-center justify-between border-t border-[#f0f0f0]">
            <span className="text-[12px] text-[#98a2b3]">
              {intl.formatMessage(
                { id: 'pages.dataDevelopment.release.total' },
                { count: total },
              )}
            </span>
            <Pagination
              current={pageNo}
              pageSize={pageSize}
              total={total}
              showSizeChanger
              showQuickJumper
              pageSizeOptions={[10, 20, 50, 100]}
              onChange={(page, size) => {
                setPageNo(size === pageSize ? page : 1);
                setPageSize(size);
              }}
            />
          </div>
        ) : null}
      </div>

      <Drawer
        title={intl.formatMessage({ id: 'pages.dataDevelopment.release.detailTitle' })}
        placement="right"
        width={780}
        open={detailOpen}
        onClose={() => setDetailOpen(false)}
        extra={
          detail ? (
            <Button size="small" onClick={() => openDevelopmentNode(detail.release.nodeId)}>
              {intl.formatMessage({ id: 'pages.dataDevelopment.releaseExperience.openDevelopmentNode' })}
            </Button>
          ) : null
        }
      >
        {detailLoading ? (
          <div className="flex h-64 items-center justify-center"><Spin size="small" /></div>
        ) : detailFailure ? (
          <div className="flex h-[420px]">
            <WorkspaceLoadFailureState
              failure={detailFailure}
              loading={detailLoading}
              onRetry={() => detailAssetId && void loadDetail(detailAssetId)}
            />
          </div>
        ) : detail && detailRevisionState ? (
          <div className="space-y-6">
            <Descriptions size="small" bordered column={2}>
              <Descriptions.Item label={intl.formatMessage({ id: 'pages.dataDevelopment.common.taskName' })}>
                {detail.release.taskName}
              </Descriptions.Item>
              <Descriptions.Item label={intl.formatMessage({ id: 'pages.dataDevelopment.common.nodeId' })}>
                <Button
                  type="link"
                  size="small"
                  className="!h-auto !p-0 !font-mono"
                  onClick={() => openDevelopmentNode(detail.release.nodeId)}
                >
                  {detail.release.nodeId}
                </Button>
              </Descriptions.Item>
              <Descriptions.Item label={intl.formatMessage({ id: 'pages.dataDevelopment.common.taskType' })}>
                {detail.release.taskType}
              </Descriptions.Item>
              <Descriptions.Item label={intl.formatMessage({ id: 'pages.dataDevelopment.common.status' })}>
                <StatusBadge status={detail.release.status} />
              </Descriptions.Item>
              <Descriptions.Item label={intl.formatMessage({ id: 'pages.dataDevelopment.releaseExperience.activeRevision' })}>
                V{detailRevisionState.activeRevisionNo}
              </Descriptions.Item>
              <Descriptions.Item label={intl.formatMessage({ id: 'pages.dataDevelopment.releaseExperience.latestPublished' })}>
                V{detailRevisionState.latestPublishedRevisionNo}
              </Descriptions.Item>
              <Descriptions.Item label={intl.formatMessage({ id: 'pages.dataDevelopment.release.revisionTime' })}>
                {detail.release.revisionCreateTime
                  ? moment(detail.release.revisionCreateTime).format('YYYY-MM-DD HH:mm:ss')
                  : '-'}
              </Descriptions.Item>
              <Descriptions.Item label={intl.formatMessage({ id: 'pages.dataDevelopment.release.statusTime' })}>
                {detail.release.updateTime
                  ? moment(detail.release.updateTime).format('YYYY-MM-DD HH:mm:ss')
                  : '-'}
              </Descriptions.Item>
              <Descriptions.Item label="Checksum" span={2}>
                <span className="break-all font-mono text-[11px] text-[#667085]">
                  {detail.release.checksum || '-'}
                </span>
              </Descriptions.Item>
            </Descriptions>

            <div
              className={[
                'rounded-md border px-3 py-2 text-[12px] leading-5',
                detailRevisionState.serving
                  ? 'border-[#abefc6] bg-[#ecfdf3] text-[#027a48]'
                  : 'border-[#eaecf0] bg-[#f9fafb] text-[#475467]',
              ].join(' ')}
            >
              {intl.formatMessage(
                {
                  id: detailRevisionState.serving
                    ? 'pages.dataDevelopment.releaseExperience.activeOnline'
                    : 'pages.dataDevelopment.releaseExperience.activeOffline',
                },
                { revision: detailRevisionState.activeRevisionNo },
              )}
            </div>

            {detailRevisionState.hasNewerPublishedRevision ? (
              <div className="rounded-md border border-[#fedf89] bg-[#fffaeb] px-3 py-2 text-[12px] leading-5 text-[#93370d]">
                {intl.formatMessage(
                  { id: 'pages.dataDevelopment.releaseExperience.newerPublished' },
                  {
                    latest: detailRevisionState.latestPublishedRevisionNo,
                    active: detailRevisionState.activeRevisionNo,
                  },
                )}
              </div>
            ) : null}

            <section>
              <div className="mb-2 text-[13px] font-medium text-[#344054]">
                {intl.formatMessage({ id: 'pages.dataDevelopment.releaseExperience.activeContent' })}
              </div>
              <pre className="max-h-[260px] overflow-auto rounded-lg border border-[#eaecf0] bg-[#fafafa] p-3 text-[12px] leading-5 text-[#475467]">
                {detail.currentRevision.definition?.content ||
                  intl.formatMessage({ id: 'pages.dataDevelopment.release.noContent' })}
              </pre>
            </section>

            <section>
              <div className="mb-2 text-[13px] font-medium text-[#344054]">
                {intl.formatMessage({ id: 'pages.dataDevelopment.releaseExperience.activeConfig' })}
              </div>
              <pre className="max-h-[180px] overflow-auto rounded-lg border border-[#eaecf0] bg-[#fafafa] p-3 text-[12px] leading-5 text-[#475467]">
                {detail.currentRevision.definition?.configJson || '{}'}
              </pre>
            </section>

            <section>
              <div className="mb-2 flex items-center justify-between">
                <div className="text-[13px] font-medium text-[#344054]">
                  {intl.formatMessage({ id: 'pages.dataDevelopment.release.history' })}
                </div>
                <div className="text-[11px] text-[#98a2b3]">
                  {intl.formatMessage({ id: 'pages.dataDevelopment.release.historyHint' })}
                </div>
              </div>
              <Table
                rowKey="id"
                size="small"
                bordered
                pagination={false}
                dataSource={detail.revisions || []}
                columns={[
                  {
                    title: intl.formatMessage({ id: 'pages.dataDevelopment.release.version' }),
                    dataIndex: 'revisionNo',
                    width: 90,
                    render: (value: number) => (
                      <span className="font-medium text-[#344054]">V{value}</span>
                    ),
                  },
                  {
                    title: intl.formatMessage({ id: 'pages.dataDevelopment.release.publishTime' }),
                    dataIndex: 'createTime',
                    width: 170,
                    render: (value?: string) => (
                      <span className="text-[12px] text-[#667085]">
                        {value ? moment(value).format('YYYY-MM-DD HH:mm:ss') : '-'}
                      </span>
                    ),
                  },
                  {
                    title: 'Checksum',
                    dataIndex: 'checksum',
                    ellipsis: true,
                    render: (value?: string) => (
                      <Tooltip title={value || undefined}>
                        <span className="font-mono text-[11px] text-[#98a2b3]">
                          {value ? value.slice(0, 14) : '-'}
                        </span>
                      </Tooltip>
                    ),
                  },
                  {
                    title: intl.formatMessage({ id: 'pages.dataDevelopment.common.action' }),
                    width: 170,
                    render: (_: unknown, revision: DevelopmentTaskRevisionSummary) => {
                      const active = revision.revisionNo === detail.release.currentRevisionNo;
                      if (active) {
                        return (
                          <span className="text-[12px] text-[#667085]">
                            {intl.formatMessage({ id: 'pages.dataDevelopment.releaseExperience.activeHistory' })}
                          </span>
                        );
                      }
                      if (!canRelease) {
                        return (
                          <span className="text-[11px] text-[#98a2b3]">
                            {intl.formatMessage({ id: 'pages.dataDevelopment.releaseExperience.readOnly' })}
                          </span>
                        );
                      }
                      const buttonText = intl.formatMessage({
                        id:
                          detail.release.status === 'OFFLINE'
                            ? 'pages.dataDevelopment.release.switchAndOnline'
                            : 'pages.dataDevelopment.release.switchVersion',
                      });
                      return (
                        <Popconfirm
                          title={intl.formatMessage(
                            { id: 'pages.dataDevelopment.release.switchConfirm' },
                            { revision: revision.revisionNo },
                          )}
                          description={intl.formatMessage({
                            id:
                              revision.revisionNo < detail.release.currentRevisionNo
                                ? 'pages.dataDevelopment.release.switchHistorical'
                                : 'pages.dataDevelopment.release.switchDescription',
                          })}
                          okText={intl.formatMessage({ id: 'pages.dataDevelopment.release.confirmSwitch' })}
                          cancelText={intl.formatMessage({ id: 'pages.dataDevelopment.common.cancel' })}
                          onConfirm={() => activateRevision(detail.release.assetId, revision)}
                        >
                          <Button
                            type="link"
                            size="small"
                            loading={
                              actionLoading ===
                              `${detail.release.assetId}:revision:${revision.revisionNo}`
                            }
                            className="!px-0 !text-[12px] !text-[#475467]"
                          >
                            {buttonText}
                          </Button>
                        </Popconfirm>
                      );
                    },
                  },
                ]}
              />
            </section>
          </div>
        ) : (
          <Empty
            image={Empty.PRESENTED_IMAGE_SIMPLE}
            description={intl.formatMessage({ id: 'pages.dataDevelopment.release.detailEmpty' })}
          />
        )}
      </Drawer>
    </ConfigProvider>
  );
};

export default ReleaseCenterPage;
