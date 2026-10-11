import {
  locateCurrentLogicalAttribute, locateCurrentLogicalRelation, locatePhysicalCoverageColumn,
} from './governanceRepairLocator';
import type { LogicalDraftDetail } from '@/services/modeling/logical';
import type { PhysicalStandardCoverageInspection } from '@/services/modeling/api';
import type { ModelingStructureRecord } from '@/services/modeling/types';
import type { ColumnDraft } from './editor/structureRules';

const col: PhysicalStandardCoverageInspection['columns'][number] = {
  columnId: 31, columnName: 'order_id', dataType: 'BIGINT', stdFieldId: 11,
  status: 'INVALID_STANDARD', explanation: '标准目前停用',
  standardName: null, semanticRole: null, standardType: null,
  physicalRole: null, aggregateFunc: null,
};
const saved: ModelingStructureRecord = {
  modelId: 50,
  columns: [{ id: 31, columnName: 'order_id', dataType: 'BIGINT', stdFieldId: 11 },
    { id: 32, columnName: 'amount', dataType: 'DECIMAL', stdFieldId: null }],
};
const rows: ColumnDraft[] = [
  { key: 901, columnName: 'order_id', dataType: 'BIGINT', nullable: false, stdFieldId: 11 },
  { key: 902, columnName: 'amount', dataType: 'DECIMAL', nullable: false, stdFieldId: null },
];
const input = () => ({
  reportModelId: 50, editorModelId: 50, saved, rows, dirty: false, column: col,
});
const logical: LogicalDraftDetail = {
  model: { id: 71, projectId: 2, processId: 10, domainId: 3,
    draftRevision: 4, code: 'order', name: '订单', status: 'DRAFT' },
  process: { id: 10, code: 'place_order', name: '下单' } as LogicalDraftDetail['process'],
  entities: [{ entity: { id: 101, logicalModelId: 71, code: 'order', name: '订单' },
    attributes: [{ id: 301, entityId: 101, code: 'order_id', name: '订单编号', stdFieldId: 11 }] }],
  relations: [{ id: 401, sourceEntityId: 101, targetEntityId: 101,
    relationType: 'ASSOCIATION', cardinality: 'UNKNOWN' }],
};

describe('evidence-to-authoring repair localization', () => {
  it('identifies a saved physical column by DAO ID but returns the active UI row key', () => {
    expect(locatePhysicalCoverageColumn(input())).toEqual({ ok: true, value: 901 });
    expect(locatePhysicalCoverageColumn({ ...input(),
      column: { ...col, columnName: 'ORDER_ID' } })).toEqual({ ok: true, value: 901 });
  });

  it('refuses dirty, foreign, stale standard/type/aggregate, duplicate or same-named replacement columns', () => {
    const invalid = [
      { ...input(), dirty: true },
      { ...input(), reportModelId: 72 },
      { ...input(), editorModelId: 72 },
      { ...input(), column: { ...col, columnId: 32 } },
      { ...input(), column: { ...col, stdFieldId: 99 } },
      { ...input(), column: { ...col, dataType: 'VARCHAR' } },
      { ...input(), column: { ...col, aggregateFunc: 'SUM' } },
      { ...input(), saved: { ...saved, columns: [
        { id: 99, columnName: 'order_id', dataType: 'BIGINT', stdFieldId: 11 },
      ] } },
      { ...input(), saved: { ...saved, columns: [
        ...(saved.columns ?? []),
        { id: 33, columnName: 'ORDER_ID', dataType: 'BIGINT', stdFieldId: 11 },
      ] } },
      { ...input(), rows: [...rows,
        { key: 903, columnName: 'ORDER_ID', dataType: 'BIGINT', nullable: false }] },
      { ...input(), rows: [{ ...rows[0], stdFieldId: 77 }] },
    ];
    invalid.forEach(args => expect(locatePhysicalCoverageColumn(args).ok).toBe(false));
  });

  it('locates current logical objects by stable identity even when their labels changed', () => {
    expect(locateCurrentLogicalAttribute(71, logical, 101, 301))
      .toEqual({ ok: true, value: { entityId: 101, attributeId: 301 } });
    expect(locateCurrentLogicalRelation(71, logical, 401))
      .toEqual({ ok: true, value: 401 });
    const renamed = { ...logical, entities: logical.entities.map(e => ({
      ...e, attributes: e.attributes.map(a => ({ ...a, name: '新业务名称' })),
    })) };
    expect(locateCurrentLogicalAttribute(71, renamed, 101, 301).ok).toBe(true);
  });

  it('cannot edit removed/moved/foreign logical snapshots by guessed names', () => {
    expect(locateCurrentLogicalAttribute(99, logical, 101, 301).ok).toBe(false);
    expect(locateCurrentLogicalAttribute(71, logical, 101, 999).ok).toBe(false);
    expect(locateCurrentLogicalAttribute(71, logical, 999, 301).ok).toBe(false);
    expect(locateCurrentLogicalRelation(71, logical, 999).ok).toBe(false);
    const moved = { ...logical, entities: [{
      ...logical.entities[0],
      attributes: [{ ...logical.entities[0].attributes[0], entityId: 777 }],
    }] };
    expect(locateCurrentLogicalAttribute(71, moved, 101, 301).ok).toBe(false);
  });
});
