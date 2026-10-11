import {
  chooseJourneyLogicalModel, logicalDraftsForProcess, parseJourneyProcessId,
  processToLogical, processToPhysical, processToSemantic,
} from './journeyNavigation';

const drafts = [
  { id: 21, processId: 7, name: '订单' },
  { id: 22, processId: 8, name: '支付' },
  { id: 23, processId: 7, name: '订单扩展' },
];

describe('guided process/model URL context', () => {
  it('allows only safe, canonical positive process identities', () => {
    expect(parseJourneyProcessId('7')).toBe(7);
    for (const input of [null, '', '0', '-1', '+1', '1.0', '01',
      '1&foo=bar', 'NaN', '9007199254740993', ' 7', '7 ']) {
      expect(parseJourneyProcessId(input)).toBeUndefined();
    }
    expect(() => processToLogical(0)).toThrow();
    expect(() => processToPhysical(Number.MAX_SAFE_INTEGER + 1)).toThrow();
  });

  it('uses distinct, non-mutating existing professional routes', () => {
    expect(processToSemantic(7)).toBe('/semantic/processes/7/edit');
    expect(processToLogical(7)).toBe('/modeling/logical?processId=7');
    expect(processToPhysical(7)).toBe('/modeling?processId=7');
    expect(processToLogical(7)).not.toContain('approved');
  });

  it('never falls back to another process when the URL is scoped', () => {
    expect(logicalDraftsForProcess(drafts, 7).map(v => v.id)).toEqual([21, 23]);
    expect(chooseJourneyLogicalModel(drafts, 8)).toBe(22);
    expect(chooseJourneyLogicalModel(drafts, 9)).toBeUndefined();
    expect(chooseJourneyLogicalModel(drafts, 7, 22)).toBe(21);
    expect(chooseJourneyLogicalModel(drafts, 7, 23)).toBe(23);
    expect(chooseJourneyLogicalModel(drafts)).toBe(21);
  });
});
