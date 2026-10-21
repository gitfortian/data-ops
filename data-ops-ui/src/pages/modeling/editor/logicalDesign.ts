import type { LogicalDraftDetail, LogicalRelation } from '@/services/modeling/logical';
import type { LogicalId } from '@/services/modeling/logicalIdentity';

export const LOGICAL_CANVAS_LIMIT = 40;
export const LOGICAL_CARD_ATTRIBUTE_LIMIT = 5;
export const LOGICAL_CARDINALITY: Record<LogicalRelation['cardinality'], string> = {
  ONE_TO_ONE: '1 : 1', ONE_TO_MANY: '1 : N', MANY_TO_ONE: 'N : 1',
  MANY_TO_MANY: 'N : N', UNKNOWN: '基数待确认',
};
export type LogicalObjectSelection = { kind: 'entity'; id: LogicalId; attributeId?: LogicalId }
  | { kind: 'relation'; id: LogicalId };

export const logicalEntityLabel = (entity: LogicalDraftDetail['entities'][number]['entity']) =>
  entity.businessName || entity.name;

/** A view projection of the selected draft, never a semantic role or lineage inference. */
export function logicalDesignView(detail: LogicalDraftDetail, focusId?: LogicalId) {
  const allIds = new Set<LogicalId>(detail.entities.map(item => item.entity.id));
  const unresolved = detail.relations.filter(item => !allIds.has(item.sourceEntityId) || !allIds.has(item.targetEntityId));
  const connected = detail.relations.filter(item => allIds.has(item.sourceEntityId) && allIds.has(item.targetEntityId));
  const ids = focusId !== undefined && allIds.has(focusId) ? new Set<LogicalId>([focusId]) : allIds;
  if (ids !== allIds) for (const relation of connected) {
    if (relation.sourceEntityId === focusId || relation.targetEntityId === focusId) {
      ids.add(relation.sourceEntityId); ids.add(relation.targetEntityId);
    }
  }
  return {
    entities: detail.entities.filter(item => ids.has(item.entity.id)),
    relations: connected.filter(item => ids.has(item.sourceEntityId) && ids.has(item.targetEntityId)),
    unresolved,
  };
}

export function logicalEntityPositions(entities: LogicalDraftDetail['entities']) {
  return new Map(entities.map((item, index) => [item.entity.id, {
    x: (index % 3) * 350, y: Math.floor(index / 3) * 300,
  }]));
}

export function logicalDesignReminders(detail: LogicalDraftDetail) {
  const missingPrimary = detail.entities.filter(item => !item.attributes.some(attribute => attribute.primaryFlag));
  const missingStandards = detail.entities.flatMap(item => item.attributes
    .filter(attribute => attribute.stdFieldId == null)
    .map(attribute => ({ entityId: item.entity.id, attributeId: attribute.id })));
  return { missingPrimary, missingStandards,
    pendingRelations: detail.relations.filter(item => item.cardinality === 'UNKNOWN') };
}
