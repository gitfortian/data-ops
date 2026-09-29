import { toSecurityProjectBrief } from './projects';

describe('toSecurityProjectBrief', () => {
  it('isolates the selectable identity from management fields', () => {
    expect(
      toSecurityProjectBrief({
        id: 7,
        projectCode: 'SEC',
        projectName: 'Security',
        owners: [],
        members: [],
        memberCount: 2,
        status: 'ENABLED',
      }),
    ).toEqual({ id: 7, projectCode: 'SEC', projectName: 'Security' });
  });
});
