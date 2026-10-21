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
  '逻辑设计',
  '物理模型',
  '字段映射',
  '保存映射版本',
  '发布设计',
] as const;

export function deriveManualJourney(facts: ManualJourneyFacts): ManualJourneyResult {
  const next = (step: ManualJourneyStep, action: string, explanation: string,
    anchor: ManualJourneyResult['anchor'], blocked = false): ManualJourneyResult => ({
    step, action, explanation, anchor, blocked, complete: false,
  });

  if (!facts.hasLogicalDraft) {
    if (facts.hasExistingDraftChoices) {
      return next(0, '选择现有逻辑草稿',
        '选择已有草稿，继续设计实体和属性。', 'logical');
    }
    return next(0, '创建逻辑草稿',
      '选择业务过程，定义实体、属性和关系。', 'logical');
  }
  if (!facts.hasFrozenLogicalVersion) {
    return next(0, '保存设计版本',
      '完成实体、属性和关系后，保存供物理建模引用的设计快照。', 'logical');
  }
  if (!facts.hasPhysicalTarget || !facts.hasMatchingPreview) {
    return next(1, '选择物理模型并核对',
      '选择目标模型，对照逻辑设计版本和物理字段。', 'physical');
  }
  if (facts.previewBlocked) {
    return next(1, '处理模型核对问题',
      '按核对结果修正模型后，重新检查字段映射。', 'mapping', true);
  }
  if (facts.physicalLayer !== 'DWD') {
    return next(2, '查看模型核对结果',
      '此处支持保存和发布 DWD 映射；当前分层请在对应物理模型中继续设计。', 'mapping', true);
  }
  if (facts.mappingUnavailable) {
    return next(2, '处理映射读取异常',
      '重新读取已保存映射，确认版本后继续。', 'mapping', true);
  }
  if (facts.reviewReady === false) {
    return next(2, '核对字段映射',
      '选择对应逻辑属性和映射规则，再检查本次选择。', 'mapping');
  }
  if (!facts.mappingDraftMatches) {
    return next(2, '保存字段映射',
      '保存已核对的映射；已有草稿有变化时，请先重新核对。', 'mapping');
  }
  if (!facts.mappingFrozenMatches) {
    return next(3, '保存映射版本',
      '为当前映射保存不可变版本，供设计发布引用。', 'mapping');
  }
  if (facts.approvalPending) {
    return next(4, '查看发布审批',
      '等待审批结果；审批通过前，设计仍未发布。', 'mapping', true);
  }
  if (facts.verifiedPublishedReceipt) {
    return {
      step: 5, action: '打开已发布物理模型',
      explanation: '设计版本及映射版本已核对。数据加工请在开发工作台继续。',
      anchor: 'published', blocked: false, complete: true,
    };
  }
  return next(4, '确认发布 DWD 设计版本',
    '确认发布当前设计和映射版本，完成后可查看发布记录。', 'mapping');
}
