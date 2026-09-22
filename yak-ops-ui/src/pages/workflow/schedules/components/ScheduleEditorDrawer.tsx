import CronSchedulerInput from '@/components/CronSchedulerEditor/CronSchedulerInput';
import YakButton from '@/components/YakButton';
import type { WorkflowDefinition } from '@/services/workflow/definitions';
import {
  createWorkflowSchedule,
  updateWorkflowSchedule,
  type WorkflowSchedule,
  type WorkflowScheduleExecutionStrategy,
  type WorkflowScheduleMisfireStrategy,
} from '@/services/workflow/schedules';
import { DatePicker, Drawer, Form, Input, Select, message } from 'antd';
import type { Dayjs } from 'dayjs';
import dayjs from 'dayjs';
import { useEffect, useState } from 'react';

interface FormValues {
  workflowId: string;
  name: string;
  cronExpression: string;
  timezone: string;
  effectiveRange?: [Dayjs, Dayjs];
  executionStrategy: WorkflowScheduleExecutionStrategy;
  misfireStrategy: WorkflowScheduleMisfireStrategy;
  inputJson: string;
}

interface Props {
  open: boolean;
  definitions: WorkflowDefinition[];
  workflowId?: string;
  schedule?: WorkflowSchedule;
  lockWorkflow?: boolean;
  onClose: () => void;
  onSaved: () => Promise<void> | void;
}

const ScheduleEditorDrawer = ({
  open,
  definitions,
  workflowId,
  schedule,
  lockWorkflow = false,
  onClose,
  onSaved,
}: Props) => {
  const [form] = Form.useForm<FormValues>();
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (!open) return;
    form.setFieldsValue({
      workflowId: schedule?.workflowId || workflowId,
      name: schedule?.name || '',
      cronExpression: schedule?.cronExpression || '0 0 2 * * ?',
      timezone: schedule?.timezone || 'Asia/Shanghai',
      effectiveRange:
        schedule?.startTime && schedule?.endTime
          ? [dayjs(schedule.startTime), dayjs(schedule.endTime)]
          : undefined,
      executionStrategy: schedule?.executionStrategy || 'SERIAL_WAIT',
      misfireStrategy: schedule?.misfireStrategy || 'FIRE_ONCE',
      inputJson: JSON.stringify(schedule?.input || {}, null, 2),
    });
  }, [form, open, schedule, workflowId]);

  const handleSave = async () => {
    try {
      const values = await form.validateFields();
      let input: Record<string, unknown> = {};
      try {
        input = values.inputJson.trim() ? JSON.parse(values.inputJson) : {};
      } catch {
        message.error('运行参数必须是合法 JSON');
        return;
      }

      const payload = {
        name: values.name.trim(),
        cronExpression: values.cronExpression.trim(),
        timezone: values.timezone,
        startTime: values.effectiveRange?.[0]?.toISOString(),
        endTime: values.effectiveRange?.[1]?.toISOString(),
        executionStrategy: values.executionStrategy,
        misfireStrategy: values.misfireStrategy,
        input,
      };

      setSaving(true);
      if (schedule) await updateWorkflowSchedule(schedule.id, payload);
      else await createWorkflowSchedule(values.workflowId, payload);
      message.success(schedule ? '调度配置已保存' : '调度定义已创建');
      await onSaved();
      onClose();
    } catch (error: any) {
      if (error?.errorFields) return;
      message.error(error instanceof Error ? error.message : '保存调度失败');
    } finally {
      setSaving(false);
    }
  };

  return (
    <Drawer
      open={open}
      width={760}
      destroyOnClose
      title={schedule ? '编辑调度' : '新建调度'}
      onClose={onClose}
      extra={
        <div className="flex gap-2">
          <YakButton onClick={onClose}>取消</YakButton>
          <YakButton type="primary" loading={saving} onClick={() => void handleSave()}>
            保存
          </YakButton>
        </div>
      }
    >
      <Form form={form} layout="vertical" requiredMark="optional">
        <Form.Item name="workflowId" label="工作流" rules={[{ required: true, message: '请选择工作流' }]}>
          <Select
            showSearch
            disabled={Boolean(schedule || lockWorkflow)}
            optionFilterProp="label"
            placeholder="选择要配置调度的工作流"
            options={definitions.map((item) => ({
              value: item.id,
              label: `${item.name} · ${item.status === 'ONLINE' ? '已上线' : item.status === 'DRAFT' ? '草稿' : '已下线'}`,
            }))}
          />
        </Form.Item>

        <Form.Item name="name" label="调度名称" rules={[{ required: true, message: '请输入调度名称' }, { max: 100 }]}>
          <Input variant="filled" placeholder="例如：每日凌晨订单同步" />
        </Form.Item>

        <Form.Item
          name="cronExpression"
          label="Cron 表达式"
          extra="点击输入框可使用可视化 Cron 调度配置；也可以直接手动输入 Quartz Cron。"
          rules={[{ required: true, message: '请配置 Cron 表达式' }]}
        >
          <CronSchedulerInput />
        </Form.Item>

        <div className="grid grid-cols-2 gap-4 max-sm:grid-cols-1">
          <Form.Item name="timezone" label="时区" rules={[{ required: true }]}>
            <Select
              options={[
                { value: 'Asia/Shanghai', label: 'Asia/Shanghai' },
                { value: 'Asia/Tokyo', label: 'Asia/Tokyo' },
                { value: 'UTC', label: 'UTC' },
              ]}
            />
          </Form.Item>
          <Form.Item name="effectiveRange" label="生效区间（可选）">
            <DatePicker.RangePicker showTime className="w-full" />
          </Form.Item>
        </div>

        <div className="grid grid-cols-2 gap-4 max-sm:grid-cols-1">
          <Form.Item
            name="executionStrategy"
            label="实例并发策略"
            extra="等待：排队到前序终态；跳过：已有实例时记为 SKIPPED；并行：允许同时创建实例。"
            rules={[{ required: true }]}
          >
            <Select
              options={[
                { value: 'SERIAL_WAIT', label: '等待上一次完成（推荐）' },
                { value: 'SERIAL_DISCARD', label: '上一次未完成则跳过' },
                { value: 'PARALLEL', label: '允许并行运行' },
              ]}
            />
          </Form.Item>
          <Form.Item
            name="misfireStrategy"
            label="错过调度策略"
            extra="服务恢复时 FIRE_ONCE 合并补跑一次；SKIP 会跳过并保留 Ledger 审计记录。"
            rules={[{ required: true }]}
          >
            <Select
              options={[
                { value: 'FIRE_ONCE', label: '恢复后补跑一次' },
                { value: 'SKIP', label: '直接跳过' },
              ]}
            />
          </Form.Item>
        </div>

        <Form.Item
          name="inputJson"
          label="调度运行参数 JSON"
          extra="运行时合并顺序：工作流版本参数 < 调度参数 < Backfill 参数 < 系统参数。系统会按逻辑计划时间注入 businessDate、scheduleTime、scheduleTimezone、triggerType、scheduleId，并在 __schedule 中保留完整副本。"
        >
          <Input.TextArea rows={7} spellCheck={false} className="font-mono text-[12px]" />
        </Form.Item>
      </Form>
    </Drawer>
  );
};

export default ScheduleEditorDrawer;