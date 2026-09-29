import { Drawer, Form, Input, message, Select, TreeSelect, Typography } from 'antd';
import { useEffect, useMemo, useState } from 'react';
import { YakButton } from '@/components/ui';
import { MODELING_DIALECT_OPTIONS } from '@/pages/modeling/constants';
import { updateModelingModel } from '@/services/modeling/api';
import type { ModelingModelRecord, ModelingUpdatePayload } from '@/services/modeling/types';
import type { SemanticDomainNode, SemanticLayerRecord, SemanticProcessRecord } from '@/services/semantic/types';
import { collectDomainSubtreeIds, toDomainTreeData } from '@/services/semantic/domainTree';

interface ModelEditModalProps {
  open: boolean;
  model?: ModelingModelRecord;
  layers: SemanticLayerRecord[];
  processes: SemanticProcessRecord[];
  domainTree: SemanticDomainNode[];
  onClose: () => void;
  onSaved: () => void | Promise<void>;
}

interface EditFormValues {
  name: string;
  dialect: string;
  description?: string;
  layerCode?: string;
  domainId?: number;
  processId?: number;
  sourceDatabase?: string;
  sourceTable?: string;
}

/** 模型基础信息编辑（编码不可改，其余字段均可编辑；业务过程随所选业务域过滤）。 */
const ModelEditModal = ({
  open,
  model,
  layers,
  processes,
  domainTree,
  onClose,
  onSaved,
}: ModelEditModalProps) => {
  const [form] = Form.useForm<EditFormValues>();
  const [saving, setSaving] = useState(false);
  const watchedDomainId = Form.useWatch('domainId', form);
  const watchedDialect = Form.useWatch('dialect', form);

  // 过程挂在域树子节点下：选中域 → 其下全部过程（F-2 同根）
  const domainSubtreeIds = useMemo(
    () => (watchedDomainId != null ? collectDomainSubtreeIds(domainTree, watchedDomainId) : null),
    [domainTree, watchedDomainId],
  );
  const filteredProcesses = useMemo(
    () => (domainSubtreeIds ? processes.filter((process) => domainSubtreeIds.has(process.domainId)) : processes),
    [processes, domainSubtreeIds],
  );

  useEffect(() => {
    if (!open) return;
    form.setFieldsValue({
      name: model?.name || '',
      dialect: model?.dialect || undefined,
      description: model?.description || '',
      layerCode: model?.layerCode || undefined,
      domainId: model?.domainId ?? undefined,
      processId: model?.processId ?? undefined,
      sourceDatabase: model?.sourceDatabase || undefined,
      sourceTable: model?.sourceTable || undefined,
    });
  }, [form, open, model]);

  // 域变化后原过程可能不属于该域，清空避免提交脏引用（与新建向导一致）
  const handleDomainChange = () => {
    form.setFieldsValue({ processId: undefined });
  };

  const handleClose = () => {
    if (saving) return;
    form.resetFields();
    onClose();
  };

  const handleSubmit = async () => {
    if (!model?.id) return;
    try {
      const values = await form.validateFields();
      setSaving(true);
      const payload: ModelingUpdatePayload = {
        name: values.name.trim(),
        dialect: values.dialect,
        description: values.description?.trim() || undefined,
        layerCode: values.layerCode ?? null,
        domainId: values.domainId ?? 0,
        processId: values.processId ?? 0,
      };
      if (values.sourceTable?.trim()) {
        payload.sourceDatabase = values.sourceDatabase?.trim() || undefined;
        payload.sourceTable = values.sourceTable.trim();
      }
      await updateModelingModel(model.id, payload);
      message.success('模型已更新');
      form.resetFields();
      await onSaved();
      onClose();
    } catch (error) {
      if (error instanceof Error) message.error(error.message);
    } finally {
      setSaving(false);
    }
  };

  return (
    <Drawer
      open={open}
      width={480}
      title="编辑模型基础信息"
      onClose={handleClose}
      destroyOnClose
      extra={
        <YakButton
          type="primary"
          className="!h-9 !rounded-lg !px-4 !text-white"
          loading={saving}
          onClick={() => void handleSubmit()}
        >
          保存
        </YakButton>
      }
    >
      <Form form={form} layout="vertical" requiredMark="optional">
        <Form.Item label="模型编码" extra="编码是模型的稳定标识，创建后不可修改">
          <Input variant="filled" value={model?.code} disabled />
        </Form.Item>
        <Form.Item
          name="name"
          label="模型名称"
          required
          rules={[
            { required: true, message: '请输入模型名称' },
            { max: 128, message: '模型名称不能超过 128 个字符' },
          ]}
        >
          <Input variant="filled" />
        </Form.Item>
        <Form.Item name="layerCode" label="模型分层">
          <Select
            variant="filled"
            allowClear
            placeholder="请选择"
            options={layers.map((item) => ({ value: item.code, label: `${item.name} (${item.code})` }))}
          />
        </Form.Item>
        <Form.Item name="domainId" label="业务域">
          <TreeSelect
            variant="filled"
            allowClear
            treeDefaultExpandAll
            placeholder="请选择业务域（仅顶层业务域可选）"
            treeData={toDomainTreeData(domainTree)}
            onChange={handleDomainChange}
          />
        </Form.Item>
        <Form.Item name="processId" label="业务过程">
          <Select
            variant="filled"
            allowClear
            showSearch
            optionFilterProp="label"
            placeholder="请选择"
            options={filteredProcesses.map((item) => ({ value: item.id, label: `${item.name}（${item.code}）` }))}
          />
        </Form.Item>
        <Form.Item
          name="dialect"
          label="目标方言"
          required
          rules={[{ required: true, message: '请选择目标方言' }]}
          // 07 号单:换方言后原字段类型可能不在新方言类型目录内,提示去重跑校验
          extra={
            watchedDialect && model?.dialect && watchedDialect !== model.dialect ? (
              <Typography.Text type="warning" className="!text-[12px]">
                方言已变更：已有字段类型可能不被新方言支持，保存后请到「表结构」重新执行「校验」
              </Typography.Text>
            ) : undefined
          }
        >
          <Select variant="filled" options={MODELING_DIALECT_OPTIONS.map(({ value, label }) => ({ value, label }))} />
        </Form.Item>
        <Form.Item
          name="description"
          label="模型描述"
          rules={[{ max: 512, message: '模型描述不能超过 512 个字符' }]}
        >
          <Input.TextArea variant="filled" rows={3} placeholder="补充模型的业务含义与用途" />
        </Form.Item>
        {model?.sourceDatasourceId || model?.sourceTable || model?.sourceDatabase ? (
          <Form.Item label="来源信息（逆向导入写入）">
            <div className="flex gap-2">
              <Form.Item name="sourceDatabase" noStyle>
                <Input variant="filled" placeholder="来源库" className="!w-1/2" />
              </Form.Item>
              <Form.Item name="sourceTable" noStyle>
                <Input variant="filled" placeholder="来源表" className="!w-1/2" />
              </Form.Item>
            </div>
          </Form.Item>
        ) : null}
      </Form>
    </Drawer>
  );
};

export default ModelEditModal;
