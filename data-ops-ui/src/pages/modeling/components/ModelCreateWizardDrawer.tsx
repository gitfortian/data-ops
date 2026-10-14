import { Alert, Button, Drawer, Form, Input, Modal, Select, Space, Tag, TreeSelect, Typography } from 'antd';
import { useEffect, useRef } from 'react';
import { YakButton } from '@/components/ui';
import { MODELING_DIALECT_OPTIONS, toSnakeCase } from '@/pages/modeling/constants';
import type { ModelingCreatePayload, ModelingDialect } from '@/services/modeling/types';
import type { SemanticDomainNode, SemanticLayerRecord, SemanticProcessRecord } from '@/services/semantic/types';
import { collectDomainSubtreeIds, toDomainTreeData } from '@/services/semantic/domainTree';
import { creationNextStep, requiresLogicalBasis, rootDomainForProcess, verifiedProcessInDomain } from './modelCreationScope';
import ModelCreationLogicalBasis from './ModelCreationLogicalBasis';

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
  /** This route ID must already have been resolved through Semantic Owner. */
  initialProcessId?: number;
  onClose: () => void;
  onSubmit: (payload: WizardSubmitPayload) => void;
  onGoLogical?: (processId: number) => void;
  onGoSource?: () => void;
}

const ModelCreateWizardDrawer = ({
  open, loading, layers, processes, domainTree, initialProcessId,
  onClose, onSubmit, onGoLogical, onGoSource,
}: ModelCreateWizardDrawerProps) => {
  const [form] = Form.useForm<WizardFormValues>();
  const codeManuallyEdited = useRef(false);
  const initialized = useRef(false);
  const selectedLayerCode = Form.useWatch('layerCode', form);
  const selectedName = Form.useWatch('name', form);
  const selectedDomainId = Form.useWatch('domainId', form);
  const selectedProcessId = Form.useWatch('processId', form);
  const verifiedInitial = initialProcessId && processes.find(p => p.id === initialProcessId);
  const initialRoot = verifiedInitial ? rootDomainForProcess(domainTree, verifiedInitial.domainId) : undefined;
  const selectedProcess = verifiedProcessInDomain(domainTree, processes, selectedDomainId, selectedProcessId);
  const filteredProcesses = selectedDomainId && domainTree.some(x => x.id === selectedDomainId)
    ? processes.filter(p => collectDomainSubtreeIds(domainTree, selectedDomainId).has(p.domainId))
    : [];
  const processScoped = Boolean(initialProcessId);
  const scopeNotReady = processScoped && (!verifiedInitial || !initialRoot);
  const hasDomainOptions = domainTree.length > 0;
  const hasLayerOptions = layers.length > 0;

  useEffect(() => {
    if (!open) {
      initialized.current = false;
      return;
    }
    // Wait for the actual Semantic domain owner tree, rather than setting only
    // a process ID and losing its business domain on a watcher re-render.
    if (scopeNotReady || initialized.current) return;
    form.resetFields();
    if (initialRoot && initialProcessId) {
      form.setFieldsValue({ domainId: initialRoot, processId: initialProcessId });
    }
    codeManuallyEdited.current = false;
    initialized.current = true;
  }, [form, open, initialProcessId, initialRoot, scopeNotReady]);

  useEffect(() => {
    if (!open || !selectedName || codeManuallyEdited.current) return;
    const snake = toSnakeCase(selectedName);
    const code = selectedLayerCode ? `${selectedLayerCode.toLowerCase()}_${snake}` : snake;
    form.setFieldsValue({ code: code.replace(/_+/g, '_').replace(/^_+|_+$/g, '') });
  }, [selectedName, selectedLayerCode, form, open]);

  const discardAndExit = (callback: () => void) => {
    if (loading) return;
    if (form.isFieldsTouched()) {
      Modal.confirm({
        title: '物理模型草稿尚未创建',
        content: '离开将放弃向导中尚未提交的名称、业务过程和物理配置。需要保存时请选择“继续编辑”。',
        cancelText: '继续编辑', okText: '放弃并离开', okType: 'danger',
        onOk: () => { form.resetFields(); callback(); },
      });
    } else {
      form.resetFields();
      callback();
    }
  };

  const handleSubmit = async () => {
    if (loading || !open || scopeNotReady || !hasDomainOptions || !hasLayerOptions || !initialized.current) return;
    try {
      const values = await form.validateFields();
      const domain = domainTree.find(root => root.id === values.domainId);
      if (!domain || !layers.some(layer => layer.code === values.layerCode)
          || !MODELING_DIALECT_OPTIONS.some(o => o.value === values.dialect)) {
        Modal.warning({ title: '当前配置已变化', content: '业务域、分层或方言不再是当前项目中可核验的配置，请刷新并重新选择。' });
        return;
      }
      if (values.processId !== undefined && !verifiedProcessInDomain(domainTree, processes, values.domainId, values.processId)) {
        form.setFields([{ name: 'processId', errors: ['业务过程与当前业务域不一致或已失效，请重新选择'] }]);
        return;
      }
      if (initialProcessId && (values.processId !== initialProcessId || values.domainId !== initialRoot)) {
        Modal.warning({ title: '业务过程上下文失效', content: '请从业务过程工作台重新进入，以免创建错误归属的物理模型。' });
        return;
      }
      onSubmit({ base: {
        name: values.name.trim(), code: values.code.trim(), dialect: values.dialect,
        description: values.description?.trim() || undefined, layerCode: values.layerCode,
        domainId: values.domainId, processId: values.processId,
      } });
    } catch {
      // Validation messages belong to Ant Form; no unverified write.
    }
  };

  return (
    <Drawer
      open={open} width="min(780px, 100vw)" placement="right" closable={!loading}
      destroyOnClose maskClosable={false} keyboard={!loading}
      onClose={() => discardAndExit(onClose)}
      title={<div>
        <div className="text-[19px] font-semibold leading-7">创建物理模型草稿</div>
        <div className="text-[12px] font-normal text-[#667085]">业务依据 → 技术目标 → 创建草稿 · 不发布、不执行</div>
      </div>}
      extra={<Space>
        <YakButton disabled={loading} onClick={() => discardAndExit(onClose)}>取消</YakButton>
        <YakButton type="primary" loading={loading}
          disabled={!hasDomainOptions || !hasLayerOptions || scopeNotReady}
          onClick={() => void handleSubmit()}>创建物理草稿</YakButton>
      </Space>}
      styles={{ header: { padding: '16px 22px', borderBottom: '1px solid #e5eaf0' },
        body: { background: '#f8fafc', padding: '20px 22px' } }}
    >
      <Form form={form} layout="vertical" requiredMark
        initialValues={{ dialect: 'MYSQL' }}
        onValuesChange={(changed: Partial<WizardFormValues>, all: WizardFormValues) => {
          // Only an explicit user domain change may invalidate a process; do
          // not clear a verified prefill just because useWatch fires.
          if (Object.prototype.hasOwnProperty.call(changed, 'domainId')
              && all.processId && !verifiedProcessInDomain(domainTree, processes, changed.domainId, all.processId)) {
            form.setFieldsValue({ processId: undefined });
          }
        }}
      >
        {scopeNotReady ? <Alert className="!mb-4" showIcon type="warning"
          message="正在核验进入时的业务过程与所属业务域"
          description="业务过程或业务域当前不可读取。此时不允许将 URL 中的 ID 当成真实授权归属创建模型。" /> : null}
        <section className="mb-4 rounded-xl border border-[#e4e9f0] bg-white p-5 max-sm:p-4">
          <div className="mb-1 flex items-center gap-2"><Tag color="blue">01</Tag><Typography.Text strong>业务上下文</Typography.Text></div>
          <Typography.Paragraph type="secondary" className="!mb-4 !text-[12px]">
            明确当前业务域、过程与业务粒度；例如订单与订单明细是不同粒度，不能只按名称自动关联。
          </Typography.Paragraph>
          <Form.Item name="domainId" label="业务域" rules={[{ required: true, message: '请选择当前项目业务域' }]}>
            <TreeSelect treeData={toDomainTreeData(domainTree)} treeDefaultExpandAll
              allowClear={!processScoped} disabled={processScoped || scopeNotReady}
              placeholder="选择正式业务域（仅根域可选）" />
          </Form.Item>
          <Form.Item name="processId" label="业务过程（推荐选择）">
            <Select showSearch optionFilterProp="label" allowClear={!processScoped}
              disabled={!selectedDomainId || processScoped || scopeNotReady}
              placeholder={selectedDomainId ? '选择当前业务域下的业务过程' : '先选择业务域'}
              options={filteredProcesses.map(p => ({ value: p.id, label: `${p.name} · ${p.grain || '粒度待确认'}` }))} />
          </Form.Item>
          {selectedProcess ? <Alert showIcon type={selectedProcess.grain ? 'info' : 'warning'}
            message={`当前业务过程：${selectedProcess.name}`}
            description={`业务粒度：${selectedProcess.grain || '尚未确认，建议先返回业务过程核对。'} · 类型：${selectedProcess.bizType === 'FACT' ? '事实' : '维度'}。后续物理设计仍需独立核验逻辑版本。`} />
            : !scopeNotReady ? <Alert showIcon type="info"
              message="业务过程尚未选定"
              description="可以建立独立的物理设计草稿，但不会凭名称猜测业务过程或逻辑模型。" /> : null}
        </section>

        <section className="mb-4 rounded-xl border border-[#e4e9f0] bg-white p-5 max-sm:p-4">
          <div className="mb-1 flex items-center gap-2"><Tag color="blue">02</Tag><Typography.Text strong>物理目标</Typography.Text></div>
          <Typography.Paragraph type="secondary" className="!mb-4 !text-[12px]">
            分层和目标方言来自当前项目已有配置；本次创建仅保存物理模型定义，不会生成建表 SQL 或自动发布。
          </Typography.Paragraph>
          <Form.Item name="layerCode" label="数仓目标分层" rules={[{ required: true, message: '请选择目标分层' }]}>
            <Select placeholder="选择已配置的数仓分层"
              options={layers.map(layer => ({ value: layer.code, label: `${layer.name}（${layer.code}）` }))} />
          </Form.Item>
          <Form.Item name="dialect" label="目标数据库方言" rules={[{ required: true, message: '请选择目标方言' }]}>
            <Select options={MODELING_DIALECT_OPTIONS} placeholder="选择目标数据库方言" />
          </Form.Item>
          <Form.Item name="name" label="物理模型名称" rules={[
            { required: true, message: '请输入模型名称' }, { max: 128, message: '名称最多 128 字符' },
          ]}>
            <Input placeholder="如：订单明细事实表" maxLength={128} />
          </Form.Item>
          <Form.Item name="code" label="物理模型编码" rules={[
            { required: true, message: '请输入物理编码' },
            { pattern: /^[A-Za-z0-9_][A-Za-z0-9_-]{0,127}$/, message: '最多128字符，仅允许字母、数字、_和-' },
          ]}>
            <Input placeholder="如：dwd_trade_order_detail" maxLength={128}
              onChange={() => { codeManuallyEdited.current = true; }} />
          </Form.Item>
          <Form.Item name="description" label="设计用途（可选）" rules={[{ max: 512 }]}>
            <Input.TextArea rows={2} placeholder="说明目标表的业务粒度、用途及预期来源" maxLength={512} />
          </Form.Item>
        </section>
        <section className="mb-4">
          <ModelCreationLogicalBasis
            layer={selectedLayerCode} processId={selectedProcess?.id}
            onOpenLogical={(id) => {
              if (onGoLogical) discardAndExit(() => onGoLogical(id));
            }}
          />
          {selectedLayerCode?.toUpperCase() === 'ODS' && onGoSource ? (
            <div className="mt-2"><Button type="link"
              onClick={() => discardAndExit(onGoSource)}>去数据源选择精确来源表（推荐）</Button></div>
          ) : null}
        </section>
        <Alert showIcon type={requiresLogicalBasis(selectedLayerCode) && !selectedProcess ? 'warning' : 'info'}
          message="提交后进入该物理模型的表结构工作区"
          description={creationNextStep(selectedLayerCode)} />
      </Form>
    </Drawer>
  );
};

export default ModelCreateWizardDrawer;
