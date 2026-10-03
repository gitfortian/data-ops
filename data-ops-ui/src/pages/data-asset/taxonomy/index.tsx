import {
  Button,
  Card,
  Dropdown,
  Form,
  Input,
  InputNumber,
  message,
  Modal,
  Radio,
  Segmented,
  Select,
  Space,
  Table,
  Tag,
  Tooltip,
  Tree,
  TreeSelect,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react';

import { YakButton, YakEmpty } from '@/components/ui';
import { usePermissionAccess } from '@/hooks/usePermissionAccess';
import {
  applyAssetRuleAgain,
  createAssetRule,
  createAssetTag,
  createDirectory,
  deleteAssetRule,
  deleteAssetTag,
  deleteDirectory,
  disableAssetRule,
  dryRunAssetRule,
  enableAssetRule,
  getDirectoryTree,
  initDirectoryTemplate,
  listAssetRules,
  listAssetTags,
  moveDirectory,
  updateAssetRule,
  updateAssetTag,
  updateDirectory,
} from '@/services/data-asset/api';
import type {
  AssetTagRecord,
  DirNode,
  RuleRecord,
  RuleType,
  RuleUpsertParams,
} from '@/services/data-asset/types';
import { getSemanticDomainTree, listSemanticLayers } from '@/services/semantic/api';
import type { SemanticDomainNode, SemanticLayerRecord } from '@/services/semantic/types';
import {
  ASSET_SOURCE_TYPE_LABELS,
  ASSET_TYPE_LABELS,
  collectDirOptions,
  formatAssetTime,
  RULE_TYPE_LABELS,
  toTreeData,
} from '../constants';

const collectDomainOptions = (
  nodes: SemanticDomainNode[],
): { value: string; label: string }[] =>
  nodes.flatMap((node) => [
    { value: node.code, label: node.name },
    ...collectDomainOptions(node.children ?? []),
  ]);

interface DirSelectOption {
  title: string;
  value: number;
  children?: DirSelectOption[];
}

const dirTreeSelectData = (nodes: DirNode[]): DirSelectOption[] =>
  nodes.map((node) => ({
    title: node.dirName,
    value: node.id,
    children: node.children?.length ? dirTreeSelectData(node.children) : undefined,
  }));

/** 目录树维护:新增/编辑/移动/删除 + 模板一键初始化。 */
const DirectoryTab = () => {
  const { can } = usePermissionAccess();
  const canCreate = can('data-asset:create');
  const canUpdate = can('data-asset:update');
  const canDelete = can('data-asset:delete');

  const [tree, setTree] = useState<DirNode[]>([]);
  const [loading, setLoading] = useState(false);
  const [editing, setEditing] = useState<{ node: DirNode | null; parentId?: number } | null>(null);
  const [moving, setMoving] = useState<DirNode | null>(null);
  const [moveTarget, setMoveTarget] = useState<number | undefined>();
  const [form] = Form.useForm();

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setTree(await getDirectoryTree());
    } catch {
      setTree([]);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const treeData = useMemo(() => toTreeData(tree), [tree]);

  const openCreate = (parentId?: number) => {
    form.resetFields();
    setEditing({ node: null, parentId });
  };

  const openEdit = (node: DirNode) => {
    form.setFieldsValue({
      dirName: node.dirName,
      iconKey: node.iconKey,
      description: node.description,
      sortOrder: node.sortOrder,
    });
    setEditing({ node });
  };

  const submitEdit = async () => {
    const values = await form.validateFields();
    try {
      if (editing?.node) {
        await updateDirectory(editing.node.id, values);
        message.success('已更新目录');
      } else {
        await createDirectory({ ...values, parentId: editing?.parentId });
        message.success('已新建目录(编码自动生成)');
      }
      setEditing(null);
      void load();
    } catch {
      // 48006 等校验提示已由全局展示;表单校验失败此处静默
    }
  };

  const runMove = async () => {
    if (!moving || !moveTarget) {
      message.warning('请选择目标父目录');
      return;
    }
    try {
      await moveDirectory(moving.id, moveTarget);
      message.success('目录已移动');
      setMoving(null);
      setMoveTarget(undefined);
      void load();
    } catch {
      // 防环 48006
    }
  };

  const runDelete = (node: DirNode) => {
    Modal.confirm({
      title: '删除目录',
      content: `「${node.dirName}」${node.builtin ? '为模板内置目录，通常不可删除' : ''}。目录非空或存在子目录时会被拒绝。确定删除？`,
      okText: '删除',
      okType: 'danger',
      cancelText: '取消',
      onOk: async () => {
        try {
          await deleteDirectory(node.id);
          message.success('已删除');
          void load();
        } catch {
          // 已由全局错误提示统一展示后端原话（如内置/非空 48007）
        }
      },
    });
  };

  const runInitTemplate = async () => {
    try {
      const created = await initDirectoryTemplate();
      message.success(`已按分层+业务域初始化 ${created} 个目录`);
      void load();
    } catch {
      // 已初始化 48008 幂等提示
    }
  };

  const nodeDropdown = (node: DirNode) => ({
    items: [
      { key: 'child', label: '新建子目录', disabled: !canCreate },
      { key: 'edit', label: '编辑', disabled: !canUpdate },
      { key: 'move', label: '移动', disabled: !canUpdate },
      { key: 'delete', label: '删除', danger: true, disabled: !canDelete },
    ],
    onClick: ({ key }: { key: string }) => {
      if (key === 'child') openCreate(node.id);
      if (key === 'edit') openEdit(node);
      if (key === 'move') setMoving(node);
      if (key === 'delete') runDelete(node);
    },
  });

  const renderTree = (nodes: DirNode[]): ReactNode[] =>
    nodes.map((node) => (
      <Tree.TreeNode
        key={node.id}
        title={
          <div className="flex items-center justify-between gap-2">
            <span className="truncate">
              {node.dirName}
              {node.builtin && <Tag className="!ml-1">内置</Tag>}
            </span>
            <Dropdown menu={nodeDropdown(node)} trigger={['click']}>
              <Button type="text" size="small" onClick={(event) => event.stopPropagation()}>
                ···
              </Button>
            </Dropdown>
          </div>
        }
      >
        {node.children?.length ? renderTree(node.children) : undefined}
      </Tree.TreeNode>
    ));

  return (
    <div>
      <div className="mb-3 flex flex-wrap items-center gap-3">
        {canCreate && (
          <YakButton size="small" type="primary" className="!text-white" onClick={() => openCreate(undefined)}>
            新建根目录
          </YakButton>
        )}
        {canCreate && (
          <Tooltip title="幂等：已存在的分层/业务域目录不覆盖">
            <YakButton size="small" onClick={runInitTemplate}>
              按分层+业务域初始化
            </YakButton>
          </Tooltip>
        )}
        <YakButton size="small" onClick={load}>
          刷新
        </YakButton>
        <span className="text-[13px] text-[#667085]">目录编码自动生成，移动时会同步更新子目录的位置。</span>
      </div>
      <Card size="small" loading={loading}>
        {tree.length === 0 ? (
          <YakEmpty compact title="暂无目录" description="点上方按钮按分层+业务域一键初始化" />
        ) : (
          <Tree defaultExpandAll blockNode>
            {renderTree(tree)}
          </Tree>
        )}
      </Card>

      <Modal
        open={Boolean(editing)}
        title={editing?.node ? `编辑目录「${editing.node.dirName}」` : '新建目录'}
        okText="保存"
        cancelText="取消"
        onOk={submitEdit}
        destroyOnClose
      >
        <Form form={form} layout="vertical" className="!mt-4">
          <Form.Item
            name="dirName"
            label="目录名称"
            rules={[{ required: true, message: '请输入目录名称' }, { max: 128 }]}
          >
            <Input placeholder="如：客户域主数据" />
          </Form.Item>
          <Form.Item name="iconKey" label="图标标识" rules={[{ max: 64 }]}>
            <Input placeholder="可空，前端按默认图标渲染" />
          </Form.Item>
          <Form.Item name="description" label="描述" rules={[{ max: 512 }]}>
            <Input.TextArea rows={2} />
          </Form.Item>
          <Form.Item name="sortOrder" label="排序" initialValue={100}>
            <InputNumber min={0} max={9999} className="w-full" />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        open={Boolean(moving)}
        title={`移动目录「${moving?.dirName ?? ''}」`}
        okText="移动"
        cancelText="取消"
        onOk={runMove}
        destroyOnClose
      >
        <TreeSelect
          className="w-full !mt-4"
          placeholder="选择目标父目录"
          treeDefaultExpandAll
          treeData={dirTreeSelectData(tree)}
          value={moveTarget}
          onChange={setMoveTarget}
        />
        <div className="mt-2 text-[13px] text-[#667085]">不能移动到当前目录或其子目录中。</div>
      </Modal>
    </div>
  );
};

const ruleConditionSummary = (rule: RuleRecord) => {
  const c = rule.conditions ?? {};
  const parts: string[] = [];
  if (c.assetTypes?.length) parts.push(`类型:${c.assetTypes.join('/')}`);
  if (c.layerCodes?.length) parts.push(`分层:${c.layerCodes.join('/')}`);
  if (c.domainCodes?.length) parts.push(`业务域:${c.domainCodes.join('/')}`);
  if (c.sourceTypes?.length) parts.push(`来源:${c.sourceTypes.join('/')}`);
  if (c.nameRegex) parts.push(`名称~${c.nameRegex}`);
  if (c.keyword) parts.push(`关键词:${c.keyword}`);
  return parts.length ? parts.join('，') : '全部资产(通配)';
};

/** 编目规则:CRUD + 试跑(启用前置 D10) + 启停 + 重应用。 */
const RuleTab = ({ dirTree, tags }: { dirTree: DirNode[]; tags: AssetTagRecord[] }) => {
  const { can } = usePermissionAccess();
  const canCreate = can('data-asset:create');
  const canUpdate = can('data-asset:update');

  const [rules, setRules] = useState<RuleRecord[]>([]);
  const [loading, setLoading] = useState(false);
  const [editing, setEditing] = useState<RuleRecord | 'new' | null>(null);
  const [layers, setLayers] = useState<SemanticLayerRecord[]>([]);
  const [domains, setDomains] = useState<{ value: string; label: string }[]>([]);
  const [dryRun, setDryRun] = useState<{
    rule: RuleRecord;
    hitCount: number;
    samples: { assetId: number; name: string }[];
  } | null>(null);
  const [form] = Form.useForm();

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setRules(await listAssetRules());
    } catch {
      setRules([]);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
    listSemanticLayers()
      .then((list) => setLayers(list.filter((layer) => layer.status === 'ENABLED')))
      .catch(() => setLayers([]));
    getSemanticDomainTree()
      .then((tree) => setDomains(collectDomainOptions(tree)))
      .catch(() => setDomains([]));
  }, [load]);

  const openEditor = (rule: RuleRecord | 'new') => {
    form.resetFields();
    if (rule !== 'new') {
      form.setFieldsValue({
        ruleName: rule.ruleName,
        ruleType: rule.ruleType,
        priority: rule.priority ?? 100,
        targetDirectoryId: rule.targetDirectoryId ?? undefined,
        targetTagId: rule.targetTagId ?? undefined,
        assetTypes: rule.conditions?.assetTypes ?? [],
        layerCodes: rule.conditions?.layerCodes ?? [],
        domainCodes: rule.conditions?.domainCodes ?? [],
        sourceTypes: rule.conditions?.sourceTypes ?? [],
        nameRegex: rule.conditions?.nameRegex ?? undefined,
        keyword: rule.conditions?.keyword ?? undefined,
      });
    } else {
      form.setFieldsValue({ ruleType: 'DIRECTORY', priority: 100 });
    }
    setEditing(rule);
  };

  const submit = async () => {
    const values = await form.validateFields();
    const params: RuleUpsertParams = {
      ruleName: values.ruleName,
      ruleType: values.ruleType,
      priority: values.priority,
      targetDirectoryId: values.ruleType === 'DIRECTORY' ? values.targetDirectoryId : null,
      targetTagId: values.ruleType === 'TAG' ? values.targetTagId : null,
      conditions: {
        assetTypes: values.assetTypes?.length ? values.assetTypes : null,
        layerCodes: values.layerCodes?.length ? values.layerCodes : null,
        domainCodes: values.domainCodes?.length ? values.domainCodes : null,
        sourceTypes: values.sourceTypes?.length ? values.sourceTypes : null,
        nameRegex: values.nameRegex || null,
        keyword: values.keyword || null,
      },
    };
    try {
      if (editing === 'new') {
        await createAssetRule(params);
        message.success('已新建规则（默认停用，请先试跑再启用）');
      } else if (editing) {
        await updateAssetRule(editing.id, params);
        message.success('已更新规则（已回到未试跑+停用）');
      }
      setEditing(null);
      void load();
    } catch {
      // 目标缺失等校验提示已由全局展示
    }
  };

  const runDry = async (rule: RuleRecord) => {
    try {
      const result = await dryRunAssetRule(rule.id);
      setDryRun({
        rule,
        hitCount: result.hitCount,
        samples: (result.samples ?? []).map((sample) => ({ assetId: sample.assetId, name: sample.name })),
      });
      void load();
    } catch {
      // 试跑失败原因已全局展示
    }
  };

  const toggle = async (rule: RuleRecord) => {
    try {
      if (rule.enabled) {
        await disableAssetRule(rule.id);
        message.success('已停用');
      } else {
        await enableAssetRule(rule.id);
        message.success('已启用');
      }
      void load();
    } catch {
      // 未试跑 48010
    }
  };

  const reapply = (rule: RuleRecord) => {
    Modal.confirm({
      title: '重应用规则',
      content: `将对全部存量命中资产执行「${rule.ruleName}」的目标动作（补目录/补标签），已由人工指定的值不覆盖。确定？`,
      okText: '重应用',
      cancelText: '取消',
      onOk: async () => {
        try {
          const count = await applyAssetRuleAgain(rule.id);
          message.success(`已重应用，命中 ${count} 个资产`);
          void load();
        } catch {
          // 原因已全局展示
        }
      },
    });
  };

  const remove = (rule: RuleRecord) => {
    Modal.confirm({
      title: '删除规则',
      content: `删除「${rule.ruleName}」不影响已应用的台账值。确定删除？`,
      okText: '删除',
      okType: 'danger',
      cancelText: '取消',
      onOk: async () => {
        try {
          await deleteAssetRule(rule.id);
          message.success('已删除');
          void load();
        } catch {
          // 原因已全局展示
        }
      },
    });
  };

  const dirNames = useMemo(
    () => Object.fromEntries(collectDirOptions(dirTree).map((o) => [o.value, o.label])),
    [dirTree],
  );
  const tagNames = useMemo(
    () => Object.fromEntries(tags.map((tag) => [tag.id, tag.tagName])),
    [tags],
  );

  const columns: ColumnsType<RuleRecord> = [
    { title: '优先级', dataIndex: 'priority', width: 70, align: 'center' as const },
    { title: '规则名称', dataIndex: 'ruleName', width: 180, ellipsis: true },
    {
      title: '类型',
      dataIndex: 'ruleType',
      width: 100,
      render: (value: RuleType) => <Tag color={value === 'DIRECTORY' ? 'geekblue' : 'purple'}>{RULE_TYPE_LABELS[value]}</Tag>,
    },
    {
      title: '命中条件',
      key: 'conditions',
      ellipsis: true,
      render: (_, rule) => (
        <Tooltip title={ruleConditionSummary(rule)}>
          <span className="text-[12px] text-[#667085]">{ruleConditionSummary(rule)}</span>
        </Tooltip>
      ),
    },
    {
      title: '目标',
      key: 'target',
      width: 150,
      ellipsis: true,
      render: (_, rule) =>
        rule.ruleType === 'DIRECTORY'
          ? dirNames[rule.targetDirectoryId ?? -1] ?? `#${rule.targetDirectoryId ?? '-'}`
          : tagNames[rule.targetTagId ?? -1] ?? `#${rule.targetTagId ?? '-'}`,
    },
    {
      title: '上次命中',
      dataIndex: 'lastApplyHit',
      width: 90,
      align: 'right' as const,
      render: (count?: number | null) => (count == null ? <span className="text-[#98a2b3]">未试跑</span> : `${count} 个`),
    },
    {
      title: '状态',
      dataIndex: 'enabled',
      width: 80,
      render: (enabled: boolean) => <Tag color={enabled ? 'green' : 'default'}>{enabled ? '启用中' : '已停用'}</Tag>,
    },
    {
      title: '操作',
      key: 'action',
      width: 250,
      render: (_, rule) => (
        <Space size={2}>
          <Button type="link" size="small" disabled={!canUpdate} onClick={() => runDry(rule)}>
            试跑
          </Button>
          <Button type="link" size="small" disabled={!canUpdate} onClick={() => toggle(rule)}>
            {rule.enabled ? '停用' : '启用'}
          </Button>
          <Button type="link" size="small" disabled={!canUpdate || !rule.enabled} onClick={() => reapply(rule)}>
            重应用
          </Button>
          <Button type="link" size="small" disabled={!canUpdate} onClick={() => openEditor(rule)}>
            编辑
          </Button>
          <Button type="link" size="small" danger disabled={!canUpdate} onClick={() => remove(rule)}>
            删除
          </Button>
        </Space>
      ),
    },
  ];

  const ruleType = Form.useWatch('ruleType', form);

  return (
    <div>
      <div className="mb-3 flex flex-wrap items-center gap-3">
        {canCreate && (
          <YakButton size="small" type="primary" className="!text-white" onClick={() => openEditor('new')}>
            新建规则
          </YakButton>
        )}
        <span className="text-[12px] text-[#98a2b3]">
          规则必须试跑后才可启用（D10）；条件之间是 AND，留空即通配；对账对新资产自动应用启用中的规则
        </span>
      </div>
      <Table<RuleRecord>
        rowKey="id"
        size="small"
        columns={columns}
        dataSource={rules}
        loading={loading}
        pagination={false}
        locale={{ emptyText: <YakEmpty compact title="暂无编目规则" description="新建后先试跑确认命中面，再启用" /> }}
      />

      <Modal
        open={Boolean(editing)}
        title={editing === 'new' ? '新建编目规则' : '编辑编目规则'}
        okText="保存"
        cancelText="取消"
        onOk={submit}
        width={640}
        destroyOnClose
      >
        <Form form={form} layout="vertical" className="!mt-4">
          <Space className="w-full" size={12}>
            <Form.Item
              name="ruleName"
              label="规则名称"
              className="!w-[280px]"
              rules={[{ required: true, message: '请输入规则名称' }, { max: 128 }]}
            >
              <Input placeholder="如：ODS 层客户表自动入贴源层" />
            </Form.Item>
            <Form.Item name="ruleType" label="规则类型" rules={[{ required: true }]}>
              <Radio.Group options={(Object.keys(RULE_TYPE_LABELS) as RuleType[]).map((value) => ({ value, label: RULE_TYPE_LABELS[value] }))} optionType="button" />
            </Form.Item>
            <Form.Item name="priority" label="优先级(小者先)" initialValue={100}>
              <InputNumber min={1} max={9999} />
            </Form.Item>
          </Space>
          <Form.Item
            name={ruleType === 'TAG' ? 'targetTagId' : 'targetDirectoryId'}
            label={ruleType === 'TAG' ? '目标标签' : '目标目录'}
            rules={[{ required: true, message: ruleType === 'TAG' ? '请选择标签' : '请选择目录' }]}
          >
            {ruleType === 'TAG' ? (
              <Select
                placeholder="选择标签"
                showSearch
                optionFilterProp="label"
                options={tags.map((tag) => ({ value: tag.id, label: tag.tagName }))}
              />
            ) : (
              <TreeSelect
                placeholder="选择目录"
                treeDefaultExpandAll
                treeData={dirTreeSelectData(dirTree)}
              />
            )}
          </Form.Item>
          <Space className="w-full" size={12} wrap>
            <Form.Item name="assetTypes" label="资产类型" className="!w-[280px]">
              <Select
                mode="multiple"
                allowClear
                placeholder="留空=全部"
                options={Object.entries(ASSET_TYPE_LABELS).map(([value, label]) => ({ value, label }))}
                maxTagCount="responsive"
              />
            </Form.Item>
            <Form.Item name="layerCodes" label="分层" className="!w-[150px]">
              <Select
                mode="multiple"
                allowClear
                placeholder="留空=全部"
                options={layers.map((layer) => ({ value: layer.code, label: layer.code }))}
                maxTagCount="responsive"
              />
            </Form.Item>
            <Form.Item name="sourceTypes" label="来源域" className="!w-[150px]">
              <Select
                mode="multiple"
                allowClear
                placeholder="留空=全部"
                options={Object.entries(ASSET_SOURCE_TYPE_LABELS)
                  .filter(([value]) => value !== 'MANUAL')
                  .map(([value, label]) => ({ value, label }))}
                maxTagCount="responsive"
              />
            </Form.Item>
            <Form.Item name="domainCodes" label="业务域" className="!w-[150px]">
              <Select
                mode="multiple"
                allowClear
                placeholder="留空=全部"
                options={domains}
                maxTagCount="responsive"
              />
            </Form.Item>
          </Space>
          <Space className="w-full" size={12}>
            <Form.Item
              name="nameRegex"
              label="名称正则"
              className="!w-[280px]"
              rules={[{ max: 256 }]}
            >
              <Input placeholder="如 ^ods_cust.*" />
            </Form.Item>
            <Form.Item name="keyword" label="关键词(名称/描述包含)" className="!w-[280px]" rules={[{ max: 64 }]}>
              <Input placeholder="如 客户" />
            </Form.Item>
          </Space>
        </Form>
      </Modal>

      <Modal
        open={Boolean(dryRun)}
        title={`试跑结果：${dryRun?.rule.ruleName ?? ''}`}
        footer={<Button onClick={() => setDryRun(null)}>关闭</Button>}
        onCancel={() => setDryRun(null)}
        destroyOnClose
      >
        <div className="mb-2 text-[13px]">
          命中 <span className="text-[16px] font-semibold text-[#FE2C55]">{dryRun?.hitCount ?? 0}</span> 个资产
          （样例最多 50 条；确认无误后点「启用」）
        </div>
        <Table
          rowKey="assetId"
          size="small"
          dataSource={(dryRun?.samples ?? []).map((sample, index) => ({ ...sample, key: `${sample.assetId}-${index}` }))}
          columns={[
            { title: '资产', dataIndex: 'name', ellipsis: true },
            { title: '资产 ID', dataIndex: 'assetId', width: 100 },
          ]}
          pagination={false}
          scroll={{ y: 300 }}
          locale={{ emptyText: <YakEmpty compact title="没有命中资产" description="条件过窄，调整后再试" /> }}
        />
      </Modal>
    </div>
  );
};

/** 标签字典:CRUD(编码自动生成不填)。 */
const TagTab = () => {
  const { can } = usePermissionAccess();
  const canCreate = can('data-asset:create');
  const canUpdate = can('data-asset:update');
  const canDelete = can('data-asset:delete');

  const [tags, setTags] = useState<AssetTagRecord[]>([]);
  const [loading, setLoading] = useState(false);
  const [keyword, setKeyword] = useState('');
  const [editing, setEditing] = useState<AssetTagRecord | 'new' | null>(null);
  const [form] = Form.useForm();

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setTags(await listAssetTags(keyword.trim() || undefined));
    } catch {
      setTags([]);
    } finally {
      setLoading(false);
    }
  }, [keyword]);

  useEffect(() => {
    void load();
  }, [load]);

  const openEditor = (tag: AssetTagRecord | 'new') => {
    form.resetFields();
    if (tag !== 'new') {
      form.setFieldsValue({
        tagName: tag.tagName,
        color: tag.color,
        description: tag.description,
      });
    }
    setEditing(tag);
  };

  const submit = async () => {
    const values = await form.validateFields();
    try {
      if (editing === 'new') {
        await createAssetTag(values);
        message.success('已新建标签（编码自动生成）');
      } else if (editing) {
        await updateAssetTag(editing.id, values);
        message.success('已更新标签');
      }
      setEditing(null);
      void load();
    } catch {
      // 已由全局错误提示统一展示后端原话（如编码重复 48009）
    }
  };

  const remove = (tag: AssetTagRecord) => {
    Modal.confirm({
      title: '删除标签',
      content:
        tag.usageCount > 0
          ? `「${tag.tagName}」正被 ${tag.usageCount} 个资产使用，删除将同步解除全部打标关系。确定删除？`
          : `确定删除「${tag.tagName}」？`,
      okText: '删除',
      okType: 'danger',
      cancelText: '取消',
      onOk: async () => {
        try {
          await deleteAssetTag(tag.id);
          message.success('已删除');
          void load();
        } catch {
          // 原因已全局展示
        }
      },
    });
  };

  const columns: ColumnsType<AssetTagRecord> = [
    {
      title: '标签',
      dataIndex: 'tagName',
      width: 200,
      render: (name: string, record) => <Tag color={record.color || undefined}>{name}</Tag>,
    },
    { title: '编码', dataIndex: 'tagCode', width: 160 },
    {
      title: '被使用',
      dataIndex: 'usageCount',
      width: 90,
      align: 'right' as const,
      render: (count: number) => (count > 0 ? `${count} 个资产` : '-'),
    },
    { title: '描述', dataIndex: 'description', ellipsis: true, render: (value?: string) => value || '-' },
    {
      title: '创建时间',
      dataIndex: 'createTime',
      width: 150,
      render: (value?: string) => formatAssetTime(value),
    },
    {
      title: '操作',
      key: 'action',
      width: 120,
      render: (_, record) => (
        <Space size={2}>
          <Button type="link" size="small" disabled={!canUpdate} onClick={() => openEditor(record)}>
            编辑
          </Button>
          <Button type="link" size="small" danger disabled={!canDelete} onClick={() => remove(record)}>
            删除
          </Button>
        </Space>
      ),
    },
  ];

  return (
    <div>
      <div className="mb-3 flex flex-wrap items-center gap-3">
        <Input.Search
          allowClear
          placeholder="按名称/编码搜索"
          className="!w-[240px]"
          onSearch={(value) => setKeyword(value.trim())}
        />
        {canCreate && (
          <YakButton size="small" type="primary" className="!text-white" onClick={() => openEditor('new')}>
            新建标签
          </YakButton>
        )}
        <span className="text-[12px] text-[#98a2b3]">标签用于人工业务分组，与目录（自动编目目标）互补</span>
      </div>
      <Table<AssetTagRecord>
        rowKey="id"
        size="small"
        columns={columns}
        dataSource={tags}
        loading={loading}
        locale={{ emptyText: <YakEmpty compact title="暂无标签" description="新建标签后到资产详情或目录页打标" /> }}
        pagination={{ pageSize: 20, showTotal: (count) => `共 ${count} 条` }}
      />

      <Modal
        open={Boolean(editing)}
        title={editing === 'new' ? '新建标签' : '编辑标签'}
        okText="保存"
        cancelText="取消"
        onOk={submit}
        destroyOnClose
      >
        <Form form={form} layout="vertical" className="!mt-4">
          <Form.Item name="tagName" label="标签名称" rules={[{ required: true, message: '请输入标签名称' }, { max: 128 }]}>
            <Input placeholder="如：核心数据" />
          </Form.Item>
          <Form.Item name="color" label="颜色" rules={[{ max: 32 }]}>
            <Select
              allowClear
              placeholder="默认灰"
              options={['gold', 'red', 'orange', 'green', 'cyan', 'blue', 'purple', 'magenta'].map((value) => ({
                value,
                label: <Tag color={value}>{value}</Tag>,
              }))}
            />
          </Form.Item>
          <Form.Item name="description" label="描述" rules={[{ max: 512 }]}>
            <Input.TextArea rows={2} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

const AssetTaxonomyPage = () => {
  const [tab, setTab] = useState<'directory' | 'rules' | 'tags'>('directory');
  const [dirTree, setDirTree] = useState<DirNode[]>([]);
  const [tags, setTags] = useState<AssetTagRecord[]>([]);

  useEffect(() => {
    getDirectoryTree().then(setDirTree).catch(() => setDirTree([]));
    listAssetTags().then(setTags).catch(() => setTags([]));
  }, [tab]);

  return (
    <div className="min-h-[calc(100dvh-64px)] bg-white px-6 pb-6 pt-5 text-[#242731] max-md:px-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <div className="text-[20px] font-semibold leading-7">目录与标签</div>
          <div className="mt-1 text-[13px] text-[#667085]">
            组织资产目录、设置编目规则与业务标签。规则需试跑通过后才能启用。
          </div>
        </div>
        <Segmented
          options={[
            { value: 'directory', label: '目录树' },
            { value: 'rules', label: '编目规则' },
            { value: 'tags', label: '标签字典' },
          ]}
          value={tab}
          onChange={(value) => setTab(value as typeof tab)}
        />
      </div>
      <div className="mt-4">
        {tab === 'directory' && <DirectoryTab />}
        {tab === 'rules' && <RuleTab dirTree={dirTree} tags={tags} />}
        {tab === 'tags' && <TagTab />}
      </div>
    </div>
  );
};

export default AssetTaxonomyPage;
