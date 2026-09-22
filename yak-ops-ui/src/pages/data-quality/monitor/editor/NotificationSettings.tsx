import { Input, Select, Switch, Tag } from 'antd';
import { BellRing } from 'lucide-react';

import { EditorSection } from './EditorLayout';
import type { NotificationSettingsState } from './model';

export const NotificationSettings = ({
  value,
  onChange,
}: {
  value: NotificationSettingsState;
  onChange: (value: NotificationSettingsState) => void;
}) => {
  const update = (patch: Partial<NotificationSettingsState>) =>
    onChange({ ...value, ...patch });
  const active = value.notifyEnabled;
  const external = value.notifyChannel !== 'MESSAGE';
  const missingTarget = active && external && !value.notifyTarget.trim();

  return (
    <EditorSection id="notification-settings" title="通知设置">
      <div className="space-y-5">
        <div className="flex items-center justify-between gap-6 rounded-lg bg-[#f7f8fa] px-4 py-3">
          <div className="flex min-w-0 items-center gap-2.5">
            <BellRing size={16} className="shrink-0 text-[#667085]" />
            <div className="text-[13px] font-medium text-[#344054]">
              开启质量通知
            </div>
          </div>
          <Switch
            size="small"
            checked={active}
            onChange={(notifyEnabled) => update({ notifyEnabled })}
          />
        </div>

        <div className="grid grid-cols-3 gap-5 max-lg:grid-cols-2 max-md:grid-cols-1">
          <div>
            <div className="mb-2 text-[12px] font-medium text-[#475467]">
              触发条件
            </div>
            <div className="flex h-8 items-center gap-2">
              <Tag color="error">质量检查未通过 / 执行异常</Tag>
            </div>
          </div>

          <div>
            <div className="mb-2 text-[12px] font-medium text-[#475467]">
              通知方式
            </div>
            <Select
              size="small"
              variant="filled"
              disabled={!active}
              value={value.notifyChannel}
              options={[
                { value: 'MESSAGE', label: '站内消息' },
                { value: 'EMAIL', label: '邮件' },
                { value: 'WEBHOOK', label: 'Webhook' },
              ]}
              onChange={(notifyChannel) => update({ notifyChannel })}
              className="w-full"
            />
          </div>

          <div>
            <div className="mb-2 text-[12px] font-medium text-[#475467]">
              告警级别
            </div>
            <Select
              size="small"
              variant="filled"
              disabled={!active}
              value={value.alertLevel}
              options={[
                { value: 'WARNING', label: '警告' },
                { value: 'CRITICAL', label: '严重' },
              ]}
              onChange={(alertLevel) => update({ alertLevel })}
              className="w-full"
            />
          </div>
        </div>

        {external ? (
          <div className="max-w-[640px]">
            <div className="mb-2 text-[12px] font-medium text-[#475467]">
              {value.notifyChannel === 'EMAIL' ? '接收邮箱' : 'Webhook 地址'}
            </div>
            <Input
              size="small"
              variant="filled"
              disabled={!active}
              value={value.notifyTarget}
              status={missingTarget ? 'error' : undefined}
              placeholder={
                value.notifyChannel === 'EMAIL'
                  ? '请输入邮箱，多个邮箱用逗号分隔'
                  : '请输入 Webhook 地址'
              }
              onChange={(event) => update({ notifyTarget: event.target.value })}
            />
            {missingTarget ? (
              <div className="mt-1.5 text-[11px] text-[#d92d20]">
                {value.notifyChannel === 'EMAIL'
                  ? '请输入告警接收邮箱'
                  : '请输入 Webhook 地址'}
              </div>
            ) : null}
          </div>
        ) : null}
      </div>
    </EditorSection>
  );
};
