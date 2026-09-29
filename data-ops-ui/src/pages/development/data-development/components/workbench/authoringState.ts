export type AuthoringSaveState = 'unsaved' | 'saving' | 'saved';
export type AuthoringPublishState = 'publishing' | 'pending' | 'clean' | 'unknown';

export interface AuthoringStateInput {
  draftRevision?: number;
  dirty?: boolean;
  saving?: boolean;
  publishing?: boolean;
  pendingPublish?: boolean;
}

export interface AuthoringState {
  draftRevision?: number;
  saveState: AuthoringSaveState;
  publishState: AuthoringPublishState;
}

export const deriveAuthoringState = ({
  draftRevision,
  dirty = false,
  saving = false,
  publishing = false,
  pendingPublish,
}: AuthoringStateInput): AuthoringState => ({
  draftRevision:
    typeof draftRevision === 'number' && draftRevision > 0
      ? draftRevision
      : undefined,
  saveState: saving ? 'saving' : dirty ? 'unsaved' : 'saved',
  publishState: publishing
    ? 'publishing'
    : pendingPublish === true
      ? 'pending'
      : pendingPublish === false
        ? 'clean'
        : 'unknown',
});
