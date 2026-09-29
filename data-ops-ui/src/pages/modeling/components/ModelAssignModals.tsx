import { Form, message, Select, TreeSelect } from 'antd';
import Modal from 'antd/es/modal/Modal';
import { useEffect, useMemo, useState } from 'react';
import { buildDirectoryOptions } from '@/pages/modeling/components/directoryTreeData';
import { assignModelingModelDirectory, assignModelingModelTags } from '@/services/modeling/api';
import type { ModelingDirectoryRecord, ModelingModelRecord, ModelingTagRecord } from '@/services/modeling/types';

interface ModelMoveModalProps {
  open: boolean;
  model?: ModelingModelRecord;
  directories: ModelingDirectoryRecord[];
  onClose: () => void;
  onSaved: () => void | Promise<void>;
}

/** 移动模型到目录；清空选择表示移出目录（未分类）。 */
const ModelMoveModal = ({ open, model, directories, onClose, onSaved }: ModelMoveModalProps) => {
  const [form] = Form.useForm<{ directoryId?: number }>();
  const [saving, setSaving] = useState(false);
  const treeData = useMemo(() => buildDirectoryOptions(directories), [directories]);

  const handleOk = async () => {
    const values = await form.validateFields();
    setSaving(true);
    try {
      await assignModelingModelDirectory(model!.id!, values.directoryId ?? 0);
      message.success('模型目录已更新');
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
    <Modal
      open={open}
      title={`移动「${model?.name}」到目录`}
      confirmLoading={saving}
      onCancel={() => {
        if (!saving) onClose();
      }}
      onOk={() => void handleOk()}
      okText="保存"
      cancelText="取消"
      destroyOnClose
    >
      <Form form={form} layout="vertical" initialValues={{ directoryId: model?.directoryId || undefined }}>
        <Form.Item name="directoryId" label="目标目录" extra="清空选择表示移出目录（未分类）">
          <TreeSelect
            allowClear
            treeDefaultExpandAll
            variant="filled"
            fieldNames={{ label: 'title', value: 'value' }}
            treeData={treeData}
            placeholder="未分类"
          />
        </Form.Item>
      </Form>
    </Modal>
  );
};

interface ModelTagAssignModalProps {
  open: boolean;
  model?: ModelingModelRecord;
  tags: ModelingTagRecord[];
  onClose: () => void;
  onSaved: () => void | Promise<void>;
}

/** 为模型设置标签（全量替换）。 */
const ModelTagAssignModal = ({ open, model, tags, onClose, onSaved }: ModelTagAssignModalProps) => {
  const [saving, setSaving] = useState(false);
  const [selected, setSelected] = useState<number[]>([]);

  useEffect(() => {
    if (open) setSelected(model?.tagIds || []);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, model?.id]);

  const handleOk = async () => {
    if (!model?.id) return;
    setSaving(true);
    try {
      await assignModelingModelTags(model.id, selected);
      message.success('模型标签已更新');
      await onSaved();
      onClose();
    } catch (error) {
      if (error instanceof Error) message.error(error.message);
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal
      open={open}
      title={`设置「${model?.name}」的标签`}
      confirmLoading={saving}
      onCancel={() => {
        if (!saving) onClose();
      }}
      onOk={() => void handleOk()}
      okText="保存"
      cancelText="取消"
      destroyOnClose
    >
      <Select
        mode="multiple"
        allowClear
        className="w-full"
        placeholder="选择标签"
        value={selected}
        options={tags.map((tag) => ({ value: tag.id!, label: tag.name! }))}
        onChange={setSelected}
      />
    </Modal>
  );
};

export { ModelMoveModal, ModelTagAssignModal };
