import { Alert, Drawer, Form, Input, InputNumber, message, Select, Switch } from 'antd';
import { useCallback, useEffect, useState } from 'react';
import type { ReactNode } from 'react';
import { YakButton } from '@/components/ui';
import { createPolicy, listLayerTemplates, updatePolicy } from '@/services/data-lifecycle/api';
import type { Granularity, LayerTemplate, PolicyRecord, PolicyScope } from '@/services/data-lifecycle/types';
import { GRANULARITY_LABELS, LAYER_LABELS, POLICY_SCOPE_LABELS } from '../../constants';

interface PolicyEditDrawerProps {
  open: boolean;
  editing: PolicyRecord | null;
  onClose: () => void;
  onSaved: () => void;
}

interface PolicyFormValues {
  policyName: string;
  scopeType: PolicyScope;
  layerCode?: string;
  partitionGranularity: Granularity;
  hotDays?: number | null;
  coldDays?: number | null;
  destroyDays?: number | null;
  hotPermanent?: boolean;
  coldPermanent?: boolean;
  destroyPermanent?: boolean;
  remark?: string;
}

/** 顺序校验:仅当两段都有值时比较(null=不限/永久视为无穷大)。 */
const orderError = (hot?: number | null, cold?: number | null, destroy?: number | null): string | null => {
  if (hot != null && cold != null && hot > cold) return '热数据保留期不能大于冷数据';
  if (cold != null && destroy != null && cold > destroy) return '冷数据保留期不能大于销毁周期';
  if (hot != null && destroy != null && hot > destroy) return '热数据保留期不能大于销毁周期';
  return null;
};

const RetentionRow = ({
  label,
  hint,
  permanent,
  permanentText = '不限',
  onPermanentChange,
  children,
}: {
  label: string;
  hint: string;
  permanent: boolean;
  permanentText?: string;
  onPermanentChange: (checked: boolean) => void;
  children: ReactNode;
}) => (
  <div className="mb-3 flex items-center gap-3">
    <div className="w-[110px] shrink-0">
      <div className="text-[13px] font-medium text-[#101828]">{label}</div>
      <div className="text-[12px] text-[#667085]">{hint}</div>
    </div>
    <div className="flex-1">{permanent ? null : children}</div>
    <Switch checked={permanent} onChange={onPermanentChange} size="small" />
    <span className="w-[64px] text-[12px] text-[#475467]">{permanent ? permanentText : '按天数'}</span>
  </div>
);

const PolicyEditDrawer = ({ open, editing, onClose, onSaved }: PolicyEditDrawerProps) => {
  const [form] = Form.useForm<PolicyFormValues>();
  const [saving, setSaving] = useState(false);
  const [templates, setTemplates] = useState<LayerTemplate[]>([]);
  const hotPermanent = Form.useWatch('hotPermanent', form) as boolean | undefined;
  const coldPermanent = Form.useWatch('coldPermanent', form) as boolean | undefined;
  const destroyPermanent = Form.useWatch('destroyPermanent', form) as boolean | undefined;
  const scopeType = Form.useWatch('scopeType', form) as PolicyScope | undefined;
  const isEdit = Boolean(editing);

  useEffect(() => {
    if (!open) return;
    listLayerTemplates()
      .then(setTemplates)
      .catch(() => setTemplates([]));
  }, [open]);

  useEffect(() => {
    if (!open) return;
    if (editing) {
      form.setFieldsValue({
        policyName: editing.policyName,
        scopeType: editing.scopeType,
        layerCode: editing.layerCode ?? undefined,
        partitionGranularity: editing.partitionGranularity,
        hotDays: editing.hotDays,
        coldDays: editing.coldDays,
        destroyDays: editing.destroyDays,
        hotPermanent: editing.hotDays == null,
        coldPermanent: editing.coldDays == null,
        destroyPermanent: editing.destroyDays == null,
        remark: editing.remark ?? undefined,
      });
    } else {
      form.resetFields();
      form.setFieldsValue({
        scopeType: 'CUSTOM',
        partitionGranularity: 'DAY',
        hotDays: null,
        coldDays: null,
        destroyDays: null,
        hotPermanent: false,
        coldPermanent: false,
        destroyPermanent: true,
      });
    }
  }, [open, editing, form]);

  const applyTemplate = useCallback(
    (layerCode: string) => {
      const t = templates.find((item) => item.layerCode.toUpperCase() === layerCode.toUpperCase());
      if (!t) return;
      form.setFieldsValue({
        partitionGranularity: t.partitionGranularity,
        hotDays: t.hotDays ?? null,
        coldDays: t.coldDays ?? null,
        destroyDays: t.destroyDays ?? null,
        hotPermanent: t.hotDays == null,
        coldPermanent: t.coldDays == null,
        destroyPermanent: t.destroyDays == null,
      } as PolicyFormValues);
      if (!form.getFieldValue('policyName')) {
        form.setFieldsValue({ policyName: `${t.layerName}策略` });
      }
    },
    [templates, form],
  );

  const handleSubmit = async () => {
    const values = await form.validateFields();
    const permanent = (name: 'hotPermanent' | 'coldPermanent' | 'destroyPermanent') =>
      form.getFieldValue(name) === true;
    const params = {
      policyName: values.policyName.trim(),
      scopeType: values.scopeType,
      layerCode: values.scopeType === 'LAYER_DEFAULT' ? values.layerCode : undefined,
      partitionGranularity: values.partitionGranularity,
      hotDays: permanent('hotPermanent') ? null : values.hotDays ?? null,
      coldDays: permanent('coldPermanent') ? null : values.coldDays ?? null,
      destroyDays: permanent('destroyPermanent') ? null : values.destroyDays ?? null,
      remark: values.remark?.trim() || undefined,
    };
    const error = orderError(params.hotDays, params.coldDays, params.destroyDays);
    if (error) {
      message.warning(error);
      return;
    }
    setSaving(true);
    try {
      if (editing) {
        await updatePolicy(editing.id, params);
        message.success('草稿已保存，发布后对模型生效');
      } else {
        await createPolicy(params);
        message.success('策略草稿已创建，发布后对模型生效');
      }
      onSaved();
    } catch {
      // 全局错误提示已展示业务原因（如 47002 保留期顺序非法）
    } finally {
      setSaving(false);
    }
  };

  return (
    <Drawer
      open={open}
      width={560}
      placement="right"
      closable={false}
      destroyOnClose
      maskClosable={!saving}
      keyboard={!saving}
      onClose={onClose}
      title={
        <div className="text-[18px] font-semibold leading-7 text-[#101828]">
          {isEdit ? `编辑策略${editing?.builtin ? '（内置）' : ''}` : '新建策略'}
        </div>
      }
      extra={
        <div className="flex items-center gap-2">
          <YakButton disabled={saving} onClick={onClose} className="!h-9 !rounded-lg !px-4">
            取消
          </YakButton>
          <YakButton
            type="primary"
            loading={saving}
            onClick={() => void handleSubmit()}
            className="!h-9 !rounded-lg !px-5 !text-white"
          >
            保存草稿
          </YakButton>
        </div>
      }
      styles={{
        header: { padding: '18px 24px', borderBottom: '1px solid #eaecf0' },
        body: { padding: 24 },
      }}
    >
      {isEdit ? (
        <Alert
          className="mb-4"
          type="info"
          showIcon
          message={`当前策略被 ${(editing?.referenceCount ?? 0)} 个绑定模型引用；编辑仅存草稿，发布后下发才使用新值，已下线/草稿修改不影响线上。`}
        />
      ) : null}
      <Form form={form} layout="vertical" requiredMark="optional">
        <Form.Item
          name="policyName"
          label="策略名称"
          required
          rules={[
            { required: true, message: '请输入策略名称' },
            { max: 128, message: '策略名称不能超过 128 个字符' },
          ]}
        >
          <Input variant="filled" placeholder="如：DWD 默认策略" className="!h-[44px] !rounded-[10px]" />
        </Form.Item>

        <div className="mb-4 grid grid-cols-2 gap-3">
          <Form.Item name="scopeType" label="适用范围" className="!mb-0">
            <Select
              variant="filled"
              disabled={isEdit}
              options={(Object.keys(POLICY_SCOPE_LABELS) as PolicyScope[]).map((value) => ({
                value,
                label: POLICY_SCOPE_LABELS[value],
              }))}
            />
          </Form.Item>
          <Form.Item
            name="layerCode"
            label="所属分层"
            className="!mb-0"
            rules={scopeType === 'LAYER_DEFAULT' ? [{ required: true, message: '请选择分层' }] : undefined}
          >
            <Select
              variant="filled"
              allowClear
              disabled={isEdit || scopeType !== 'LAYER_DEFAULT'}
              placeholder={scopeType === 'LAYER_DEFAULT' ? '选择后将预填推荐保留期' : '仅分层默认策略需要'}
              onChange={(value?: string) => value && applyTemplate(value)}
              options={templates.map((t) => ({
                value: t.layerCode,
                label: LAYER_LABELS[t.layerCode.toUpperCase()] ?? t.layerCode,
              }))}
            />
          </Form.Item>
        </div>

        <Form.Item name="partitionGranularity" label="分区粒度" className="mt-4">
          <Select
            variant="filled"
            options={(Object.keys(GRANULARITY_LABELS) as Granularity[]).map((value) => ({
              value,
              label: GRANULARITY_LABELS[value],
            }))}
          />
        </Form.Item>

        <div className="mb-2 text-[13px] font-medium text-[#101828]">保留周期</div>
        <div className="mb-4 rounded-[10px] bg-[#f9fafb] p-4">
          <RetentionRow
            label="热数据"
            hint="近期高频访问"
            permanent={Boolean(hotPermanent)}
            onPermanentChange={(checked) =>
              form.setFieldsValue({ hotPermanent: checked, hotDays: checked ? null : 7 } as PolicyFormValues)
            }
          >
            <Form.Item name="hotDays" className="!mb-0">
              <InputNumber
                variant="filled"
                min={1}
                max={36500}
                precision={0}
                addonAfter="天"
                placeholder="如 7"
                className="!w-full !rounded-[10px]"
              />
            </Form.Item>
          </RetentionRow>
          <RetentionRow
            label="冷数据"
            hint="低频访问仍保留"
            permanent={Boolean(coldPermanent)}
            onPermanentChange={(checked) =>
              form.setFieldsValue({ coldPermanent: checked, coldDays: checked ? null : 30 } as PolicyFormValues)
            }
          >
            <Form.Item name="coldDays" className="!mb-0">
              <InputNumber
                variant="filled"
                min={1}
                max={36500}
                precision={0}
                addonAfter="天"
                placeholder="如 30"
                className="!w-full !rounded-[10px]"
              />
            </Form.Item>
          </RetentionRow>
          <RetentionRow
            label="销毁"
            hint="超过后由存储自动清理分区"
            permanent={Boolean(destroyPermanent)}
            permanentText="永久保留"
            onPermanentChange={(checked) =>
              form.setFieldsValue({ destroyPermanent: checked, destroyDays: checked ? null : 90 } as PolicyFormValues)
            }
          >
            <Form.Item name="destroyDays" className="!mb-0">
              <InputNumber
                variant="filled"
                min={1}
                max={36500}
                precision={0}
                addonAfter="天"
                placeholder="如 90"
                className="!w-full !rounded-[10px]"
              />
            </Form.Item>
          </RetentionRow>
          <div className="mt-1 text-[12px] text-[#667085]">要求：热 ≤ 冷 ≤ 销毁；「不限」表示该段不设期限。</div>
        </div>

        <Form.Item name="remark" label="备注" rules={[{ max: 512, message: '备注不能超过 512 个字符' }]}>
          <Input.TextArea variant="filled" rows={3} placeholder="补充策略用途说明（可选）" />
        </Form.Item>
      </Form>
    </Drawer>
  );
};

export default PolicyEditDrawer;
