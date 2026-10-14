import {
  physicalEvidencePath, parseLogicalPhysicalHint, logicalDraftNavigationPath,
} from './logicalPhysicalJourney';

const ids = { processId: 29, logicalId: 71, logicalVersionNo: 3, physicalId: 701 };

it('roundtrips only precise frozen logical and physical identities', () => {
  const path = physicalEvidencePath(ids);
  expect(path).toBe('/modeling/models/701?tab=logical&processId=29&logicalId=71&logicalVersion=3');
  const parsed = new URL(path!, 'http://localhost');
  expect(parseLogicalPhysicalHint(parsed.searchParams, 701)).toEqual(ids);
  expect(logicalDraftNavigationPath(29, 71)).toBe('/modeling/logical?processId=29&draftId=71&review=physical');
});

it('rejects malformed, repeated or fabricated version hints without replacing the target', () => {
  for (const search of [
    'processId=29&logicalId=71&logicalVersion=0',
    'processId=29&logicalId=71&logicalVersion=-1',
    'processId=29&logicalId=71&logicalVersion=1.5',
    'processId=29&logicalId=71&logicalVersion=3&logicalVersion=4',
    'processId=29&logicalId=71',
    'processId=29&logicalId=71&logicalVersion=2147483648',
    'processId=29&logicalId=71&logicalVersion=3&processId=99',
  ]) expect(parseLogicalPhysicalHint(new URLSearchParams(search), 701)).toBeNull();
  expect(physicalEvidencePath({ ...ids, logicalId: 0 })).toBeNull();
  expect(physicalEvidencePath({ ...ids, processId: 2147483648 })).toBeNull();
  expect(logicalDraftNavigationPath(0, 71)).toBeNull();
});
