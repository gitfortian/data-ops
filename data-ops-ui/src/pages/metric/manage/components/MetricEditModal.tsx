import { Form, Input, Modal, message, Select, Space, Tag, TreeSelect } from 'antd';
import { MinusCircleOutlined } from '@ant-design/icons';
import type { TextAreaRef } from 'antd/es/input/TextArea';
import { useModel } from '@umijs/max';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { YakButton } from '@/components/ui';
import { createMetric, getMetric, pageMetrics, updateMetric } from '@/services/metric/api';
import type {
  MetricCreatePayload,
  MetricQualifier,
  MetricRecord,
  MetricType,
  StatPeriod,
} from '@/services/metric/types';
import { getModelingStructure, pageModelingModels } from '@/services/modeling/api';
import type { ModelingModelRecord } from '@/services/modeling/types';
import { getSemanticDomainTree, pageSemanticProcesses, pageSemanticStandards } from '@/services/semantic/api';
import { collectDomainSubtreeIds, toDomainTreeData } from '@/services/semantic/domainTree';
import type { SemanticDomainNode, SemanticProcessRecord, SemanticStandardRecord } from '@/services/semantic/types';
import { STAT_PERIOD_OPTIONS } from '../../constants';

interface MetricEditModalProps {
  open: boolean;
  editing: MetricRecord | null;
  onClose: () => void;
  onSaved: () => void;
}

interface MetricFormValues {
  metricName: string;
  metricCode: string;
  domainId?: number;
  processId?: number;
  metricType: MetricType;
  caliberId?: number;
  calRule?: string;
  measureExpr?: string;
  filterExpr?: string;
  dimModelIds?: number[];
  refMetricId?: number;
  dimConstraint?: string;
  qualifiers?: MetricQualifier[];
  modelId?: number;
  statDimensions?: string;
  statPeriod: StatPeriod;
  unitId?: number;
  businessDesc?: string;
  owner?: string;
  formulaText?: string;
  compositions?: { subMetricId: number; operator: string; expression?: string }[];
}

// ── Helpers ──

const OPERATOR_CODE_MAP: Record<string, string> = {
  '+': 'ADD',
  '-': 'SUB',
  '*': 'MUL',
  '/': 'DIV',
  '(': 'LPAREN',
  ')': 'RPAREN',
};
const CODE_TO_SYMBOL: Record<string, string> = {
  ADD: '+',
  SUB: '-',
  MUL: '*',
  DIV: '/',
  LPAREN: '(',
  RPAREN: ')',
};

/** Tokenize a formula expression into metric codes, numeric constants, operators, and parentheses. */
const tokenizeFormula = (text: string): string[] => text.match(/\d+\.\d+|[A-Za-z0-9_]+|[+\-*/()]|\S/g) ?? [];

const isConstantToken = (t: string) => /^\d+(\.\d+)?$/.test(t);
const isArithOperator = (t: string) => t === '+' || t === '-' || t === '*' || t === '/';

/** Validate formula text against available metric codes (operand/operator state machine). */
const validateFormula = (
  text: string,
  metricCodes: Set<string>,
): { valid: boolean; error?: string; depCodes: string[] } => {
  if (!text?.trim()) return { valid: false, error: '请输入公式表达式', depCodes: [] };

  const tokens = tokenizeFormula(text);
  const depCodes: string[] = [];
  let depth = 0;
  let expectOperand = true;

  for (const t of tokens) {
    if (t === '(') {
      if (!expectOperand) return { valid: false, error: '缺少运算符', depCodes };
      depth++;
      continue;
    }
    if (t === ')') {
      if (expectOperand) return { valid: false, error: '括号内缺少操作数', depCodes };
      if (depth === 0) return { valid: false, error: '括号不匹配', depCodes };
      depth--;
      expectOperand = false;
      continue;
    }
    if (isArithOperator(t)) {
      if (expectOperand) return { valid: false, error: '缺少左操作数或运算符连续出现', depCodes };
      expectOperand = true;
      continue;
    }
    if (!expectOperand) return { valid: false, error: '缺少运算符', depCodes };
    if (isConstantToken(t)) {
      expectOperand = false;
      continue;
    }
    if (!/^[A-Za-z0-9_]+$/.test(t) || !metricCodes.has(t)) {
      return { valid: false, error: `未知指标编码: ${t}`, depCodes };
    }
    if (!depCodes.includes(t)) depCodes.push(t);
    expectOperand = false;
  }

  if (depth !== 0) return { valid: false, error: '括号不匹配', depCodes };
  if (expectOperand) return { valid: false, error: '表达式不完整（结尾缺少操作数）', depCodes };
  if (depCodes.length === 0) return { valid: false, error: '公式中缺少指标引用', depCodes };
  return { valid: true, depCodes };
};

/** Reconstruct formula text from compositions (for editing mode). */
const buildFormulaText = (compositions: MetricRecord['compositions']): string => {
  if (!compositions?.length) return '';
  return compositions
    .map((c) => {
      if (c.operator === 'REF') return c.subMetricCode ?? c.expression ?? '';
      if (c.operator === 'LPAREN') return '(';
      if (c.operator === 'RPAREN') return ')';
      return c.expression ?? CODE_TO_SYMBOL[c.operator] ?? c.operator;
    })
    .join(' ');
};

const AGG_OPTIONS = [
  { label: '求和 SUM', value: 'SUM' },
  { label: '计数 COUNT', value: 'COUNT' },
  { label: '去重计数 COUNT_DISTINCT', value: 'COUNT_DISTINCT' },
  { label: '平均 AVG', value: 'AVG' },
  { label: '最大 MAX', value: 'MAX' },
  { label: '最小 MIN', value: 'MIN' },
];
const MEASURE_PATTERN = /^([A-Za-z_][A-Za-z0-9_]*)\(\s*([A-Za-z0-9_.]+)\s*\)$/;

/** 度量表达式选择器：聚合函数 + 模型字段，可自动拼出 FUNC(col)；无法解析的历史值回退为手输。 */
const MeasureExprInput = ({
  value,
  onChange,
  columnOptions,
}: {
  value?: string;
  onChange?: (v: string) => void;
  columnOptions: { label: string; value: string }[];
}) => {
  const [draft, setDraft] = useState<{ agg?: string; col?: string }>({});
  useEffect(() => setDraft({}), [columnOptions]);
  const parsed = MEASURE_PATTERN.exec(value ?? '');
  if (value && !parsed) {
    return <Input value={value} onChange={(e) => onChange?.(e.target.value)} placeholder="如 SUM(order_amount)" />;
  }
  const agg = parsed?.[1] ?? draft.agg;
  const col = parsed?.[2] ?? draft.col;
  const emit = (a?: string, c?: string) => {
    setDraft({ agg: a, col: c });
    onChange?.(a && c ? `${a}(${c})` : '');
  };
  return (
    <Space.Compact className="w-full">
      <Select
        showSearch
        allowClear
        optionFilterProp="label"
        placeholder="聚合函数"
        value={agg}
        onChange={(a) => emit(a, col)}
        options={AGG_OPTIONS}
        className="!w-[42%]"
      />
      <Select
        showSearch
        allowClear
        optionFilterProp="label"
        placeholder={columnOptions.length ? '选择字段' : '请先选择数据来源'}
        value={col}
        onChange={(c) => emit(agg, c)}
        options={columnOptions}
        className="!w-[58%]"
        notFoundContent="该模型暂无字段"
      />
    </Space.Compact>
  );
};

/** stat_dimensions 是 JSON 列：入库存数组，编辑回显顿号文本，与 dimModelIds 的 JSON 落库同形态。 */
const statDimensionsToText = (raw?: string): string => {
  if (!raw) return '';
  try {
    const parsed = JSON.parse(raw);
    if (Array.isArray(parsed)) return parsed.join('、');
  } catch {
    // 存量非 JSON 值原样保留
  }
  return raw;
};
const statDimensionsToStored = (text?: string): string | undefined => {
  const trimmed = text?.trim();
  if (!trimmed) return undefined;
  try {
    const parsed = JSON.parse(trimmed);
    if (Array.isArray(parsed)) return trimmed;
  } catch {
    // 落入顿号/逗号拆分
  }
  const arr = trimmed.split(/[、,，]/).map((s) => s.trim()).filter(Boolean);
  return JSON.stringify(arr.length ? arr : [trimmed]);
};

// ── 派生限定条件(02):结构化编辑器与后端 MetricQualifier 同契约 ──

const QUALIFIER_OP_OPTIONS = ['=', '!=', '>', '>=', '<', '<=', 'IN', 'LIKE', 'BETWEEN'].map((v) => ({
  label: v,
  value: v,
}));

const isBlankQualifier = (q?: MetricQualifier) => !q || (!q.field?.trim() && !q.op?.trim() && !q.value?.trim());

/** 与后端 MetricQualifier.compile 同规则的展示级编译(非法行跳过)——继承框预览用。 */
const qualifierToText = (q: MetricQualifier): string | null => {
  if (isBlankQualifier(q) || !q.field?.trim() || !q.op) return null;
  const raw = (q.value ?? '').trim();
  switch (q.op) {
    case 'IN':
      if (!raw) return null;
      return `${q.field} IN (${raw
        .split(/[,，]/)
        .map((s) => s.trim())
        .filter(Boolean)
        .map((s) => (/^-?\d+(\.\d+)?$/.test(s) ? s : `'${s.replace(/'/g, "''")}'`))
        .join(', ')})`;
    case 'LIKE':
      return raw ? `${q.field} LIKE '${raw.replace(/'/g, "''")}'` : null;
    case 'BETWEEN': {
      const bounds = raw.split(/\s+AND\s+|[,，]/i).map((s) => s.trim()).filter(Boolean);
      if (bounds.length !== 2) return null;
      const lit = (s: string) => (/^-?\d+(\.\d+)?$/.test(s) ? s : `'${s.replace(/'/g, "''")}'`);
      return `${q.field} BETWEEN ${lit(bounds[0])} AND ${lit(bounds[1])}`;
    }
    default:
      if (!QUALIFIER_OP_OPTIONS.some((o) => o.value === q.op) || !raw) return null;
      return /^-?\d+(\.\d+)?$/.test(raw)
        ? `${q.field} ${q.op} ${raw}`
        : `${q.field} ${q.op} '${raw.replace(/'/g, "''")}'`;
  }
};

const compileQualifierText = (qs?: MetricQualifier[]): string | null => {
  const parts = (qs ?? []).map(qualifierToText).filter((s): s is string => Boolean(s));
  return parts.length ? parts.join(' AND ') : null;
};

/** qualifiersJson → 编辑器行；空数组兜底一行占位，保证选中原子后即可加条件。 */
const parseQualifiers = (raw?: string): MetricQualifier[] => {
  if (raw) {
    try {
      const parsed = JSON.parse(raw);
      if (Array.isArray(parsed)) {
        const rows = parsed.map((q) => ({ field: q?.field ?? '', op: q?.op ?? '=', value: q?.value ?? '' }));
        return rows.length ? rows : [{ field: '', op: '=', value: '' }];
      }
    } catch {
      // 非法 JSON 视为无限定
    }
  }
  return [{ field: '', op: '=', value: '' }];
};

const toStoredQualifiers = (qs?: MetricQualifier[]): string | undefined => {
  const effective = (qs ?? []).filter((q) => !isBlankQualifier(q));
  return effective.length ? JSON.stringify(effective) : undefined;
};

/** MetricRecord → 表单值：dimModelIds JSON 反解、compositions 重建公式回显。 */
const toFormValues = (m: MetricRecord): MetricFormValues => {
  let dimModelIds: number[] | undefined;
  if (m.dimModelIds) {
    try {
      const parsed = JSON.parse(m.dimModelIds);
      if (Array.isArray(parsed)) dimModelIds = parsed.map(Number);
    } catch {
      dimModelIds = undefined;
    }
  }
  const vals: MetricFormValues = {
    metricName: m.metricName,
    metricCode: m.metricCode,
    domainId: m.domainId,
    processId: m.processId,
    metricType: m.metricType,
    caliberId: m.caliberId,
    calRule: m.calRule,
    measureExpr: m.measureExpr,
    filterExpr: m.filterExpr,
    dimModelIds,
    refMetricId: m.refMetricId,
    dimConstraint: m.dimConstraint,
    qualifiers: parseQualifiers(m.qualifiersJson),
    modelId: m.modelId,
    statDimensions: statDimensionsToText(m.statDimensions),
    statPeriod: m.statPeriod,
    unitId: m.unitId,
    businessDesc: m.businessDesc,
    owner: m.owner,
  };
  if (m.compositions?.length) vals.formulaText = buildFormulaText(m.compositions);
  return vals;
};

const MetricEditModal = ({ open, editing, onClose, onSaved }: MetricEditModalProps) => {
  const [form] = Form.useForm<MetricFormValues>();
  const [saving, setSaving] = useState(false);
  const isEditing = Boolean(editing);
  const { initialState } = useModel('@@initialState');
  const currentUserName = initialState?.currentUser?.userName;

  // ── Data sources ──
  const [domainTree, setDomainTree] = useState<SemanticDomainNode[]>([]);
  const [processes, setProcesses] = useState<SemanticProcessRecord[]>([]);
  const [calibers, setCalibers] = useState<SemanticStandardRecord[]>([]);
  const [units, setUnits] = useState<SemanticStandardRecord[]>([]);
  const [dwdModels, setDwdModels] = useState<ModelingModelRecord[]>([]);
  const [dimModels, setDimModels] = useState<ModelingModelRecord[]>([]);
  const [allMetrics, setAllMetrics] = useState<MetricRecord[]>([]);
  const [detail, setDetail] = useState<MetricRecord | null>(null);
  const [modelColumns, setModelColumns] = useState<{ label: string; value: string }[]>([]);
  const [derivedModelColumns, setDerivedModelColumns] = useState<{ label: string; value: string }[]>([]);
  const [legacyDimConstraint, setLegacyDimConstraint] = useState<string | undefined>();

  const metricType = Form.useWatch('metricType', form);
  const domainId = Form.useWatch('domainId', form);
  const refMetricId = Form.useWatch('refMetricId', form);
  const modelId = Form.useWatch('modelId', form);
  const formulaText = Form.useWatch('formulaText', form);
  const qualifiers = Form.useWatch('qualifiers', form);

  const formulaRef = useRef<TextAreaRef>(null);

  const isAtomic = metricType === 'ATOMIC' || !metricType;
  const isDerived = metricType === 'DERIVED';
  const isComposite = metricType === 'COMPOSITE';

  const atomicMetrics = useMemo(() => allMetrics.filter((m) => m.metricType === 'ATOMIC'), [allMetrics]);

  const domainTreeData = useMemo(() => toDomainTreeData(domainTree), [domainTree]);

  // 过程挂在域树子节点下，精确匹配顶层域 id 会永远为空（F-2）：按域树推导“选中域 → 其下全部过程”
  const domainSubtreeIds = useMemo(
    () => (domainId ? collectDomainSubtreeIds(domainTree, domainId) : null),
    [domainTree, domainId],
  );
  const filteredProcesses = useMemo(
    () => (domainSubtreeIds ? processes.filter((p) => domainSubtreeIds.has(p.domainId)) : processes),
    [processes, domainSubtreeIds],
  );

  /** Currently selected atomic metric (for derived form). */
  const selectedAtomic = useMemo(
    () => (refMetricId ? atomicMetrics.find((m) => m.id === refMetricId) : undefined),
    [refMetricId, atomicMetrics],
  );

  /** Metrics available for the formula editor: any type except composite, minus self. */
  const availableMetrics = useMemo(
    () => allMetrics.filter((m) => m.metricType !== 'COMPOSITE' && (!editing || m.id !== editing.id)),
    [allMetrics, editing],
  );

  // ── Formula real-time validation ──
  const formulaValidation = useMemo(() => {
    if (!formulaText?.trim()) return { valid: false, error: undefined, depCodes: [] as string[] };
    const codes = new Set(availableMetrics.map((m) => m.metricCode));
    return validateFormula(formulaText, codes);
  }, [formulaText, availableMetrics]);

  // ── Load data ──
  const loadData = useCallback(async () => {
    try {
      const [domainResult, processResult, caliberResult, unitResult, dwdResult, dimResult, metricResult] =
        await Promise.allSettled([
          getSemanticDomainTree(),
          pageSemanticProcesses({ pageNo: 1, pageSize: 200 }),
          pageSemanticStandards({ pageNo: 1, pageSize: 200, kind: 'CALIBER' }),
          pageSemanticStandards({ pageNo: 1, pageSize: 200, kind: 'UNIT' }),
          pageModelingModels({ pageNo: 1, pageSize: 200, layerCode: 'DWD' }),
          pageModelingModels({ pageNo: 1, pageSize: 200, layerCode: 'DIM' }),
          pageMetrics({ pageNo: 1, pageSize: 200 }),
        ]);

      if (domainResult.status === 'fulfilled') setDomainTree(domainResult.value ?? []);
      if (processResult.status === 'fulfilled') setProcesses(processResult.value.bizData ?? []);
      if (caliberResult.status === 'fulfilled') setCalibers(caliberResult.value.bizData ?? []);
      if (unitResult.status === 'fulfilled') setUnits(unitResult.value.bizData ?? []);
      if (dwdResult.status === 'fulfilled') setDwdModels(dwdResult.value.bizData ?? []);
      if (dimResult.status === 'fulfilled') setDimModels(dimResult.value.bizData ?? []);
      if (metricResult.status === 'fulfilled') setAllMetrics(metricResult.value.records ?? []);
    } catch {
      // Data source loading failure is non-blocking
    }
  }, []);

  // 编辑打开时拉完整详情（列表行没有 compositions/dimModelIds 解析所需数据）
  useEffect(() => {
    if (!open) {
      setDetail(null);
      return;
    }
    void loadData();
    if (!editing) {
      form.resetFields();
      form.setFieldsValue({ owner: currentUserName || undefined });
      setLegacyDimConstraint(undefined);
      return;
    }
    let cancelled = false;
    void (async () => {
      setDetail(null);
      let source: MetricRecord = editing;
      try {
        const fetched = await getMetric(editing.id);
        if (fetched) source = fetched;
      } catch {
        // 详情失败时退回列表行数据
      }
      if (cancelled) return;
      setDetail(source);
      form.setFieldsValue(toFormValues(source));
      setLegacyDimConstraint(!source.qualifiersJson && source.dimConstraint ? source.dimConstraint : undefined);
    })();
    return () => {
      cancelled = true;
    };
  }, [open, editing, form, loadData]);

  // 载入所选来源模型的字段，供度量表达式选择器使用
  useEffect(() => {
    if (!open || !isAtomic || !modelId) {
      setModelColumns([]);
      return;
    }
    let cancelled = false;
    getModelingStructure(modelId)
      .then((structure) => {
        if (cancelled) return;
        setModelColumns(
          (structure?.columns ?? [])
            .filter((c) => c.columnName)
            .map((c) => ({
              label: c.comment ? `${c.columnName}（${c.comment}）` : (c.columnName as string),
              value: c.columnName as string,
            })),
        );
      })
      .catch(() => {
        if (!cancelled) setModelColumns([]);
      });
    return () => {
      cancelled = true;
    };
  }, [open, isAtomic, modelId]);

  // Reset type-specific fields when metricType changes
  useEffect(() => {
    if (!open || isEditing) return;
    form.setFieldsValue({
      processId: undefined,
      caliberId: undefined,
      measureExpr: undefined,
      filterExpr: undefined,
      dimModelIds: undefined,
      refMetricId: undefined,
      dimConstraint: undefined,
      qualifiers: undefined,
      modelId: undefined,
      unitId: undefined,
      formulaText: undefined,
      compositions: undefined,
    });
    setLegacyDimConstraint(undefined);
  }, [metricType, open, isEditing, form]);

  // Inherit processId from selected atomic metric for DERIVED type
  useEffect(() => {
    if (!open || metricType !== 'DERIVED' || !selectedAtomic) return;
    form.setFieldsValue({ processId: selectedAtomic.processId });
  }, [selectedAtomic, open, metricType, form]);

  // 派生限定字段的候选 = 原子指标来源模型(DWD)的字段(能选不填)
  useEffect(() => {
    const atomicModelId = isDerived ? selectedAtomic?.modelId : undefined;
    if (!open || !atomicModelId) {
      setDerivedModelColumns([]);
      return;
    }
    let cancelled = false;
    getModelingStructure(atomicModelId)
      .then((structure) => {
        if (cancelled) return;
        setDerivedModelColumns(
          (structure?.columns ?? [])
            .filter((c) => c.columnName)
            .map((c) => ({
              label: c.comment ? `${c.columnName}（${c.comment}）` : (c.columnName as string),
              value: c.columnName as string,
            })),
        );
      })
      .catch(() => {
        if (!cancelled) setDerivedModelColumns([]);
      });
    return () => {
      cancelled = true;
    };
  }, [open, isDerived, selectedAtomic]);

  // ── Formula editor helpers ──
  const insertAtCursor = useCallback(
    (text: string) => {
      const el = formulaRef.current?.resizableTextArea?.textArea;
      if (!el) {
        const cur = form.getFieldValue('formulaText') || '';
        form.setFieldsValue({ formulaText: cur + (cur ? ' ' : '') + text });
        return;
      }
      const start = el.selectionStart ?? 0;
      const end = el.selectionEnd ?? 0;
      const cur = form.getFieldValue('formulaText') || '';
      const before = cur.substring(0, start);
      const after = cur.substring(end);
      const needSpace = before.length > 0 && before[before.length - 1] !== ' ';
      const inserted = `${needSpace ? ' ' : ''}${text} `;
      form.setFieldsValue({ formulaText: before + inserted + after });
      setTimeout(() => {
        const pos = (before + inserted).length;
        el.focus();
        el.setSelectionRange(pos, pos);
      }, 0);
    },
    [form],
  );

  const handleSubmit = async () => {
    let values: MetricFormValues;
    try {
      values = await form.validateFields();
    } catch {
      return; // antd 已在对应字段下方展示校验错误
    }
    setSaving(true);
    try {
      // Build compositions from formula text for COMPOSITE type
      let compositions: MetricCreatePayload['compositions'] | undefined;
      if (values.metricType === 'COMPOSITE' && values.formulaText) {
        const tokens = tokenizeFormula(values.formulaText);
        let sortIdx = 0;
        compositions = [];
        for (let i = 0; i < tokens.length; i++) {
          const t = tokens[i];
          if (t === '(' || t === ')') {
            compositions.push({
              operator: OPERATOR_CODE_MAP[t],
              subMetricId: 0,
              expression: t,
              sortOrder: sortIdx++,
            });
          } else if (/^[A-Za-z0-9_]+$/.test(t)) {
            const metric = availableMetrics.find((m) => m.metricCode === t);
            if (!metric?.id) throw new Error(`指标 ${t} 未找到`);
            compositions.push({ subMetricId: metric.id, operator: 'REF', sortOrder: sortIdx++ });
          } else if (OPERATOR_CODE_MAP[t]) {
            compositions.push({
              subMetricId: 0,
              operator: OPERATOR_CODE_MAP[t],
              sortOrder: sortIdx++,
            });
          }
        }
      }

      const payload: MetricCreatePayload = {
        metricName: values.metricName,
        metricCode: values.metricCode || undefined,
        domainId: values.domainId,
        processId: values.processId ?? (selectedAtomic?.processId || undefined),
        metricType: values.metricType,
        caliberId: values.caliberId,
        calRule: values.calRule,
        measureExpr: values.measureExpr,
        filterExpr: values.filterExpr,
        dimModelIds: values.dimModelIds ? JSON.stringify(values.dimModelIds) : undefined,
        refMetricId: values.refMetricId,
        // 结构化限定优先(02)：有有效行则走后端自动组装；无则保留存量自由文本兼容
        dimConstraint: values.qualifiers?.some((q) => !isBlankQualifier(q)) ? undefined : values.dimConstraint,
        qualifiersJson:
          values.metricType === 'DERIVED' ? toStoredQualifiers(values.qualifiers) : undefined,
        modelId: values.modelId,
        statDimensions: statDimensionsToStored(values.statDimensions),
        statPeriod: values.statPeriod,
        unitId: values.unitId,
        businessDesc: values.businessDesc,
        owner: values.owner,
        compositions,
      };

      if (editing) {
        await updateMetric(editing.id, { ...payload, expectedVersion: (detail ?? editing).version });
        message.success('指标已更新');
      } else {
        await createMetric(payload);
        message.success('指标已创建');
      }
      onSaved();
    } catch (error) {
      const msg = error instanceof Error && error.message ? error.message : '保存失败，请稍后重试';
      message.error(msg);
      if ((error as { code?: number })?.code === 44002) {
        form.setFields([{ name: 'metricCode', errors: [msg] }]);
      }
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal
      title={isEditing ? '编辑指标' : '新建指标'}
      open={open}
      onOk={handleSubmit}
      confirmLoading={saving}
      onCancel={onClose}
      okText={isEditing ? '保存' : '创建'}
      cancelText="取消"
      destroyOnHidden
      width={720}
    >
      <Form
        form={form}
        layout="vertical"
        preserve={false}
        requiredMark
        initialValues={{ metricType: 'ATOMIC', statPeriod: 'DAY' }}
      >
        {/* ── Common fields ── */}
        <div className="grid grid-cols-2 gap-x-4 max-sm:grid-cols-1">
          {!isEditing ? (
            <Form.Item
              name="metricCode"
              label="指标编码"
              rules={[{ pattern: /^[A-Za-z0-9_]{0,64}$/, message: '仅允许字母、数字和下划线，最多 64 位' }]}
            >
              <Input placeholder="选填。留空时按名称自动生成：字母数字直转，中文名自动附加校验码后缀保证唯一" />
            </Form.Item>
          ) : null}

          <Form.Item name="metricName" label="指标名称" rules={[{ required: true, message: '请输入指标名称' }]}>
            <Input placeholder="如 总交易额" />
          </Form.Item>

          <Form.Item name="domainId" label="业务域" rules={[{ required: true, message: '请选择业务域' }]}>
            <TreeSelect
              showSearch
              treeNodeFilterProp="title"
              placeholder="选择业务域（仅顶层业务域可选）"
              treeData={domainTreeData}
              treeDefaultExpandAll
              onChange={() => form.setFieldsValue({ processId: undefined })}
            />
          </Form.Item>

          <Form.Item name="metricType" label="指标类型" rules={[{ required: true, message: '请选择指标类型' }]}>
            <Select
              disabled={isEditing}
              options={[
                { label: '原子指标', value: 'ATOMIC' },
                { label: '派生指标', value: 'DERIVED' },
                { label: '复合指标', value: 'COMPOSITE' },
              ]}
            />
          </Form.Item>
        </div>

        {/* ── Atomic metric fields ── */}
        {isAtomic ? (
          <>
            <div className="mb-2 text-[13px] font-medium text-[#344054]">口径定义</div>
            <div className="grid grid-cols-2 gap-x-4 max-sm:grid-cols-1">
              <Form.Item name="processId" label="业务过程" rules={[{ required: true, message: '请选择业务过程' }]}>
                <Select
                  showSearch
                  optionFilterProp="label"
                  placeholder="选择业务过程"
                  options={filteredProcesses.map((p) => ({ label: p.name, value: p.id }))}
                />
              </Form.Item>

              <Form.Item name="caliberId" label="口径引用">
                <Select
                  showSearch
                  allowClear
                  optionFilterProp="label"
                  placeholder="从 semantic 口径标准选（可选）"
                  options={calibers.map((c) => ({ label: `${c.name}（${c.code}）`, value: c.id }))}
                  onChange={(value) => {
                    // 旧-工单 46:选中口径标准后从标准带出 cal_rule,用户仍可改
                    const std = value == null ? undefined : calibers.find((c) => c.id === value);
                    if (std?.calRule) form.setFieldsValue({ calRule: std.calRule });
                  }}
                />
              </Form.Item>

              <Form.Item
                name="measureExpr"
                label="度量表达式"
                rules={[{ required: true, message: '请选择聚合函数与字段' }]}
              >
                <MeasureExprInput columnOptions={modelColumns} />
              </Form.Item>

              <Form.Item name="modelId" label="数据来源 (DWD)" rules={[{ required: true, message: '请选择 DWD 模型' }]}>
                <Select
                  showSearch
                  optionFilterProp="label"
                  placeholder="选择 DWD 模型"
                  options={dwdModels.map((m) => ({ label: `${m.name ?? m.code}`, value: m.id }))}
                  onChange={() => form.setFieldsValue({ measureExpr: undefined })}
                />
              </Form.Item>

              <Form.Item name="dimModelIds" label="维度 (DIM)">
                <Select
                  showSearch
                  allowClear
                  mode="multiple"
                  optionFilterProp="label"
                  placeholder="选择 DIM 模型（可选）"
                  options={dimModels.map((m) => ({ label: `${m.name ?? m.code}`, value: m.id }))}
                />
              </Form.Item>

              <Form.Item name="filterExpr" label="过滤条件">
                <Input placeholder="如 order_status != '已取消'（可选）" />
              </Form.Item>

              <Form.Item name="unitId" label="单位">
                <Select
                  showSearch
                  allowClear
                  optionFilterProp="label"
                  placeholder="从 semantic 单位标准选（可选）"
                  options={units.map((u) => ({ label: `${u.name}（${u.code}）`, value: u.id }))}
                />
              </Form.Item>
            </div>
          </>
        ) : null}

        {/* ── Derived metric fields ── */}
        {isDerived ? (
          <>
            <div className="mb-2 text-[13px] font-medium text-[#344054]">派生定义</div>
            <div className="grid grid-cols-2 gap-x-4 max-sm:grid-cols-1">
              {/* Process: read-only when atomic selected, hidden field to store inherited value */}
              <Form.Item label="业务过程">
                <Input
                  disabled
                  value={selectedAtomic?.processName || '选择原子指标后自动带出'}
                  placeholder="选择原子指标后自动带出"
                />
                <Form.Item name="processId" noStyle>
                  <Input type="hidden" />
                </Form.Item>
              </Form.Item>

              <Form.Item
                name="refMetricId"
                label="引用原子指标"
                rules={[{ required: true, message: '请选择引用的原子指标' }]}
              >
                <Select
                  showSearch
                  optionFilterProp="label"
                  placeholder="选择原子指标"
                  options={atomicMetrics
                    .filter((m) => !editing || m.id !== editing.id)
                    .map((m) => ({ label: `${m.metricName}（${m.metricCode}）`, value: m.id }))}
                />
              </Form.Item>
            </div>

            {/* Inherited info: show when atomic metric selected */}
            {selectedAtomic ? (
              <div className="mb-4 rounded-lg border border-[#e5e7eb] bg-white p-3">
                <div className="mb-1.5 text-[12px] font-medium text-[#667085]">继承信息（保存时自动组装，只读）</div>
                <div className="grid grid-cols-2 gap-x-4 gap-y-2">
                  <div className="col-span-2">
                    <span className="text-[12px] text-[#667085]">度量表达式（继承原子）：</span>
                    <span className="text-[13px] text-[#344054]">{selectedAtomic.measureExpr || '-'}</span>
                  </div>
                  <div className="col-span-2">
                    <span className="text-[12px] text-[#667085]">过滤条件（原子 + 限定条件 AND 合并）：</span>
                    <span className="text-[13px] text-[#344054]">
                      {[selectedAtomic.filterExpr, compileQualifierText(qualifiers)].filter(Boolean).join(' AND ') ||
                        '（添加限定条件后自动组装）'}
                    </span>
                  </div>
                  <div>
                    <span className="text-[12px] text-[#667085]">单位：</span>
                    <span className="text-[13px] text-[#344054]">
                      {selectedAtomic.unitId ? (units.find((u) => u.id === selectedAtomic.unitId)?.name ?? '-') : '-'}
                    </span>
                  </div>
                  <div>
                    <span className="text-[12px] text-[#667085]">口径引用：</span>
                    <span className="text-[13px] text-[#344054]">
                      {selectedAtomic.caliberId
                        ? (calibers.find((c) => c.id === selectedAtomic.caliberId)?.name ?? '-')
                        : '-'}
                    </span>
                  </div>
                </div>
              </div>
            ) : null}

            {/* 结构化限定条件(02)：字段/运算符选择器拼装，保存时后端自动组装派生定义 */}
            <div className="mb-1 text-[13px] font-medium text-[#344054]">限定条件</div>
            <Form.List name="qualifiers">
              {(fields, { add, remove }) => (
                <div className="mb-3">
                  {fields.map((field) => (
                    <Space key={field.key} align="baseline" className="mb-1 flex">
                      <Form.Item name={[field.name, 'field']} noStyle>
                        <Select
                          showSearch
                          allowClear
                          optionFilterProp="label"
                          placeholder={selectedAtomic ? '选择字段' : '先选择原子指标'}
                          disabled={!selectedAtomic}
                          options={derivedModelColumns}
                          notFoundContent={selectedAtomic ? '原子来源模型暂无字段' : undefined}
                          className="!w-[220px]"
                        />
                      </Form.Item>
                      <Form.Item name={[field.name, 'op']} noStyle>
                        <Select placeholder="运算符" options={QUALIFIER_OP_OPTIONS} className="!w-[100px]" />
                      </Form.Item>
                      <Form.Item name={[field.name, 'value']} noStyle>
                        <Input placeholder="值（IN 用逗号分隔，BETWEEN 用「下界 AND 上界」）" className="!w-[240px]" />
                      </Form.Item>
                      <MinusCircleOutlined className="text-[#667085]" onClick={() => remove(field.name)} />
                    </Space>
                  ))}
                  <YakButton size="small" onClick={() => add({ field: '', op: '=', value: '' })}>
                    + 添加限定条件
                  </YakButton>
                </div>
              )}
            </Form.List>

            {/* 存量自由文本 dimConstraint：仅展示，保存后升级为结构化限定 */}
            {legacyDimConstraint ? (
              <div className="mb-3 rounded-lg border border-[#e5e7eb] bg-[#fafafa] p-3">
                <div className="mb-1 text-[12px] text-[#667085]">存量维度限定（自由文本，添加结构化限定后将被替代）</div>
                <div className="text-[13px] text-[#344054]">{legacyDimConstraint}</div>
              </div>
            ) : null}
          </>
        ) : null}

        {/* ── Composite metric fields (Formula Editor) ── */}
        {isComposite ? (
          <>
            <div className="mb-2 text-[13px] font-medium text-[#344054]">复合定义</div>
            <div className="rounded-lg border border-[#e5e7eb] bg-white p-3">
              {/* Available metrics - click to insert */}
              <div className="mb-2">
                <div className="mb-1 text-[12px] text-[#667085]">可用子指标（点击插入）</div>
                <div className="flex flex-wrap gap-1.5">
                  {availableMetrics.length === 0 ? (
                    <span className="text-[12px] text-[#667085]">暂无可用指标，请先创建原子/派生指标</span>
                  ) : (
                    availableMetrics.map((m) => (
                      <Tag
                        key={m.id}
                        className="cursor-pointer !rounded !px-2 !py-0.5 !text-[12px] hover:!text-[#1677ff]"
                        onClick={() => insertAtCursor(m.metricCode)}
                      >
                        {m.metricName}（{m.metricCode}）
                      </Tag>
                    ))
                  )}
                </div>
              </div>

              {/* Operator buttons */}
              <div className="mb-2">
                <div className="mb-1 text-[12px] text-[#667085]">运算符</div>
                <div className="flex gap-1.5">
                  {(['+', '-', '*', '/', '(', ')'] as const).map((op) => (
                    <YakButton
                      key={op}
                      className="!h-7 !min-w-[32px] !px-2 !text-sm !font-medium"
                      onClick={() => insertAtCursor(op)}
                    >
                      {op}
                    </YakButton>
                  ))}
                </div>
              </div>

              {/* Formula input */}
              <Form.Item
                name="formulaText"
                rules={[
                  { required: true, message: '请输入公式表达式' },
                  {
                    validator: (_, value) => {
                      if (!value?.trim()) return Promise.resolve();
                      const codes = new Set(availableMetrics.map((m) => m.metricCode));
                      const result = validateFormula(value, codes);
                      return result.valid ? Promise.resolve() : Promise.reject(new Error(result.error));
                    },
                  },
                ]}
                className="!mb-2"
              >
                <Input.TextArea
                  ref={formulaRef}
                  rows={3}
                  placeholder="输入公式，如 order_cnt / uv（点击上方标签和运算符快速插入）"
                  className="!font-mono"
                />
              </Form.Item>

              {/* Validation status & preview */}
              {formulaText?.trim() ? (
                <div className="space-y-1.5">
                  <div className={`text-[12px] ${formulaValidation.valid ? 'text-[#12b76a]' : 'text-[#d92d20]'}`}>
                    {formulaValidation.valid ? '✅ 语法正确' : `❌ ${formulaValidation.error}`}
                  </div>
                  {formulaValidation.valid ? (
                    <>
                      <div className="rounded bg-[#f9fafb] px-3 py-1.5 font-mono text-[13px] text-[#344054]">
                        {formulaText}
                      </div>
                      <div className="flex flex-wrap items-center gap-2">
                        <span className="text-[12px] text-[#667085]">依赖子指标：</span>
                        {formulaValidation.depCodes.map((code) => {
                          const m = availableMetrics.find((x) => x.metricCode === code);
                          return (
                            <Tag key={code} color="blue" className="!text-[12px]">
                              {m?.metricName ?? code}（{code}）
                            </Tag>
                          );
                        })}
                      </div>
                    </>
                  ) : null}
                </div>
              ) : null}
            </div>
          </>
        ) : null}

        {/* ── Bottom common fields ── */}
        <div className="mt-3 grid grid-cols-2 gap-x-4 max-sm:grid-cols-1">
          <Form.Item name="statPeriod" label="统计周期" rules={[{ required: true, message: '请选择统计周期' }]}>
            <Select options={STAT_PERIOD_OPTIONS} />
          </Form.Item>
          <Form.Item name="statDimensions" label="统计维度">
            <Input placeholder="如 门店、日期（选填）" />
          </Form.Item>
        </div>

        <Form.Item name="calRule" label="口径规则" extra="选择口径引用后自动从标准带出，可修改">
          <Input.TextArea rows={2} placeholder="口径规则说明（选填）" />
        </Form.Item>

        <Form.Item name="businessDesc" label="业务口径描述">
          <Input.TextArea rows={2} placeholder="描述指标的业务含义和口径（选填）" />
        </Form.Item>

        <Form.Item name="owner" label="负责人">
          <Input placeholder="选填" />
        </Form.Item>
      </Form>
    </Modal>
  );
};

export default MetricEditModal;
