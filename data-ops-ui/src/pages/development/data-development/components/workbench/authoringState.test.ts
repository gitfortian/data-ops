import { deriveAuthoringState } from './authoringState';

describe('authoring state', () => {
  it('shows an unsaved editor independently from published state', () => {
    expect(
      deriveAuthoringState({
        draftRevision: 4,
        dirty: true,
        pendingPublish: false,
      }),
    ).toEqual({
      draftRevision: 4,
      saveState: 'unsaved',
      publishState: 'clean',
    });
  });

  it('prioritizes active save and publish operations', () => {
    expect(
      deriveAuthoringState({
        draftRevision: 7,
        dirty: true,
        saving: true,
        publishing: true,
        pendingPublish: true,
      }),
    ).toEqual({
      draftRevision: 7,
      saveState: 'saving',
      publishState: 'publishing',
    });
  });

  it('distinguishes a saved draft with pending publish changes', () => {
    expect(
      deriveAuthoringState({
        draftRevision: 2,
        pendingPublish: true,
      }),
    ).toEqual({
      draftRevision: 2,
      saveState: 'saved',
      publishState: 'pending',
    });
  });

  it('does not expose draft zero as a persisted revision', () => {
    expect(deriveAuthoringState({ draftRevision: 0 })).toEqual({
      draftRevision: undefined,
      saveState: 'saved',
      publishState: 'unknown',
    });
  });
});
