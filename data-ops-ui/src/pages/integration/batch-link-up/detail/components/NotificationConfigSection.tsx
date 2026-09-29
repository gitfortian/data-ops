import { QuestionCircleOutlined } from '@ant-design/icons';
import { Popover, Radio, Select, Switch, Tag, Typography } from 'antd';
import { useEffect, useMemo, useState } from 'react';

import { useSecurityProject } from '@/contexts/SecurityProjectContext';
import {
  getSecurityProject,
  type SecurityProjectUser,
} from '@/services/security/projects';

import {
  fetchNotificationAlertChannels,
  type NotificationAlertChannel,
} from '../alertChannels';
import type { SyncEditorState } from '../model';
import EditorSection from './EditorSection';

interface NotificationConfigSectionProps {
  editor: SyncEditorState;
  onChange: (value: SyncEditorState) => void;
}

interface HelpTitleProps {
  label: string;
  help: string;
  prominent?: boolean;
}

function HelpTitle({
  label,
  help,
  prominent = false,
}: HelpTitleProps) {
  return (
    <div className="flex items-center gap-1.5">
      <span
        className={
          prominent
            ? 'text-[13px] font-medium text-[#344054]'
            : 'text-[12px] font-medium text-[#475467]'
        }
      >
        {label}
      </span>
      <Popover
        placement="top"
        trigger={['hover', 'focus']}
        content={(
          <div className="max-w-[320px] text-[12px] leading-5 text-[#475467]">
            {help}
          </div>
        )}
      >
        <button
          type="button"
          aria-label={`${label}说明`}
          className="inline-flex cursor-help items-center border-0 bg-transparent p-0 text-[#98a2b3] outline-none transition-colors hover:text-[#667085] focus:text-[#667085]"
        >
          <QuestionCircleOutlined className="text-[13px]" />
        </button>
      </Popover>
    </div>
  );
}

const uniqueUsers = (
  users: SecurityProjectUser[],
): SecurityProjectUser[] => {
  const result = new Map<number, SecurityProjectUser>();
  users.forEach((user) => {
    if (Number.isSafeInteger(user.id) && user.id > 0) {
      result.set(user.id, user);
    }
  });
  return Array.from(result.values());
};

export default function NotificationConfigSection({
  editor,
  onChange,
}: NotificationConfigSectionProps) {
  const { currentProject } = useSecurityProject();
  const [projectUsers, setProjectUsers] = useState<SecurityProjectUser[]>([]);
  const [loadingUsers, setLoadingUsers] = useState(false);
  const [alertChannels, setAlertChannels] = useState<
    NotificationAlertChannel[]
  >([]);
  const [loadingAlertChannels, setLoadingAlertChannels] = useState(false);

  useEffect(() => {
    let cancelled = false;
    const projectId = Number(currentProject?.id);
    if (!Number.isSafeInteger(projectId) || projectId <= 0) {
      setProjectUsers([]);
      return undefined;
    }

    setLoadingUsers(true);
    getSecurityProject(projectId)
      .then((project) => {
        if (cancelled) return;
        setProjectUsers(
          uniqueUsers([
            ...(project.owners || []),
            ...(project.members || []),
          ]),
        );
      })
      .catch(() => {
        if (!cancelled) setProjectUsers([]);
      })
      .finally(() => {
        if (!cancelled) setLoadingUsers(false);
      });

    return () => {
      cancelled = true;
    };
  }, [currentProject?.id]);

  useEffect(() => {
    let cancelled = false;
    setLoadingAlertChannels(true);
    fetchNotificationAlertChannels()
      .then((channels) => {
        if (!cancelled) setAlertChannels(channels);
      })
      .catch(() => {
        if (!cancelled) setAlertChannels([]);
      })
      .finally(() => {
        if (!cancelled) setLoadingAlertChannels(false);
      });

    return () => {
      cancelled = true;
    };
  }, []);

  const updateNotification = (
    patch: Partial<SyncEditorState['notification']>,
  ) => {
    onChange({
      ...editor,
      notification: {
        ...editor.notification,
        ...patch,
      },
    });
  };

  const userOptions = useMemo(() => {
    const options = projectUsers.map((user) => ({
      value: user.id,
      label: user.realName?.trim()
        ? `${user.realName.trim()} (${user.userName})`
        : user.userName,
    }));
    const known = new Set(options.map((item) => item.value));
    editor.notification.recipientUserIds.forEach((id) => {
      if (!known.has(id)) {
        options.push({
          value: id,
          label: `用户 #${id}（已不在当前项目）`,
        });
      }
    });
    return options;
  }, [editor.notification.recipientUserIds, projectUsers]);

  const alertOptions = useMemo(() => {
    const selected = new Set(editor.notification.alertChannelIds);
    const options = alertChannels.map((channel) => ({
      value: channel.id,
      label: `${channel.name} (${channel.type})${
        channel.enabled ? '' : ' · 已停用'
      }`,
      disabled: !channel.enabled && !selected.has(channel.id),
    }));
    const known = new Set(options.map((item) => item.value));
    editor.notification.alertChannelIds.forEach((id) => {
      if (!known.has(id)) {
        options.push({
          value: id,
          label: `告警渠道 #${id}（已不存在）`,
          disabled: false,
        });
      }
    });
    return options;
  }, [alertChannels, editor.notification.alertChannelIds]);

  const active = editor.notification.enabled;
  const explicit =
    editor.notification.recipientType === 'EXPLICIT_USERS';
  const missingAlertChannel =
    active &&
    editor.notification.alertEnabled &&
    editor.notification.alertChannelIds.length === 0;
  const missingRecipient =
    active &&
    editor.notification.inAppEnabled &&
    explicit &&
    editor.notification.recipientUserIds.length === 0;

  return (
    <EditorSection title="通知设置">
      <div className="space-y-5">
        <div className="flex items-center justify-between gap-6 rounded-lg bg-[#f7f8fa] px-4 py-3">
          <HelpTitle
            label="开启任务通知"
            prominent
            help="仅在重试策略耗尽或不可重试的最终失败时触发，不会为每次失败重试重复发送。"
          />
          <Switch
            checked={active}
            onChange={(enabled) => updateNotification({ enabled })}
          />
        </div>

        <div className="grid grid-cols-2 gap-5 max-md:grid-cols-1">
          <div>
            <div className="mb-2">
              <HelpTitle
                label="触发条件"
                help="当前只开放高价值失败通知，成功通知暂不启用。"
              />
            </div>
            <div className="flex h-8 items-center">
              <Tag color="error">最终执行失败</Tag>
            </div>
          </div>

          <div>
            <div className="mb-2">
              <HelpTitle
                label="通知方式"
                help="外部通知由全局 Alert 渠道和插件统一发送；任务只保存渠道 ID，不保存 Webhook 等敏感配置。"
              />
            </div>
            <div className="space-y-2">
              <div className="flex h-8 items-center gap-3">
                <Switch
                  disabled={!active}
                  checked={editor.notification.inAppEnabled}
                  onChange={(inAppEnabled) =>
                    updateNotification({ inAppEnabled })
                  }
                />
                <span className="text-[12px] text-[#344054]">站内消息</span>
              </div>
              <div className="flex h-8 items-center gap-3">
                <Switch
                  disabled={!active}
                  checked={editor.notification.alertEnabled}
                  onChange={(alertEnabled) =>
                    updateNotification({ alertEnabled })
                  }
                />
                <span className="text-[12px] text-[#344054]">外部告警渠道</span>
              </div>
            </div>
          </div>
        </div>

        {editor.notification.alertEnabled ? (
          <div>
            <div className="mb-2">
              <HelpTitle
                label="外部告警渠道"
                help="仅可选择已经在告警中心保存过配置的渠道；停用渠道不会实际发送。"
              />
            </div>
            <div className="max-w-[640px]">
              <Select
                mode="multiple"
                allowClear
                showSearch
                optionFilterProp="label"
                loading={loadingAlertChannels}
                disabled={!active}
                value={editor.notification.alertChannelIds}
                options={alertOptions}
                placeholder="选择已配置的告警渠道"
                className="w-full"
                status={missingAlertChannel ? 'error' : undefined}
                onChange={(alertChannelIds) =>
                  updateNotification({ alertChannelIds })
                }
              />
              {missingAlertChannel ? (
                <Typography.Text
                  type="danger"
                  className="mt-1.5 block !text-[11px]"
                >
                  开启外部告警后至少选择一个已配置渠道
                </Typography.Text>
              ) : null}
            </div>
          </div>
        ) : null}

        <div>
          <div className="mb-2">
            <HelpTitle
              label="站内消息接收人"
              help="项目负责人会发送给当前 Project 的 OWNER 用户；指定用户仅可选择当前 Project 的负责人和成员，保存时只记录用户 ID。"
            />
          </div>
          <Radio.Group
            disabled={!active || !editor.notification.inAppEnabled}
            value={editor.notification.recipientType}
            onChange={(event) =>
              updateNotification({
                recipientType: event.target.value,
                recipientUserIds:
                  event.target.value === 'PROJECT_OWNER'
                    ? []
                    : editor.notification.recipientUserIds,
              })
            }
          >
            <Radio value="PROJECT_OWNER">项目负责人</Radio>
            <Radio value="EXPLICIT_USERS">指定用户</Radio>
          </Radio.Group>

          {explicit ? (
            <div className="mt-3 max-w-[640px]">
              <Select
                mode="multiple"
                allowClear
                showSearch
                optionFilterProp="label"
                loading={loadingUsers}
                disabled={!active || !editor.notification.inAppEnabled}
                value={editor.notification.recipientUserIds}
                options={userOptions}
                placeholder={
                  currentProject
                    ? '选择当前项目中的接收用户'
                    : '当前没有可用的项目空间'
                }
                className="w-full"
                status={missingRecipient ? 'error' : undefined}
                onChange={(recipientUserIds) =>
                  updateNotification({ recipientUserIds })
                }
              />
              {missingRecipient ? (
                <Typography.Text
                  type="danger"
                  className="mt-1.5 block !text-[11px]"
                >
                  指定用户模式至少选择一个当前项目成员
                </Typography.Text>
              ) : null}
            </div>
          ) : null}
        </div>
      </div>
    </EditorSection>
  );
}
