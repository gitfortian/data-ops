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
      title="手工登记资产"
      okText="登记"
      confirmLoading={submitting}
      onOk={submit}
      onCancel={onClose}
      destroyOnClose
    >
      <Form form={form} layout="vertical" className="!mt-4" requiredMark={false}>
        <Form.Item
          name="name"
          label="资产名称"
          rules={[{ required: true, message: '请填写资产名称' }]}
        >
          <Input placeholder="如：外部研报数据集" maxLength={128} />
        </Form.Item>
        <Form.Item name="assetCode" label="资产编码" extra="留空自动生成 manual:{编码}，无需手填">
          <Input placeholder="自动生成" maxLength={64} />
        </Form.Item>
        <Form.Item name="assetType" label="资产类型" initialValue="DOC">
          <Select
            options={MANUAL_TYPES.map((type) => ({ value: type, label: ASSET_TYPE_LABELS[type] }))}
          />
        </Form.Item>
        <Form.Item name="description" label="描述">
          <Input.TextArea rows={2} maxLength={1024} showCount placeholder="这份资产是什么、怎么用" />
        </Form.Item>
        <Form.Item name="owner" label="负责人">
          <UserSelect placeholder="搜索并选择负责人" max={1} />
        </Form.Item>
        <Form.Item name="accessUri" label="访问入口">
          <Input placeholder="https://… 或系统内路径" maxLength={512} />
        </Form.Item>
        <Form.Item name="layerCode" label="数仓分层">
          <Select
            allowClear
            placeholder="可选"
            options={layers.map((layer) => ({ value: layer.code, label: `${layer.name}(${layer.code})` }))}
          />
        </Form.Item>
        <Form.Item name="directoryId" label="归入目录">
          <TreeSelect
            allowClear
            treeDefaultExpandAll
            placeholder="可选，上架前也可由预检补齐"
            treeData={toDirOptions(dirTree)}
          />
        </Form.Item>
      </Form>
    </Modal>
  );
};

export default ManualRegisterModal;
