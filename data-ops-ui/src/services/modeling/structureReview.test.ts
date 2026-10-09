import HttpUtils from '@/utils/HttpUtils';
import { matchesStructureReview, prepareModelStructureReview, type ModelStructureReviewContext } from './structureReview';

jest.mock('@/utils/HttpUtils', () => ({ __esModule: true, default: { getData: jest.fn() } }));
const context: ModelStructureReviewContext = { projectId: '42', modelId: '7', baselineVersionNo: 3, baselineVersionId: '11', definition: 'a'.repeat(64),
  baselineColumnCount: 1, savedColumnCount: 2, changes: [], mappingChecks: [], coverageGaps: ['NO_HISTORICAL_MAPPING_SNAPSHOT'] };
it('uses the original model API for the exact selected baseline', async () => {
  (HttpUtils.getData as jest.Mock).mockResolvedValue(context);
  expect(await prepareModelStructureReview('7', 3)).toEqual(context);
  expect(HttpUtils.getData).toHaveBeenCalledWith('/api/v1/modeling/models/7/structure-review?baselineVersionNo=3');
  expect(matchesStructureReview(context, '42', '7', 3)).toBe(true);
});
it.each([{ projectId: '99' }, { modelId: '8' }, { baselineVersionNo: 4 }, { definition: 'notprepared' },
  { changes: [{ area: 'SQL', name: 'sql', before: null, after: 'select secret' }] },
  { mappingChecks: [{ targetColumn: 'amount', mappingPresent: true, transformPresent: false, reasons: ['AUTO_PUBLISH'] }] },
  { savedColumnCount: 101 }])('rejects mismatched or invalid prepared evidence %j', (overrides) => {
  expect(matchesStructureReview({ ...context, ...overrides }, '42', '7', 3)).toBe(false);
});
