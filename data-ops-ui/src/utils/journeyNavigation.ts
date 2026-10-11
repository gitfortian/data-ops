/**
 * F-040 navigation-only context. A process ID in the URL is never an
 * authorization token or proof that a logical/physical model belongs to it.
 * Destination pages must resolve it using their existing project-scoped owners.
 */
export const parseJourneyProcessId = (raw: string | null): number | undefined => {
  if (!raw || !/^[1-9][0-9]*$/.test(raw)) return undefined;
  const value = Number(raw);
  return Number.isSafeInteger(value) && value > 0 ? value : undefined;
};

export const processToLogical = (processId: number): string =>
  `/modeling/logical?processId=${requirePositiveId(processId)}`;

export const processToPhysical = (processId: number): string =>
  `/modeling?processId=${requirePositiveId(processId)}`;

/** Navigate to one owner record; its destination must re-verify both IDs. */
export const processToLogicalGovernance = (processId: number, draftId: number): string =>
  `/modeling/logical?processId=${requirePositiveId(processId)}&draftId=${requirePositiveId(draftId)}&review=semantic`;

export const processToPhysicalGovernance = (processId: number, modelId: number): string =>
  `/modeling/models/${requirePositiveId(modelId)}?processId=${requirePositiveId(processId)}&review=standard-coverage`;

export const processToSemantic = (processId: number): string =>
  `/semantic/processes/${requirePositiveId(processId)}/edit`;

const requirePositiveId = (id: number): number => {
  if (!Number.isSafeInteger(id) || id <= 0) throw new Error('无效的业务过程身份');
  return id;
};

/** Restrict navigated logical selections to the resolved business process. */
export const logicalDraftsForProcess = <T extends { processId: number }>(
  drafts: T[], processId?: number,
): T[] => processId === undefined
    ? drafts
    : drafts.filter((draft) => draft.processId === processId);

/** Explicitly scoped navigation must never fall through to an unrelated first draft. */
export const chooseJourneyLogicalModel = <T extends { id: number; processId: number }>(
  drafts: T[], requestedProcessId?: number, preferredId?: number,
): number | undefined => {
  const eligible = logicalDraftsForProcess(drafts, requestedProcessId);
  if (preferredId !== undefined && eligible.some((draft) => draft.id === preferredId)) {
    return preferredId;
  }
  return eligible[0]?.id;
};


/** Owner re-read required: never silently use a same-name or first available draft. */
export const exactJourneyLogicalDraft = <T extends { id: number; processId: number }>(
  drafts: T[], processId: number, draftId: number,
): T | undefined => Number.isSafeInteger(processId) && processId > 0
    && Number.isSafeInteger(draftId) && draftId > 0
    ? drafts.find((draft) => draft.id === draftId && draft.processId === processId)
    : undefined;

/** Use an actual version returned by the Modeling owner, not a guessed revision. */
export const latestJourneyFrozenVersion = (versions: { versionNo: number }[]): number | undefined =>
  versions.filter((item) => Number.isSafeInteger(item.versionNo) && item.versionNo > 0)
    .reduce<number | undefined>((latest, item) => latest === undefined
      ? item.versionNo : Math.max(latest, item.versionNo), undefined);
