import {
  Alert,
  Form,
  Input,
  Modal,
  Radio,
  Segmented,
  Select,
  Space,
  Switch,
  Table,
  Tag,
  Tooltip,
  Typography,
  message,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { MinusCircleOutlined, PlusOutlined } from '@ant-design/icons';
import { history, useSearchParams } from '@umijs/max';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { YakButton, YakEmpty } from '@/components/ui';
import {
  applyMdmTransform,
  createMdmCleanRule,
  deleteMdmCleanRule,
  discoverMdmDuplicates,
  executeMdmMerge,
  ignoreMdmDedupGroup,
  listMdmAttributes,
  listMdmCleanRules,
  listMdmDedupIgnores,
  listMdmMergeLogs,
  previewMdmMerge,
  previewMdmTransform,
  setMdmCleanRuleEnabled,
  unignoreMdmDedupGroup,
  updateMdmCleanRule,
} from '@/services/mdm/api';
import { pageMdmEntities } from '@/services/mdm/api';
import type {
  MdmAttributeRecord,
  MdmCleanRuleRecord,
  MdmCleanRuleType,
  MdmDedupGroup,
  MdmDedupIgnoreRecord,
  MdmEntityRecord,
  MdmMatchType,
  MdmMergeLogRecord,
  MdmMergePreview,
  MdmTransformChange,
  MdmTransformPreview,
} from '@/services/mdm/types';

/** 去重的一行匹配字段：属性未选时留空，提交前由表单校验拦住。 */
interface MatchFieldRow {
  attrCode?: string;
  matchType: MdmMatchType;
}

/** 标准化的一行映射：属性 + 原值 → 目标值；同一属性可有多行，提交时折叠成一个对象。 */
interface MappingRow {
  attrCode?: string;
  fromValue?: string;
  toValue?: string;
}

/** 补全的一行：属性 + 默认值。 */
interface DefaultRow {
  attrCode?: string;
  defaultValue?: string;
}

/** 三种类型共用一个表单：各自只用其中一段字段，提交前按 ruleType 组装成对应 JSON。 */
interface RuleFormValues {
  ruleType: MdmCleanRuleType;
  ruleName: string;
  condition: 'AND' | 'OR';
  fields: MatchFieldRow[];
  mappings: MappingRow[];
  defaults: DefaultRow[];
}

/** 规则类型：'ALL' 只用于筛选，不是后端的类型值。 */
type RuleTypeFilter = 'ALL' | MdmCleanRuleType;

const RULE_TYPE_LABELS: Record<MdmCleanRuleType, string> = {
  DEDUP: '去重',
  STANDARDIZE: '标准化',
  COMPLETE: '补全',
};

const RULE_TYPE_OPTIONS = (['DEDUP', 'STANDARDIZE', 'COMPLETE'] as MdmCleanRuleType[]).map(
  (value) => ({ value, label: RULE_TYPE_LABELS[value] }),
);

/** 各类型一句人话说明：选类型时说不清「标准化和补全差在哪」，就还是得回去看文档。 */
const RULE_TYPE_HINTS: Record<MdmCleanRuleType, string> = {
  DEDUP: '按字段匹配找出重复记录，再由人工确认后合并',
  STANDARDIZE: '按码值映射改写属性（如 M→1、F→2），命中源值才改',
  COMPLETE: '给空值属性填默认值，已有值的记录不动',
};

const RULE_NAME_PLACEHOLDERS: Record<MdmCleanRuleType, string> = {
  DEDUP: '如：手机号相同 + 姓名相似',
  STANDARDIZE: '如：性别码值标准化',
  COMPLETE: '如：会员等级补全',
};

const RULE_TYPE_FILTERS: { value: RuleTypeFilter; label: string }[] = [
  { value: 'DEDUP', label: '去重' },
  { value: 'STANDARDIZE', label: '标准化' },
  { value: 'COMPLETE', label: '补全' },
  { value: 'ALL', label: '全部' },
];

const MATCH_TYPE_OPTIONS = [
  { label: '精确匹配（EXACT）', value: 'EXACT' },
  { label: '模糊匹配（去空格小写）', value: 'FUZZY' },
];

/** 忽略原因预置项:能选就不填,选不中再补一句备注。 */
const DEFAULT_IGNORE_REASON = '不同主体的巧合重复（如常见姓名）';

const IGNORE_REASON_OPTIONS = [
  DEFAULT_IGNORE_REASON,
  '测试/占位数据',
  '同一主体但业务要求保留多条',
].map((value) => ({ label: value, value }));

const renderMap = (value: Record<string, unknown>) => {
  const entries = Object.entries(value ?? {});
  return entries.length === 0 ? (
    <Typography.Text type="secondary">-</Typography.Text>
  ) : (
    <Space size={4} wrap>
      {entries.map(([key, item]) => (
        <Tag key={key}>
          {key}: {String(item)}
        </Tag>
      ))}
    </Space>
  );
};

/** rule_expr 由后端原样存取，编辑时要按类型摊回表单行；解析失败按空行处理，让人重写。 */
const parseExpr = (raw?: string): Record<string, unknown> => {
  if (!raw) {
    return {};
  }
  try {
    return JSON.parse(raw) as Record<string, unknown>;
  } catch {
    return {};
  }
};

const asObject = (value: unknown): Record<string, unknown> =>
  value && typeof value === 'object' ? (value as Record<string, unknown>) : {};

/** {属性:{原值:目标值}} → 平铺行；一个原值一行，比嵌套编辑器少两层点击。 */
const mappingRows = (value: unknown) =>
  Object.entries(asObject(value)).flatMap(([attrCode, map]) =>
    Object.entries(asObject(map)).map(([fromValue, toValue]) => ({
      attrCode,
      fromValue,
      toValue: String(toValue ?? ''),
    })),
  );

const defaultRows = (value: unknown) =>
  Object.entries(asObject(value)).map(([attrCode, defaultValue]) => ({
    attrCode,
    defaultValue: String(defaultValue ?? ''),
  }));

/** 表单行 → 后端 rule_expr：三种类型的形状只在这一处组装。 */
const buildRuleExpr = (values: RuleFormValues): string => {
  if (values.ruleType === 'DEDUP') {
    return JSON.stringify({
      fields: (values.fields ?? []).map((field) => ({
        attrCode: field.attrCode,
        matchType: field.matchType,
      })),
      condition: values.condition ?? 'AND',
    });
  }
  if (values.ruleType === 'STANDARDIZE') {
    const fields: Record<string, Record<string, string>> = {};
    (values.mappings ?? []).forEach((row) => {
      fields[row.attrCode as string] = {
        ...(fields[row.attrCode as string] ?? {}),
        [String(row.fromValue)]: String(row.toValue),
      };
    });
    return JSON.stringify({ fields });
  }
  const defaults: Record<string, string> = {};
  (values.defaults ?? []).forEach((row) => {
    defaults[row.attrCode as string] = String(row.defaultValue);
  });
  return JSON.stringify({ defaults });
};

/** 列表里的标准化/补全规则摊成人话（gender：M→1、F→2），不让运维去猜原始 JSON。 */
const transformChips = (rule: MdmCleanRuleRecord): string[] => {
  const expr = parseExpr(rule.rawExpr);
  if (rule.ruleType === 'STANDARDIZE') {
    return Object.entries(asObject(expr.fields)).map(
      ([attrCode, map]) =>
        `${attrCode}：${Object.entries(asObject(map))
          .map(([fromValue, toValue]) => `${fromValue}→${toValue}`)
          .join('、')}`,
    );
  }
  if (rule.ruleType === 'COMPLETE') {
    return Object.entries(asObject(expr.defaults)).map(
      ([attrCode, defaultValue]) => `${attrCode} 空值填「${defaultValue}」`,
    );
  }
  return [];
};

/** 预览明细只展示真正被改掉的属性，整条快照摊开会淹没变更点。 */
const changedAttrs = (before: Record<string, unknown>, after: Record<string, unknown>) =>
  Object.entries(after ?? {}).filter(
    ([key, value]) => String(before?.[key] ?? '') !== String(value ?? ''),
  );

/** 主数据清洗:去重规则 → 重复发现 → 合并；标准化/补全规则 → 影响预览 → 批量执行。 */
const MdmCleansingPage = () => {
  const [form] = Form.useForm<RuleFormValues>();
  const [searchParams, setSearchParams] = useSearchParams();
  const [entities, setEntities] = useState<MdmEntityRecord[]>([]);
  const [entityId, setEntityId] = useState<number | undefined>(() => {
    const parsed = Number(searchParams.get('entityId'));
    return Number.isInteger(parsed) && parsed > 0 ? parsed : undefined;
  });
  const [attributes, setAttributes] = useState<MdmAttributeRecord[]>([]);
  const [rules, setRules] = useState<MdmCleanRuleRecord[]>([]);
  // 默认看全部类型：新规则落在哪种筛选下都看得见，不会出现「刚创建就消失」。
  const [ruleTypeFilter, setRuleTypeFilter] = useState<RuleTypeFilter>('ALL');
  const [ruleModalOpen, setRuleModalOpen] = useState(false);
  const [editingRule, setEditingRule] = useState<MdmCleanRuleRecord | null>(null);
  const [savingRule, setSavingRule] = useState(false);
  const [discoverRuleId, setDiscoverRuleId] = useState<number | undefined>(undefined);
  const [discovering, setDiscovering] = useState(false);
  const [groups, setGroups] = useState<MdmDedupGroup[]>([]);
  const [groupTotal, setGroupTotal] = useState(0);
  const [groupPageNo, setGroupPageNo] = useState(1);
  const [ignored, setIgnored] = useState<MdmDedupIgnoreRecord[]>([]);
  const [ignoreTarget, setIgnoreTarget] = useState<MdmDedupGroup | null>(null);
  const [ignoreReason, setIgnoreReason] = useState<string>(DEFAULT_IGNORE_REASON);
  const [ignoreNote, setIgnoreNote] = useState('');
  const [ignoring, setIgnoring] = useState(false);
  const [mergeTarget, setMergeTarget] = useState<MdmDedupGroup | null>(null);
  const [mergeOpen, setMergeOpen] = useState(false);
  const [masterRecordId, setMasterRecordId] = useState<number | undefined>(undefined);
  const [mergedRecordIds, setMergedRecordIds] = useState<number[]>([]);
  const [preview, setPreview] = useState<MdmMergePreview | null>(null);
  const [previewing, setPreviewing] = useState(false);
  const [merging, setMerging] = useState(false);
  const [mergeLogs, setMergeLogs] = useState<MdmMergeLogRecord[]>([]);
  /** 正在预览/执行的标准化、补全规则。 */
  const [transformRule, setTransformRule] = useState<MdmCleanRuleRecord | null>(null);
  const [transformPreview, setTransformPreview] = useState<MdmTransformPreview | null>(null);
  const [transformPreviewing, setTransformPreviewing] = useState(false);
  const [applyingTransform, setApplyingTransform] = useState(false);
  const watchedRuleType = Form.useWatch('ruleType', form);
  /**
   * 兜底必须直接读表单 store，不能只等 useWatch：弹窗是 destroyOnClose、表单 preserve={false}，
   * 打开时 useWatch 还没拿到刚写入的 ruleType，会先挂错类型的 Form.List；等 watch 再翻正时，
   * 上一个 List 卸载会把刚回填的行一起清掉（编辑回填因此全空）。
   */
  const formRuleType: MdmCleanRuleType =
    watchedRuleType ?? form.getFieldValue('ruleType') ?? 'DEDUP';

  const loadEntities = useCallback(async () => {
    try {
      const result = await pageMdmEntities({ pageNo: 1, pageSize: 200 });
      setEntities(result.bizData ?? []);
    } catch {
      setEntities([]);
    }
  }, []);

  const loadRules = useCallback(async () => {
    if (!entityId) {
      setRules([]);
      return;
    }
    try {
      setRules(
        await listMdmCleanRules(
          entityId,
          ruleTypeFilter === 'ALL' ? undefined : ruleTypeFilter,
        ),
      );
    } catch {
      setRules([]);
    }
  }, [entityId, ruleTypeFilter]);

  /** 只有启用中的去重规则能驱动「去重发现」；列表按其他类型筛选时，发现区仍指向去重规则。 */
  const dedupRules = useMemo(
    () => rules.filter((rule) => rule.ruleType === 'DEDUP' && rule.enabled),
    [rules],
  );

  /**
   * 发现区没得选的原因有三种，混成一句话会把人支使去点没问题的地方：
   * 类型筛选挡住了、去重规则全被停用、还是压根没建过规则。
   */
  const dedupSelectPlaceholder = useMemo(() => {
    if (dedupRules.length > 0) return '选择去重规则执行发现';
    if (ruleTypeFilter !== 'ALL' && ruleTypeFilter !== 'DEDUP') {
      return '当前类型筛选下没有去重规则，切回「去重」或「全部」';
    }
    return rules.some((rule) => rule.ruleType === 'DEDUP')
      ? '去重规则已全部停用，启用后才能执行发现'
      : '还没有去重规则，先在上方新建一条';
  }, [dedupRules, rules, ruleTypeFilter]);

  const loadAttributes = useCallback(async () => {
    if (!entityId) {
      setAttributes([]);
      return;
    }
    try {
      setAttributes(await listMdmAttributes(entityId));
    } catch {
      setAttributes([]);
    }
  }, [entityId]);

  const loadMergeLogs = useCallback(async () => {
    if (!entityId) {
      setMergeLogs([]);
      return;
    }
    try {
      setMergeLogs(await listMdmMergeLogs(entityId));
    } catch {
      setMergeLogs([]);
    }
  }, [entityId]);

  const loadIgnored = useCallback(async () => {
    if (!entityId || !discoverRuleId) {
      setIgnored([]);
      return;
    }
    try {
      setIgnored(await listMdmDedupIgnores(entityId, discoverRuleId));
    } catch {
      setIgnored([]);
    }
  }, [entityId, discoverRuleId]);

  /** entityId 进查询串:总览卡片/实体详情可跳到本页并留住上下文,刷新也不丢。 */
  const selectEntity = (next?: number) => {
    setEntityId(next);
    setSearchParams(next ? { entityId: String(next) } : {}, { replace: true });
  };

  useEffect(() => {
    void loadEntities();
  }, [loadEntities]);

  useEffect(() => {
    setGroups([]);
    setGroupTotal(0);
    setDiscoverRuleId(undefined);
    void loadRules();
    void loadAttributes();
    void loadMergeLogs();
  }, [loadRules, loadAttributes, loadMergeLogs]);

  useEffect(() => {
    void loadIgnored();
  }, [loadIgnored]);

  const attributeOptions = useMemo(
    () =>
      attributes.map((attribute) => ({
        label: `${attribute.name}（${attribute.code}）`,
        value: attribute.code,
      })),
    [attributes],
  );

  const openCreateRule = () => {
    setEditingRule(null);
    form.resetFields();
    // 当前筛选是哪一类，新建就默认落在哪一类，省一次点选。
    const initialType: MdmCleanRuleType =
      ruleTypeFilter === 'ALL' ? 'DEDUP' : ruleTypeFilter;
    form.setFieldsValue({
      ruleType: initialType,
      condition: 'AND',
      fields: [{ matchType: 'EXACT' }],
      mappings: [{ attrCode: undefined }],
      defaults: [{ attrCode: undefined }],
    });
    setRuleModalOpen(true);
  };

  const openEditRule = (rule: MdmCleanRuleRecord) => {
    setEditingRule(rule);
    form.resetFields();
    const expr = parseExpr(rule.rawExpr);
    form.setFieldsValue({
      ruleType: rule.ruleType,
      ruleName: rule.ruleName,
      condition: rule.condition ?? 'AND',
      fields: rule.fields ?? [],
      mappings: rule.ruleType === 'STANDARDIZE' ? mappingRows(expr.fields) : [],
      defaults: rule.ruleType === 'COMPLETE' ? defaultRows(expr.defaults) : [],
    });
    setRuleModalOpen(true);
  };

  const submitRule = async () => {
    let values: RuleFormValues;
    try {
      values = await form.validateFields();
    } catch {
      // 校验信息已经贴在对应表单项下面，这里只需要不保存。
      return;
    }
    if (!entityId) return;
    setSavingRule(true);
    try {
      const payload = {
        ruleName: values.ruleName,
        ruleExpr: buildRuleExpr(values),
      };
      if (editingRule) {
        await updateMdmCleanRule(editingRule.id, payload);
        message.success('规则已更新');
      } else {
        await createMdmCleanRule({ ...payload, entityId, ruleType: values.ruleType });
        message.success(`${RULE_TYPE_LABELS[values.ruleType]}规则已创建`);
      }
      setRuleModalOpen(false);
      await loadRules();
    } catch {
      message.error(editingRule ? '规则更新失败' : '规则创建失败（名称重复或表达式不合法）');
    } finally {
      setSavingRule(false);
    }
  };

  const toggleRule = async (rule: MdmCleanRuleRecord, enabled: boolean) => {
    try {
      await setMdmCleanRuleEnabled(rule.id, enabled);
      message.success(enabled ? '规则已启用' : '规则已停用');
      if (!enabled && rule.id === discoverRuleId) {
        setDiscoverRuleId(undefined);
      }
      await loadRules();
    } catch {
      message.error('状态切换失败');
    }
  };

  const removeRule = (rule: MdmCleanRuleRecord) => {
    Modal.confirm({
      title: `删除${RULE_TYPE_LABELS[rule.ruleType] ?? '清洗'}规则`,
      content: `确定删除规则「${rule.ruleName}」？${
        rule.ruleType === 'DEDUP' ? '该规则下登记的忽略组会一并清除。' : ''
      }`,
      okText: '删除',
      okType: 'danger',
      cancelText: '取消',
      onOk: async () => {
        try {
          await deleteMdmCleanRule(rule.id);
          message.success('规则已删除');
          await loadRules();
        } catch {
          message.error('删除失败');
        }
      },
    });
  };

  const runDiscover = useCallback(
    async (pageNo: number) => {
      if (!entityId || !discoverRuleId) {
        message.warning('请先选择实体与去重规则');
        return;
      }
      setDiscovering(true);
      try {
        const result = await discoverMdmDuplicates({
          entityId,
          ruleId: discoverRuleId,
          pageNo,
          pageSize: 10,
        });
        setGroups(result.bizData ?? []);
        setGroupTotal(result.pagination?.total ?? 0);
        setGroupPageNo(pageNo);
      } catch {
        setGroups([]);
        setGroupTotal(0);
        message.error('去重发现失败');
      } finally {
        setDiscovering(false);
      }
    },
    [entityId, discoverRuleId],
  );

  const openIgnore = (group: MdmDedupGroup) => {
    setIgnoreTarget(group);
    setIgnoreReason(DEFAULT_IGNORE_REASON);
    setIgnoreNote('');
  };

  const submitIgnore = async () => {
    if (!entityId || !discoverRuleId || !ignoreTarget) return;
    setIgnoring(true);
    try {
      await ignoreMdmDedupGroup({
        entityId,
        ruleId: discoverRuleId,
        // 组键原样回传:后端刻意不做任何归一,改写过的键与 SQL 聚合结果对不上,
        // 忽略会变成一条永不生效的静音记录。
        matchKey: ignoreTarget.matchKey,
        matchBasis: ignoreTarget.matchBasis,
        reason: ignoreNote.trim() ? `${ignoreReason}：${ignoreNote.trim()}` : ignoreReason,
      });
      message.success('已忽略该组，此规则下的去重发现不再返回它');
      setIgnoreTarget(null);
      await loadIgnored();
      await runDiscover(groupPageNo);
    } catch {
      message.error('忽略失败（规则或重复组已变化）');
    } finally {
      setIgnoring(false);
    }
  };

  const revokeIgnore = (row: MdmDedupIgnoreRecord) => {
    Modal.confirm({
      title: '撤销忽略',
      content: '撤销后该重复组会重新出现在去重发现结果里。',
      okText: '撤销忽略',
      cancelText: '取消',
      onOk: async () => {
        try {
          await unignoreMdmDedupGroup(row.id);
          message.success('已撤销忽略');
          await loadIgnored();
          await runDiscover(groupPageNo);
        } catch {
          message.error('撤销失败（忽略记录可能已被清理）');
        }
      },
    });
  };

  const openMerge = (group: MdmDedupGroup) => {
    setMergeTarget(group);
    setPreview(null);
    const first = group.records[0];
    if (first) {
      setMasterRecordId(first.id);
      setMergedRecordIds(group.records.slice(1).map((record) => record.id));
    } else {
      setMasterRecordId(undefined);
      setMergedRecordIds([]);
    }
    setMergeOpen(true);
  };

  const runPreview = async () => {
    if (!entityId || !masterRecordId || mergedRecordIds.length === 0) {
      message.warning('请选择主记录与至少一条被合并记录');
      return;
    }
    setPreviewing(true);
    try {
      setPreview(
        await previewMdmMerge({
          entityId,
          masterRecordId,
          mergedRecordIds,
        }),
      );
    } catch {
      setPreview(null);
      message.error('合并预览失败（记录可能已合并）');
    } finally {
      setPreviewing(false);
    }
  };

  const confirmMerge = async () => {
    if (!entityId || !masterRecordId || !mergeTarget) return;
    setMerging(true);
    try {
      await executeMdmMerge({
        entityId,
        ruleId: discoverRuleId,
        masterRecordId,
        mergedRecordIds,
      });
      message.success('合并成功');
      setMergeOpen(false);
      await loadMergeLogs();
      await runDiscover(groupPageNo);
    } catch {
      message.error('合并失败（记录可能已被其他操作合并）');
    } finally {
      setMerging(false);
    }
  };

  /** 打开标准化/补全的影响预览：先看会改多少条、改哪些值，再决定是否执行。 */
  const openTransformPreview = async (rule: MdmCleanRuleRecord) => {
    if (!entityId) return;
    setTransformRule(rule);
    setTransformPreview(null);
    setTransformPreviewing(true);
    try {
      setTransformPreview(await previewMdmTransform(entityId, rule.id));
    } catch {
      setTransformPreview(null);
      message.error('影响预览失败（规则表达式或记录已变化）');
    } finally {
      setTransformPreviewing(false);
    }
  };

  const applyTransform = () => {
    if (!entityId || !transformRule) return;
    const affected = transformPreview?.affectedCount ?? 0;
    Modal.confirm({
      title: `执行${RULE_TYPE_LABELS[transformRule.ruleType]}`,
      content: `将按规则「${transformRule.ruleName}」直接更新 ${affected} 条生效记录的属性值，不生成变更审批单、执行后不可回滚。`,
      okText: '确认执行',
      okType: 'danger',
      cancelText: '返回',
      onOk: async () => {
        setApplyingTransform(true);
        try {
          const count = await applyMdmTransform({ entityId, ruleId: transformRule.id });
          message.success(`已更新 ${count} 条记录`);
          setTransformRule(null);
        } catch {
          message.error('执行失败（记录可能已被其他操作更新）');
        } finally {
          setApplyingTransform(false);
        }
      },
    });
  };

  const ruleColumns: ColumnsType<MdmCleanRuleRecord> = [
    { title: '规则名称', dataIndex: 'ruleName', width: 200 },
    {
      title: '类型',
      dataIndex: 'ruleType',
      width: 90,
      render: (ruleType: MdmCleanRuleType) => <Tag>{RULE_TYPE_LABELS[ruleType] ?? ruleType}</Tag>,
    },
    {
      title: '规则内容',
      key: 'expr',
      render: (_, record) =>
        record.ruleType === 'DEDUP' ? (
          <Space size={4} wrap>
            {(record.fields ?? []).map((field) => (
              <Tag key={field.attrCode} color={field.matchType === 'EXACT' ? 'blue' : 'orange'}>
                {field.attrCode}（{field.matchType === 'EXACT' ? '精确' : '模糊'}）
              </Tag>
            ))}
            {/* 组合条件只对去重有意义，跟着匹配字段走，不再单开一列空一半。 */}
            {(record.fields ?? []).length > 1 && (
              <Tag color={record.condition === 'OR' ? 'purple' : 'geekblue'}>
                {record.condition === 'OR' ? '任一命中' : '全部命中'}
              </Tag>
            )}
            {(record.fields ?? []).length === 0 && (
              <Typography.Text type="secondary">未配置匹配字段</Typography.Text>
            )}
          </Space>
        ) : (
          <Space size={4} wrap>
            {transformChips(record).map((chip) => (
              <Tag key={chip}>{chip}</Tag>
            ))}
          </Space>
        ),
    },
    {
      title: '启用',
      dataIndex: 'enabled',
      width: 80,
      render: (enabled: boolean, record) => (
        <Switch
          size="small"
          checked={enabled}
          onChange={(checked) => toggleRule(record, checked)}
        />
      ),
    },
    {
      title: '操作',
      key: 'action',
      width: 170,
      render: (_, record) => (
        <Space size={8}>
          <Typography.Link onClick={() => openEditRule(record)}>编辑</Typography.Link>
          {record.ruleType === 'DEDUP' ? (
            // 去重规则的下一步是「发现」，直接把规则填进发现区，省一次下拉选择。
            <Typography.Link onClick={() => setDiscoverRuleId(record.id)}>去发现</Typography.Link>
          ) : record.enabled ? (
            <Typography.Link onClick={() => openTransformPreview(record)}>预览影响</Typography.Link>
          ) : (
            <Tooltip title="规则已停用，启用后才能预览与执行">
              <Typography.Link disabled>预览影响</Typography.Link>
            </Tooltip>
          )}
          <Typography.Link type="danger" onClick={() => removeRule(record)}>
            删除
          </Typography.Link>
        </Space>
      ),
    },
  ];

  const groupColumns: ColumnsType<MdmDedupGroup> = [
    {
      title: '组记录数',
      dataIndex: 'total',
      width: 100,
      render: (total: number) => <Tag color="red">{total}</Tag>,
    },
    {
      title: '置信度',
      dataIndex: 'confidence',
      width: 100,
      render: (confidence: number) => `${Math.round(confidence * 100)}%`,
    },
    { title: '匹配依据', dataIndex: 'matchBasis', ellipsis: true },
    {
      title: '记录预览',
      key: 'records',
      width: 260,
      render: (_, group) => (
        <Space size={4} wrap>
          {group.records.slice(0, 3).map((record) => (
            <Tag key={record.id}>{record.masterId}</Tag>
          ))}
          {group.total > group.records.length && (
            <Typography.Text type="secondary">+{group.total - group.records.length}</Typography.Text>
          )}
        </Space>
      ),
    },
    {
      title: '操作',
      key: 'action',
      width: 150,
      render: (_, group) => (
        <Space size={8}>
          <Typography.Link onClick={() => openMerge(group)}>合并</Typography.Link>
          <Typography.Link onClick={() => openIgnore(group)}>忽略</Typography.Link>
        </Space>
      ),
    },
  ];

  const mergeRecordOptions = (mergeTarget?.records ?? []).map((record) => ({
    label: `${record.masterId}（v${record.version}）`,
    value: record.id,
  }));

  return (
    <div className="min-h-[calc(100dvh-64px)] bg-white px-6 pb-4 pt-5 text-[#242731] max-md:px-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <div className="text-[20px] font-semibold leading-7">主数据清洗</div>
          <div className="mt-1 text-[13px] text-[#667085]">
            去重规则找重复后人工合并，标准化/补全规则预览影响后批量改写属性值；质量检查（完整性/格式）请
            <Typography.Link className="!text-[13px]" onClick={() => history.push('/data-quality/overview')}>
              前往「数据质量」
            </Typography.Link>
          </div>
        </div>
        <Select
          className="!w-60"
          placeholder="选择主数据实体"
          value={entityId}
          onChange={selectEntity}
          showSearch
          optionFilterProp="label"
          options={entities.map((entity) => ({
            label: `${entity.name}（${entity.code}）`,
            value: entity.id,
          }))}
        />
      </div>

      <div className="mt-5">
        <div className="mb-2 flex flex-wrap items-center justify-between gap-2">
          <div className="text-[15px] font-semibold">清洗规则</div>
          <Space size={8} wrap>
            <Segmented
              size="small"
              options={RULE_TYPE_FILTERS}
              value={ruleTypeFilter}
              onChange={(value) => setRuleTypeFilter(value as RuleTypeFilter)}
            />
            <YakButton
              type="primary"
              className="!h-8 !rounded-lg !px-3 !text-white"
              onClick={openCreateRule}
            >
              新建清洗规则
            </YakButton>
          </Space>
        </div>
        <Table<MdmCleanRuleRecord>
          rowKey="id"
          columns={ruleColumns}
          dataSource={rules}
          size="middle"
          pagination={false}
          locale={{
            emptyText: (
              <YakEmpty
                compact
                title="暂无清洗规则"
                description="去重规则找重复、标准化规则改码值、补全规则填空值，三类都在「新建清洗规则」里配置"
              />
            ),
          }}
        />
      </div>

      <div className="mt-6">
        <div className="mb-2 flex flex-wrap items-center gap-3">
          <div className="text-[15px] font-semibold">去重发现</div>
          <Select
            className="!w-64"
            placeholder={dedupSelectPlaceholder}
            value={discoverRuleId}
            onChange={setDiscoverRuleId}
            options={dedupRules.map((rule) => ({ label: rule.ruleName, value: rule.id }))}
          />
          <YakButton
            type="primary"
            className="!h-8 !rounded-lg !px-3 !text-white"
            loading={discovering}
            onClick={() => runDiscover(1)}
          >
            执行去重发现
          </YakButton>
        </div>
        <Table<MdmDedupGroup>
          rowKey={(group) => `${group.matchKey}#${group.matchBasis}`}
          columns={groupColumns}
          dataSource={groups}
          loading={discovering}
          size="middle"
          locale={{
            emptyText: (
              <YakEmpty
                compact
                title="暂无重复组"
                description="选择规则后点击「执行去重发现」，重复组按匹配键服务端聚合"
              />
            ),
          }}
          pagination={{
            current: groupPageNo,
            pageSize: 10,
            total: groupTotal,
            showTotal: (count) => `共 ${count} 个重复组`,
            onChange: (page) => runDiscover(page),
          }}
        />
      </div>

      <div className="mt-6">
        <div className="mb-2 flex flex-wrap items-center gap-3">
          <div className="text-[15px] font-semibold">已忽略组</div>
          <Typography.Text type="secondary" className="text-[13px]">
            忽略只对当前规则生效：换规则即重新出现，撤销后立即回到待处理列表
          </Typography.Text>
        </div>
        <Table<MdmDedupIgnoreRecord>
          rowKey="id"
          size="middle"
          pagination={false}
          dataSource={ignored}
          locale={{
            emptyText: (
              <YakEmpty
                compact
                title={discoverRuleId ? '该规则下暂无忽略组' : '暂无忽略组'}
                description={
                  discoverRuleId
                    ? '在重复组行内点「忽略」即可登记已知非重复'
                    : '先在上方选择去重规则'
                }
              />
            ),
          }}
          columns={[
            {
              title: '匹配依据',
              dataIndex: 'matchBasis',
              ellipsis: true,
              // 存量忽略记录可能没带展示用依据，退回原始组键，至少能认出被静音的是哪一组。
              render: (value: string | undefined, row) => value || row.matchKey,
            },
            { title: '忽略原因', dataIndex: 'reason', render: (value?: string) => value || '-' },
            {
              title: '忽略人',
              dataIndex: 'createdBy',
              width: 120,
              render: (value?: string) => value || '-',
            },
            { title: '忽略时间', dataIndex: 'createTime', width: 180 },
            {
              title: '操作',
              key: 'action',
              width: 100,
              render: (_, row) => (
                <Typography.Link onClick={() => revokeIgnore(row)}>
                  撤销忽略
                </Typography.Link>
              ),
            },
          ]}
        />
      </div>

      <div className="mt-6">
        <div className="mb-2 text-[15px] font-semibold">合并日志</div>
        <Table<MdmMergeLogRecord>
          rowKey="id"
          size="middle"
          pagination={false}
          dataSource={mergeLogs}
          locale={{ emptyText: <YakEmpty compact title="暂无合并记录" /> }}
          columns={[
            { title: '主记录', dataIndex: 'masterRecordId', width: 110 },
            { title: '结果摘要', dataIndex: 'result' },
            {
              title: '操作人',
              dataIndex: 'createdBy',
              width: 120,
              render: (value?: string) => value || '-',
            },
            { title: '时间', dataIndex: 'createTime', width: 180 },
          ]}
        />
      </div>

      <Modal
        title={editingRule ? '编辑清洗规则' : '新建清洗规则'}
        open={ruleModalOpen}
        onOk={submitRule}
        confirmLoading={savingRule}
        onCancel={() => setRuleModalOpen(false)}
        okText={editingRule ? '保存' : '创建'}
        cancelText="取消"
        destroyOnClose
        width={640}
      >
        <Form form={form} layout="vertical" preserve={false}>
          <Form.Item
            name="ruleType"
            label="规则类型"
            rules={[{ required: true }]}
            extra={RULE_TYPE_HINTS[formRuleType]}
          >
            {/* 后端按存量 ruleType 校验表达式，类型创建后不可改，表单里就不给改。 */}
            <Radio.Group
              options={RULE_TYPE_OPTIONS}
              optionType="button"
              buttonStyle="solid"
              disabled={!!editingRule}
            />
          </Form.Item>
          <Form.Item
            name="ruleName"
            label="规则名称"
            rules={[{ required: true, message: '请输入规则名称' }, { max: 128, message: '不超过 128 字符' }]}
          >
            <Input placeholder={RULE_NAME_PLACEHOLDERS[formRuleType]} />
          </Form.Item>

          {formRuleType === 'DEDUP' && (
            <>
              <Form.Item name="condition" label="组合条件" rules={[{ required: true }]}>
                <Radio.Group>
                  <Radio.Button value="AND">全部命中（AND）</Radio.Button>
                  <Radio.Button value="OR">任一命中（OR）</Radio.Button>
                </Radio.Group>
              </Form.Item>
              <Form.List
                name="fields"
                rules={[
                  {
                    validator: async (_, rows) => {
                      if (!rows || rows.length === 0) {
                        throw new Error('至少配置一个匹配字段');
                      }
                    },
                  },
                ]}
              >
                {(rows, { add, remove }, { errors }) => (
                  <Form.Item label="匹配字段" required>
                    {rows.map((field) => (
                      <Space key={field.key} align="baseline" className="mb-2 w-full">
                        <Form.Item
                          name={[field.name, 'attrCode']}
                          rules={[{ required: true, message: '选择属性' }]}
                          className="!mb-0 flex-1"
                        >
                          <Select
                            placeholder="选择属性"
                            showSearch
                            optionFilterProp="label"
                            options={attributeOptions}
                          />
                        </Form.Item>
                        <Form.Item
                          name={[field.name, 'matchType']}
                          rules={[{ required: true, message: '选择匹配方式' }]}
                          className="!mb-0 w-44"
                        >
                          <Select options={MATCH_TYPE_OPTIONS} />
                        </Form.Item>
                        <MinusCircleOutlined
                          className="text-[#667085]"
                          onClick={() => remove(field.name)}
                        />
                      </Space>
                    ))}
                    <YakButton
                      type="dashed"
                      className="!h-8 !rounded-lg !px-3"
                      icon={<PlusOutlined />}
                      onClick={() => add({ matchType: 'EXACT' })}
                    >
                      添加匹配字段
                    </YakButton>
                    <Form.ErrorList errors={errors} />
                  </Form.Item>
                )}
              </Form.List>
            </>
          )}

          {formRuleType === 'STANDARDIZE' && (
            <Form.List
              name="mappings"
              rules={[
                {
                  validator: async (_, rows) => {
                    if (!rows || rows.length === 0) {
                      throw new Error('至少配置一条值映射');
                    }
                    const keys = rows.map((row: MappingRow) => `${row?.attrCode}=${row?.fromValue}`);
                    if (new Set(keys).size !== keys.length) {
                      // 组装成对象时同名键会合并，不拦下来用户会以为配了两条。
                      throw new Error('同一属性下的原值不能重复');
                    }
                  },
                },
              ]}
            >
              {(rows, { add, remove }, { errors }) => (
                <Form.Item label="值映射（原值 → 目标值）" required>
                  {rows.map((field) => (
                    <Space key={field.key} align="baseline" className="mb-2 w-full">
                      <Form.Item
                        name={[field.name, 'attrCode']}
                        rules={[{ required: true, message: '选择属性' }]}
                        className="!mb-0 flex-1"
                      >
                        <Select
                          placeholder="选择属性"
                          showSearch
                          optionFilterProp="label"
                          options={attributeOptions}
                        />
                      </Form.Item>
                      <Form.Item
                        name={[field.name, 'fromValue']}
                        rules={[{ required: true, message: '原值' }]}
                        className="!mb-0 w-28"
                      >
                        <Input placeholder="原值" />
                      </Form.Item>
                      <Form.Item
                        name={[field.name, 'toValue']}
                        rules={[{ required: true, message: '目标值' }]}
                        className="!mb-0 w-28"
                      >
                        <Input placeholder="目标值" />
                      </Form.Item>
                      <MinusCircleOutlined
                        className="text-[#667085]"
                        onClick={() => remove(field.name)}
                      />
                    </Space>
                  ))}
                  <YakButton
                    type="dashed"
                    className="!h-8 !rounded-lg !px-3"
                    icon={<PlusOutlined />}
                    onClick={() => add({})}
                  >
                    添加值映射
                  </YakButton>
                  <Form.ErrorList errors={errors} />
                </Form.Item>
              )}
            </Form.List>
          )}

          {formRuleType === 'COMPLETE' && (
            <Form.List
              name="defaults"
              rules={[
                {
                  validator: async (_, rows) => {
                    if (!rows || rows.length === 0) {
                      throw new Error('至少配置一个补全字段');
                    }
                    const codes = rows.map((row: DefaultRow) => row?.attrCode);
                    if (new Set(codes).size !== codes.length) {
                      throw new Error('同一属性只能有一个默认值');
                    }
                  },
                },
              ]}
            >
              {(rows, { add, remove }, { errors }) => (
                <Form.Item label="补全默认值（仅当属性为空时填入）" required>
                  {rows.map((field) => (
                    <Space key={field.key} align="baseline" className="mb-2 w-full">
                      <Form.Item
                        name={[field.name, 'attrCode']}
                        rules={[{ required: true, message: '选择属性' }]}
                        className="!mb-0 flex-1"
                      >
                        <Select
                          placeholder="选择属性"
                          showSearch
                          optionFilterProp="label"
                          options={attributeOptions}
                        />
                      </Form.Item>
                      <Form.Item
                        name={[field.name, 'defaultValue']}
                        rules={[{ required: true, message: '默认值' }]}
                        className="!mb-0 flex-1"
                      >
                        <Input placeholder="默认值，如：普通" />
                      </Form.Item>
                      <MinusCircleOutlined
                        className="text-[#667085]"
                        onClick={() => remove(field.name)}
                      />
                    </Space>
                  ))}
                  <YakButton
                    type="dashed"
                    className="!h-8 !rounded-lg !px-3"
                    icon={<PlusOutlined />}
                    onClick={() => add({})}
                  >
                    添加补全字段
                  </YakButton>
                  <Form.ErrorList errors={errors} />
                </Form.Item>
              )}
            </Form.List>
          )}
        </Form>
      </Modal>

      <Modal
        title={
          transformRule
            ? `${RULE_TYPE_LABELS[transformRule.ruleType]}预览 · ${transformRule.ruleName}`
            : '清洗影响预览'
        }
        open={!!transformRule}
        onCancel={() => setTransformRule(null)}
        okText="执行清洗"
        okButtonProps={{
          danger: true,
          disabled: !transformPreview || transformPreview.affectedCount === 0,
          loading: applyingTransform,
        }}
        cancelText="关闭"
        onOk={applyTransform}
        destroyOnClose
        width={860}
      >
        <Table<MdmTransformChange>
          rowKey="recordId"
          size="small"
          loading={transformPreviewing}
          dataSource={transformPreview?.changes ?? []}
          pagination={false}
          scroll={{ y: 360 }}
          locale={{
            emptyText: (
              <YakEmpty
                compact
                title={transformPreviewing ? '正在统计影响' : '没有记录会被改动'}
                description={
                  transformPreviewing
                    ? undefined
                    : '按当前实体下生效记录的属性值匹配，命中才会计入；改规则后可重新预览'
                }
              />
            ),
          }}
          columns={[
            { title: '记录', dataIndex: 'masterId', width: 160, ellipsis: true },
            {
              title: '属性变更',
              key: 'diff',
              render: (_, change) => (
                <Space size={4} wrap>
                  {changedAttrs(change.before, change.after).map(([key, value]) => (
                    <Tag key={key}>
                      {key}: {String(change.before?.[key] ?? '（空）')} → {String(value)}
                    </Tag>
                  ))}
                </Space>
              ),
            },
          ]}
        />
        {transformPreview && !transformPreviewing && (
          <Alert
            className="mt-3"
            type={transformPreview.affectedCount === 0 ? 'info' : 'warning'}
            showIcon
            message={
              transformPreview.affectedCount === 0
                ? '受影响记录 0 条，无需执行'
                : `将更新 ${transformPreview.affectedCount.toLocaleString()} 条生效记录`
            }
            description={
              transformPreview.truncated
                ? `明细只展示前 ${transformPreview.changes.length} 条样本，执行仍按全量 ${transformPreview.affectedCount.toLocaleString()} 条生效；清洗直接改写记录属性，不生成变更审批单。`
                : '清洗直接改写记录属性，不生成变更审批单，执行后不可回滚。'
            }
          />
        )}
      </Modal>

      <Modal
        title="合并重复组"
        open={mergeOpen}
        onCancel={() => setMergeOpen(false)}
        footer={null}
        destroyOnClose
        width={720}
      >
        {mergeTarget && (
          <div className="space-y-4">
            <div className="rounded-lg bg-[#f6f7f8] px-3 py-2 text-[13px] text-[#667085]">
              匹配依据：{mergeTarget.matchBasis || '-'}
            </div>
            <div className="grid grid-cols-2 gap-4">
              <div>
                <div className="mb-1 text-[13px] font-medium">保留的主记录</div>
                <Select
                  className="w-full"
                  value={masterRecordId}
                  onChange={(value) => {
                    setMasterRecordId(value);
                    setPreview(null);
                    setMergedRecordIds(
                      (mergeTarget.records
                        .map((record) => record.id)
                        .filter((id) => id !== value)),
                    );
                  }}
                  options={mergeRecordOptions}
                />
              </div>
              <div>
                <div className="mb-1 text-[13px] font-medium">被合并记录</div>
                <Select
                  mode="multiple"
                  className="w-full"
                  value={mergedRecordIds}
                  onChange={(value) => {
                    setMergedRecordIds(value);
                    setPreview(null);
                  }}
                  options={mergeRecordOptions.filter((option) => option.value !== masterRecordId)}
                  placeholder="选择被合并记录"
                />
              </div>
            </div>
            <div className="flex items-center gap-3">
              <YakButton className="!h-8 !rounded-lg !px-3" loading={previewing} onClick={runPreview}>
                合并预览
              </YakButton>
              <YakButton
                type="primary"
                danger
                className="!h-8 !rounded-lg !px-3 !text-white"
                disabled={!preview}
                loading={merging}
                onClick={confirmMerge}
              >
                执行合并
              </YakButton>
              {preview && (
                <Typography.Text type="secondary" className="text-[13px]">
                  合并不可回滚，但记录与日志可追溯
                </Typography.Text>
              )}
            </div>

            {preview && (
              <div className="space-y-3">
                <div>
                  <div className="mb-1 text-[13px] font-medium">记录对比</div>
                  <Table
                    rowKey={(record) => record.masterId}
                    size="small"
                    pagination={false}
                    dataSource={preview.records}
                    columns={[
                      { title: '主数据 ID', dataIndex: 'masterId', width: 160 },
                      {
                        title: '状态',
                        dataIndex: 'status',
                        width: 80,
                        render: (status: string) => (
                          <Tag color={status === 'ACTIVE' ? 'green' : 'default'}>{status}</Tag>
                        ),
                      },
                      { title: '版本', dataIndex: 'version', width: 70 },
                      {
                        title: '属性',
                        dataIndex: 'attributes',
                        render: (value: Record<string, unknown>) => renderMap(value),
                      },
                      {
                        title: '来源',
                        dataIndex: 'sourceIds',
                        render: (value: Record<string, unknown>) => renderMap(value),
                      },
                    ]}
                  />
                </div>
                <div>
                  <div className="mb-1 text-[13px] font-medium">合并后属性（主记录优先，非空补全）</div>
                  {renderMap(preview.mergedAttributes)}
                </div>
                <div>
                  <div className="mb-1 text-[13px] font-medium">合并后来源（多源去重并集）</div>
                  {renderMap(preview.mergedSourceIds)}
                </div>
              </div>
            )}
          </div>
        )}
      </Modal>

      <Modal
        title="忽略重复组"
        open={Boolean(ignoreTarget)}
        onOk={submitIgnore}
        confirmLoading={ignoring}
        onCancel={() => setIgnoreTarget(null)}
        okText="忽略该组"
        cancelText="取消"
        destroyOnClose
        width={520}
      >
        {ignoreTarget && (
          <div className="space-y-4">
            <div className="rounded-lg bg-[#f6f7f8] px-3 py-2 text-[13px]">
              <div className="text-[#242731]">{ignoreTarget.matchBasis || '-'}</div>
              <div className="mt-1 text-[#667085]">
                组内 {ignoreTarget.total} 条记录 · 忽略只改「是否再提示」，不动任何数据
              </div>
            </div>
            <div>
              <div className="mb-1 text-[13px] font-medium">忽略原因</div>
              <Select
                className="w-full"
                value={ignoreReason}
                onChange={setIgnoreReason}
                options={IGNORE_REASON_OPTIONS}
              />
            </div>
            <div>
              <div className="mb-1 text-[13px] font-medium">补充备注（可选）</div>
              <Input
                value={ignoreNote}
                onChange={(event) => setIgnoreNote(event.target.value)}
                placeholder="留空则只记上面选定的原因"
                maxLength={120}
              />
            </div>
            <Typography.Text type="secondary" className="text-[13px]">
              忽略仅对当前去重规则生效，可在下方「已忽略组」随时撤销
            </Typography.Text>
          </div>
        )}
      </Modal>
    </div>
  );
};

export default MdmCleansingPage;
