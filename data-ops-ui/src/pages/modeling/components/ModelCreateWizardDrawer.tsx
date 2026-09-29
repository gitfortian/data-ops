import { Drawer, Form, Input, Select, TreeSelect } from 'antd';
import { useEffect, useRef, useState } from 'react';
import { YakButton } from '@/components/ui';
import { MODELING_DIALECT_OPTIONS, toSnakeCase } from '@/pages/modeling/constants';
import type { ModelingCreatePayload, ModelingDialect } from '@/services/modeling/types';
import type { SemanticDomainNode, SemanticLayerRecord, SemanticProcessRecord } from '@/services/semantic/types';
import { collectDomainSubtreeIds, toDomainTreeData } from '@/services/semantic/domainTree';

export interface WizardSubmitPayload {
  base: ModelingCreatePayload;
}

interface WizardFormValues {
  name: string;
  code: string;
  layerCode: string;
  dialect: ModelingDialect;
  domainId?: number;
  processId?: number;
  description?: string;
}

interface ModelCreateWizardDrawerProps {
  open: boolean;
  loading: boolean;
  layers: SemanticLayerRecord[];
  processes: SemanticProcessRecord[];
  domainTree: SemanticDomainNode[];
  onClose: () => void;
  onSubmit: (payload: WizardSubmitPayload) => void;
}

const ModelCreateWizardDrawer = ({
  open,
  loading,
  layers,
  processes,
  domainTree,
  onClose,
  onSubmit,
}: ModelCreateWizardDrawerProps) => {
  const [form] = Form.useForm<WizardFormValues>();
  const codeManuallyEdited = useRef(false);

  useEffect(() => {
    if (!open) return;
    form.resetFields();
    codeManuallyEdited.current = false;
  }, [form, open]);

  const handleClose = () => {
    if (loading) return;
    form.resetFields();
    codeManuallyEdited.current = false;
    onClose();
  };

  const selectedLayerCode = Form.useWatch('layerCode', form);
  const selectedName = Form.useWatch('name', form);
  const selectedDomainId = Form.useWatch('domainId', form);

  // 过程挂在域树子节点下：选中域 → 其下全部过程（F-2 同根）
  const filteredProcesses = selectedDomainId
    ? processes.filter((p) => collectDomainSubtreeIds(domainTree, selectedDomainId).has(p.domainId))
    : processes;

  useEffect(() => {
    if (!selectedName || codeManuallyEdited.current) return;
    const snake = toSnakeCase(selectedName);
    const code = selectedLayerCode ? `${selectedLayerCode.toLowerCase()}_${snake}` : snake;
    form.setFieldsValue({ code: code.replace(/_+/g, '_').replace(/^_+|_+$/g, '') });
  }, [selectedName, selectedLayerCode, form]);

  // 业务域变化时，清空业务过程
  useEffect(() => {
    form.setFieldsValue({ processId: undefined });
  }, [selectedDomainId, form]);

  const handleNext = async () => {
    try {
      const values = await form.validateFields();
      const base: ModelingCreatePayload = {
        name: values.name.trim(),
        code: values.code.trim(),
        dialect: values.dialect,
        description: values.description?.trim() || undefined,
        layerCode: values.layerCode,
        domainId: values.domainId,
        processId: values.processId,
      };
      onSubmit({ base });
    } catch {
      // 表单校验失败由 Form 自身展示
    }
  };

  return (
    <Drawer
      open={open}
      width={600}
      placement="right"
      closable={false}
      destroyOnClose
      maskClosable={!loading}
      keyboard={!loading}
      onClose={handleClose}
      title={<div className="text-[18px] font-semibold leading-7 text-[#101828]">新建模型</div>}
      extra={
        <div className="flex items-center gap-2">
          <YakButton disabled={loading} onClick={handleClose} className="!h-9 !rounded-lg !px-4">
            取消
          </YakButton>
          <YakButton
            type="primary"
            loading={loading}
            onClick={() => void handleNext()}
            className="!h-9 !rounded-lg !px-5 !text-white"
          >
            下一步
          </YakButton>
        </div>
      }
      styles={{
        header: { padding: '18px 24px', borderBottom: '1px solid #eaecf0' },
        body: { padding: 24 },
      }}
    >
      <Form form={form} layout="vertical" requiredMark initialValues={{ dialect: 'MYSQL' }}>
        <Form.Item
          name="name"
          label="模型名称"
          required
          rules={[
            { required: true, message: '请输入模型名称' },
            { max: 128, message: '模型名称不能超过 128 个字符' },
          ]}
        >
          <Input variant="filled" placeholder="如：用户维度表" className="!h-[44px] !rounded-[10px]" />
        </Form.Item>

        <Form.Item
          name="code"
          label="模型编码"
          required
          rules={[
            { required: true, message: '请输入模型编码' },
            {
              pattern: /^[A-Za-z0-9_][A-Za-z0-9_-]{0,127}$/,
              message: '仅允许字母、数字、下划线和中划线，且以字母、数字或下划线开头',
            },
          ]}
        >
          <Input
            variant="filled"
            placeholder="如：dim_user"
            className="!h-[44px] !rounded-[10px]"
            onChange={() => {
              codeManuallyEdited.current = true;
            }}
          />
        </Form.Item>

        <Form.Item
          name="layerCode"
          label="模型分层"
          required
          rules={[{ required: true, message: '请选择目标分层' }]}
        >
          <Select
            variant="filled"
            placeholder="请选择"
            options={layers.map((item) => ({ value: item.code, label: `${item.name} (${item.code})` }))}
          />
        </Form.Item>

        {/* 07 号单:六方言与后端 ModelDialect 同源(原硬编码 MYSQL) */}
        <Form.Item
          name="dialect"
          label="目标方言"
          required
          rules={[{ required: true, message: '请选择目标方言' }]}
        >
          <Select
            variant="filled"
            placeholder="请选择"
            options={MODELING_DIALECT_OPTIONS.map(({ value, label }) => ({ value, label }))}
          />
        </Form.Item>

        <Form.Item
          name="domainId"
          label="业务域"
          required
          rules={[{ required: true, message: '请选择业务域' }]}
        >
          <TreeSelect
            variant="filled"
            allowClear
            treeDefaultExpandAll
            placeholder="请选择业务域（仅顶层业务域可选）"
            treeData={toDomainTreeData(domainTree)}
          />
        </Form.Item>

        <Form.Item name="processId" label="业务过程">
          <Select
            variant="filled"
            allowClear
            placeholder="请选择"
            options={filteredProcesses.map((item) => ({ value: item.id, label: `${item.name}（${item.code}）` }))}
          />
        </Form.Item>

        <Form.Item name="description" label="模型描述" rules={[{ max: 512, message: '模型描述不能超过 512 个字符' }]}>
          <Input.TextArea variant="filled" rows={3} placeholder="补充模型的业务含义与用途" />
        </Form.Item>
      </Form>
    </Drawer>
  );
};

export default ModelCreateWizardDrawer;
