import YakButton from '@/components/YakButton';
import VersionHistoryPanel from '@/components/version/VersionHistoryPanel';
import type { WorkflowFailureStrategy } from '@/services/workflow';
import {
  getWorkflowVersion,
  isWorkflowPublished,
  listWorkflowVersions,
  rollbackWorkflowVersion,
  type WorkflowDefinition,
  type WorkflowVersionSummary,
} from '@/services/workflow/definitions';
import { useIntl } from '@umijs/max';
import {
  Button,
  Dropdown,
  InputNumber,
  Modal,
  Popover,
  Select,
  Tooltip,
  message,
} from 'antd';
import {
  ChevronDown,
  CircleStop,
  History,
  Play,
  Rocket,
  Save,
  SlidersHorizontal,
} from 'lucide-react';
import { useMemo, useState } from 'react';

interface WorkflowToolbarProps {
  definition?: WorkflowDefinition;
  name: string;
  description: string;
  workflowTimeoutSeconds: number;
  failureStrategy: WorkflowFailureStrategy;
  nodesCount: number;
  edgesCount: number;
  locked: boolean;
  saving: boolean;
  testing: boolean;
  statusAction: boolean;
  onNameChange: (value: string) => void;
  onDescriptionChange: (value: string) => void;
  onWorkflowTimeoutChange: (value: number) => void;
  onFailureStrategyChange: (value: WorkflowFailureStrategy) => void;
  onClear: () => void;
  onSave: () => void;
  onTestRun: () => void;
  onOnline: () => void;
  onOffline: () => void;
  /** 回滚成功后的宿主刷新（definition 状态/激活版本已变化）。 */
  onRollback: (versionNo: number) => Promise<void>;
}

const FAILURE_OPTIONS: Array<{ value: WorkflowFailureStrategy; messageId: string }> = [
  {
    value: 'CONTINUE_INDEPENDENT_BRANCHES',
    messageId: 'pages.workflow.editor.toolbar.failure.continue',
  },
  { value: 'FAIL_FAST', messageId: 'pages.workflow.editor.toolbar.failure.fast' },
  {
    value: 'TERMINATE_ALL',
    messageId: 'pages.workflow.editor.toolbar.failure.terminate',
  },
];

const WorkflowToolbar = (props: WorkflowToolbarProps) => {
  const intl = useIntl();
  const {
    definition,
    name,
    workflowTimeoutSeconds,
    failureStrategy,
    nodesCount,
    edgesCount,
    locked,
    saving,
    testing,
    statusAction,
    onWorkflowTimeoutChange,
    onFailureStrategyChange,
    onSave,
    onTestRun,
    onOnline,
    onOffline,
    onRollback,
  } = props;
  const [versionOpen, setVersionOpen] = useState(false);
  const [runtimeSettingsOpen, setRuntimeSettingsOpen] = useState(false);
  const [versions, setVersions] = useState<WorkflowVersionSummary[]>([]);

  const status = definition?.status || 'DRAFT';
  const activeVersionNo = definition?.activeVersionNo;
  const draftChanged = definition?.draftChanged ?? true;
  const nextVersionNo = (definition?.latestVersionNo || 0) + 1;
  const hasPublished = Boolean(activeVersionNo);
  const hasRuntimeOverrides =
    workflowTimeoutSeconds > 0 ||
    failureStrategy !== 'CONTINUE_INDEPENDENT_BRANCHES';
  const canPublish = !hasPublished || draftChanged || status === 'OFFLINE';
  const busy = saving || statusAction;
  const reenable = status === 'OFFLINE' && hasPublished && !draftChanged;
  const publishingUpdate = hasPublished && draftChanged;
  const publishingOfflineUpdate = status === 'OFFLINE' && publishingUpdate;
  const targetVersionNo = reenable
    ? activeVersionNo || 1
    : publishingUpdate
      ? nextVersionNo
      : 1;

  const lifecycleText = !hasPublished
    ? intl.formatMessage({ id: 'pages.workflow.editor.toolbar.lifecycle.draft' })
    : status === 'OFFLINE'
      ? `${intl.formatMessage(
          { id: 'pages.workflow.editor.toolbar.lifecycle.offline' },
          { version: activeVersionNo || 0 },
        )}${
          draftChanged
            ? ` · ${intl.formatMessage({ id: 'pages.workflow.editor.toolbar.lifecycle.draftChanged' })}`
            : ''
        }`
      : draftChanged
        ? `${intl.formatMessage(
            { id: 'pages.workflow.editor.toolbar.lifecycle.online' },
            { version: activeVersionNo || 0 },
          )} · ${intl.formatMessage({ id: 'pages.workflow.editor.toolbar.lifecycle.draftChanged' })}`
        : intl.formatMessage(
            { id: 'pages.workflow.editor.toolbar.lifecycle.online' },
            { version: activeVersionNo || 0 },
          );

  const publishButtonText = intl.formatMessage({
    id: !canPublish
      ? 'pages.workflow.editor.toolbar.latest'
      : reenable
        ? 'pages.workflow.editor.toolbar.reenable'
        : publishingOfflineUpdate
          ? 'pages.workflow.editor.toolbar.publishOfflineUpdate'
          : publishingUpdate
            ? 'pages.workflow.editor.toolbar.publishUpdate'
            : 'pages.workflow.editor.toolbar.publish',
  });

  const loadVersions = async () => {
    if (!definition?.id) return;
    try {
      setVersions(await listWorkflowVersions(definition.id));
    } catch (error) {
      message.error(
        error instanceof Error
          ? error.message
          : intl.formatMessage({ id: 'pages.workflow.editor.toolbar.versionLoadFailed' }),
      );
    }
  };

  const confirmPublish = () => {
    if (!canPublish || testing || busy) return;
    const titleId = reenable
      ? 'pages.workflow.editor.toolbar.reenableTitle'
      : publishingOfflineUpdate
        ? 'pages.workflow.editor.toolbar.publishOnlineTitle'
        : publishingUpdate
          ? 'pages.workflow.editor.toolbar.publishUpdateTitle'
          : 'pages.workflow.editor.toolbar.publishTitle';
    const contentId = reenable
      ? 'pages.workflow.editor.toolbar.reenableContent'
      : publishingUpdate
        ? 'pages.workflow.editor.toolbar.publishUpdateContent'
        : 'pages.workflow.editor.toolbar.publishContent';
    const okId = reenable
      ? 'pages.workflow.editor.toolbar.reenable'
      : publishingOfflineUpdate
        ? 'pages.workflow.editor.toolbar.publishOfflineUpdate'
        : publishingUpdate
          ? 'pages.workflow.editor.toolbar.publishUpdate'
          : 'pages.workflow.editor.toolbar.publish';

    Modal.confirm({
      centered: true,
      title: intl.formatMessage({ id: titleId }, { version: targetVersionNo }),
      content: intl.formatMessage({ id: contentId }, { version: targetVersionNo }),
      okText: intl.formatMessage({ id: okId }),
      cancelText: intl.formatMessage({ id: 'pages.workflow.editor.common.cancel' }),
      onOk: onOnline,
    });
  };

  const confirmOffline = () => {
    if (testing || busy || !isWorkflowPublished(status) || !hasPublished) return;
    Modal.confirm({
      centered: true,
      title: intl.formatMessage(
        { id: 'pages.workflow.editor.toolbar.offlineTitle' },
        { version: activeVersionNo || 0 },
      ),
      content: intl.formatMessage({ id: 'pages.workflow.editor.toolbar.offlineContent' }),
      okText: intl.formatMessage({ id: 'pages.workflow.editor.toolbar.offline' }),
      cancelText: intl.formatMessage({ id: 'pages.workflow.editor.common.cancel' }),
      okButtonProps: { danger: true },
      onOk: onOffline,
    });
  };

  const versionContent = useMemo(
    () => (
      <div className="w-[420px] overflow-hidden rounded-[10px] border border-[#e4e7ec] bg-white shadow-[0_10px_28px_rgba(22,24,35,.12)]">
        <div className="flex h-11 items-center justify-between border-b border-[#f0f1f3] px-3.5">
          <div className="text-[12px] font-semibold text-[#344054]">
            {intl.formatMessage({ id: 'pages.workflow.editor.toolbar.versions' })}
          </div>
          <span className="text-[9px] text-[#98a2b3]">
            {hasPublished
              ? intl.formatMessage(
                  { id: 'pages.workflow.editor.toolbar.currentVersion' },
                  { version: activeVersionNo || 0 },
                )
              : intl.formatMessage({ id: 'pages.workflow.editor.toolbar.notPublished' })}
          </span>
        </div>
        <div className="max-h-[420px] overflow-auto p-3">
          <VersionHistoryPanel
            listVersions={async () => {
              const list = await listWorkflowVersions(definition?.id || '');
              setVersions(list);
              return list.map((item) => ({
                versionNo: item.versionNo,
                createdAt: item.publishedAt,
                changeNote: `${item.nodeCount} 节点 / ${item.edgeCount} 边`,
              }));
            }}
            getVersion={async (versionNo) => {
              const detail = await getWorkflowVersion(definition?.id || '', versionNo);
              return { runSpec: detail?.runSpec, editorMeta: detail?.editorMeta };
            }}
            rollback={async (versionNo) => {
              await onRollback(versionNo);
            }}
            currentVersionNo={activeVersionNo ?? null}
            rollbackDescription="将用该版本内容追加为新版本并立即置为启用+已发布（不抹历史）；当前草稿不受影响。"
            refreshKey={versions.length}
          />
        </div>
      </div>
    ),
    [activeVersionNo, definition?.id, hasPublished, intl, onRollback, versions.length],
  );

  const runtimeSettingsContent = (
    <div className="w-[300px] overflow-hidden rounded-[10px] border border-[#e4e7ec] bg-white shadow-[0_10px_28px_rgba(22,24,35,.12)]">
      <div className="border-b border-[#f0f1f3] px-4 py-3">
        <div className="text-[12px] font-semibold text-[#344054]">
          {intl.formatMessage({ id: 'pages.workflow.editor.toolbar.runtimeSettings' })}
        </div>
        <div className="mt-1 text-[9px] leading-4 text-[#98a2b3]">
          {intl.formatMessage({ id: 'pages.workflow.editor.toolbar.runtimeSettingsHint' })}
        </div>
      </div>
      <div className="space-y-4 p-4">
        <div>
          <div className="mb-1.5 text-[10px] font-medium text-[#667085]">
            {intl.formatMessage({ id: 'pages.workflow.editor.toolbar.failureStrategy' })}
          </div>
          <Select
            size="small"
            disabled={locked}
            className="w-full"
            value={failureStrategy}
            options={FAILURE_OPTIONS.map((item) => ({
              value: item.value,
              label: intl.formatMessage({ id: item.messageId }),
            }))}
            onChange={(value) => onFailureStrategyChange(value as WorkflowFailureStrategy)}
          />
          <div className="mt-1 text-[9px] leading-4 text-[#98a2b3]">
            {intl.formatMessage({ id: 'pages.workflow.editor.toolbar.failureHint' })}
          </div>
        </div>
        <div>
          <div className="mb-1.5 text-[10px] font-medium text-[#667085]">
            {intl.formatMessage({ id: 'pages.workflow.editor.toolbar.workflowTimeout' })}
          </div>
          <InputNumber
            size="small"
            controls={false}
            disabled={locked}
            min={0}
            max={7 * 24 * 60 * 60}
            value={workflowTimeoutSeconds}
            addonAfter={intl.formatMessage({ id: 'pages.workflow.editor.common.seconds' })}
            className="!w-full"
            onChange={(value) => onWorkflowTimeoutChange(Number(value || 0))}
          />
          <div className="mt-1 text-[9px] leading-4 text-[#98a2b3]">
            {intl.formatMessage({ id: 'pages.workflow.editor.toolbar.timeoutHint' })}
          </div>
        </div>
      </div>
    </div>
  );

  const publishMoreItems =
    isWorkflowPublished(status) && hasPublished
      ? [
          {
            key: 'offline',
            label: (
              <span className="text-[#b42318]">
                {intl.formatMessage({ id: 'pages.workflow.editor.toolbar.offline' })}
              </span>
            ),
            icon: <CircleStop size={13} className="text-[#b42318]" />,
          },
        ]
      : [];

  const fallbackName = intl.formatMessage({ id: 'pages.workflow.editor.defaultName' });

  return (
    <header className="workflow-editor-toolbar flex h-[52px] shrink-0 items-center justify-between border-b border-[#e8eaee] bg-white px-4">
      <div className="min-w-0">
        <div
          className="max-w-[420px] truncate text-[13px] font-semibold leading-5 text-[#161823]"
          title={name || fallbackName}
        >
          {name || fallbackName}
        </div>
        <div className="mt-0.5 flex h-4 items-center gap-2 text-[9px] leading-4 text-[#98a2b3]">
          <span>{lifecycleText}</span>
          <span className="h-1 w-1 rounded-full bg-[#d0d5dd]" />
          <span>
            {intl.formatMessage(
              { id: 'pages.workflow.editor.toolbar.nodesEdges' },
              { nodes: nodesCount, edges: edgesCount },
            )}
          </span>
        </div>
      </div>

      <div className="flex shrink-0 items-center gap-1.5">
        <div className="flex items-center gap-0.5">
          <Popover
            open={runtimeSettingsOpen}
            onOpenChange={setRuntimeSettingsOpen}
            trigger="click"
            placement="bottomRight"
            arrow={false}
            content={runtimeSettingsContent}
            overlayInnerStyle={{ padding: 0, background: 'transparent', boxShadow: 'none' }}
          >
            <Tooltip title={intl.formatMessage({ id: 'pages.workflow.editor.toolbar.runtimeSettings' })}>
              <Button
                type="text"
                aria-label={intl.formatMessage({ id: 'pages.workflow.editor.toolbar.runtimeSettings' })}
                disabled={busy}
                icon={<SlidersHorizontal size={14} />}
                className="relative !flex !h-8 !w-8 !min-w-0 !items-center !justify-center !rounded-[7px] !p-0 !text-[#667085] hover:!bg-[#f5f6f7] hover:!text-[#344054]"
              >
                {hasRuntimeOverrides ? (
                  <span className="absolute right-[5px] top-[5px] h-1.5 w-1.5 rounded-full bg-[#fe2c55] ring-2 ring-white" />
                ) : null}
              </Button>
            </Tooltip>
          </Popover>

          <Popover
            open={versionOpen}
            onOpenChange={(open) => {
              setVersionOpen(open);
              if (open) void loadVersions();
            }}
            trigger="click"
            placement="bottomRight"
            arrow={false}
            content={versionContent}
            overlayInnerStyle={{ padding: 0, background: 'transparent', boxShadow: 'none' }}
          >
            <Tooltip title={intl.formatMessage({ id: 'pages.workflow.editor.toolbar.versions' })}>
              <Button
                type="text"
                aria-label={intl.formatMessage({ id: 'pages.workflow.editor.toolbar.versions' })}
                disabled={!definition?.id || busy}
                icon={<History size={14} />}
                className="!flex !h-8 !w-8 !min-w-0 !items-center !justify-center !rounded-[7px] !p-0 !text-[#667085] hover:!bg-[#f5f6f7] hover:!text-[#344054]"
              />
            </Tooltip>
          </Popover>
        </div>

        <span className="mx-1 h-5 w-px bg-[#eceef1]" />

        <Tooltip title={intl.formatMessage({ id: 'pages.workflow.editor.toolbar.testHint' })}>
          <YakButton
            type="text"
            loading={testing}
            disabled={!definition?.id || busy}
            icon={<Play size={13} />}
            onClick={onTestRun}
            className="bg-[#f5f6f7]"
          >
            {intl.formatMessage({
              id: testing
                ? 'pages.workflow.editor.toolbar.testing'
                : 'pages.workflow.editor.toolbar.test',
            })}
          </YakButton>
        </Tooltip>

        <YakButton
          type="text"
          loading={saving}
          disabled={testing || statusAction}
          icon={<Save size={13} />}
          onClick={onSave}
          className="bg-[#f5f6f7]"
        >
          {intl.formatMessage({ id: 'pages.workflow.editor.toolbar.saveDraft' })}
        </YakButton>

        <div className="flex items-center">
          <Tooltip
            title={
              !canPublish
                ? intl.formatMessage({ id: 'pages.workflow.editor.toolbar.noPublishNeeded' })
                : undefined
            }
          >
            <span>
              <YakButton
                type="primary"
                loading={statusAction}
                disabled={testing || saving || !canPublish}
                icon={<Rocket size={13} />}
                onClick={confirmPublish}
                className={publishMoreItems.length ? '!rounded-r-[4px]' : undefined}
              >
                {publishButtonText}
              </YakButton>
            </span>
          </Tooltip>

          {publishMoreItems.length ? (
            <Dropdown
              trigger={['click']}
              placement="bottomRight"
              menu={{
                items: publishMoreItems,
                onClick: ({ key }) => {
                  if (key === 'offline') confirmOffline();
                },
              }}
            >
              <YakButton
                type="primary"
                iconOnly
                aria-label={intl.formatMessage({ id: 'pages.workflow.editor.toolbar.morePublishActions' })}
                disabled={testing || busy}
                icon={<ChevronDown size={12} />}
                className="!-ml-px !rounded-l-[4px] !border-l-white/20"
              />
            </Dropdown>
          ) : null}
        </div>
      </div>
    </header>
  );
};

export default WorkflowToolbar;
