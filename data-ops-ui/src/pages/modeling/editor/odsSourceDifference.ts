import type { ModelingColumnPayload, ModelingImportColumnView } from '@/services/modeling/types';

export interface OdsFieldDifference {
  kind: 'same' | 'changed' | 'added' | 'technical';
  details: string[];
}

const key = (name: string) => name.trim().toLowerCase();
const text = (value?: string) => value?.trim() || '';
const width = (value?: number | null) => value && value > 0 ? value : null;

/** Structural comparison is read-only; a difference never invents a source mapping. */
export function compareOdsSourceColumns(
  source: ModelingImportColumnView[], columns: ModelingColumnPayload[], primaryKey: string[],
) {
  const physicalSource = source.filter(column => !column.technical);
  const sources = new Map(physicalSource.map(column => [key(column.name), column]));
  const technical = new Set(source.filter(column => column.technical).map(column => key(column.name)));
  const targets = new Set(columns.map(column => key(column.columnName)));
  const keys = new Set(primaryKey.map(key));
  const byTargetName = new Map<string, OdsFieldDifference>();
  const modelOnly: string[] = [];
  const changed: string[] = [];
  for (const column of columns) {
    const name = key(column.columnName);
    const original = sources.get(name);
    if (!original) {
      const generated = technical.has(name);
      byTargetName.set(name, { kind: generated ? 'technical' : 'added', details: [] });
      if (!generated) modelOnly.push(column.columnName);
      continue;
    }
    const details: string[] = [];
    if (original.name !== column.columnName.trim()) details.push(`名称：${original.name} → ${column.columnName}`);
    if (text(original.typeName).toUpperCase() !== text(column.dataType).toUpperCase()) {
      details.push(`类型：${original.typeName} → ${column.dataType}`);
    }
    if (width(original.size) !== width(column.length)) details.push(`长度：${width(original.size) ?? '未设置'} → ${width(column.length) ?? '未设置'}`);
    if ((original.decimalDigits ?? original.scale ?? 0) !== (column.scale ?? 0)) details.push('小数位不同');
    if ((original.nullable ?? false) !== (column.nullable ?? false)) details.push('可空设置不同');
    if ((original.primaryKey ?? false) !== keys.has(name)) details.push('主键设置不同');
    if (text(original.defaultValue) !== text(column.defaultValue)) details.push('默认值不同');
    if (text(original.remarks) !== text(column.comment)) details.push('注释不同');
    byTargetName.set(name, { kind: details.length ? 'changed' : 'same', details });
    if (details.length) changed.push(column.columnName);
  }
  return {
    byTargetName, modelOnly, changed,
    sourceOnly: physicalSource.filter(column => !targets.has(key(column.name))).map(column => column.name),
  };
}
