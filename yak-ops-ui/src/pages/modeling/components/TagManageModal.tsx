import { Button, Empty, Input, List, Modal, message, Popconfirm } from 'antd';
import { useEffect, useState } from 'react';
import { createModelingTag, deleteModelingTag, listModelingTags } from '@/services/modeling/api';
import type { ModelingTagRecord } from '@/services/modeling/types';

interface TagManageModalProps {
  open: boolean;
  onClose: () => void;
  /** 标签变化后由父组件刷新标签字典与列表。 */
  onChanged: () => void | Promise<void>;
}

/** 标签管理：新建、删除（删除自动解除全部模型关联）。 */
const TagManageModal = ({ open, onClose, onChanged }: TagManageModalProps) => {
  const [tags, setTags] = useState<ModelingTagRecord[]>([]);
  const [loading, setLoading] = useState(false);
  const [name, setName] = useState('');
  const [creating, setCreating] = useState(false);

  const loadTags = async () => {
    setLoading(true);
    try {
      setTags((await listModelingTags()) || []);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '标签加载失败');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (open) void loadTags();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open]);

  const handleCreate = async () => {
    const trimmed = name.trim();
    if (!trimmed) return;
    setCreating(true);
    try {
      await createModelingTag(trimmed);
      setName('');
      message.success('标签已创建');
      await loadTags();
      await onChanged();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '标签创建失败');
    } finally {
      setCreating(false);
    }
  };

  const handleDelete = async (id?: number) => {
    if (!id) return;
    try {
      await deleteModelingTag(id);
      message.success('标签已删除');
      await loadTags();
      await onChanged();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '标签删除失败');
    }
  };

  return (
    <Modal
      open={open}
      title="标签管理"
      footer={null}
      width={420}
      onCancel={() => {
        if (!loading) onClose();
      }}
    >
      <div className="mb-4 flex items-center gap-2">
        <Input
          value={name}
          placeholder="输入标签名称"
          onChange={(event) => setName(event.target.value)}
          onPressEnter={() => void handleCreate()}
        />
        <Button type="primary" loading={creating} onClick={() => void handleCreate()}>
          新建
        </Button>
      </div>
      <List
        size="small"
        loading={loading}
        locale={{
          emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无标签" />,
        }}
        dataSource={tags}
        renderItem={(tag) => (
          <List.Item
            actions={[
              <Popconfirm
                key="delete"
                title="删除该标签？"
                description="将同时解除所有模型与该标签的关联。"
                okText="删除"
                cancelText="取消"
                okButtonProps={{ danger: true }}
                onConfirm={() => void handleDelete(tag.id)}
              >
                <Button type="link" size="small" danger>
                  删除
                </Button>
              </Popconfirm>,
            ]}
          >
            <span>{tag.name}</span>
          </List.Item>
        )}
      />
    </Modal>
  );
};

export default TagManageModal;
