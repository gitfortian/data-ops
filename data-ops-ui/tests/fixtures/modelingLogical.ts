import type { LogicalDraftDetail } from '@/services/modeling/logical';

export const logicalSample = (projectId = 1): LogicalDraftDetail => ({
  model: { id: 10, projectId, domainId: 2, processId: 7, draftRevision: 3,
    code: 'order_design', name: '订单逻辑设计', status: 'DRAFT' },
  process: { id: 7, domainId: 2, code: 'order', name: '下单', grain: '一笔订单', bizType: 'FACT', sortOrder: 0 },
  entities: [
    { entity: { id: 11, logicalModelId: 10, code: 'order', name: 'Order', businessName: '订单' },
      attributes: [
        { id: 101, entityId: 11, code: 'order_id', name: '订单编号', logicalType: 'STRING', primaryFlag: true, stdFieldId: 201 },
        { id: 102, entityId: 11, code: 'amount', name: '实付金额', logicalType: 'DECIMAL', nullable: false },
      ] },
    { entity: { id: 12, logicalModelId: 10, code: 'customer', name: 'Customer', businessName: '客户' }, attributes: [] },
    { entity: { id: 13, logicalModelId: 10, code: 'campaign', name: 'Campaign', businessName: '活动' }, attributes: [] },
  ],
  relations: [
    { id: 51, sourceEntityId: 11, targetEntityId: 12, relationType: 'ASSOCIATION', cardinality: 'UNKNOWN', description: '订单所属客户' },
    { id: 52, sourceEntityId: 12, targetEntityId: 13, relationType: 'ASSOCIATION', cardinality: 'MANY_TO_MANY' },
  ],
});
