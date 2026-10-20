import { deriveManualJourney, MANUAL_JOURNEY_LABELS, type ManualJourneyFacts } from './manualModelingJourney';

const facts: ManualJourneyFacts = {
  hasLogicalDraft: true,
  hasExistingDraftChoices: true,
  hasFrozenLogicalVersion: true,
  hasPhysicalTarget: true,
  hasMatchingPreview: true,
  physicalLayer: 'DWD',
  previewBlocked: false,
  reviewReady: true,
  mappingDraftMatches: true,
  mappingFrozenMatches: true,
  verifiedPublishedReceipt: false,
  approvalPending: false,
  mappingUnavailable: false,
};

it('covers the complete five-step manual modeling path without claiming a Runtime execution', () => {
  expect(MANUAL_JOURNEY_LABELS).toHaveLength(5);
  expect(deriveManualJourney({ ...facts, hasLogicalDraft: false }).step).toBe(0);
  expect(deriveManualJourney({ ...facts, hasFrozenLogicalVersion: false }).step).toBe(0);
  expect(deriveManualJourney({ ...facts, hasPhysicalTarget: false }).step).toBe(1);
  expect(deriveManualJourney({ ...facts, hasMatchingPreview: false }).step).toBe(1);
  expect(deriveManualJourney({ ...facts, reviewReady: false }).step).toBe(2);
  expect(deriveManualJourney({ ...facts, mappingDraftMatches: false }).step).toBe(2);
  expect(deriveManualJourney({ ...facts, mappingFrozenMatches: false }).step).toBe(3);
  expect(deriveManualJourney(facts).step).toBe(4);
  const published = deriveManualJourney({ ...facts, verifiedPublishedReceipt: true });
  expect(published.step).toBe(5);
  expect(published.complete).toBe(true);
  expect(published.explanation).toContain('数据加工请在开发工作台继续');
});

it('selects an existing logical draft instead of proposing a duplicate creation', () => {
  const existing = deriveManualJourney({ ...facts,
    hasLogicalDraft: false, hasExistingDraftChoices: true,
  });
  const empty = deriveManualJourney({ ...facts,
    hasLogicalDraft: false, hasExistingDraftChoices: false,
  });
  expect(existing.action).toBe('选择现有逻辑草稿');
  expect(empty.action).toBe('创建逻辑草稿');
  expect(existing.complete).toBe(false);
});

it('refuses to call a preflight success an accepted mapping or publication', () => {
  const previewOnly = deriveManualJourney({ ...facts,
    reviewReady: false, mappingDraftMatches: false, mappingFrozenMatches: false,
  });
  expect(previewOnly.step).toBe(2);
  expect(previewOnly.complete).toBe(false);
  const blocked = deriveManualJourney({ ...facts, previewBlocked: true });
  expect(blocked.step).toBe(1);
  expect(blocked.blocked).toBe(true);
});

it('DWS and ADS cannot inherit DWD-only mutation steps', () => {
  for (const layer of ['DWS', 'ADS', 'DIM', 'ODS', undefined]) {
    const blocked = deriveManualJourney({ ...facts, physicalLayer: layer });
    expect(blocked.step).toBe(2);
    expect(blocked.blocked).toBe(true);
    expect(blocked.complete).toBe(false);
  }
});

it('does not mistake a pending approval for successful design publication', () => {
  const pending = deriveManualJourney({ ...facts, approvalPending: true });
  expect(pending.step).toBe(4);
  expect(pending.complete).toBe(false);
  expect(pending.blocked).toBe(true);
  expect(pending.explanation).toContain('审批通过前，设计仍未发布');
});

it('forces unsafe or unavailable mapping evidence through explicit recovery', () => {
  const invalid = deriveManualJourney({ ...facts, mappingUnavailable: true });
  expect(invalid.blocked).toBe(true);
  expect(invalid.complete).toBe(false);
  expect(invalid.action).toContain('异常');
});

it('prioritizes recovery when saved provider evidence is unavailable', () => {
  const invalid = deriveManualJourney({ ...facts, reviewReady: false, mappingUnavailable: true });
  expect(invalid.step).toBe(2);
  expect(invalid.blocked).toBe(true);
  expect(invalid.action).toContain('异常');
});

it('pending approval takes precedence over a prior session publication receipt', () => {
  const pending = deriveManualJourney({ ...facts,
    verifiedPublishedReceipt: true, approvalPending: true,
  });
  expect(pending.complete).toBe(false);
  expect(pending.blocked).toBe(true);
  expect(pending.action).toContain('审批');
});

it('does not confuse a saved mutable draft with an immutable revision', () => {
  const saved = deriveManualJourney({ ...facts, mappingFrozenMatches: false });
  expect(saved.step).toBe(3);
  expect(saved.complete).toBe(false);
  expect(saved.action).toBe('保存映射版本');
});
