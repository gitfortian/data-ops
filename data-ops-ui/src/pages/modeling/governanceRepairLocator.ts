import type { ColumnDraft } from './editor/structureRules';
import type { ModelingColumnRecord, ModelingStructureRecord } from '@/services/modeling/types';
import type { PhysicalStandardCoverageInspection } from '@/services/modeling/api';
import type { LogicalDraftDetail } from '@/services/modeling/logical';

/** Navigation-only result: never patches standards or writes data. */
export type RepairTarget<T> = { ok: true; value: T } | { ok: false; reason: string };
const reject = <T,>(reason: string): RepairTarget<T> => ({ ok: false, reason });
const normalized = (value?: string): string => value?.trim().toLowerCase() ?? '';
type PhysicalColumn = PhysicalStandardCoverageInspection['columns'][number];

/**
 * The editor uses freshly generated UI row keys, NOT physical column DAO IDs.
 * Anchor a diagnostic on (persisted columnId, name, ref, type), verify it
 * against the locally loaded saved structure, then find the exactly matching
 * editable row. Names alone are NOT a valid identity match.
 */
export const locatePhysicalCoverageColumn = (input: {
  reportModelId: number;
  editorModelId: number;
  saved: ModelingStructureRecord | undefined;
  rows: ColumnDraft[];
  dirty: boolean;
  column: PhysicalColumn;
}): RepairTarget<number> => {
  const { saved, rows, column } = input;
  if (input.dirty) return reject('存在未保存的物理结构变更；旧的标准覆盖证据已不能精确定位，请先保存或放弃编辑并重新核验');
  if (!saved || saved.modelId !== input.reportModelId
      || input.editorModelId !== input.reportModelId) {
    return reject('当前物理模型与报告所属模型不一致，请重新核验');
  }
  if (!Number.isSafeInteger(column.columnId) || column.columnId <= 0
      || !normalized(column.columnName)) {
    return reject('报告的物理字段身份缺失，请重新核验');
  }
  const savedMatches = (saved.columns ?? []).filter((row) => row.id === column.columnId);
  if (savedMatches.length !== 1) return reject('原始物理字段已删除、变更身份或不唯一，请重新核验');
  const stored: ModelingColumnRecord = savedMatches[0];
  if (normalized(stored.columnName) !== normalized(column.columnName)
      || (stored.stdFieldId ?? null) !== (column.stdFieldId ?? null)
      || normalized(stored.dataType) !== normalized(column.dataType)
      || (stored.aggregateFunc ?? null) !== (column.aggregateFunc ?? null)) {
    return reject('物理字段名称、标准引用、类型或聚合含义已变化；旧证据不能作为当前编辑依据');
  }
  const storedName = normalized(stored.columnName);
  if ((saved.columns ?? []).filter((row) => normalized(row.columnName) === storedName).length !== 1) {
    return reject('物理字段名称重复，不能安全定位');
  }
  const editorMatches = rows.filter((row) => normalized(row.columnName) === storedName);
  if (editorMatches.length !== 1
      || (editorMatches[0].stdFieldId ?? null) !== (stored.stdFieldId ?? null)
      || normalized(editorMatches[0].dataType) !== normalized(stored.dataType)) {
    return reject('编辑器中的字段与已保存结构不一致，请重新加载并核验');
  }
  return { ok: true, value: editorMatches[0].key };
};

/**
 * Frozen logical version is immutable. These resolvers locate only current
 * authoring objects by stable IDs, never by labels and never mutate history.
 */
export const locateCurrentLogicalAttribute = (
  modelId: number, detail: LogicalDraftDetail, entityId: number, attributeId: number,
): RepairTarget<{ entityId: number; attributeId: number }> => {
  if (detail.model.id !== modelId) return reject('逻辑模型身份不一致');
  const matchingEntities = detail.entities.filter(e => e.entity.id === entityId);
  if (matchingEntities.length !== 1) return reject('冻结属性所属业务实体已删除或身份发生变化');
  const matches = matchingEntities[0].attributes.filter(a =>
    a.id === attributeId && a.entityId === entityId);
  const identityCount = detail.entities.flatMap(e => e.attributes)
    .filter(a => a.id === attributeId).length;
  if (matches.length !== 1 || identityCount !== 1) {
    return reject('当前逻辑草稿已删除或移动该属性，请先查看差异，不允许按同名属性代替');
  }
  return { ok: true, value: { entityId, attributeId } };
};

export const locateCurrentLogicalRelation = (
  modelId: number, detail: LogicalDraftDetail, relationId: number,
): RepairTarget<number> => {
  if (detail.model.id !== modelId) return reject('逻辑模型身份不一致');
  if (detail.relations.filter(r => r.id === relationId).length !== 1) {
    return reject('该冻结关系已删除或身份发生变化，请先查看设计差异');
  }
  return { ok: true, value: relationId };
};
