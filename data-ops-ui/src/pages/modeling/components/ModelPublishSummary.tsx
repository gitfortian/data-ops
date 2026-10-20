import { Tag } from 'antd';
import { Database } from 'lucide-react';
import type { ModelingColumnRecord, ModelingStructureRecord } from '@/services/modeling/types';
import { MODELING_DIALECT_LABELS } from '../constants';
import './ModelPublishSummary.less';

interface ModelPublishSummaryProps {
  before: ModelingStructureRecord | null;
  after: ModelingStructureRecord;
  publishedVersionNo: number | null;
  layerCode?: string;
  approval: boolean;
}

interface PropertyChange { label: string; before: string; after: string }
interface ColumnChange {
  name: string;
  status: 'added' | 'changed' | 'removed';
  column: ModelingColumnRecord;
  properties: PropertyChange[];
}

const columnProperties: { key: keyof ModelingColumnRecord; label: string }[] = [
  { key: 'columnName', label: '字段名' }, { key: 'dataType', label: '类型' },
  { key: 'length', label: '长度' }, { key: 'scale', label: '小数位' },
  { key: 'nullable', label: '可空' }, { key: 'defaultValue', label: '默认值' },
  { key: 'comment', label: '注释' }, { key: 'businessDescription', label: '业务描述' },
  { key: 'stdFieldId', label: '标准字段' }, { key: 'stdTypeId', label: '类型标准' },
  { key: 'stdNamingId', label: '命名标准' }, { key: 'stdCodeSetCode', label: '码集' },
  { key: 'stdUnitId', label: '单位标准' }, { key: 'stdCaliberId', label: '口径标准' },
  { key: 'stdSecurityId', label: '安全标准' }, { key: 'fieldRole', label: '字段角色' },
  { key: 'aggregateFunc', label: '聚合函数' }, { key: 'transformExpr', label: '转换表达式' },
];
const statusLabels = { added: '新增', changed: '修改', removed: '删除' };
const statusColors = { added: 'green', changed: 'gold', removed: 'red' };
const fieldKey = (name?: string) => (name ?? '').toLowerCase();
const text = (value: string | number | boolean | null | undefined): string =>
  value === null || value === undefined || value === '' ? '未设置'
    : typeof value === 'boolean' ? (value ? '是' : '否')
    : value === 'DIMENSION' ? '维度' : value === 'MEASURE' ? '度量' : String(value);

const columnType = (column: ModelingColumnRecord): string => {
  const size = column.length == null ? ''
    : `(${column.length}${column.scale == null ? '' : `, ${column.scale}`})`;
  return `${column.dataType || '未设置'}${size}`;
};

/** Match fields by name so an insertion cannot turn every subsequent field into a change. */
function compareColumns(before: ModelingStructureRecord, after: ModelingStructureRecord): ColumnChange[] {
  const previous = new Map((before.columns ?? []).map(column => [fieldKey(column.columnName), column]));
  const nextNames = new Set((after.columns ?? []).map(column => fieldKey(column.columnName)));
  const changes: ColumnChange[] = [];
  (after.columns ?? []).forEach(column => {
    const old = previous.get(fieldKey(column.columnName));
    if (!old) {
      changes.push({ name: column.columnName || '未命名字段', status: 'added', column, properties: [] });
      return;
    }
    const properties = columnProperties.flatMap(({ key, label }) => {
      return (old[key] ?? '') === (column[key] ?? '') ? []
        : [{ label, before: text(old[key]), after: text(column[key]) }];
    });
    if (properties.length) changes.push({ name: column.columnName || '未命名字段', status: 'changed', column, properties });
  });
  (before.columns ?? []).forEach(column => {
    if (!nextNames.has(fieldKey(column.columnName))) {
      changes.push({ name: column.columnName || '未命名字段', status: 'removed', column, properties: [] });
    }
  });
  return changes;
}

function compareConfiguration(before: ModelingStructureRecord, after: ModelingStructureRecord): PropertyChange[] {
  const changes: PropertyChange[] = [];
  const compare = (label: string, previous: string, next: string) => {
    if (previous !== next) changes.push({ label, before: previous || '未设置', after: next || '未设置' });
  };
  compare('物理表名', before.tableName || before.modelCode || '', after.tableName || after.modelCode || '');
  compare('表注释', before.tableComment ?? '', after.tableComment ?? '');
  compare('模型名称', before.modelName ?? '', after.modelName ?? '');
  compare('模型描述', before.modelDescription ?? '', after.modelDescription ?? '');
  compare('数据库方言', before.dialect ?? '', after.dialect ?? '');
  compare('主键', (before.primaryKey ?? []).join('、'), (after.primaryKey ?? []).join('、'));
  const partitionText = ({ partition }: ModelingStructureRecord) => partition?.type
    ? [partition.type, (partition.columns ?? []).join('、'), partition.expression].filter(Boolean).join(' · ') : '';
  compare('分区', partitionText(before), partitionText(after));
  const oldIndexes = new Map((before.indexes ?? []).map(index => [index.indexName, index]));
  const newIndexes = new Map((after.indexes ?? []).map(index => [index.indexName, index]));
  for (const name of new Set([...oldIndexes.keys(), ...newIndexes.keys()])) {
    const describe = (index?: NonNullable<ModelingStructureRecord['indexes']>[number]) => index
      ? [index.uniqueIndex ? '唯一索引' : '普通索引', index.indexType, (index.columns ?? []).join('、')].filter(Boolean).join(' · ') : '';
    compare(`索引 · ${name || '未命名'}`, describe(oldIndexes.get(name)), describe(newIndexes.get(name)));
  }
  for (const key of new Set([...Object.keys(before.tableProperties ?? {}), ...Object.keys(after.tableProperties ?? {})])) {
    compare(`表属性 · ${key}`, before.tableProperties?.[key] ?? '', after.tableProperties?.[key] ?? '');
  }
  // Only compare relative order among existing fields; insertion/removal already has its own entry.
  const oldNames = (before.columns ?? []).map(column => fieldKey(column.columnName));
  const newNames = (after.columns ?? []).map(column => fieldKey(column.columnName));
  const commonNames = new Set(oldNames.filter(name => newNames.includes(name)));
  compare('字段顺序', oldNames.filter(name => commonNames.has(name)).join(' → '),
    newNames.filter(name => commonNames.has(name)).join(' → '));
  return changes;
}

const PropertyDifference = ({ change }: { change: PropertyChange }) => (
  <div className="model-publish-property">
    <span className="model-publish-property-label">{change.label}</span>
    <span className="model-publish-old">{change.before}</span>
    <span aria-label="变更为" className="model-publish-arrow">→</span>
    <span>{change.after}</span>
  </div>
);

/** Presentation of saved structures only; publication gates and commands remain in the editor. */
export default function ModelPublishSummary({ before, after, publishedVersionNo, layerCode, approval }: ModelPublishSummaryProps) {
  const firstPublish = !publishedVersionNo;
  const columns = after.columns ?? [];
  const primaryKey = new Set((after.primaryKey ?? []).map(fieldKey));
  const changes = before && !firstPublish ? compareColumns(before, after) : [];
  const configuration = before && !firstPublish ? compareConfiguration(before, after) : [];
  const nextVersion = (publishedVersionNo ?? 0) + 1;

  return (
    <div className="model-publish-summary">
      <div className="model-publish-identity">
        <span className="model-publish-icon"><Database size={21} aria-hidden /></span>
        <div className="model-publish-name">
          <strong>{after.modelName || after.modelCode || after.tableName || '物理模型'}</strong>
          <div>{after.tableName || after.modelCode}{layerCode ? ` · ${layerCode}` : ''}
            {after.dialect ? ` · ${MODELING_DIALECT_LABELS[after.dialect] || after.dialect}` : ''}</div>
        </div>
        <span className="model-publish-version">
          {firstPublish ? '首次发布' : `V${publishedVersionNo} →`} <b>V{nextVersion}</b>
        </span>
      </div>

      <div className="model-publish-stats">
        {[['字段', columns.length], ['主键字段', primaryKey.size], ['索引', after.indexes?.length ?? 0]].map(([label, count]) => (
          <div key={label}><span>{label}</span><strong>{count}</strong></div>
        ))}
      </div>

      {!firstPublish && before ? (
        <div className="model-publish-changes" aria-label="发布变更概览">
          {(['added', 'changed', 'removed'] as const).map(status => (
            <span key={status}>{statusLabels[status]}字段 <b>{changes.filter(change => change.status === status).length}</b></span>
          ))}
          {configuration.length ? <span>配置变更 <b>{configuration.length}</b></span> : null}
          {!changes.length && !configuration.length ? <div>与上一发布版本的结构一致</div> : null}
        </div>
      ) : !firstPublish ? <p className="model-publish-note">上一发布版本结构不可用，无法生成变更摘要。</p> : null}

      <p className="model-publish-note">{approval
        ? '审批通过后生成新的设计版本。'
        : '将已保存的表结构发布为新的设计版本。'}</p>

      {firstPublish ? (
        <details className="model-publish-details">
          <summary>查看字段明细（{columns.length}）</summary>
          <div className="model-publish-table-scroll">
            <table><thead><tr><th>字段名</th><th>类型</th><th>约束</th><th>注释</th></tr></thead>
              <tbody>{columns.map((column, index) => (
                <tr key={column.columnName || index}>
                  <td className="model-publish-field-name">{column.columnName}</td>
                  <td>{columnType(column)}</td>
                  <td>{primaryKey.has(fieldKey(column.columnName)) ? '主键' : column.nullable ? '可空' : '非空'}</td>
                  <td>{column.comment || '—'}</td>
                </tr>
              ))}</tbody>
            </table>
          </div>
        </details>
      ) : changes.length || configuration.length ? (
        <details className="model-publish-details">
          <summary>查看变更明细</summary>
          <div className="model-publish-table-scroll">
            {changes.length ? <table><thead><tr><th>字段名</th><th>变更</th><th>内容</th></tr></thead>
              <tbody>{changes.map(change => (
                <tr key={change.name}>
                  <td className="model-publish-field-name">{change.name}</td>
                  <td><Tag color={statusColors[change.status]}>{statusLabels[change.status]}</Tag></td>
                  <td>{change.properties.length
                    ? change.properties.map(property => <PropertyDifference key={property.label} change={property} />)
                    : <>{columnType(change.column)}{change.column.comment ? ` · ${change.column.comment}` : ''}</>}</td>
                </tr>
              ))}</tbody>
            </table> : null}
            {configuration.map(change => <div className="model-publish-config" key={change.label}>
              <PropertyDifference change={change} />
            </div>)}
          </div>
        </details>
      ) : null}
    </div>
  );
}
