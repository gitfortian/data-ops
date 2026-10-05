import { governanceEntryPath, governanceQuestions, governanceSourcePath, parseGovernanceTarget } from './governance';

it('roundtrips asset and execution selections to existing routes', () => {
  const asset = { assetId: 7 };
  expect(parseGovernanceTarget(governanceEntryPath(asset).split('?')[1])).toEqual(asset);
  expect(governanceSourcePath(asset)).toBe('/data-asset/detail/7');
  const quality = { qualityExecutionNo: 'Q_20261005-1' };
  expect(parseGovernanceTarget(governanceEntryPath(quality).split('?')[1])).toEqual(quality);
  expect(governanceQuestions(quality)[0]).toContain('未执行');
});

it.each(['?assetId=-1', '?assetId=0', '?assetId=9007199254740993', '?assetId=7&qualityExecutionNo=q', '?qualityExecutionNo=../../secret', '?qualityExecutionNo=%3Cscript%3E'])('rejects malformed selections %s', (search) => {
  expect(parseGovernanceTarget(search)).toBeNull();
});
