import { Form, Input, message, Modal, Select, TreeSelect } from 'antd';
import { useEffect, useState } from 'react';

import UserSelect from '@/components/UserSelect';
import { getDirectoryTree, registerManualAsset } from '@/services/data-asset/api';
import type { AssetType, DirNode } from '@/services/data-asset/types';
import { listSemanticLayers } from '@/services/semantic/api';
import type { SemanticLayerRecord } from '@/services/semantic/types';
import { ASSET_TYPE_LABELS } from '../constants';

interface DirTreeOption {
  title: string;
  value: number;
  children?: DirTreeOption[];
}

const toDirOptions = (nodes: DirNode[]): DirTreeOption[] =>
  nodes.map((node) => ({
    title: node.dirName,
    value: node.id,
    children: node.children?.length ? toDirOptions(node.children) : undefined,
  }));

const MANUAL_TYPES: AssetType[] = ['DOC', 'TABLE', 'DASHBOARD', 'DATASET', 'TASK', 'CHART'];

/** 手工登记(存量无法自动接入的对象);asset_key/编码自动生成不填。 */
const ManualRegisterModal = ({
  open,
  onClose,
  onCreated,
}: {
  open: boolean;
  onClose: () => void;
  onCreated: () => void;
}) => {
  const [form] = Form.useForm();
  const [submitting, setSubmitting] = useState(false);
  const [layers, setLayers] = useState<SemanticLayerRecord[]>([]);
  const [dirTree, setDirTree] = useState<DirNode[]>([]);

  useEffect(() => {
    if (!open) return;
    form.resetFields();
    getDirectoryTree().then(setDirTree).catch(() => setDirTree([]));
    listSemanticLayers()
      .then((list) => setLayers(list.filter((layer) => layer.status === 'ENABLED')))
      .catch(() => setLayers([]));
  }, [open, form]);

  const submit = async () => {
    const values = await form.validateFields();
    setSubmitting(true);
    try {
      await registerManualAsset({
        ...values,
        owner: Array.isArray(values.owner) ? values.owner[0] : values.owner,
      });
      message.success('已手工登记为待上架资产');
      onCreated();
    } catch {
      // 全局错误提示已展示原因
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Modal
      open={open}
      width={700}
      title={<div>
        <div className="text-[18px] font-semibold text-[#25364c]">手工登记资产</div>
        <div className="mt-1 text-[12px] font-normal text-[#8794a6]">登记后进入待上架池；发布还需单独预检与审批。</div>
      </div>}
      okText="保存为待上架"
      cancelText="取消"
      confirmLoading={submitting}
      maskClosable={!submitting}
      onOk={submit}
      onCancel={onClose}
      destroyOnClose
    >
      <Form form={form} layout="vertical" className="!mt-5" requiredMark={false}>
        <div className="mb-4 rounded-xl border border-solid border-[#e4ebf3] bg-[#f4f7fb] px-4 py-3 text-[12px] leading-5 text-[#708096]">
          适用于暂时无法通过来源域自动纳管的资产。编码可自动生成，保存不会从数据源采集表结构，也不会直接上架。
        </div>
        <h3 className="mb-3 text-[14px] font-semibold text-[#304158]">01 · 基本信息</h3>
        <Form.Item name="name" label="资产名称"
          rules={[{ required: true, message: '请填写资产名称' }, { max: 128 }]}>
          <Input placeholder="例如：外部研报数据集" />
        </Form.Item>
        <div className="grid grid-cols-1 gap-x-4 sm:grid-cols-2">
          <Form.Item name="assetType" label="资产类型" initialValue="DOC">
            <Select options={MANUAL_TYPES.map((type) => ({ value: type, label: ASSET_TYPE_LABELS[type] }))} />
          </Form.Item>
          <Form.Item name="owner" label="治理负责人（可在上架前补齐）">
            <UserSelect placeholder="搜索并选择负责人" max={1} />
          </Form.Item>
        </div>
        <Form.Item name="description" label="业务描述（可选）">
          <Input.TextArea rows={3} maxLength={1024} showCount placeholder="说明这份资产的业务含义、用途和使用约束" />
        </Form.Item>
        <h3 className="mb-3 mt-3 border-0 border-t border-solid border-[#edf0f4] pt-4 text-[14px] font-semibold text-[#304158]">02 · 访问与归属</h3>
        <Form.Item name="accessUri" label="访问入口（可选）">
          <Input placeholder="https://… 或系统内路径" maxLength={512} />
        </Form.Item>
        <div className="grid grid-cols-1 gap-x-4 sm:grid-cols-2">
          <Form.Item name="layerCode" label="数仓分层（可选）">
            <Select allowClear placeholder="选择分层"
              options={layers.map((layer) => ({ value: layer.code, label: `${layer.name} (${layer.code})` }))} />
          </Form.Item>
          <Form.Item name="directoryId" label="资产目录（可选）">
            <TreeSelect allowClear treeDefaultExpandAll placeholder="上架前也可补齐" treeData={toDirOptions(dirTree)} />
          </Form.Item>
        </div>
        <details className="rounded-xl border border-solid border-[#e8edf3] bg-[#fbfcfe] px-4 py-3">
          <summary className="cursor-pointer text-[12px] font-medium text-[#73839a]">高级配置 · 自定义资产编码</summary>
          <Form.Item name="assetCode" label="资产编码" extra="不填写将自动生成唯一的 manual: 编码。" className="!mb-0 !mt-4">
            <Input placeholder="留空自动生成" maxLength={64} />
          </Form.Item>
        </details>
      </Form>
    </Modal>
  );
};

export default ManualRegisterModal;
