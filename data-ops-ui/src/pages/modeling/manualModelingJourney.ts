/**
 * Manual B2 journey navigation. This is an advisory view over facts already
 * returned by the existing Modeling APIs; it grants no new publish authority.
 */
export type ManualJourneyFacts = {
  hasLogicalDraft: boolean;
  hasExistingDraftChoices: boolean;
  hasFrozenLogicalVersion: boolean;
  hasPhysicalTarget: boolean;
  hasMatchingPreview: boolean;
  physicalLayer?: string;
  previewBlocked: boolean;
  reviewReady: boolean;
  mappingDraftMatches: boolean;
  mappingFrozenMatches: boolean;
  verifiedPublishedReceipt: boolean;
  approvalPending: boolean;
  mappingUnavailable: boolean;
};

export type ManualJourneyStep = 0 | 1 | 2 | 3 | 4 | 5;
export type ManualJourneyResult = {
  step: ManualJourneyStep;
  action: string;
  explanation: string;
  anchor: 'logical' | 'physical' | 'mapping' | 'published';
  blocked: boolean;
  complete: boolean;
};

export const MANUAL_JOURNEY_LABELS = [
  '逻辑设计与冻结',
  '选择并预检物理目标',
  '核验并保存映射',
  '冻结映射修订',
  '发布设计与历史回读',
] as const;

export function deriveManualJourney(facts: ManualJourneyFacts): ManualJourneyResult {
  const next = (step: ManualJourneyStep, action: string, explanation: string,
    anchor: ManualJourneyResult['anchor'], blocked = false): ManualJourneyResult => ({
    step, action, explanation, anchor, blocked, complete: false,
  });

  if (!facts.hasLogicalDraft) {
    if (facts.hasExistingDraftChoices) {
      return next(0, '选择现有逻辑草稿',
        '当前项目已有可选草稿，优先选择并核对业务过程，不重复创建业务模型。', 'logical');
    }
    return next(0, '创建逻辑草稿',
      '从业务过程创建逻辑草稿，补齐业务实体、粒度和属性；不会自动生成物理表。', 'logical');
  }
  if (!facts.hasFrozenLogicalVersion) {
    return next(0, '定位逻辑设计并冻结快照',
      '先完成逻辑字段与业务关系的人工设计，再冻结一个可追溯的逻辑版本。', 'logical');
  }
  if (!facts.hasPhysicalTarget || !facts.hasMatchingPreview) {
    return next(1, '选择物理模型并发起预检',
      '使用已冻结的逻辑版本，查找当前项目的目标物理模型并重新核验结构。', 'physical');
  }
  if (facts.previewBlocked) {
    return next(1, '处理物理预检阻断项',
      '物理预检有阻断项，必须修复并重新核验，不能据此保存或发布映射。', 'mapping', true);
  }
  if (facts.physicalLayer !== 'DWD') {
    return next(2, '查看物理预检结果',
      '当前正式 MappingSet 保存与设计发布只支持 DWD；其他层不可套用 DWD 写操作。', 'mapping', true);
  }
  if (facts.mappingUnavailable) {
    return next(2, '处理映射读取或持久化异常',
      '当前映射版本无法确认，必须重新读取并恢复正确证据，不允许覆盖或继续发布。', 'mapping', true);
  }
  if (facts.reviewReady === false) {
    return next(2, '完成人工映射选择与校验',
      '逐列选择同标准字段的逻辑属性及 DIRECT/RENAME 规则，再执行服务端人工映射核验。', 'mapping');
  }
  if (!facts.mappingDraftMatches) {
    return next(2, '保存或重新核对 MappingSet',
      '保存人工确认的映射；若已有草稿与当前逻辑/物理证据不一致，先重新核验。', 'mapping');
  }
  if (!facts.mappingFrozenMatches) {
    return next(3, '冻结 MappingRevision',
      '基于当前已保存草稿执行冻结，持久产生不可变修订；冻结并不等于发布。', 'mapping');
  }
  if (facts.approvalPending) {
    return next(4, '查看发布审批',
      '审批尚未结束。没有当前审批的正式发布回执，不能把设计标记为已发布。', 'mapping', true);
  }
  if (facts.verifiedPublishedReceipt) {
    return {
      step: 5, action: '打开已发布物理模型',
      explanation: '当前会话已回读服务端历史，核对版本号与 MappingRevision；未创建任务或执行 SQL。',
      anchor: 'published', blocked: false, complete: true,
    };
  }
  return next(4, '确认发布 DWD 设计版本',
    '完成正式发布确认后必须回读准确的 ModelVersion 与 MappingRevision；不代表加工任务执行。', 'mapping');
}
