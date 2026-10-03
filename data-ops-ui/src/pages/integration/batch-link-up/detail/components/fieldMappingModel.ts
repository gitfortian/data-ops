import type { DataSourceColumnOption } from "../hooks/useDataSourceColumns";

export interface FieldMappingValue {
  source: string;
  target: string;
}
export interface FieldMappingRow {
  key: string;
  sourceField?: string;
  targetField?: string;
}

export const normalizeFieldName = (value: string) => value.trim().toLowerCase();

export const getColumnLabel = (column: DataSourceColumnOption) =>
  String(column.label || column.value);

export const getFieldType = (column?: DataSourceColumnOption) => {
  if (!column?.description) {
    return "-";
  }

  return column.description.split(" · ")[0] || "-";
};

export const parseManualFields = (value: string) =>
  value
    .split(/\r?\n/)
    .map((item) => item.trim())
    .filter(Boolean);

export const rowsToMappingValue = (
  rows: FieldMappingRow[]
): FieldMappingValue[] =>
  rows
    .filter((row) => Boolean(row.sourceField) && Boolean(row.targetField))
    .map((row) => ({
      source: row.sourceField as string,
      target: row.targetField as string,
    }));

export const mappingValueToRows = (
  value: FieldMappingValue[],
  createMappingKey: (index: number) => string
): FieldMappingRow[] =>
  value.map((item, index) => ({
    key: createMappingKey(index),
    sourceField: item.source,
    targetField: item.target,
  }));

export const mappingValueSignature = (value: FieldMappingValue[]) =>
  value.map((item) => `${item.source}\u0000${item.target}`).join("\u0001");

export const buildSameNameMappings = (
  sourceColumns: DataSourceColumnOption[],
  targetColumns: DataSourceColumnOption[],
  createMappingKey: (index: number) => string
): FieldMappingRow[] => {
  const targetFieldMap = new Map(
    targetColumns.map((column) => [
      normalizeFieldName(column.value),
      column.value,
    ])
  );

  return sourceColumns
    .map((column, index) => {
      const targetField = targetFieldMap.get(normalizeFieldName(column.value));

      if (!targetField) {
        return null;
      }

      return {
        key: createMappingKey(index),
        sourceField: column.value,
        targetField,
      };
    })
    .filter(Boolean) as FieldMappingRow[];
};

export const buildPositionMappings = (
  sourceColumns: DataSourceColumnOption[],
  targetColumns: DataSourceColumnOption[],
  createMappingKey: (index: number) => string
): FieldMappingRow[] => {
  const mappingSize = Math.min(sourceColumns.length, targetColumns.length);

  return Array.from({ length: mappingSize }, (_, index) => ({
    key: createMappingKey(index),
    sourceField: sourceColumns[index]?.value,
    targetField: targetColumns[index]?.value,
  }));
};
