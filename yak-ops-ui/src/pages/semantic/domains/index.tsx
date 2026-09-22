import { Button, Card, Form, Input, Modal, message, Space, Tree, Typography } from 'antd';
import type { DataNode, TreeProps } from 'antd/es/tree';
import { useCallback, useEffect, useState } from 'react';
import { YakButton, YakEmpty } from '@/components/ui';
import {
  createSemanticDomain,
  deleteSemanticDomain,
  getSemanticDomainTree,
  moveSemanticDomain,
  updateSemanticDomain,
} from '@/services/semantic/api';
import type { SemanticDomainNode } from '@/services/semantic/types';

interface DomainFormValues {
  code?: string;
  name: string;
  owner?: string;
  description?: string;
  sortOrder?: number;
}

/** 找节点及其父链(用于环提示与建子域)。 */
const findNode = (
  nodes: SemanticDomainNode[],
  id: number,
): { node: SemanticDomainNode; parent: SemanticDomainNode | null } | null => {
  for (const node of nodes) {
    if (node.id === id) {
      return { node, parent: null };
    }
    const child = findNode(node.children, id);
    if (child) {
      return { node: child.node, parent: child.parent ?? node };
    }
  }
  return null;
};

const toTreeData = (nodes: SemanticDomainNode[]): DataNode[] =>
  nodes.map((node) => ({
    key: node.id,
    title: (
      <Space size={6}>
        <span>{node.name}</span>
        <Typography.Text type="secondary" className="!text-[12px]">
          {node.code}
        </Typography.Text>
      </Space>
    ),
    children: toTreeData(node.children),
  }));

/** defaultExpandAll 只作用于首帧,树数据异步到达后全折叠;受控展开所有分支。 */
const collectBranchKeys = (nodes: SemanticDomainNode[]): number[] =>
  nodes.flatMap((node) =>
    node.children?.length ? [node.id, ...collectBranchKeys(node.children)] : [],
  );

const BusinessDomainsPage = () => {
  const [tree, setTree] = useState<SemanticDomainNode[]>([]);
  const [expandedKeys, setExpandedKeys] = useState<number[]>([]);
  const [loading, setLoading] = useState(false);
  const [selected, setSelected] = useState<SemanticDomainNode | null>(null);
  const [editorOpen, setEditorOpen] = useState(false);
  const [editing, setEditing] = useState<SemanticDomainNode | null>(null);
  const [parentForCreate, setParentForCreate] = useState<SemanticDomainNode | null>(null);
  const [form] = Form.useForm<DomainFormValues>();
  const [saving, setSaving] = useState(false);

  const loadTree = useCallback(async () => {
    setLoading(true);
    try {
      const data = await getSemanticDomainTree();
      setTree(data ?? []);
      setExpandedKeys(collectBranchKeys(data ?? []));
      setSelected((current) => {
        if (!current) {
          return null;
        }
        return findNode(data ?? [], current.id)?.node ?? null;
      });
    } catch {
      message.error('加载业务域失败，请稍后重试');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadTree();
  }, [loadTree]);

  const openCreate = (parent: SemanticDomainNode | null) => {
    setEditing(null);
    setParentForCreate(parent);
    setEditorOpen(true);
    form.resetFields();
  };

  const openEdit = (node: SemanticDomainNode) => {
    setEditing(node);
    setParentForCreate(null);
    setEditorOpen(true);
    form.setFieldsValue({
      name: node.name,
      owner: node.owner,
      description: node.description,
      sortOrder: node.sortOrder,
    });
  };

  const submitEditor = async () => {
    const values = await form.validateFields();
    setSaving(true);
    try {
      if (editing) {
        await updateSemanticDomain(editing.id, values);
        message.success('业务域已更新');
      } else {
        await createSemanticDomain(parentForCreate?.id, {
          code: values.code,
          name: values.name,
          owner: values.owner,
          description: values.description,
          sortOrder: values.sortOrder,
        });
        message.success('业务域已创建');
      }
      setEditorOpen(false);
      await loadTree();
    } catch {
      message.error('保存失败（编码可能已存在），请检查后重试');
    } finally {
      setSaving(false);
    }
  };

  const removeNode = (node: SemanticDomainNode) => {
    Modal.confirm({
      title: '删除业务域',
      content: `确定删除「${node.name}（${node.code}）」？存在子业务域或业务过程时将被阻断。`,
      okText: '删除',
      okType: 'danger',
      cancelText: '取消',
      onOk: async () => {
        try {
          await deleteSemanticDomain(node.id);
          message.success('已删除');
          setSelected(null);
          await loadTree();
        } catch {
          message.error('删除失败（可能存在子域或业务过程）');
        }
      },
    });
  };

  const handleDrop: TreeProps['onDrop'] = (info) => {
    const dragId = Number(info.dragNode.key);
    const dropId = Number(info.node.key);
    const dropIntoGap = !info.dropToGap;
    if (dropIntoGap) {
      if (dragId === dropId) {
        return;
      }
      moveSemanticDomain(dragId, dropId)
        .then(() => loadTree())
        .catch(() => message.error('移动失败（不能移到自己的子孙之下）'));
      return;
    }
    // 落在节点前后间隙:保持同父,仅提示排序可在编辑表单内调整。
    message.info('已按同级处理；如需微调排序，请在编辑表单中修改排序值');
    void moveSemanticDomain(dragId, undefined).catch(() => undefined);
    void loadTree();
  };

  return (
    <div className="min-h-[calc(100dvh-64px)] bg-white px-6 pb-4 pt-5 text-[#242731] max-md:px-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <div className="text-[20px] font-semibold leading-7">业务域</div>
          <div className="mt-1 text-[13px] text-[#667085]">
            按主题划分业务过程与建模主线（交易/用户/商品…），是派生建模的顶层入口
          </div>
        </div>
        <Space>
          <YakButton
            type="primary"
            className="!h-9 !rounded-lg !px-4 !text-white"
            onClick={() => {
              openCreate(null);
            }}
          >
            新建根域
          </YakButton>
          <YakButton
            className="!h-9 !rounded-lg !px-4"
            disabled={!selected}
            onClick={() => {
              openCreate(selected);
            }}
          >
            新建子域
          </YakButton>
        </Space>
      </div>

      <Card className="mt-4" styles={{ body: { padding: 16 } }}>
        {tree.length === 0 && !loading ? (
          <YakEmpty compact title="还没有业务域" description="点击右上角「新建根域」开始搭建业务语义主线" />
        ) : (
          <div className="flex gap-6">
            <div className="min-w-[320px] flex-1">
              <Tree
                blockNode
                draggable
                showLine
                expandedKeys={expandedKeys}
                onExpand={(keys) => setExpandedKeys(keys as number[])}
                treeData={toTreeData(tree)}
                selectedKeys={selected ? [selected.id] : []}
                onSelect={(keys) => {
                  const id = Number(keys[0]);
                  const hit = findNode(tree, id);
                  setSelected(hit?.node ?? null);
                }}
                onDrop={handleDrop}
              />
            </div>
            <div className="w-[320px] border-l border-l-[#f0f0f0] pl-6">
              {selected ? (
                <div>
                  <Typography.Text strong className="!text-[15px]">
                    {selected.name}
                  </Typography.Text>
                  <Typography.Paragraph type="secondary" className="!mb-2 !text-[12px]">
                    {selected.code}
                  </Typography.Paragraph>
                  <Typography.Paragraph className="!text-[13px]">
                    {selected.description || '暂无描述'}
                  </Typography.Paragraph>
                  <Typography.Paragraph className="!text-[13px]">负责人：{selected.owner || '-'}</Typography.Paragraph>
                  <Space>
                    <Button type="link" size="small" onClick={() => openEdit(selected)}>
                      编辑
                    </Button>
                    <Button type="link" size="small" danger onClick={() => removeNode(selected)}>
                      删除
                    </Button>
                  </Space>
                </div>
              ) : (
                <Typography.Text type="secondary">选中左侧节点查看详情</Typography.Text>
              )}
            </div>
          </div>
        )}
      </Card>

      <Modal
        open={editorOpen}
        title={editing ? '编辑业务域' : parentForCreate ? `新建子域（父：${parentForCreate.name}）` : '新建根域'}
        width={520}
        okText="保存"
        cancelText="取消"
        confirmLoading={saving}
        destroyOnClose
        onCancel={() => setEditorOpen(false)}
        onOk={() => {
          void submitEditor();
        }}
      >
        <Form form={form} layout="vertical" className="pt-2">
          {!editing ? (
            <Form.Item
              name="code"
              label="编码"
              rules={[
                { required: true, message: '请输入编码' },
                { pattern: /^[A-Za-z0-9_]{1,64}$/, message: '仅允许字母、数字和下划线，1~64 位' },
              ]}
            >
              <Input maxLength={64} placeholder="如 trade" />
            </Form.Item>
          ) : null}
          <Form.Item name="name" label="名称" rules={[{ required: true, message: '请输入名称' }]}>
            <Input maxLength={128} placeholder="业务域名称" />
          </Form.Item>
          <Form.Item name="owner" label="负责人">
            <Input maxLength={64} placeholder="可选" />
          </Form.Item>
          <Form.Item name="sortOrder" label="排序">
            <Input type="number" placeholder="0" />
          </Form.Item>
          <Form.Item name="description" label="描述">
            <Input.TextArea rows={2} maxLength={512} placeholder="业务域说明（可选）" />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default BusinessDomainsPage;
