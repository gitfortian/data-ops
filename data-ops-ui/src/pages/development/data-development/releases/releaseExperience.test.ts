import {
  isReleaseMutableStatus,
  releaseNodeUrl,
  releaseRevisionState,
} from './releaseExperience';

describe('data-development release experience', () => {
  it('keeps the active revision pointer when a release is offline', () => {
    expect(
      releaseRevisionState({
        status: 'OFFLINE',
        currentRevisionNo: 3,
        latestRevisionNo: 5,
        hasNewerRevision: true,
      }),
    ).toEqual({
      activeRevisionNo: 3,
      latestPublishedRevisionNo: 5,
      serving: false,
      activeButNotServing: true,
      hasNewerPublishedRevision: true,
    });
  });

  it('separates online serving state from published revision history', () => {
    expect(
      releaseRevisionState({
        status: 'ONLINE',
        currentRevisionNo: 5,
        latestRevisionNo: 5,
        hasNewerRevision: false,
      }),
    ).toMatchObject({
      activeRevisionNo: 5,
      serving: true,
      activeButNotServing: false,
      hasNewerPublishedRevision: false,
    });
    expect(isReleaseMutableStatus('DISABLED')).toBe(false);
  });

  it('returns release users to the exact development node', () => {
    expect(releaseNodeUrl('node / 42')).toBe(
      '/data-development?nodeId=node%20%2F%2042',
    );
  });
});
