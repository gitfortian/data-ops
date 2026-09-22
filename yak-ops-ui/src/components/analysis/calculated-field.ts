import type {
  Aggregation,
  AnalysisCalculatedField,
  AnalysisFormulaFunction,
  AnalysisFormulaNode,
  AnalysisSpec,
  DatasetQueryMetric,
  DatasetQueryResult,
  MetricBinding,
  PublishedDataset,
} from './model';

export const CALCULATED_FIELD_PREFIX = 'calc:';

const AGGREGATIONS = new Set<Aggregation>([
  'SUM',
  'AVG',
  'COUNT',
  'COUNT_DISTINCT',
  'MAX',
  'MIN',
]);
const SCALAR_FUNCTIONS = new Set<AnalysisFormulaFunction>(['ABS', 'ROUND', 'COALESCE']);

export const calculatedFieldKey = (field: Pick<AnalysisCalculatedField, 'id'> | string) => (
  `${CALCULATED_FIELD_PREFIX}${typeof field === 'string' ? field : field.id}`
);

export const calculatedFieldFor = (
  spec: Pick<AnalysisSpec, 'analysis'>,
  key: string,
) => spec.analysis?.calculatedFields?.find((field) => calculatedFieldKey(field) === key);

export const isCalculatedFieldKey = (
  spec: Pick<AnalysisSpec, 'analysis'>,
  key: string,
) => Boolean(calculatedFieldFor(spec, key));

export const calculatedFieldMetric = (field: AnalysisCalculatedField): MetricBinding => ({
  field: calculatedFieldKey(field),
  // The formula itself owns aggregate semantics. SUM is a compatibility marker that lets
  // the existing metric binding / encoding pipeline carry a virtual numeric field.
  aggregation: 'SUM',
});

const metricKey = (field: string, aggregation: Aggregation) => `${field}|${aggregation}`;

export const calculatedFieldDependencies = (ast: AnalysisFormulaNode) => {
  const dependencies = new Map<string, MetricBinding>();
  const visit = (node: AnalysisFormulaNode) => {
    if (node.kind === 'metric') {
      dependencies.set(metricKey(node.field, node.aggregation), {
        field: node.field,
        aggregation: node.aggregation,
      });
      return;
    }
    if (node.kind === 'unary') {
      visit(node.value);
      return;
    }
    if (node.kind === 'binary') {
      visit(node.left);
      visit(node.right);
      return;
    }
    if (node.kind === 'function') node.args.forEach(visit);
  };
  visit(ast);
  return [...dependencies.values()];
};

class FormulaParser {
  private index = 0;

  constructor(
    private readonly expression: string,
    private readonly dataset: PublishedDataset,
  ) {}

  parse(): AnalysisFormulaNode {
    if (!this.expression.trim()) throw new Error('请输入计算公式');
    const value = this.parseAdditive();
    this.skipWhitespace();
    if (!this.eof()) throw new Error(`公式第 ${this.index + 1} 个字符附近存在无法识别的内容`);
    return value;
  }

  private parseAdditive(): AnalysisFormulaNode {
    let left = this.parseMultiplicative();
    while (true) {
      this.skipWhitespace();
      const operator = this.peek();
      if (operator !== '+' && operator !== '-') return left;
      this.index += 1;
      left = { kind: 'binary', operator, left, right: this.parseMultiplicative() };
    }
  }

  private parseMultiplicative(): AnalysisFormulaNode {
    let left = this.parseUnary();
    while (true) {
      this.skipWhitespace();
      const operator = this.peek();
      if (operator !== '*' && operator !== '/') return left;
      this.index += 1;
      left = { kind: 'binary', operator, left, right: this.parseUnary() };
    }
  }

  private parseUnary(): AnalysisFormulaNode {
    this.skipWhitespace();
    if (this.peek() === '-') {
      this.index += 1;
      return { kind: 'unary', operator: '-', value: this.parseUnary() };
    }
    return this.parsePrimary();
  }

  private parsePrimary(): AnalysisFormulaNode {
    this.skipWhitespace();
    const current = this.peek();
    if (current === '(') {
      this.index += 1;
      const value = this.parseAdditive();
      this.expect(')', '缺少右括号 )');
      return value;
    }
    if (current && /[0-9.]/.test(current)) return this.parseNumber();
    if (current && /[A-Za-z_]/.test(current)) return this.parseFunction();
    throw new Error(`公式第 ${this.index + 1} 个字符附近需要数字、函数或括号`);
  }

  private parseNumber(): AnalysisFormulaNode {
    this.skipWhitespace();
    const start = this.index;
    let dots = 0;
    while (!this.eof()) {
      const char = this.peek();
      if (char === '.') {
        dots += 1;
        if (dots > 1) break;
        this.index += 1;
        continue;
      }
      if (!char || !/[0-9]/.test(char)) break;
      this.index += 1;
    }
    const text = this.expression.slice(start, this.index);
    const value = Number(text);
    if (!text || text === '.' || !Number.isFinite(value)) throw new Error(`非法数值：${text || '.'}`);
    return { kind: 'literal', value };
  }

  private parseFunction(): AnalysisFormulaNode {
    const name = this.parseIdentifier().toUpperCase();
    this.expect('(', `函数 ${name} 后需要 (`);
    if (AGGREGATIONS.has(name as Aggregation)) {
      const aggregation = name as Aggregation;
      const field = this.parseFieldReference();
      this.expect(')', `${aggregation} 缺少右括号 )`);
      this.validateAggregateField(field, aggregation);
      return { kind: 'metric', field, aggregation };
    }
    if (!SCALAR_FUNCTIONS.has(name as AnalysisFormulaFunction)) {
      throw new Error(`暂不支持函数 ${name}`);
    }

    const args: AnalysisFormulaNode[] = [];
    this.skipWhitespace();
    if (this.peek() !== ')') {
      while (true) {
        args.push(this.parseAdditive());
        this.skipWhitespace();
        if (this.peek() !== ',') break;
        this.index += 1;
      }
    }
    this.expect(')', `${name} 缺少右括号 )`);
    this.validateFunctionArity(name as AnalysisFormulaFunction, args.length);
    return { kind: 'function', name: name as AnalysisFormulaFunction, args };
  }

  private parseFieldReference() {
    this.skipWhitespace();
    if (this.peek() !== '[') throw new Error('聚合函数中的字段必须使用 [字段] 写法');
    this.index += 1;
    const start = this.index;
    while (!this.eof() && this.peek() !== ']') this.index += 1;
    if (this.eof()) throw new Error('字段引用缺少右方括号 ]');
    const raw = this.expression.slice(start, this.index).trim();
    this.index += 1;
    if (!raw) throw new Error('字段引用不能为空');

    const direct = this.dataset.fields.find((field) => field.key === raw);
    if (direct) return direct.key;
    const aliases = this.dataset.fields.filter((field) => (
      field.label === raw || field.physicalName === raw
    ));
    if (aliases.length === 1) return aliases[0].key;
    if (aliases.length > 1) throw new Error(`字段 ${raw} 存在重名，请使用字段 key`);
    throw new Error(`Dataset 中不存在字段 ${raw}`);
  }

  private validateAggregateField(fieldKey: string, aggregation: Aggregation) {
    const field = this.dataset.fields.find((item) => item.key === fieldKey);
    if (!field) throw new Error(`Dataset 中不存在字段 ${fieldKey}`);
    if (
      (aggregation === 'SUM' || aggregation === 'AVG' || aggregation === 'MAX' || aggregation === 'MIN')
      && field.dataType !== 'number'
    ) {
      throw new Error(`${aggregation} 仅支持数值字段，${field.label} 当前为 ${field.dataType}`);
    }
  }

  private validateFunctionArity(name: AnalysisFormulaFunction, count: number) {
    if (name === 'ABS' && count !== 1) throw new Error('ABS 需要 1 个参数');
    if (name === 'ROUND' && (count < 1 || count > 2)) throw new Error('ROUND 需要 1~2 个参数');
    if (name === 'COALESCE' && count < 2) throw new Error('COALESCE 至少需要 2 个参数');
  }

  private parseIdentifier() {
    this.skipWhitespace();
    const start = this.index;
    while (!this.eof() && /[A-Za-z0-9_]/.test(this.peek() || '')) this.index += 1;
    return this.expression.slice(start, this.index);
  }

  private expect(char: string, error: string) {
    this.skipWhitespace();
    if (this.peek() !== char) throw new Error(error);
    this.index += 1;
  }

  private skipWhitespace() {
    while (!this.eof() && /\s/.test(this.peek() || '')) this.index += 1;
  }

  private peek() {
    return this.expression[this.index];
  }

  private eof() {
    return this.index >= this.expression.length;
  }
}

export const parseCalculatedFieldExpression = (
  expression: string,
  dataset: PublishedDataset,
) => {
  const ast = new FormulaParser(expression, dataset).parse();
  const dependencies = calculatedFieldDependencies(ast);
  if (!dependencies.length) throw new Error('计算字段至少需要引用一个聚合字段');
  return { ast, dependencies };
};

/** Expand virtual metrics into the physical aggregate metrics required by Dataset Runtime. */
export const queryMetricsForAnalysis = (spec: AnalysisSpec): DatasetQueryMetric[] => {
  const result = new Map<string, DatasetQueryMetric>();
  spec.metrics.forEach((metric) => {
    const calculated = calculatedFieldFor(spec, metric.field);
    const dependencies = calculated
      ? calculatedFieldDependencies(calculated.ast)
      : [metric];
    dependencies.forEach((dependency) => {
      result.set(metricKey(dependency.field, dependency.aggregation), {
        fieldId: dependency.field,
        aggregation: dependency.aggregation,
      });
    });
  });
  return [...result.values()];
};

const bindingIndex = (
  result: DatasetQueryResult,
  field: string,
  aggregation: Aggregation,
) => result.bindings.findIndex((binding) => (
  binding.fieldId === field && binding.aggregation === aggregation
));

const finiteOrNull = (value: number | null) => (
  value !== null && Number.isFinite(value) ? value : null
);

const evaluateFormulaNode = (
  node: AnalysisFormulaNode,
  result: DatasetQueryResult,
  rowIndex: number,
): number | null => {
  if (node.kind === 'literal') return node.value;
  if (node.kind === 'metric') {
    const index = bindingIndex(result, node.field, node.aggregation);
    if (index < 0) return null;
    const raw = result.rows[rowIndex]?.[index];
    if (raw === null || raw === undefined) return null;
    const number = Number(raw);
    return Number.isFinite(number) ? number : null;
  }
  if (node.kind === 'unary') {
    const value = evaluateFormulaNode(node.value, result, rowIndex);
    return value === null ? null : finiteOrNull(-value);
  }
  if (node.kind === 'binary') {
    const left = evaluateFormulaNode(node.left, result, rowIndex);
    const right = evaluateFormulaNode(node.right, result, rowIndex);
    if (left === null || right === null) return null;
    if (node.operator === '/' && right === 0) return null;
    const value = node.operator === '+'
      ? left + right
      : node.operator === '-'
        ? left - right
        : node.operator === '*'
          ? left * right
          : left / right;
    return finiteOrNull(value);
  }

  const args = node.args.map((arg) => evaluateFormulaNode(arg, result, rowIndex));
  if (node.name === 'COALESCE') return args.find((value) => value !== null) ?? null;
  if (args[0] === null) return null;
  if (node.name === 'ABS') return finiteOrNull(Math.abs(args[0]!));
  const digits = args[1] === null || args[1] === undefined
    ? 0
    : Math.min(6, Math.max(0, Math.round(args[1])));
  const factor = 10 ** digits;
  return finiteOrNull(Math.round(args[0]! * factor) / factor);
};

/**
 * Append active calculated metrics to a Dataset result so the existing chart/table and
 * Phase 8 table-calculation renderers can consume them through the normal binding lookup.
 */
export const materializeCalculatedFields = (
  spec: AnalysisSpec,
  result: DatasetQueryResult,
): DatasetQueryResult => {
  const active = spec.metrics.flatMap((metric) => {
    const calculated = calculatedFieldFor(spec, metric.field);
    return calculated ? [{ metric, calculated }] : [];
  });
  if (!active.length) return result;

  const bindings = [...result.bindings];
  const columns = [...result.columns];
  const valuesByField = active.map(({ metric, calculated }, index) => {
    const key = calculatedFieldKey(calculated);
    bindings.push({
      key: `calc${index + result.bindings.length}`,
      fieldId: key,
      displayName: calculated.name,
      dataType: 'NUMBER',
      aggregation: metric.aggregation,
    });
    columns.push({
      name: key,
      label: calculated.name,
      typeName: 'NUMBER',
      jdbcType: 3,
      nullable: true,
    });
    return result.rows.map((_, rowIndex) => evaluateFormulaNode(calculated.ast, result, rowIndex));
  });

  return {
    ...result,
    bindings,
    columns,
    rows: result.rows.map((row, rowIndex) => [
      ...row,
      ...valuesByField.map((values) => values[rowIndex]),
    ]),
  };
};
