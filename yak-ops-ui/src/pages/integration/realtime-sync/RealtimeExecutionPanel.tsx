import {
  CheckCircleOutlined,
  CloudServerOutlined,
  PlayCircleOutlined,
  ReloadOutlined,
  SafetyOutlined,
  StopOutlined,
} from '@ant-design/icons';
import { Alert, Button, Card, ConfigProvider, Descriptions, Space, Steps, Tag, message } from 'antd';
import { useEffect, useMemo, useState } from 'react';
import { BRAND_THEME } from '@/styles/brand';
import { realtimeApi } from './api';
import type { RealtimeAction } from './api';
import type { RealtimeJob, RuntimeCapabilities } from './types';
import YakButton from '@/components/YakButton';

type ExecutionAction = Exclude<RealtimeAction, 'restart'>;

const stateLabel: Record<string, string> = {
  STOPPED: '已停止',
  STARTING: '启动中',
  RUNNING: '运行中',
  STOPPING: '停止中',
  FAILED: '失败',
  UNKNOWN: '未知',
  CONFLICT: '冲突',
};

const isExecutionStartingAction = (action: ExecutionAction) =>
  action === 'start' ||
  action === 'restart-execution' ||
  action === 'apply-published-version';

export default function RealtimeExecutionPanel({
  job,
  onEdit,
  onBack,
}: {
  job: RealtimeJob;
  onEdit: () => void;
  onBack: () => void;
}) {
  const [current, setCurrent] = useState(job);
  const [capabilities, setCapabilities] = useState<RuntimeCapabilities>({});
  const [validated, setValidated] = useState(false);
  const [acting, setActing] = useState<ExecutionAction>();

  useEffect(() => {
    setCurrent(job);
  }, [job]);

  useEffect(() => {
    let cancelled = false;
    realtimeApi
      .capabilities(current.runtimeEnvironmentId)
      .then((response) => {
        if (!cancelled) setCapabilities(response.data || {});
      })
      .catch(() => {
        if (!cancelled) setCapabilities({});
      });
    return () => {
      cancelled = true;
    };
  }, [current.runtimeEnvironmentId]);

  const refreshJob = async () => {
    const response = await realtimeApi.detail(current.id);
    setCurrent(response.data);
    return response.data;
  };

  const waitForStartResult = async () => {
    for (let attempt = 0; attempt < 12; attempt += 1) {
      const refreshed = await refreshJob();
      if (['RUNNING', 'FAILED', 'UNKNOWN', 'CONFLICT'].includes(refreshed.observedState)) {
        return refreshed;
      }
      await new Promise((resolve) => window.setTimeout(resolve, 1500));
    }
    return refreshJob();
  };

  const run = async (action: ExecutionAction) => {
    setActing(action);
    try {
      await realtimeApi.action(current.id, action);
      if (action === 'validate') {
        setValidated(true);
        message.success('Flink CDC 运行校验通过');
        await refreshJob();
      } else if (action === 'publish') {
        setValidated(true);
        const refreshed = await refreshJob();
        message.success(
          refreshed.desiredState === 'RUNNING'
            ? '当前草稿已发布；正在运行的 SyncExecution 继续使用启动时的 DefinitionVersion'
            : '当前定义版本已发布',
        );
      } else if (isExecutionStartingAction(action)) {
        const refreshed = await waitForStartResult();
        if (refreshed.observedState === 'RUNNING') {
          if (action === 'restart-execution') {
            message.success('已按当前 DefinitionVersion 创建新的 SyncExecution');
          } else if (action === 'apply-published-version') {
            message.success('已显式应用命令开始时固定的 Published DefinitionVersion');
          } else {
            message.success('实时同步任务已启动');
          }
        } else if (refreshed.observedState === 'STARTING') {
          message.warning('Flink 任务仍在启动，可返回列表继续观察状态');
        } else {
          message.warning(
            `Flink 执行结果：${stateLabel[refreshed.observedState] || refreshed.observedState}`,
          );
        }
      } else if (action === 'stop') {
        await refreshJob();
        message.success('已提交停止请求');
      } else {
        await refreshJob();
        message.success('状态对账已完成');
      }
    } catch (error: any) {
      message.error(error?.message || '执行操作失败');
      try {
        await refreshJob();
      } catch {
        // Keep the last known state when the follow-up read also fails.
      }
    } finally {
      setActing(undefined);
    }
  };

  const hasPublishedVersion = current.publishedVersion != null;
  const currentDraftPublished =
    current.releaseState === 'PUBLISHED' && current.publishedVersion === current.definitionVersion;
  const hasUnpublishedChanges = hasPublishedVersion && !currentDraftPublished;
  const running = current.desiredState === 'RUNNING';
  const stableRunning = running && current.observedState === 'RUNNING';
  const startable =
    hasPublishedVersion &&
    current.desiredState === 'STOPPED' &&
    ['STOPPED', 'FAILED'].includes(current.observedState);
  const runtimeDisabled = capabilities.deployEnabled === false;
  const blockStartForDisplayedRuntime = currentDraftPublished && runtimeDisabled;
  const runtimeDisabledReason = capabilities.deployDisabledReason || '当前运行环境不可提交任务';

  const steps = useMemo(
    () => [
      {
        title: '保存草稿',
        description: `定义 v${current.definitionVersion}`,
        status: 'finish' as const,
      },
      {
        title: '运行校验',
        description: '当前草稿 / Connector',
        status: validated || currentDraftPublished ? ('finish' as const) : ('process' as const),
      },
      {
        title: '发布版本',
        description: currentDraftPublished
          ? `已发布 v${current.publishedVersion}`
          : hasPublishedVersion
            ? `已发布 v${current.publishedVersion} · 当前草稿未发布`
            : '锁定当前定义版本',
        status: currentDraftPublished
          ? ('finish' as const)
          : validated
            ? ('process' as const)
            : hasPublishedVersion
              ? ('finish' as const)
              : ('wait' as const),
      },
      {
        title: '运行实例',
        description: running
          ? current.publishedUpdateAvailable
            ? `${stateLabel[current.observedState] || current.observedState} · 有已发布更新可显式应用`
            : `${stateLabel[current.observedState] || current.observedState} · 固定启动时 DefinitionVersion`
          : hasUnpublishedChanges
            ? `下一次启动使用已发布 v${current.publishedVersion}`
            : '等待启动',
        status: current.observedState === 'RUNNING'
          ? ('finish' as const)
          : hasPublishedVersion
            ? ('process' as const)
            : ('wait' as const),
      },
    ],
    [
      current,
      currentDraftPublished,
      hasPublishedVersion,
      hasUnpublishedChanges,
      running,
      validated,
    ],
  );

  return (
    <ConfigProvider theme={BRAND_THEME} variant="filled">
      <div className="min-h-[calc(100vh-64px)] bg-[#f7f8fa] px-6 py-6 text-[#161823]">
        <div className="mx-auto w-full max-w-[1100px]">
          <header className="mb-5 flex flex-wrap items-start justify-between gap-4 rounded-xl bg-white px-7 py-6">
            <div>
              <div className="flex items-center gap-2 text-[12px] font-medium text-[var(--yak-brand-color)]">
                <CheckCircleOutlined />
                配置已保存 · 版本与运行显式解耦
              </div>
              <h1 className="mb-0 mt-1 text-[20px] font-semibold text-[#101828]">{current.name}</h1>
              <div className="mt-1 text-[12px] text-[#98a2b3]">
                任务 ID：{current.id} · 当前草稿 v{current.definitionVersion}
                {hasPublishedVersion ? ` · 已发布 v${current.publishedVersion}` : ''}
              </div>
            </div>
            <Space>
              <Button disabled={Boolean(acting)} onClick={onEdit}>
                继续编辑
              </Button>
              <Button disabled={Boolean(acting)} onClick={onBack}>
                返回任务列表
              </Button>
            </Space>
          </header>

          <Card className="mb-5">
            <Steps responsive items={steps} />
          </Card>

          {hasUnpublishedChanges && !running && (
            <Alert
              className="mb-5"
              type="info"
              showIcon
              message={`当前草稿 v${current.definitionVersion} 尚未发布`}
              description={`点击“启动任务”会运行已发布版本 v${current.publishedVersion}；当前草稿及其运行环境配置不会影响这次启动。`}
            />
          )}

          {running && (
            <Alert
              className="mb-5"
              type={hasUnpublishedChanges ? 'info' : 'success'}
              showIcon
              message={
                hasUnpublishedChanges
                  ? `当前 SyncExecution 正在运行，草稿 v${current.definitionVersion} 可继续编辑`
                  : '当前 SyncExecution 与任务定义生命周期已解耦'
              }
              description={
                hasUnpublishedChanges
                  ? '保存草稿不会修改当前运行实例。发布草稿只会推进 Published DefinitionVersion；当前 Flink Job 继续使用启动时固定的 DefinitionVersion。'
                  : '即使继续编辑或重新发布任务，当前运行实例仍保持启动时的 DefinitionVersion 与 RuntimeEnvironmentSnapshot，不会发生热更新。'
              }
            />
          )}

          {current.publishedUpdateAvailable && (
            <Alert
              className="mb-5"
              type="warning"
              showIcon
              message="存在新的 Published DefinitionVersion"
              description="“重启当前版本”会保持当前运行版本；“应用已发布版本”才会停止当前 Execution，并显式创建使用新 Published DefinitionVersion 的 Execution。两个命令不会互相替代。"
            />
          )}

          {runtimeDisabled && currentDraftPublished && (
            <Alert
              className="mb-5"
              type="warning"
              showIcon
              message="当前草稿绑定的运行环境暂不可提交任务"
              description={runtimeDisabledReason}
            />
          )}

          <div className="grid grid-cols-[minmax(0,1.15fr)_minmax(320px,0.85fr)] gap-5 max-lg:grid-cols-1">
            <Card title="下一步操作">
              <div className="space-y-4">
                <div className="rounded-xl border border-[#eaecf0] p-4">
                  <div className="flex flex-wrap items-center justify-between gap-3">
                    <div>
                      <div className="flex items-center gap-2 text-[14px] font-semibold text-[#101828]">
                        <SafetyOutlined /> 1. 校验当前草稿
                      </div>
                      <div className="mt-1 text-[12px] leading-5 text-[#667085]">
                        校验当前草稿绑定的运行环境和 Connector；已有 SyncExecution 不参与这次定义校验。
                      </div>
                    </div>
                    <YakButton
                      loading={acting === 'validate'}
                      disabled={Boolean(acting) || !current.spec || runtimeDisabled}
                      onClick={() => void run('validate')}
                    >
                      运行校验
                    </YakButton>
                  </div>
                </div>

                <div className="rounded-xl border border-[#eaecf0] p-4">
                  <div className="flex flex-wrap items-center justify-between gap-3">
                    <div>
                      <div className="flex items-center gap-2 text-[14px] font-semibold text-[#101828]">
                        <CloudServerOutlined /> 2. 发布当前版本
                      </div>
                      <div className="mt-1 text-[12px] leading-5 text-[#667085]">
                        发布形成不可变 DefinitionVersion；运行中发布只推进 Task 的 Published Ref，不修改当前 SyncExecution。
                      </div>
                    </div>
                    <YakButton
                      type="primary"
                      loading={acting === 'publish'}
                      disabled={Boolean(acting) || currentDraftPublished || !current.spec || runtimeDisabled}
                      onClick={() => void run('publish')}
                    >
                      {currentDraftPublished
                        ? '已发布'
                        : running
                          ? '发布当前版本（不影响运行）'
                          : '发布当前版本'}
                    </YakButton>
                  </div>
                </div>

                <div className="rounded-xl border border-[#eaecf0] p-4">
                  <div className="flex flex-wrap items-center justify-between gap-3">
                    <div>
                      <div className="flex items-center gap-2 text-[14px] font-semibold text-[#101828]">
                        <PlayCircleOutlined /> 3. 运行实例
                      </div>
                      <div className="mt-1 text-[12px] leading-5 text-[#667085]">
                        重启固定当前 DefinitionVersion；应用已发布版本是独立的显式升级命令。两个命令都会先预检目标版本，再停止当前稳定运行实例。
                      </div>
                    </div>
                    {running ? (
                      <Space wrap>
                        <YakButton
                          danger
                          icon={<StopOutlined />}
                          loading={acting === 'stop'}
                          disabled={Boolean(acting)}
                          onClick={() => void run('stop')}
                        >
                          停止任务
                        </YakButton>
                        <YakButton
                          icon={<ReloadOutlined />}
                          loading={acting === 'restart-execution'}
                          disabled={Boolean(acting) || !stableRunning}
                          onClick={() => void run('restart-execution')}
                        >
                          重启当前版本
                        </YakButton>
                        {current.publishedUpdateAvailable && (
                          <YakButton
                            type="primary"
                            danger
                            icon={<PlayCircleOutlined />}
                            loading={acting === 'apply-published-version'}
                            disabled={Boolean(acting) || !stableRunning}
                            onClick={() => void run('apply-published-version')}
                          >
                            应用已发布版本
                          </YakButton>
                        )}
                      </Space>
                    ) : (
                      <YakButton
                        type="primary"
                        danger
                        icon={<PlayCircleOutlined />}
                        loading={acting === 'start'}
                        disabled={Boolean(acting) || !startable || blockStartForDisplayedRuntime}
                        onClick={() => void run('start')}
                      >
                        {hasUnpublishedChanges ? `启动已发布 v${current.publishedVersion}` : '启动任务'}
                      </YakButton>
                    )}
                  </div>
                </div>
              </div>
            </Card>

            <div className="space-y-5">
              <Card title="当前状态">
                <Descriptions size="small" column={1}>
                  <Descriptions.Item label="当前草稿">
                    v{current.definitionVersion} · {current.releaseState}
                  </Descriptions.Item>
                  <Descriptions.Item label="已发布版本">
                    {hasPublishedVersion ? `v${current.publishedVersion}` : '未发布'}
                  </Descriptions.Item>
                  <Descriptions.Item label="当前运行定义">
                    {running ? '启动时 DefinitionVersion 快照（保持不变）' : '无活动运行'}
                  </Descriptions.Item>
                  <Descriptions.Item label="已发布更新">
                    {current.publishedUpdateAvailable ? '有，可显式应用' : '无'}
                  </Descriptions.Item>
                  <Descriptions.Item label="运行状态">
                    <Tag>{stateLabel[current.observedState] || current.observedState}</Tag>
                  </Descriptions.Item>
                  <Descriptions.Item label="当前草稿运行环境">
                    {capabilities.runtimeEnvironmentName || `环境 #${current.runtimeEnvironmentId}`}
                  </Descriptions.Item>
                  <Descriptions.Item label="运行实例环境">
                    {current.latestDeployment?.runtimeEnvironment?.name || '无活动运行'}
                  </Descriptions.Item>
                  <Descriptions.Item label="Engine JobId">
                    {current.latestDeployment?.engineJobId || '-'}
                  </Descriptions.Item>
                </Descriptions>
                <YakButton
                  className="mt-3"
                  size="small"
                  icon={<ReloadOutlined />}
                  disabled={Boolean(acting)}
                  onClick={() => void refreshJob()}
                >
                  刷新状态
                </YakButton>
              </Card>

              <Alert
                type="info"
                showIcon
                message="Wizard 与 YAML 共用同一个 SyncDefinition"
                description="编辑方式不会进入运行领域。发布形成不可变 DefinitionVersion；SyncExecution 只引用启动时的版本和运行环境快照。"
              />
            </div>
          </div>
        </div>
      </div>
    </ConfigProvider>
  );
}
